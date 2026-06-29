package com.smsforwarder.ui.screen

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.smsforwarder.service.SmsForwardService
import com.smsforwarder.ui.viewmodel.MainViewModel

/**
 * 设置页面
 * - 转发指令配置
 * - 停止指令配置
 * - 授权有效期配置
 * - 服务启停
 */
@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val forwardCmd by viewModel.commandForward.collectAsState()
    val stopCmd by viewModel.commandStop.collectAsState()
    val duration by viewModel.authDuration.collectAsState()

    var editForwardCmd by remember { mutableStateOf(forwardCmd) }
    var editStopCmd by remember { mutableStateOf(stopCmd) }
    var editDuration by remember { mutableStateOf(duration) }

    // 同步编辑框与数据
    LaunchedEffect(forwardCmd, stopCmd, duration) {
        editForwardCmd = forwardCmd
        editStopCmd = stopCmd
        editDuration = duration
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ===== 指令设置 =====
        Text("指令设置", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        OutlinedTextField(
            value = editForwardCmd,
            onValueChange = { editForwardCmd = it },
            label = { Text("申请转发指令") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = { viewModel.saveCommandForward(editForwardCmd) },
            modifier = Modifier.align(Alignment.End),
            enabled = editForwardCmd.isNotBlank()
        ) {
            Text("保存")
        }

        OutlinedTextField(
            value = editStopCmd,
            onValueChange = { editStopCmd = it },
            label = { Text("停止转发指令") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = { viewModel.saveCommandStop(editStopCmd) },
            modifier = Modifier.align(Alignment.End),
            enabled = editStopCmd.isNotBlank()
        ) {
            Text("保存")
        }

        Divider()

        // ===== 有效期设置 =====
        Text("有效期设置", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        OutlinedTextField(
            value = editDuration,
            onValueChange = { editDuration = it },
            label = { Text("授权有效期（分钟）") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = { viewModel.saveAuthDuration(editDuration) },
            modifier = Modifier.align(Alignment.End),
            enabled = editDuration.isNotBlank() && (editDuration.toLongOrNull() ?: 0) > 0
        ) {
            Text("保存")
        }

        Divider()

        // ===== 服务控制 =====
        Text("服务控制", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        Button(
            onClick = {
                context.startForegroundService(
                    Intent(context, SmsForwardService::class.java)
                )
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary
            )
        ) {
            Text("启动服务")
        }

        OutlinedButton(
            onClick = {
                context.startService(
                    Intent(context, SmsForwardService::class.java).apply {
                        action = SmsForwardService.ACTION_STOP_SERVICE
                    }
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("停止服务")
        }

        Divider()

        // ===== 系统设置引导 =====
        Text("系统设置引导", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        Text(
            "为确保后台服务持续运行，建议进行以下设置：",
            style = MaterialTheme.typography.bodyMedium
        )

        OutlinedButton(
            onClick = {
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                context.startActivity(intent)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("电池优化白名单设置")
        }
    }
}