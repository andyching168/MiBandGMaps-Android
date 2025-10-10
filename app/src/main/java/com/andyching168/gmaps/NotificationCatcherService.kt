package com.andyching168.gmaps

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import android.graphics.drawable.Icon
import android.graphics.drawable.BitmapDrawable
import android.graphics.Bitmap
import android.graphics.Color
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.security.MessageDigest
import java.math.BigInteger

class NotificationCatcherService : NotificationListenerService() {
    private lateinit var viewModel: NavigationViewModel
    private val CHANNEL_ID = "gmaps_navigation_channel"
    private val NOTIFICATION_ID = 1001
    private val EXIT_ACTION = "com.andyching168.gmaps.EXIT_APP"

    companion object {
        const val EXIT_APP_ACTION = "com.andyching168.gmaps.EXIT_APP"
    }

    override fun onCreate() {
        super.onCreate()
        try {
            viewModel = NotificationCatcherApp.getInstance().getNavigationViewModel()
            // 初始化時設置為沒有通知
            viewModel.updateNavigationInfo(NavigationInfo(hasNotification = false))
            
            // 創建通知渠道
            createNotificationChannel()
            
            // 檢查是否有必要的藍牙權限
            val hasBluetoothPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                checkPermission(android.Manifest.permission.BLUETOOTH_CONNECT) && 
                checkPermission(android.Manifest.permission.BLUETOOTH_SCAN)
            } else {
                checkPermission(android.Manifest.permission.BLUETOOTH) && 
                checkPermission(android.Manifest.permission.BLUETOOTH_ADMIN)
            }
            
            // 根據權限情況處理
            if (hasBluetoothPermission) {
                // 啟動前台服務
                startForeground()
                
                // 初始化小米手環連接
                viewModel.initializeWearable(applicationContext)
            } else {
                Log.e("NotificationCatcherService", "缺少必要的藍牙權限，無法啟動前台服務")
                // 仍然創建通知但不使用FOREGROUND_SERVICE_CONNECTED_DEVICE類型
                startSimpleForeground()
            }
            
            // 請求忽略電池優化
            requestIgnoreBatteryOptimization()
            
        } catch (e: SecurityException) {
            Log.e("NotificationCatcherService", "啟動前台服務失敗，權限不足: ${e.message}")
            // 嘗試啟動一個基本的前台服務，不需要特殊權限
            startSimpleForeground()
        } catch (e: Exception) {
            Log.e("NotificationCatcherService", "服務啟動失敗: ${e.message}")
        }
    }
    
    private fun checkPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == EXIT_ACTION) {
            // 處理結束APP操作
            exitApp()
            return START_NOT_STICKY
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun exitApp() {
        try {
            // 發送空 JSON 到小米手環
            viewModel.sendEmptyJsonToWearable(applicationContext)
            Log.d("NotificationCatcherService", "已發送空 JSON 到手環")
            
            // 停止前台服務
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            
            // 啟動MainActivity並帶上結束應用的動作
            val exitIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                action = EXIT_APP_ACTION
            }
            startActivity(exitIntent)
            
            Log.d("NotificationCatcherService", "已發送結束應用指令")
        } catch (e: Exception) {
            Log.e("NotificationCatcherService", "結束應用時出錯", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "導航監聽服務",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "用於保持導航監聽服務在背景運行"
                setShowBadge(false)
            }
            
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(serviceChannel)
        }
    }
    
    private fun startSimpleForeground() {
        // 創建一個基本的前台服務通知，不需要特殊權限
        val contentIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, contentIntent, 
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val exitIntent = Intent(this, NotificationCatcherService::class.java).apply {
            action = EXIT_ACTION
        }
        val exitPendingIntent = PendingIntent.getService(
            this, 1, exitIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Google 地圖導航同步")
            .setContentText("正在監聽導航通知 (基本模式)")
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "結束APP", exitPendingIntent)
            .build()
            
        startForeground(NOTIFICATION_ID, notification)
        Log.d("NotificationCatcherService", "已啟動基本前台服務")
    }
    
    private fun startForeground() {
        // 創建返回到應用程式的 PendingIntent
        val contentIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, contentIntent, 
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        // 創建結束應用的 PendingIntent
        val exitIntent = Intent(this, NotificationCatcherService::class.java).apply {
            action = EXIT_ACTION
        }
        val exitPendingIntent = PendingIntent.getService(
            this, 1, exitIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Google 地圖導航同步")
            .setContentText("正在監聽導航通知")
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "結束APP", exitPendingIntent)
            .build()
            
        startForeground(NOTIFICATION_ID, notification)
        Log.d("NotificationCatcherService", "前台服務已啟動")
    }
    
    private fun requestIgnoreBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                Log.d("NotificationCatcher", "應用未被列入白名單，建議手動設置")
            }
        }
    }

    private fun simpleIconHash(bitmap: Bitmap): String {
        val resized = Bitmap.createScaledBitmap(bitmap, 32, 32, true)
        val grayscale = IntArray(32 * 32)
        resized.getPixels(grayscale, 0, 32, 0, 0, 32, 32)

        // 區域亮度平均值
        val parts = 4
        val avgByRegion = Array(parts * parts) { 0 }
        for (y in 0 until 32) {
            for (x in 0 until 32) {
                val gray = Color.red(grayscale[y * 32 + x]) // 灰階代表亮度即可
                val regionX = x / (32 / parts)
                val regionY = y / (32 / parts)
                val index = regionY * parts + regionX
                avgByRegion[index] += gray
            }
        }

        return avgByRegion.joinToString("-") { (it / ((32 / parts) * (32 / parts))).toString() }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)
        val packageName = sbn.packageName
        val notification = sbn.notification
        val extras = notification.extras

        if (packageName == "com.google.android.apps.maps") {
            // 記錄原始通知內容
            val rawNotification = """
                Package: $packageName
                Title: ${extras.get("android.title")}
                Direction: ${extras.get("android.text")}
                SubText: ${extras.get("android.subText")}
                Icon: ${extras.get("android.largeIcon")}
                Extras: ${extras.keySet().joinToString("\n") { key ->
                    "$key: ${extras.get(key)}"
                }}
            """.trimIndent()
            
            viewModel.setLastRawNotification(rawNotification)
            Log.d("NotificationCatcher", "原始通知內容:\n$rawNotification")

            // 從 extras 中獲取所有資訊
            val title = extras.get("android.title")?.toString() ?: ""
            val direction = extras.get("android.text")?.toString() ?: ""
            val subText = extras.get("android.subText")?.toString() ?: ""
            
            // 處理圖標資訊
            val icon = extras.get("android.largeIcon") as? Icon
            try {
                val drawable = icon?.loadDrawable(this)
                if (drawable is BitmapDrawable) {
                    val bitmap = drawable.bitmap
                    val hash = simpleIconHash(bitmap)
                    
                    Log.d("NotificationCatcher", """
                        圖標信息:
                        寬度: ${bitmap.width}
                        高度: ${bitmap.height}
                        區域亮度哈希值: $hash
                    """.trimIndent())
                    
                    // 更新 ViewModel 中的哈希值
                    viewModel.setLastIconHash(hash, bitmap)
                }
            } catch (e: Exception) {
                Log.e("NotificationCatcher", "獲取圖標信息時出錯", e)
            }

            val info = parseNavigationInfo(title, direction, subText)
            Log.d("NotificationCatcher", "解析後的資訊: direction=${info.direction}")
            viewModel.updateNavigationInfo(info.copy(hasNotification = true))
            Log.d("NotificationCatcher", "更新導航資訊: $info")
            
            // 自動發送導航資訊到小米手環
            try {
                viewModel.sendNavigationDataToWearable(applicationContext)
                Log.d("NotificationCatcher", "已自動發送導航資訊到手環")
            } catch (e: Exception) {
                Log.e("NotificationCatcher", "發送導航資訊到手環失敗", e)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        super.onNotificationRemoved(sbn)
        if (sbn.packageName == "com.google.android.apps.maps") {
            // 當 Google Maps 通知被移除時，設置為沒有通知並發送空 JSON
            viewModel.updateNavigationInfo(NavigationInfo(hasNotification = false))
            try {
                viewModel.sendEmptyJsonToWearable(applicationContext)
                Log.d("NotificationCatcher", "已發送空 JSON 到手環")
            } catch (e: Exception) {
                Log.e("NotificationCatcher", "發送空 JSON 到手環失敗", e)
            }
        }
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        val binder = super.onBind(intent)
        Log.d("NotificationCatcherService", "服務已綁定")
        return binder
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d("NotificationCatcherService", "通知監聽器已連接，正在檢查現有通知...")
        // 獲取所有當前活動的通知
        val activeNotifications = activeNotifications
        if (activeNotifications.isNullOrEmpty()) {
            Log.d("NotificationCatcherService", "沒有發現活動中的通知。")
            return
        }

        // 尋找 Google Maps 的導航通知
        for (sbn in activeNotifications) {
            if (sbn.packageName == "com.google.android.apps.maps") {
                Log.d("NotificationCatcherService", "發現現有的 Google Maps 通知，正在處理...")
                // 處理這個已存在的通知
                onNotificationPosted(sbn)
                break // 假設只有一個導航通知
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d("NotificationCatcherService", "服務已銷毀")
    }

    private fun parseNavigationInfo(title: String, direction: String, subText: String): NavigationInfo {
        // 解析距離和時間
        var totalDistance = ""
        var turnDistance = title  // 轉彎距離直接使用 title
        var duration = ""
        var eta = ""

        // 從 subText 中解析所有資訊
        subText.split("·").forEach { part ->
            when {
                part.contains("公尺") -> {
                    val meters = part.trim().replace("公尺", "").trim()
                    totalDistance = if (meters.toIntOrNull() ?: 0 >= 1000) {
                        "${(meters.toIntOrNull() ?: 0) / 1000.0} 公里"
                    } else {
                        "$meters 公尺"
                    }
                }
                part.contains("公里") -> totalDistance = part.trim()
                part.contains("分鐘") -> duration = part.trim()
                part.contains("預計到達時間") -> {
                    // 移除「預計到達時間：」前綴，只保留時間
                    eta = part.trim().replace("預計到達時間：", "").replace("預計到達時間", "").trim()
                }
            }
        }

        // 獲取當前導航信息，保留已設置的 turnDirection
        val currentInfo = viewModel.navigationInfo.value
        val info = NavigationInfo(
            direction = direction,
            totalDistance = totalDistance,
            turnDistance = turnDistance,
            duration = duration,
            eta = eta,
            status = "導航中",
            turnDirection = currentInfo.turnDirection,  // 保留已設置的轉彎方向
            hasNotification = true
        )
        
        Log.d("NotificationCatcher", "解析結果: $info")
        return info
    }
} 