package com.smsforwarder.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smsforwarder.data.model.MonitoredNumberEntity
import com.smsforwarder.ui.viewmodel.MainViewModel

/**
 * 被监控号码管理页面
 * 默认号码（10086202）不可删除、不可修改
 */
@Composable
fun MonitoredNumberScreen(viewModel: MainViewModel) {
    val numbers by viewModel.monitoredNumbers.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (numbers.isEmpty()) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("暂无被监控号码", style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(8.dp))
                Text("添加需要读取短信的号码", style = MaterialTheme.typography.bodySmall)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(numbers, key = { it.id }) { item ->
                    MonitoredNumberCard(
                        item = item,
                        onDelete = {
                            if (!item.isDefault) {
                                viewModel.deleteMonitoredNumber(item.id)
                            }
                        }
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "添加监控号码")
        }
    }

    if (showAddDialog) {
        AddMonitoredNumberDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { phone, desc ->
                viewModel.addMonitoredNumber(phone, desc)
                showAddDialog = false
            }
        )
    }
}

@Composable
fun MonitoredNumberCard(item: MonitoredNumberEntity, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (item.isDefault) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = "默认",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(end = 8.dp)
                )
            } else {
                Icon(
                    Icons.Default.Star,
                    contentDescription = "监控号码",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.phoneNumber,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                if (item.description.isNotBlank()) {
                    Text(
                        text = item.description,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                }
                if (item.isDefault) {
                    Text(
                        text = "默认号码（不可删除）",
                        color = MaterialTheme.colorScheme.secondary,
                        fontSize = 12.sp
                    )
                }
            }
            if (!item.isDefault) {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
fun AddMonitoredNumberDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var phone by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加监控号码") },
        text = {
            Column {
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("短信号码") },
                    placeholder = { Text("如: 10086") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("备注说明（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(phone, description) },
                enabled = phone.isNotBlank()
            ) {
                Text("添加")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}