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
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.andyching168.gmaps.ui.theme.GoogleMapsTheme
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.util.Log
import android.Manifest
import android.content.ComponentName
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    // 需要的權限列表
    private val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    } else {
        arrayOf(
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }
    
    // 錯誤訊息狀態
    private var errorMessage by mutableStateOf<String?>(null)
    
    // 註冊權限請求
    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        var allGranted = true
        permissions.entries.forEach { entry ->
            if (!entry.value) {
                allGranted = false
                Log.d("MainActivity", "權限被拒絕: ${entry.key}")
            }
        }
        
        if (allGranted) {
            Log.d("MainActivity", "所有權限已授予")
            startNotificationService()
        } else {
            Log.d("MainActivity", "有權限被拒絕，可能影響應用功能")
            // 即使有權限被拒絕，也嘗試啟動服務
            startNotificationService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 檢查是否是結束應用的意圖
        if (intent?.action == NotificationCatcherService.EXIT_APP_ACTION) {
            Log.d("MainActivity", "收到結束應用指令")
            finishAndRemoveTask()
            System.exit(0)
            return
        }
        
        enableEdgeToEdge()
        setContent {
            GoogleMapsTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NavigationScreen()
                    
                    // 顯示錯誤對話框（如果有）
                    ErrorDialog()
                }
            }
        }
        
        // 檢查是否需要請求電池優化白名單
        checkBatteryOptimization()
        
        // 檢查並請求所有必需的權限
        checkAndRequestPermissions()
    }
    
    @Composable
    private fun ErrorDialog() {
        // 顯示錯誤對話框
        errorMessage?.let { message ->
            AlertDialog(
                onDismissRequest = { errorMessage = null },
                title = { Text("錯誤") },
                text = { Text(message) },
                confirmButton = {
                    Button(onClick = { errorMessage = null }) {
                        Text("確定")
                    }
                }
            )
        }
    }
    
    private fun checkAndRequestPermissions() {
        // 檢查是否已獲取所有權限
        val permissionsToRequest = mutableListOf<String>()
        
        for (permission in requiredPermissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != 
                PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(permission)
            }
        }
        
        if (permissionsToRequest.isEmpty()) {
            // 已有所有權限，可以啟動服務
            Log.d("MainActivity", "已有所有必需權限")
            startNotificationService()
        } else {
            // 請求缺少的權限
            Log.d("MainActivity", "請求權限: ${permissionsToRequest.joinToString()}")
            requestPermissionsLauncher.launch(permissionsToRequest.toTypedArray())
        }
        
        // 確保通知監聽權限也已開啟
        ensureNotificationListenerPermission()
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        
        // 處理新的意圖，檢查是否是結束應用的指令
        if (intent.action == NotificationCatcherService.EXIT_APP_ACTION) {
            Log.d("MainActivity", "收到結束應用指令(onNewIntent)")
            finishAndRemoveTask()
            System.exit(0)
        }
    }
    
    private fun startNotificationService() {
        try {
            // 啟動前台服務
            val serviceIntent = Intent(this, NotificationCatcherService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            Log.d("MainActivity", "已啟動通知監聽服務")
        } catch (e: Exception) {
            Log.e("MainActivity", "啟動通知監聽服務失敗", e)
            // 顯示錯誤訊息
            val errorMsg = "啟動服務失敗: ${e.message}"
            Log.e("MainActivity", errorMsg)
            
            // 使用Toast顯示錯誤訊息
            Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
            
            // 同時更新Compose狀態以顯示錯誤
            errorMessage = errorMsg
        }
    }
    
    override fun onResume() {
        super.onResume()
        // 檢查通知監聽權限是否已開啟
        if (isNotificationServiceEnabled()) {
            // 如果權限已開啟，重新綁定服務以確保其處於活動狀態
            rebindNotificationService()
        }
        // 確保通知監聽權限已開啟（如果尚未開啟，會引導使用者去設定）
        ensureNotificationListenerPermission()
    }

    private fun isNotificationServiceEnabled(): Boolean {
        val enabledListeners = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return enabledListeners?.contains(packageName) == true
    }

    private fun rebindNotificationService() {
        Log.d("MainActivity", "正在重新綁定通知服務...")
        val componentName = ComponentName(this, NotificationCatcherService::class.java)
        try {
            packageManager.setComponentEnabledSetting(
                componentName,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
            packageManager.setComponentEnabledSetting(
                componentName,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
            Log.d("MainActivity", "通知服務已重新綁定。")
        } catch (e: Exception) {
            Log.e("MainActivity", "重新綁定服務失敗", e)
        }
    }
    
    private fun checkBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            }
        }
    }
    
    private fun ensureNotificationListenerPermission() {
        // 檢查通知監聽權限
        val enabledListeners = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        if (enabledListeners == null || !enabledListeners.contains(packageName)) {
            // 提示用戶開啟通知監聽權限
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            startActivity(intent)
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 4.dp),
            onClick = {
                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                context.startActivity(intent)
            }
        ) {
            Text(
                text = "開啟通知存取權限",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        // 功能按鈕行
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 第一行按鈕
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // 開啟 Google Maps 按鈕
                Button(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp),
                    onClick = {
                        viewModel.openGoogleMaps(context)
                    }
                ) {
                    Text(
                        text = "開啟 Google Maps",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1
                    )
                }

                // 顯示 JSON 按鈕
                Button(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp),
                    onClick = {
                        showJsonDialog = true
                    }
                ) {
                    Text(
                        text = "顯示 JSON",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1
                    )
                }
            }
            
            // 第二行按鈕 - 結束應用程式
            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 4.dp, start = 32.dp, end = 32.dp),
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
                Text(
                    text = "結束應用程式",
                    style = MaterialTheme.typography.bodyMedium
                )
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
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 第一行按鈕 - 重新連接和開啟手環應用
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Button(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 4.dp),
                            onClick = {
                                viewModel.queryConnectedDevices(context)
                            }
                        ) {
                            Text(
                                text = "重新連接",
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1
                            )
                        }
                        
                        Button(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 4.dp),
                            onClick = {
                                viewModel.openWearableApp(context)
                            }
                        ) {
                            Text(
                                text = "開啟手環應用",
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1
                            )
                        }
                    }
                    
                    // 第二行按鈕 - 發送資料
                    Button(
                        modifier = Modifier
                            .fillMaxWidth(0.75f)
                            .padding(top = 8.dp),
                        onClick = {
                            viewModel.sendNavigationDataToWearable(context)
                        }
                    ) {
                        Text(
                            text = "發送資料",
                            style = MaterialTheme.typography.bodyMedium
                        )
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