package com.smsforwarder.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smsforwarder.SmsForwarderApp
import com.smsforwarder.data.model.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as SmsForwarderApp).repository

    // 白名单列表
    val whitelist: StateFlow<List<WhitelistEntity>> = repository.getAllWhitelist()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 被监控号码列表
    val monitoredNumbers: StateFlow<List<MonitoredNumberEntity>> = repository.getAllMonitoredNumbers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 转发日志
    val forwardLogs: StateFlow<List<ForwardLogEntity>> = repository.getAllForwardLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 设置
    val commandForward = MutableStateFlow(SmsForwarderApp.DEFAULT_COMMAND_FORWARD)
    val commandStop = MutableStateFlow(SmsForwarderApp.DEFAULT_COMMAND_STOP)
    val authDuration = MutableStateFlow(SmsForwarderApp.DEFAULT_AUTH_DURATION.toString())

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            commandForward.value = repository.getSetting(SmsForwarderApp.KEY_COMMAND_FORWARD)
                ?: SmsForwarderApp.DEFAULT_COMMAND_FORWARD
            commandStop.value = repository.getSetting(SmsForwarderApp.KEY_COMMAND_STOP)
                ?: SmsForwarderApp.DEFAULT_COMMAND_STOP
            authDuration.value = repository.getSetting(SmsForwarderApp.KEY_AUTH_DURATION)
                ?: SmsForwarderApp.DEFAULT_AUTH_DURATION.toString()
        }
    }

    // ===== 白名单操作 =====

    fun addWhitelist(phone: String, nickname: String = "") {
        viewModelScope.launch {
            val existing = repository.getWhitelistByPhone(phone)
            if (existing != null) return@launch // 已存在
            repository.addWhitelist(
                WhitelistEntity(
                    phoneNumber = phone,
                    nickname = nickname
                )
            )
        }
    }

    fun updateWhitelist(id: Long, phone: String, nickname: String) {
        viewModelScope.launch {
            repository.updateWhitelist(
                WhitelistEntity(
                    id = id,
                    phoneNumber = phone,
                    nickname = nickname
                )
            )
        }
    }

    fun deleteWhitelist(id: Long) {
        viewModelScope.launch {
            repository.deleteWhitelist(id)
        }
    }

    // ===== 被监控号码操作 =====

    fun addMonitoredNumber(phone: String, description: String = "") {
        viewModelScope.launch {
            repository.addMonitoredNumber(
                MonitoredNumberEntity(
                    phoneNumber = phone,
                    description = description
                )
            )
        }
    }

    fun updateMonitoredNumber(id: Long, phone: String, description: String) {
        viewModelScope.launch {
            repository.updateMonitoredNumber(
                MonitoredNumberEntity(
                    id = id,
                    phoneNumber = phone,
                    description = description
                )
            )
        }
    }

    fun deleteMonitoredNumber(id: Long) {
        viewModelScope.launch {
            repository.deleteMonitoredNumber(id)
        }
    }

    // ===== 设置操作 =====

    fun saveCommandForward(cmd: String) {
        viewModelScope.launch {
            repository.setSetting(SmsForwarderApp.KEY_COMMAND_FORWARD, cmd)
            commandForward.value = cmd
        }
    }

    fun saveCommandStop(cmd: String) {
        viewModelScope.launch {
            repository.setSetting(SmsForwarderApp.KEY_COMMAND_STOP, cmd)
            commandStop.value = cmd
        }
    }

    fun saveAuthDuration(minutes: String) {
        viewModelScope.launch {
            repository.setSetting(SmsForwarderApp.KEY_AUTH_DURATION, minutes)
            authDuration.value = minutes
        }
    }

    // ===== 日志操作 =====

    fun clearLogs() {
        viewModelScope.launch {
            repository.clearForwardLogs()
        }
    }
}