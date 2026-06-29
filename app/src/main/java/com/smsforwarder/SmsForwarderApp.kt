package com.smsforwarder

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.smsforwarder.data.db.AppDatabase
import com.smsforwarder.data.repository.AppRepository
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

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = AppDatabase.getInstance(this)
        repository = AppRepository(database)

        createNotificationChannel()
        initDefaultSettings()
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
            // 默认转发指令
            if (repository.getSetting(KEY_COMMAND_FORWARD) == null) {
                repository.setSetting(KEY_COMMAND_FORWARD, DEFAULT_COMMAND_FORWARD)
            }
            if (repository.getSetting(KEY_COMMAND_STOP) == null) {
                repository.setSetting(KEY_COMMAND_STOP, DEFAULT_COMMAND_STOP)
            }
            // 默认有效期（30分钟）
            if (repository.getSetting(KEY_AUTH_DURATION) == null) {
                repository.setSetting(KEY_AUTH_DURATION, DEFAULT_AUTH_DURATION.toString())
            }

            // 默认被监控号码：10086202
            val monitoredNumbers = repository.getAllMonitoredNumberList()
            if (monitoredNumbers.isEmpty()) {
                repository.addMonitoredNumber(
                    com.smsforwarder.data.model.MonitoredNumberEntity(
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