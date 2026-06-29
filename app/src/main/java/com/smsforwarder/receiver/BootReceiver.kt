package com.smsforwarder.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.smsforwarder.service.SmsForwardService

/**
 * 开机自启动广播接收器
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val serviceIntent = Intent(context, SmsForwardService::class.java)
            context.startForegroundService(serviceIntent)
        }
    }
}