package com.smsforwarder.util

import android.content.Context
import android.telephony.SmsManager

/**
 * 发送短信工具
 */
object SmsSender {
    fun sendSms(context: Context, phoneNumber: String, message: String): Boolean {
        return try {
            val smsManager = SmsManager.getDefault()
            // 如果短信过长，拆分发送
            val parts = smsManager.divideMessage(message)
            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(phoneNumber, null, message, null, null)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}