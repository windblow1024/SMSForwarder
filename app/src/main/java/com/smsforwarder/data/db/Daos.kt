package com.smsforwarder.data.db

import androidx.room.*
import com.smsforwarder.data.model.*
import kotlinx.coroutines.flow.Flow

// ===== 白名单 DAO =====

@Dao
interface WhitelistDao {
    @Query("SELECT * FROM whitelist ORDER BY createdAt DESC")
    fun getAll(): Flow<List<WhitelistEntity>>

    @Query("SELECT * FROM whitelist WHERE phoneNumber = :phone")
    suspend fun getByPhone(phone: String): WhitelistEntity?

    @Query("SELECT * FROM whitelist WHERE phoneNumber = :phone")
    fun getByPhoneFlow(phone: String): Flow<WhitelistEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: WhitelistEntity): Long

    @Update
    suspend fun update(entity: WhitelistEntity)

    @Delete
    suspend fun delete(entity: WhitelistEntity)

    @Query("DELETE FROM whitelist WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE whitelist SET authStatus = :status, authExpireTime = :expireTime, updatedAt = :now WHERE id = :id")
    suspend fun updateAuth(id: Long, status: Int, expireTime: Long, now: Long = System.currentTimeMillis())

    @Query("SELECT * FROM whitelist WHERE authStatus = 1 AND authExpireTime > :now")
    suspend fun getAuthorized(now: Long = System.currentTimeMillis()): List<WhitelistEntity>

    @Query("UPDATE whitelist SET authStatus = 0, authExpireTime = 0, updatedAt = :now WHERE authStatus = 1 AND authExpireTime <= :now")
    suspend fun expireAll(now: Long = System.currentTimeMillis())
}

// ===== 被监控号码 DAO =====

@Dao
interface MonitoredNumberDao {
    @Query("SELECT * FROM monitored_numbers ORDER BY isDefault DESC, id ASC")
    fun getAll(): Flow<List<MonitoredNumberEntity>>

    @Query("SELECT * FROM monitored_numbers ORDER BY isDefault DESC, id ASC")
    suspend fun getAllList(): List<MonitoredNumberEntity>

    @Query("SELECT phoneNumber FROM monitored_numbers")
    suspend fun getAllPhoneNumbers(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: MonitoredNumberEntity): Long

    @Update
    suspend fun update(entity: MonitoredNumberEntity)

    @Delete
    suspend fun delete(entity: MonitoredNumberEntity)

    @Query("DELETE FROM monitored_numbers WHERE id = :id")
    suspend fun deleteById(id: Long)
}

// ===== 转发日志 DAO =====

@Dao
interface ForwardLogDao {
    @Query("SELECT * FROM forward_log ORDER BY forwardTime DESC")
    fun getAll(): Flow<List<ForwardLogEntity>>

    @Query("SELECT * FROM forward_log ORDER BY forwardTime DESC LIMIT :limit")
    fun getRecent(limit: Int): Flow<List<ForwardLogEntity>>

    @Insert
    suspend fun insert(entity: ForwardLogEntity): Long

    @Query("DELETE FROM forward_log")
    suspend fun clearAll()
}

// ===== 设置 DAO =====

@Dao
interface SettingsDao {
    @Query("SELECT value FROM settings WHERE `key` = :key")
    suspend fun getValue(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setValue(entity: SettingsEntity)

    @Query("DELETE FROM settings WHERE `key` = :key")
    suspend fun deleteByKey(key: String)
}