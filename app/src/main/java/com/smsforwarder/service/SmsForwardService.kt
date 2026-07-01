package com.smsforwarder.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.smsforwarder.SmsForwarderApp
import com.smsforwarder.data.model.ForwardLogEntity
import com.smsforwarder.data.model.WhitelistEntity
import com.smsforwarder.ui.MainActivity
import com.smsforwarder.util.SmsReader
import com.smsforwarder.util.SmsSender
import kotlinx.coroutines.*

/**
 * 短信转发前台服务
 *
 * 核心逻辑：
 * 1. 接收 SmsReceiver 传来的短信
 * 2. 检查发送者是否在白名单中
 * 3. 解析指令（申请转发 / 停止转发）
 * 4. 授权期内监听被监控号码的新增短信，实时转发
 *
 * 注：processIncomingSms 静态方法可在 Service 未启动时直接调用
 * （解决 Android 14+ 后台无法启动前台服务的限制）
 */
class SmsForwardService : Service() {

    companion object {
        private const val TAG = "SmsForwardService"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_RECEIVED_SMS = "com.smsforwarder.action.RECEIVED_SMS"
        const val EXTRA_SENDER = "extra_sender"
        const val EXTRA_MESSAGE = "extra_message"

        const val ACTION_STOP_SERVICE = "com.smsforwarder.action.STOP_SERVICE"

        // 全局处理协程作用域（供静态方法使用）
        private val globalScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /**
         * 规范化手机号码：去除 +86、空格、横线、括号等前缀和符号
         */
        private fun normalizePhone(phone: String): String {
            return phone.replace(Regex("[\\s\\-()]"), "")
                .replace(Regex("^\\+86"), "")
                .replace(Regex("^0086"), "")
                .replace(Regex("^86"), "")
        }

        /**
         * 处理收到的短信（可在 Service 未启动时直接调用）
         * 包含安全初始化检查和异常捕获，防止 HyperOS 等厂商 ROM 杀进程后
         * Application 未完全初始化时抛出异常导致短信丢失
         */
        fun processIncomingSms(context: Context, sender: String, body: String) {
            globalScope.launch {
                try {
                    val app = context.applicationContext as? SmsForwarderApp
                    if (app == null || !::repository.isInitialized) {
                        Log.w(TAG, "Application 未初始化，短信跳过: from=$sender")
                        return@launch
                    }
                    val repo = app.repository
                    Log.d(TAG, "直接处理短信: from=$sender, body=$body")
                    handleIncomingSmsImpl(repo, sender, body, context)
                } catch (e: Exception) {
                    Log.e(TAG, "处理短信异常: from=$sender", e)
                }
            }
        }

        /**
         * 核心处理逻辑（静态方法 + 实例方法共用）
         */
        private suspend fun handleIncomingSmsImpl(
            repo: com.smsforwarder.data.repository.AppRepository,
            sender: String,
            body: String,
            context: Context? = null
        ) {
            // 规范化号码：+86159... -> 159...
            val normalizedSender = normalizePhone(sender)
            Log.d(TAG, "处理短信: from=$sender (normalized=$normalizedSender), body=$body")

            // 1. 检查发送者是否在白名单中（用规范化号码匹配）
            val whitelistEntry = repo.getWhitelistByPhone(normalizedSender)
            if (whitelistEntry == null) {
                Log.d(TAG, "发送者 $sender 不在白名单中，忽略")
                repo.addForwardLog(
                    ForwardLogEntity(
                        whitelistPhone = normalizedSender,
                        sourceNumber = "",
                        content = "收到非白名单号码短信，已忽略",
                        forwardTime = System.currentTimeMillis(),
                        result = ""
                    )
                )
                return
            }

            // 2. 读取指令配置
            val forwardCmd = repo.getSetting(SmsForwarderApp.KEY_COMMAND_FORWARD)
                ?: SmsForwarderApp.DEFAULT_COMMAND_FORWARD
            val stopCmd = repo.getSetting(SmsForwarderApp.KEY_COMMAND_STOP)
                ?: SmsForwarderApp.DEFAULT_COMMAND_STOP

            // 3. 匹配指令
            when {
                body.trim() == forwardCmd -> {
                    repo.addForwardLog(
                        ForwardLogEntity(
                            whitelistPhone = normalizedSender,
                            sourceNumber = "",
                            content = "收到申请转发指令",
                            forwardTime = System.currentTimeMillis(),
                            result = ""
                        )
                    )
                    handleForwardCommandImpl(repo, whitelistEntry, normalizedSender, context)
                }
                body.trim() == stopCmd -> {
                    repo.addForwardLog(
                        ForwardLogEntity(
                            whitelistPhone = normalizedSender,
                            sourceNumber = "",
                            content = "收到停止转发指令",
                            forwardTime = System.currentTimeMillis(),
                            result = ""
                        )
                    )
                    handleStopCommandImpl(repo, whitelistEntry, normalizedSender, context)
                }
                else -> {
                    Log.d(TAG, "未知指令: $body")
                    repo.addForwardLog(
                        ForwardLogEntity(
                            whitelistPhone = normalizedSender,
                            sourceNumber = "",
                            content = "收到未知指令: $body",
                            forwardTime = System.currentTimeMillis(),
                            result = ""
                        )
                    )
                }
            }
        }

        private suspend fun handleForwardCommandImpl(
            repo: com.smsforwarder.data.repository.AppRepository,
            entry: WhitelistEntity,
            sender: String,
            context: Context?
        ) {
            Log.d(TAG, "白名单号码 $sender 申请转发")

            val durationStr = repo.getSetting(SmsForwarderApp.KEY_AUTH_DURATION)
                ?: SmsForwarderApp.DEFAULT_AUTH_DURATION.toString()
            val durationMinutes = durationStr.toLongOrNull()
                ?: SmsForwarderApp.DEFAULT_AUTH_DURATION
            val expireTime = System.currentTimeMillis() + durationMinutes * 60 * 1000

            repo.updateAuth(entry.id, 1, expireTime)

            val confirmMsg = "授权成功，有效期${durationMinutes}分钟"

            repo.addForwardLog(
                ForwardLogEntity(
                    whitelistPhone = sender,
                    sourceNumber = "",
                    content = confirmMsg,
                    forwardTime = System.currentTimeMillis(),
                    result = ""
                )
            )

            // 发送确认短信
            if (context != null) {
                SmsSender.sendSms(context, sender, confirmMsg)
            }

            Log.d(TAG, "白名单 $sender 已获得授权，有效期至 $expireTime")
        }

        private suspend fun handleStopCommandImpl(
            repo: com.smsforwarder.data.repository.AppRepository,
            entry: WhitelistEntity,
            sender: String,
            context: Context?
        ) {
            repo.updateAuth(entry.id, 0, 0)

            val confirmMsg = "转发授权已停止"

            repo.addForwardLog(
                ForwardLogEntity(
                    whitelistPhone = sender,
                    sourceNumber = "",
                    content = confirmMsg,
                    forwardTime = System.currentTimeMillis(),
                    result = ""
                )
            )

            if (context != null) {
                SmsSender.sendSms(context, sender, confirmMsg)
            }

            Log.d(TAG, "白名单 $sender 已停止授权")
        }
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
        serviceScope.launch {
            addLog("系统", "服务已启动")
        }
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
                serviceScope.launch {
                    addLog("系统", "服务停止")
                }
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

    private suspend fun addLog(whitelistPhone: String, content: String) {
        repository.addForwardLog(
            ForwardLogEntity(
                whitelistPhone = whitelistPhone,
                sourceNumber = "",
                content = content,
                forwardTime = System.currentTimeMillis(),
                result = ""
            )
        )
    }

    /**
     * 实例方法：处理收到的短信
     */
    private suspend fun handleIncomingSms(sender: String, body: String) {
        val normalized = normalizePhone(sender)
        handleIncomingSmsImpl(repository, sender, body, this)
    }

    /**
     * 实例方法：处理"申请转发"指令
     */
    private suspend fun handleForwardCommand(entry: WhitelistEntity, sender: String) {
        handleForwardCommandImpl(repository, entry, sender, this)

        // 额外逻辑：启动定时轮询（只有 Service 实例运行时才有）
        val durationStr = repository.getSetting(SmsForwarderApp.KEY_AUTH_DURATION)
            ?: SmsForwarderApp.DEFAULT_AUTH_DURATION.toString()
        val durationMinutes = durationStr.toLongOrNull()
            ?: SmsForwarderApp.DEFAULT_AUTH_DURATION

        // 初始化此号码的最后检查时间
        authTimestamps[sender] = System.currentTimeMillis() - 30000

        // 启动定时检查
        startPeriodicCheck()

        updateNotification("已授权 $sender，有效期 ${durationMinutes}分钟")
    }

    /**
     * 实例方法：处理"停止转发"指令
     */
    private suspend fun handleStopCommand(entry: WhitelistEntity, sender: String) {
        handleStopCommandImpl(repository, entry, sender, this)
        authTimestamps.remove(sender)

        if (authTimestamps.isEmpty()) {
            checkJob?.cancel()
            checkJob = null
        }

        updateNotification("已停止 $sender 的转发授权")
    }

    private fun startPeriodicCheck() {
        if (checkJob?.isActive == true) return

        checkJob = serviceScope.launch {
            while (isActive) {
                try {
                    checkAndForwardNewSms()
                } catch (e: Exception) {
                    Log.e(TAG, "检查短信异常", e)
                }
                delay(3000)
            }
        }
    }

    private suspend fun checkAndForwardNewSms() {
        val now = System.currentTimeMillis()

        val authorizedList = repository.getAuthorizedNumbers()
        if (authorizedList.isEmpty()) {
            authTimestamps.clear()
            checkJob?.cancel()
            checkJob = null
            updateNotification("服务运行中（无活跃授权）")
            return
        }

        val monitoredNumbers = repository.getAllMonitoredPhoneNumbers()
        if (monitoredNumbers.isEmpty()) {
            Log.d(TAG, "没有配置被监控号码")
            return
        }

        for (authEntry in authorizedList) {
            val whitelistPhone = authEntry.phoneNumber
            val sinceTime = authTimestamps[whitelistPhone]
                ?: (now - 30000)

            val newMessages = SmsReader.readNewSms(
                this, monitoredNumbers, sinceTime
            )

            if (newMessages.isNotEmpty()) {
                Log.d(TAG, "发现 ${newMessages.size} 条新短信，转发给 $whitelistPhone")

                for (msg in newMessages) {
                    val forwardContent = "【转发信息】${msg.address}：${msg.body}"
                    val success = SmsSender.sendSms(this, whitelistPhone, forwardContent)

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

            authTimestamps[whitelistPhone] = now
        }

        updateNotification("转发服务运行中（${authorizedList.size}个授权）")
    }

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