package com.andyching168.gmaps

import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import com.xiaomi.xms.wearable.Wearable
import com.xiaomi.xms.wearable.auth.Permission
import com.xiaomi.xms.wearable.node.Node
import com.xiaomi.xms.wearable.node.NodeApi

class NavigationViewModel : ViewModel() {
    private val _navigationInfo = MutableStateFlow(NavigationInfo())
    val navigationInfo: StateFlow<NavigationInfo> = _navigationInfo.asStateFlow()

    private val _unknownHashes = MutableStateFlow<List<Triple<String, String, Bitmap?>>>(emptyList())
    val unknownHashes: StateFlow<List<Triple<String, String, Bitmap?>>> = _unknownHashes.asStateFlow()

    private var lastRawNotification: String = ""
    private var lastIconHash: String = ""
    private var lastUnknownHash: String = ""

    // 小米手環相關
    private var nodeId: String? = null
    private var currentNode: Node? = null
    private lateinit var nodeApi: NodeApi
    private var isWearableInitialized = false
    
    // 手環連接狀態
    private val _wearableConnectionStatus = MutableStateFlow("未連接")
    val wearableConnectionStatus: StateFlow<String> = _wearableConnectionStatus.asStateFlow()

    // 日誌
    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    // 哈希值對應表
    private val iconHashMap: Map<String, String> = mapOf(
        // 基本方向
        "3-59-0-0-123-151-99-71-11-71-0-95-0-0-0-47" to "left",   // 左轉
        "0-0-59-3-71-99-151-123-95-0-71-11-47-0-0-0" to "right",  // 右轉
        "0-39-39-0-0-175-175-0-0-55-55-0-0-23-23-0" to "straight", // 直行
        "0-39-39-0-0-175-175-0-0-63-63-0-0-31-31-0" to "straight", // 直行
        "0-39-39-0-0-175-175-0-0-139-135-0-7-55-55-3" to "GoStraight", // 直行(接到下一個路）
        
        // 靠左/靠右
        "0-119-47-0-0-191-79-0-0-0-111-23-0-0-31-31" to "side_left", // 靠左
        "0-47-119-0-0-79-191-0-23-111-0-0-31-31-0-0" to "side_right", // 靠右
        
        // 分岔路
        "0-23-111-47-43-127-155-95-0-139-11-0-0-63-0-0" to "ForkRight", // 分岔路（靠右）
        "47-111-11-3-95-171-131-43-0-11-139-0-0-0-63-0" to "ForkLeft", // 分岔路（靠左）
        
        // 下交流道
        "47-15-99-15-95-139-171-103-95-75-43-3-47-15-0-0" to "ExitRight", // 下交流道(右)
        
        //急轉
        "0-0-51-19-47-79-91-103-119-171-0-95-0-0-0-47" to "SharpTurnLeft", // 向左後急轉
        "19-51-0-0-103-91-79-47-95-0-171-119-47-0-0-0" to "SharpTurnRight", // 向右後急轉

        // 圓環
        "0-143-143-0-0-167-167-0-0-139-139-0-0-39-39-0" to "Roundabout", // 圓環
        "23-123-119-11-115-51-171-75-91-127-119-0-0-63-0-0" to "Exit1st", // 駛出圓環(4分之1)
        "0-59-79-0-0-131-159-3-39-131-147-3-0-0-39-15" to "Exit2nd", // 駛出圓環(2分之1)
        
        // 迴轉
        "0-51-115-15-11-115-23-107-75-203-51-95-0-15-0-47" to "UTurnLeft", // 迴轉（左）
        
        // 目的地
        "99-131-11-0-119-111-39-11-67-167-203-143-0-55-91-103" to "DestinationLeft", // 目的地在左方
        "0-11-131-99-11-39-115-119-143-203-167-67-103-91-55-0" to "DestinationRight", // 目的地在右方
        "7-103-103-7-87-71-75-83-35-87-83-35-0-71-71-0" to "DestinationFront", // 目的地在前方

        //其他
        "0-0-0-0-0-0-0-0-0-0-0-0-0-0-0-0" to "Blank" // 空白
    )

    // 容錯值設定
    private val TOLERANCE = 30

