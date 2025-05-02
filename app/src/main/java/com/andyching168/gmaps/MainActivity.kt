package com.andyching168.gmaps

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.andyching168.gmaps.ui.theme.GoogleMapsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GoogleMapsTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NavigationScreen()
                }
            }
        }
    }
}

@Composable
fun NavigationScreen() {
    val viewModel: NavigationViewModel = NotificationCatcherApp.getInstance().getNavigationViewModel()
    val context = LocalContext.current
    val navigationInfo by viewModel.navigationInfo.collectAsStateWithLifecycle()
    val unknownHashes by viewModel.unknownHashes.collectAsStateWithLifecycle()
    val wearableStatus by viewModel.wearableConnectionStatus.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()
    var showJsonDialog by remember { mutableStateOf(false) }
    
    // 初始化小米手環API
    LaunchedEffect(key1 = Unit) {
        viewModel.initializeWearable(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 權限設定按鈕
        Button(
            onClick = {
                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                context.startActivity(intent)
            }
        ) {
            Text("開啟通知存取權限")
        }

        // 功能按鈕行
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            
            // 開啟 Google Maps 按鈕
            Button(
                onClick = {
                    viewModel.openGoogleMaps(context)
                }
            ) {
                Text("開啟 Google Maps")
            }

            // 顯示 JSON 按鈕
            Button(
                onClick = {
                    showJsonDialog = true
                }
            ) {
                Text("顯示 JSON")
            }

            // 結束應用程式按鈕
            Button(
                onClick = {
                    // 發送空 JSON 到小米手環
                    viewModel.sendEmptyJsonToWearable(context)
                    
                    // 停止通知監聽服務
                    val serviceIntent = Intent(context, NotificationCatcherService::class.java)
                    context.stopService(serviceIntent)
                    
                    // 完全關閉應用程式
                    (context as? ComponentActivity)?.finishAffinity()
                    System.exit(0)
                }
            ) {
                Text("結束應用程式")
            }
        }

        // 小米手環連接狀態卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "小米手環連接狀態",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                
                Text(
                    text = wearableStatus,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (wearableStatus.startsWith("已連接")) 
                                MaterialTheme.colorScheme.primary 
                            else 
                                MaterialTheme.colorScheme.error
                )
                
                // 手環操作按鈕
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(
                        onClick = {
                            viewModel.queryConnectedDevices(context)
                        }
                    ) {
                        Text("重新連接")
                    }
                    
                    Button(
                        onClick = {
                            viewModel.openWearableApp(context)
                        }
                    ) {
                        Text("開啟手環應用")
                    }
                    
                    Button(
                        onClick = {
                            viewModel.sendNavigationDataToWearable(context)
                        }
                    ) {
                        Text("發送資料")
                    }
                }
            }
        }

        // 導航資訊顯示
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "導航狀態",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                if (!navigationInfo.hasNotification) {
                    Text(
                        text = "目前沒有導航通知",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (navigationInfo.isRerouting) {
                    Text(
                        text = "正在重新規劃路線...",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    NavigationInfoItem("方向", navigationInfo.direction)
                    NavigationInfoItem("剩餘總距離", navigationInfo.totalDistance)
                    NavigationInfoItem("轉彎距離", navigationInfo.turnDistance)
                    NavigationInfoItem("轉彎方向", navigationInfo.turnDirection)
                    NavigationInfoItem("時間", navigationInfo.duration)
                    NavigationInfoItem("預計到達", navigationInfo.eta)
                }
            }
        }

        // 日誌顯示卡片
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "操作日誌",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    items(logs) { log ->
                        Text(
                            text = log,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // 未知哈希值列表
        if (unknownHashes.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "未知哈希值列表",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                    ) {
                        items(unknownHashes) { (timestamp, hash, bitmap) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (bitmap != null) {
                                        Image(
                                            bitmap = bitmap.asImageBitmap(),
                                            contentDescription = "未知圖標",
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = timestamp,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = hash,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                                Button(
                                    onClick = {
                                        viewModel.copyHashToClipboard(context, hash)
                                    }
                                ) {
                                    Text("複製")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // JSON 對話框
    if (showJsonDialog) {
        AlertDialog(
            onDismissRequest = { showJsonDialog = false },
            title = { Text("導航資訊 JSON") },
            text = {
                Column {
                    Text(
                        text = viewModel.generateNavigationJson(),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.verticalScroll(rememberScrollState())
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.copyJsonToClipboard(context)
                    }
                ) {
                    Text("複製")
                }
            },
            dismissButton = {
                Button(
                    onClick = { showJsonDialog = false }
                ) {
                    Text("關閉")
                }
            }
        )
    }
}

@Composable
fun NavigationInfoItem(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
    }
}