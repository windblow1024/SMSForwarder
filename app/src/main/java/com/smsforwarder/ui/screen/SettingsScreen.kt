package com.smsforwarder.ui.screen

import android.content.Intent
import android.os.PowerManager
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
/**
 * 电池优化状态卡片
 * 根据当前优化状态显示不同颜色和提示文字
 */
@Composable
fun BatteryOptimizationCard(isOptimized: Boolean, onClick: () -> Unit) {
    val color = if (isOptimized) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isOptimized) {
                    Text("⚠️ ", style = MaterialTheme.typography.titleMedium)
                } else {
                    Text("✅ ", style = MaterialTheme.typography.titleMedium)
                }
                Text(
                    text = if (isOptimized) "电池优化未关闭" else "电池优化已忽略",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (isOptimized) {
                    "App 可能被系统后台清理导致收不到短信，建议加入白名单"
                } else {
                    "App 已加入电池优化白名单，后台存活更稳定"
                },
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
                Text(
                    if (isOptimized) "前往关闭电池优化"
                    else "电池优化白名单设置"
                )
            }
        }
    }
}

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
                viewModel.addServiceLog("服务启动")
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
                viewModel.addServiceLog("服务停止")
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
            "为确保后台服务持续运行（尤其小米 HyperOS / 华为等厂商系统），建议进行以下设置：",
            style = MaterialTheme.typography.bodyMedium
        )

        // 检测电池优化状态
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isIgnoringBattery = powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false

        BatteryOptimizationCard(
            isOptimized = !isIgnoringBattery,
            onClick = {
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                context.startActivity(intent)
            }
        )

        // 其他厂商 ROM 提示
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    "其他优化建议",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "1️⃣ 系统设置 → 应用设置 → 应用管理 → 短信转发器 → 自启动 → 打开\n" +
                    "2️⃣ 系统设置 → 通知与控制中心 → 状态栏 → 显示通知图标（确保前台服务不被清理）\n" +
                    "3️⃣ 多任务界面下拉锁定 App（防止一键清理时被关闭）",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}