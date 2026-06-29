package com.smsforwarder.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.smsforwarder.R
import com.smsforwarder.SmsForwarderApp
import com.smsforwarder.data.model.ForwardLogEntity
import com.smsforwarder.data.model.WhitelistEntity
import com.smsforwarder.ui.MainActivity
import com.smsforwarder.util.SmsReader
import com.smsforwarder.util.SmsSender
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

/**
 * 短信转发前台服务
 *
 * 核心逻辑：
 * 1. 接收 SmsReceiver 传来的短信
 * 2. 检查发送者是否在白名单中
 * 3. 解析指令（申请转发 / 停止转发）
 * 4. 授权期内监听被监控号码的新增短信，实时转发
 */
class SmsForwardService : Service() {

    companion object {
        private const val TAG = "SmsForwardService"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_RECEIVED_SMS = "com.smsforwarder.action.RECEIVED_SMS"
        const val EXTRA_SENDER = "extra_sender"
        const val EXTRA_MESSAGE = "extra_message"

        const val ACTION_START_SERVICE = "com.smsforwarder.action.START_SERVICE"
        const val ACTION_STOP_SERVICE = "com.smsforwarder.action.STOP_SERVICE"
    }

    private val app get() = application as SmsForwarderApp
    private val repository get() = app.repository
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // 已授权白名单的号码 -> 其最后检查时间戳
    private val authTimestamps = mutableMapOf<String, Long>()

