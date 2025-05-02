package com.andyching168.gmaps

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import android.graphics.drawable.Icon
import android.graphics.drawable.BitmapDrawable
import android.graphics.Bitmap
import android.graphics.Color
import java.security.MessageDigest
import java.math.BigInteger

class NotificationCatcherService : NotificationListenerService() {
    private lateinit var viewModel: NavigationViewModel

    override fun onCreate() {
        super.onCreate()
        viewModel = NotificationCatcherApp.getInstance().getNavigationViewModel()
        // 初始化時設置為沒有通知
        viewModel.updateNavigationInfo(NavigationInfo(hasNotification = false))
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
                part.contains("預計到達時間") -> eta = part.trim()
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