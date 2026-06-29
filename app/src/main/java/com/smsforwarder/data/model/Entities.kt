package com.smsforwarder.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 白名单号码
 */
@Entity(tableName = "whitelist")
data class WhitelistEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val phoneNumber: String,       // 白名单手机号码
    val nickname: String = "",     // 备注名
    val authStatus: Int = 0,       // 0=未授权 1=已授权
    val authExpireTime: Long = 0,  // 授权过期时间戳(毫秒)
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * 被监控的短信号码（即要读取其短信的号码）
 */
@Entity(tableName = "monitored_numbers")
data class MonitoredNumberEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val phoneNumber: String,       // 被监控的号码
    val description: String = "",  // 备注说明
    val isDefault: Boolean = false // 是否为默认号码
)

/**
 * 转发日志
 */
@Entity(tableName = "forward_log")
data class ForwardLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val whitelistPhone: String,    // 触发转发的白名单号码
    val sourceNumber: String,      // 被读取的短信号码
    val content: String,           // 转发内容
    val forwardTime: Long = System.currentTimeMillis(),
    val result: String = ""        // 成功/失败
)

/**
 * 键值对设置
 */
@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey
    val key: String,
    val value: String
)