    // 比較兩個哈希值是否在容錯範圍內
    private fun isHashSimilar(hash1: String, hash2: String): Boolean {
        val parts1 = hash1.split("-").map { it.toInt() }
        val parts2 = hash2.split("-").map { it.toInt() }
        
        if (parts1.size != parts2.size) return false
        
        val differences = parts1.zip(parts2).map { (p1, p2) -> Math.abs(p1 - p2) }
        val maxDiff = differences.maxOrNull() ?: 0
        
        // 記錄最大差異，方便調試
        if (maxDiff > 20) {
            Log.d("NotificationCatcher", """
                哈希值比較:
                原始: $hash1
                當前: $hash2
                最大差異: $maxDiff
                差異分布: ${differences.joinToString(", ")}
            """.trimIndent())
        }
        
        return differences.all { it <= TOLERANCE }
    }

    // 根據哈希值獲取方向（帶容錯）
    private fun getDirectionWithTolerance(hash: String): String? {
        val direction = iconHashMap.entries.find { isHashSimilar(it.key, hash) }?.value
        if (direction != null) {
            Log.d("NotificationCatcher", "成功匹配方向: $direction")
        } else {
            Log.d("NotificationCatcher", "未找到匹配的方向，當前哈希值: $hash")
        }
        return direction
    }

    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val TIME_THRESHOLD = TimeUnit.SECONDS.toMillis(10) // 10秒時間閾值

    // 初始化小米手環相關API
    fun initializeWearable(context: Context) {
        try {
            if (!isWearableInitialized) {
                nodeApi = Wearable.getNodeApi(context)
                isWearableInitialized = true
                log("初始化 Wearable API 成功")
                queryConnectedDevices(context)
            }
        } catch (e: Exception) {
            log("初始化 Wearable API 失敗: ${e.message}")
        }
    }
    
    // 查詢已連接的設備
    fun queryConnectedDevices(context: Context) {
        if (!isWearableInitialized) {
            log("Wearable API 尚未初始化")
            return
        }
        
        nodeApi.connectedNodes.addOnSuccessListener { nodes ->
            if (nodes.isNotEmpty()) {
                currentNode = nodes[0]
                nodeId = currentNode?.id
                _wearableConnectionStatus.value = "已連接: ${currentNode?.name}"
                log("已連接設備: ${currentNode?.name}")
                checkAndRequestPermissions(context)
            } else {
                _wearableConnectionStatus.value = "未發現已連接的設備"
                log("未發現已連接的設備")
            }
        }.addOnFailureListener { e ->
            _wearableConnectionStatus.value = "查詢設備失敗"
            log("查詢已連接設備失敗: ${e.message}")
        }
    }
    
    // 檢查並請求權限
    private fun checkAndRequestPermissions(context: Context) {
        nodeId?.let { did ->
            val authApi = Wearable.getAuthApi(context)
            authApi.checkPermission(did, Permission.DEVICE_MANAGER)
                .addOnSuccessListener { granted ->
                    if (!granted) {
                        authApi.requestPermission(did, Permission.DEVICE_MANAGER)
                            .addOnSuccessListener {
                                log("已獲取設備管理權限")
                            }.addOnFailureListener { e ->
                                log("請求設備管理權限失敗: ${e.message}")
                            }
                    } else {
                        log("已有設備管理權限")
                    }
                }.addOnFailureListener { e ->
                    log("檢查權限失敗: ${e.message}")
                }
        } ?: log("沒有連接的設備，無法檢查權限")
    }
    
    // 開啟手環應用
    fun openWearableApp(context: Context) {
        nodeId?.let { nid ->
            nodeApi.isWearAppInstalled(nid)
                .addOnSuccessListener {
                    nodeApi.launchWearApp(nid,"pages/index")
                        .addOnSuccessListener {
                            log("成功開啟手環端應用")
                            Toast.makeText(context, "已開啟手環端應用", Toast.LENGTH_SHORT).show()
                        }.addOnFailureListener { e ->
                            log("開啟手環端應用失敗: ${e.message}")
                            Toast.makeText(context, "開啟手環端應用失敗", Toast.LENGTH_SHORT).show()
                        }
                }
                .addOnFailureListener {
                    log("手環未安裝相應小程式")
                    Toast.makeText(context, "手環未安裝相應小程式，請先安裝", Toast.LENGTH_SHORT).show()
                }
        } ?: Toast.makeText(context, "未連接到設備", Toast.LENGTH_SHORT).show()
    }
    
    // 發送導航資訊到手環
    fun sendNavigationDataToWearable(context: Context) {
        val jsonData = generateNavigationJson()
        sendMessageToWearable(context, jsonData)
    }
    
