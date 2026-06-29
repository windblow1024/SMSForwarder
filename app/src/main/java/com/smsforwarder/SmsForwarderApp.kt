package com.smsforwarder

import android.Manifest
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.smsforwarder.data.db.AppDatabase
import com.smsforwarder.data.model.MonitoredNumberEntity
import com.smsforwarder.data.repository.AppRepository
import com.smsforwarder.receiver.SmsReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SmsForwarderApp : Application() {

    lateinit var database: AppDatabase
        private set
    lateinit var repository: AppRepository
        private set

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // 动态注册的短信接收器（比静态注册更可靠）
    private val smsReceiver = SmsReceiver()

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = AppDatabase.getInstance(this)
        repository = AppRepository(database)

        createNotificationChannel()
        initDefaultSettings()

        // 动态注册短信接收广播（解决 Android 14+ 对静态注册广播的限制）
        registerSmsReceiver()
    }

    /**
     * 动态注册短信接收广播
     * 相比 AndroidManifest 静态注册，动态注册在 Android 14+ 更可靠
     */
    private fun registerSmsReceiver() {
        try {
            val hasSmsPermission = ContextCompat.checkSelfPermission(
                this, Manifest.permission.RECEIVE_SMS
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasSmsPermission) return

            val filter = IntentFilter("android.provider.Telephony.SMS_RECEIVED")
            filter.priority = IntentFilter.SYSTEM_HIGH_PRIORITY

            // RECEIVER_EXPORTED 需要 API 33+，低版本用 null
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Context.RECEIVER_EXPORTED
            } else {
                0
            }
            registerReceiver(smsReceiver, filter, flags)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_SERVICE,
                "短信转发服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "短信转发器后台服务状态"
                setShowBadge(false)
            }

            val logChannel = NotificationChannel(
                CHANNEL_FORWARD,
                "转发通知",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "短信转发结果通知"
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
            manager.createNotificationChannel(logChannel)
        }
    }

    private fun initDefaultSettings() {
        appScope.launch {
            if (repository.getSetting(SmsForwarderApp.KEY_COMMAND_FORWARD) == null) {
                repository.setSetting(SmsForwarderApp.KEY_COMMAND_FORWARD, DEFAULT_COMMAND_FORWARD)
            }
            if (repository.getSetting(SmsForwarderApp.KEY_COMMAND_STOP) == null) {
                repository.setSetting(SmsForwarderApp.KEY_COMMAND_STOP, DEFAULT_COMMAND_STOP)
            }
            if (repository.getSetting(SmsForwarderApp.KEY_AUTH_DURATION) == null) {
                repository.setSetting(SmsForwarderApp.KEY_AUTH_DURATION, DEFAULT_AUTH_DURATION.toString())
            }

            val monitoredNumbers = repository.getAllMonitoredNumberList()
            if (monitoredNumbers.isEmpty()) {
                repository.addMonitoredNumber(
                    MonitoredNumberEntity(
                        phoneNumber = "10086202",
                        description = "默认监控号码",
                        isDefault = true
                    )
                )
            }
        }
    }

    companion object {
        const val CHANNEL_SERVICE = "sms_forward_service"
        const val CHANNEL_FORWARD = "sms_forward_notify"

        const val KEY_COMMAND_FORWARD = "command_forward"
        const val KEY_COMMAND_STOP = "command_stop"
        const val KEY_AUTH_DURATION = "auth_duration_minutes"
        const val DEFAULT_COMMAND_FORWARD = "申请转发"
        const val DEFAULT_COMMAND_STOP = "停止转发"
        const val DEFAULT_AUTH_DURATION = 30L

        const val PREF_NAME = "sms_forwarder_prefs"

        @Volatile
        private var instance: SmsForwarderApp? = null

        fun getInstance(): SmsForwarderApp =
            instance ?: throw IllegalStateException("SmsForwarderApp not initialized")
    }
}