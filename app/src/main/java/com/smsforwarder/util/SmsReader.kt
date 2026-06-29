package com.smsforwarder.util

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.Telephony

/**
 * 读取本机指定号码的短信（仅新增短信）
 */
object SmsReader {

    /**
     * 查询指定号码的新短信（时间戳之后）
     */
    fun readNewSms(
        context: Context,
        phoneNumbers: List<String>,
        sinceTimestamp: Long
    ): List<SmsMessage> {
        if (phoneNumbers.isEmpty()) return emptyList()

        val uri: Uri = Telephony.Sms.Inbox.CONTENT_URI
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.READ
        )

        // 使用 REPLACE 去掉号码中的特殊字符后精确匹配
        val cleanAddress = "REPLACE(REPLACE(REPLACE(${Telephony.Sms.ADDRESS}, ' ', ''), '-', ''), '(', '')"
        val phoneConditions = phoneNumbers.joinToString(" OR ") {
            "$cleanAddress = '${it.replace(Regex("[\\s\\-()]"), "")}'"
        }
        val selection = "($phoneConditions) AND ${Telephony.Sms.DATE} > $sinceTimestamp"
        val sortOrder = "${Telephony.Sms.DATE} ASC"

        val results = mutableListOf<SmsMessage>()

        try {
            val cursor: Cursor? = context.contentResolver.query(
                uri, projection, selection, null, sortOrder
            )

            cursor?.use { c ->
                val idIndex = c.getColumnIndex(Telephony.Sms._ID)
                val addrIndex = c.getColumnIndex(Telephony.Sms.ADDRESS)
                val bodyIndex = c.getColumnIndex(Telephony.Sms.BODY)
                val dateIndex = c.getColumnIndex(Telephony.Sms.DATE)
                val readIndex = c.getColumnIndex(Telephony.Sms.READ)

                while (c.moveToNext()) {
                    val id = if (idIndex >= 0) c.getLong(idIndex) else 0L
                    val address = if (addrIndex >= 0) c.getString(addrIndex) ?: "" else ""
                    val body = if (bodyIndex >= 0) c.getString(bodyIndex) ?: "" else ""
                    val date = if (dateIndex >= 0) c.getLong(dateIndex) else 0L
                    val read = if (readIndex >= 0) c.getInt(readIndex) else 0

                    results.add(
                        SmsMessage(
                            id = id,
                            address = normalizePhoneNumber(address),
                            body = body,
                            date = date,
                            isRead = read == 1
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return results
    }

    private fun normalizePhoneNumber(phone: String): String {
        return phone.replace(Regex("[\\s\\-()]"), "")
    }

    data class SmsMessage(
        val id: Long,
        val address: String,
        val body: String,
        val date: Long,
        val isRead: Boolean
    )
}