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
 * 监听所有收到的短信，判断发送者是否在白名单中，并解析指令
 */
class SmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "android.provider.Telephony.SMS_RECEIVED") return

        val bundle: Bundle? = intent.extras ?: return
        val pdus = bundle.get("pdus") as? Array<*> ?: return

        // 解析所有短信片段
        val messages = mutableListOf<SmsMessage>()
        for (pdu in pdus) {
            val format = bundle.getString("format")
            val message = SmsMessage.createFromPdu(pdu as ByteArray, format)
            message?.let { messages.add(it) }
        }

        if (messages.isEmpty()) return

        // 取第一条短信的发送号码和内容
        val firstMsg = messages.first()
        val senderNumber = firstMsg.displayOriginatingAddress ?: return
        val messageBody = firstMsg.messageBody ?: return

        Log.d(TAG, "收到短信: from=$senderNumber, body=$messageBody")

        // 将短信处理交给 Service（Service 里做数据库查询和授权检查）
        val serviceIntent = Intent(context, SmsForwardService::class.java).apply {
            action = SmsForwardService.ACTION_RECEIVED_SMS
            putExtra(SmsForwardService.EXTRA_SENDER, senderNumber)
            putExtra(SmsForwardService.EXTRA_MESSAGE, messageBody)
        }

        try {
            context.startForegroundService(serviceIntent)
        } catch (e: Exception) {
            Log.e(TAG, "启动 Service 失败", e)
        }
    }
}