    // 發送訊息到手環
    private fun sendMessageToWearable(context: Context, message: String) {
        nodeId?.let { nid ->
            val messageApi = Wearable.getMessageApi(context)
            messageApi.sendMessage(nid, message.toByteArray())
                .addOnSuccessListener {
                    log("成功發送訊息: $message")
                    //Toast.makeText(context, "已發送到手環", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { e ->
                    log("發送訊息失敗: ${e.message}")
                    Toast.makeText(context, "發送訊息失敗", Toast.LENGTH_SHORT).show()
                }
        } ?: Toast.makeText(context, "未連接到設備", Toast.LENGTH_SHORT).show()
    }
    
    // 添加日誌
    private fun log(message: String) {
        val currentLogs = _logs.value.toMutableList()
        currentLogs.add("${getCurrentTime()} - $message")
        // 保留最近的 50 條日誌
        if (currentLogs.size > 50) {
            currentLogs.removeAt(0)
        }
        _logs.value = currentLogs
    }
    
    // 獲取當前時間
    private fun getCurrentTime(): String {
        return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
    }

    fun updateNavigationInfo(info: NavigationInfo) {
        _navigationInfo.value = info
        
        // 當收到新的導航資訊時，自動發送到手環（可選）
        // 如果啟用此功能，請取消下面這行的註釋
        // navigationInfo.value.context?.let { sendNavigationDataToWearable(it) }
    }

    fun setLastRawNotification(raw: String) {
        lastRawNotification = raw
    }

    fun setLastIconHash(hash: String, bitmap: Bitmap? = null) {
        lastIconHash = hash
        val direction = getDirectionWithTolerance(hash)
        if (direction != null) {
            val currentInfo = _navigationInfo.value
            _navigationInfo.value = currentInfo.copy(turnDirection = direction)
        } else {
            // 只在哈希值變化時添加
            if (lastUnknownHash != hash) {
                val currentList = _unknownHashes.value.toMutableList()
                val timestamp = dateFormat.format(Date())
                currentList.add(Triple(timestamp, hash, bitmap))
                _unknownHashes.value = currentList
                lastUnknownHash = hash
            }
        }
    }

    fun copyHashToClipboard(context: Context, hash: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Hash Value", hash)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "已複製到剪貼簿", Toast.LENGTH_SHORT).show()
    }

    fun getLastTurnDirection(): String {
        return iconHashMap[lastIconHash] ?: ""
    }

    fun showRawNotification(context: Context) {
        if (lastRawNotification.isNotEmpty()) {
            Log.d("NotificationCatcher", "顯示原始通知內容:\n$lastRawNotification")
            Toast.makeText(context, lastRawNotification, Toast.LENGTH_LONG).show()
        } else {
            Log.d("NotificationCatcher", "目前沒有通知內容")
            Toast.makeText(context, "目前沒有通知內容", Toast.LENGTH_SHORT).show()
        }
    }

    fun openGoogleMaps(context: Context) {
        try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage("com.google.android.apps.maps")
            if (launchIntent != null) {
                context.startActivity(launchIntent)
            } else {
                Toast.makeText(context, "找不到 Google Maps 應用程式", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "無法開啟 Google Maps", Toast.LENGTH_SHORT).show()
            Log.e("NotificationCatcher", "開啟 Google Maps 失敗", e)
        }
    }


    fun generateNavigationJson(): String {
        val json = JSONObject().apply {
            put("turnDirection", _navigationInfo.value.turnDirection)
            put("turnDistance", _navigationInfo.value.turnDistance)
            put("direction", _navigationInfo.value.direction)
        }
        return json.toString(4) // 使用 4 個空格進行格式化
    }

    // 生成空 JSON
    private fun generateEmptyJson(): String {
        val json = JSONObject().apply {
            put("turnDirection", "")
            put("turnDistance", "")
            put("direction", "")
        }
        return json.toString(4)
    }

    // 發送空 JSON 到手環
    fun sendEmptyJsonToWearable(context: Context) {
        val emptyJson = generateEmptyJson()
        sendMessageToWearable(context, emptyJson)
    }

    fun copyJsonToClipboard(context: Context) {
        val jsonString = generateNavigationJson()
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Navigation Info", jsonString)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "已複製到剪貼簿", Toast.LENGTH_SHORT).show()
    }
} 