    // 定时检查任务
    private var checkJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "服务创建")
        startForeground(NOTIFICATION_ID, createNotification("服务已启动"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_RECEIVED_SMS -> {
                val sender = intent.getStringExtra(EXTRA_SENDER) ?: return START_STICKY
                val message = intent.getStringExtra(EXTRA_MESSAGE) ?: return START_STICKY
                serviceScope.launch {
                    handleIncomingSms(sender, message)
                }
            }
            ACTION_STOP_SERVICE -> {
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        checkJob?.cancel()
        serviceScope.cancel()
        Log.d(TAG, "服务销毁")
        super.onDestroy()
    }

    /**
     * 处理收到的短信
     */
    private suspend fun handleIncomingSms(sender: String, body: String) {
        Log.d(TAG, "处理短信: from=$sender, body=$body")

        // 1. 检查发送者是否在白名单中
        val whitelistEntry = repository.getWhitelistByPhone(sender)
        if (whitelistEntry == null) {
            Log.d(TAG, "发送者 $sender 不在白名单中，忽略")
            return
        }

        // 2. 读取指令配置
        val forwardCmd = repository.getSetting(SmsForwarderApp.KEY_COMMAND_FORWARD)
            ?: SmsForwarderApp.DEFAULT_COMMAND_FORWARD
        val stopCmd = repository.getSetting(SmsForwarderApp.KEY_COMMAND_STOP)
            ?: SmsForwarderApp.DEFAULT_COMMAND_STOP

        // 3. 匹配指令
        when {
            body.trim() == forwardCmd -> {
                handleForwardCommand(whitelistEntry, sender)
            }
            body.trim() == stopCmd -> {
                handleStopCommand(whitelistEntry, sender)
            }
            else -> {
                Log.d(TAG, "未知指令: $body")
            }
        }
    }

    /**
     * 处理"申请转发"指令
     */
    private suspend fun handleForwardCommand(entry: WhitelistEntity, sender: String) {
        Log.d(TAG, "白名单号码 $sender 申请转发")

        // 读取有效期配置
        val durationStr = repository.getSetting(SmsForwarderApp.KEY_AUTH_DURATION)
            ?: SmsForwarderApp.DEFAULT_AUTH_DURATION.toString()
        val durationMinutes = durationStr.toLongOrNull() ?: SmsForwarderApp.DEFAULT_AUTH_DURATION
        val expireTime = System.currentTimeMillis() + durationMinutes * 60 * 1000

        // 更新授权状态
        repository.updateAuth(entry.id, 1, expireTime)

        // 初始化此号码的最后检查时间
        authTimestamps[sender] = System.currentTimeMillis()

        // 启动定时检查
        startPeriodicCheck()

        updateNotification("已授权 $sender，有效期 ${durationMinutes}分钟")
        Log.d(TAG, "白名单 $sender 已获得授权，有效期至 $expireTime")
    }

    /**
     * 处理"停止转发"指令
     */
    private suspend fun handleStopCommand(entry: WhitelistEntity, sender: String) {
        repository.updateAuth(entry.id, 0, 0)
        authTimestamps.remove(sender)
        Log.d(TAG, "白名单 $sender 已停止授权")

        // 如果没有任何授权了，停止检查
        if (authTimestamps.isEmpty()) {
            checkJob?.cancel()
            checkJob = null
        }

        updateNotification("已停止 $sender 的转发授权")
    }

    /**
     * 启动定时检查任务
     * 每隔 3 秒检查一次被监控号码是否有新短信
     */
    private fun startPeriodicCheck() {
        if (checkJob?.isActive == true) return

        checkJob = serviceScope.launch {
            while (isActive) {
                try {
                    checkAndForwardNewSms()
                } catch (e: Exception) {
                    Log.e(TAG, "检查短信异常", e)
                }
                delay(3000) // 每 3 秒检查一次
            }
        }
    }

    /**
     * 检查所有已授权白名单对应的被监控号码是否有新短信
     */
    private suspend fun checkAndForwardNewSms() {
        val now = System.currentTimeMillis()

        // 1. 获取所有在有效期内的授权号码
        val authorizedList = repository.getAuthorizedNumbers()
        if (authorizedList.isEmpty()) {
            // 没有有效授权，停止检查
            authTimestamps.clear()
            checkJob?.cancel()
            checkJob = null
            updateNotification("服务运行中（无活跃授权）")
            return
        }

        // 2. 获取所有被监控的号码
        val monitoredNumbers = repository.getAllMonitoredPhoneNumbers()
        if (monitoredNumbers.isEmpty()) {
            Log.d(TAG, "没有配置被监控号码")
            return
        }

        // 3. 对每个被授权号码，检查其监控号码的新短信
        for (authEntry in authorizedList) {
            val whitelistPhone = authEntry.phoneNumber

            // 获取此白名单号码的上次检查时间
            val sinceTime = authTimestamps[whitelistPhone] ?: (now - 5000) // 默认查最近5秒

            // 读取新短信
            val newMessages = SmsReader.readNewSms(
                this, monitoredNumbers, sinceTime
            )

            if (newMessages.isNotEmpty()) {
                Log.d(TAG, "发现 ${newMessages.size} 条新短信，转发给 $whitelistPhone")

                for (msg in newMessages) {
                    // 构建转发内容
                    val forwardContent = "【转发信息】${msg.address}：${msg.body}"

                    // 发送短信
                    val success = SmsSender.sendSms(this, whitelistPhone, forwardContent)

                    // 记录日志
                    repository.addForwardLog(
                        ForwardLogEntity(
                            whitelistPhone = whitelistPhone,
                            sourceNumber = msg.address,
                            content = forwardContent,
                            forwardTime = System.currentTimeMillis(),
                            result = if (success) "成功" else "失败"
                        )
                    )

                    if (success) {
                        Log.d(TAG, "已转发短信给 $whitelistPhone: $forwardContent")
                    } else {
                        Log.e(TAG, "转发短信给 $whitelistPhone 失败")
                    }
                }
            }

            // 更新此白名单号码的最后检查时间
            authTimestamps[whitelistPhone] = now
        }

        // 更新通知
        updateNotification("转发服务运行中（${authorizedList.size}个授权）")
    }

    /**
     * 更新前台通知
     */
    private fun updateNotification(text: String) {
        val notification = createNotification(text)
        val manager = getSystemService(android.app.NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, SmsForwarderApp.CHANNEL_SERVICE)
            .setContentTitle("短信转发器")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}