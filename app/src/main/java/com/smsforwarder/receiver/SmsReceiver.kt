package com.smsforwarder.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.telephony.SmsMessage
import android.util.Log
import com.smsforwarder.service.SmsForwardService

/**
 * 短信接收广播接收器
 *
 * 监听所有收到的短信，尝试通过 SmsForwardService 处理。
 * 如果 Service 未运行（Android 14+ 限制后台启动前台服务），
 * 则直接在本广播中调用 SmsForwardService 的静态方法处理。
 */
class SmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "android.provider.Telephony.SMS_RECEIVED") return

        val bundle: Bundle = intent.extras ?: return
        val pdus = bundle.get("pdus") as? Array<*> ?: return

        val messages = mutableListOf<SmsMessage>()
        for (pdu in pdus) {
            val format = bundle.getString("format")
            val message = SmsMessage.createFromPdu(pdu as ByteArray, format)
            message?.let { messages.add(it) }
        }

        if (messages.isEmpty()) return

        val firstMsg = messages.first()
        val senderNumber = firstMsg.displayOriginatingAddress ?: return
        val messageBody = firstMsg.messageBody ?: return

        Log.d(TAG, "收到短信: from=$senderNumber, body=$messageBody")

        // 优先尝试通过前台 Service 处理
        val serviceIntent = Intent(context, SmsForwardService::class.java).apply {
            action = SmsForwardService.ACTION_RECEIVED_SMS
            putExtra(SmsForwardService.EXTRA_SENDER, senderNumber)
            putExtra(SmsForwardService.EXTRA_MESSAGE, messageBody)
        }

        try {
            context.startForegroundService(serviceIntent)
        } catch (e: Exception) {
            Log.e(TAG, "startForegroundService 失败，直接处理", e)
            // Android 14+ 后台无法启动前台服务时，直接处理
            SmsForwardService.processIncomingSms(
                context, senderNumber, messageBody
            )
        }
    }
}