package com.smsforwarder.data.repository

import com.smsforwarder.data.db.AppDatabase
import com.smsforwarder.data.model.*
import kotlinx.coroutines.flow.Flow

class AppRepository(private val db: AppDatabase) {

    // ===== 白名单 =====

    fun getAllWhitelist(): Flow<List<WhitelistEntity>> = db.whitelistDao().getAll()

    suspend fun getWhitelistByPhone(phone: String): WhitelistEntity? =
        db.whitelistDao().getByPhone(phone)

    suspend fun addWhitelist(entity: WhitelistEntity): Long =
        db.whitelistDao().insert(entity)

    suspend fun updateWhitelist(entity: WhitelistEntity) =
        db.whitelistDao().update(entity)

    suspend fun deleteWhitelist(id: Long) =
        db.whitelistDao().deleteById(id)

    suspend fun updateAuth(id: Long, status: Int, expireTime: Long) =
        db.whitelistDao().updateAuth(id, status, expireTime)

    suspend fun getAuthorizedNumbers(): List<WhitelistEntity> =
        db.whitelistDao().getAuthorized()

    suspend fun expireAllAuth() =
        db.whitelistDao().expireAll()

    // ===== 被监控号码 =====

    fun getAllMonitoredNumbers(): Flow<List<MonitoredNumberEntity>> =
        db.monitoredNumberDao().getAll()

    suspend fun getAllMonitoredNumberList(): List<MonitoredNumberEntity> =
        db.monitoredNumberDao().getAllList()

    suspend fun getAllMonitoredPhoneNumbers(): List<String> =
        db.monitoredNumberDao().getAllPhoneNumbers()

    suspend fun addMonitoredNumber(entity: MonitoredNumberEntity): Long =
        db.monitoredNumberDao().insert(entity)

    suspend fun updateMonitoredNumber(entity: MonitoredNumberEntity) =
        db.monitoredNumberDao().update(entity)

    suspend fun deleteMonitoredNumber(id: Long) =
        db.monitoredNumberDao().deleteById(id)

    // ===== 转发日志 =====

    fun getAllForwardLogs(): Flow<List<ForwardLogEntity>> =
        db.forwardLogDao().getAll()

    fun getRecentForwardLogs(limit: Int = 100): Flow<List<ForwardLogEntity>> =
        db.forwardLogDao().getRecent(limit)

    suspend fun addForwardLog(entity: ForwardLogEntity): Long =
        db.forwardLogDao().insert(entity)

    suspend fun clearForwardLogs() =
        db.forwardLogDao().clearAll()

    // ===== 设置 =====

    suspend fun getSetting(key: String): String? =
        db.settingsDao().getValue(key)

    suspend fun setSetting(key: String, value: String) =
        db.settingsDao().setValue(SettingsEntity(key, value))

    suspend fun removeSetting(key: String) =
        db.settingsDao().deleteByKey(key)
}