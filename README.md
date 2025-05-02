# MiBandGMaps-Android

![image](https://github.com/andyching168/MiBandGMaps-Android/blob/main/Screenshot_2025-05-02-10-02-07-557_com.andyching168.gmaps.jpg)

這個應用程式可以捕獲 Google Maps 的導航通知，並將導航資訊同步到小米手環的快應用上顯示。

## 功能特點

- 實時監聽 Google Maps 的導航通知
- 解析導航信息，包括：
  - 當前道路名稱
  - 剩餘總距離
  - 轉彎距離
  - 轉彎方向
  - 預計到達時間
- 使用區域亮度哈希算法識別導航方向圖標
- 自動收集未知的導航圖標哈希值
- 支持複製哈希值以便後續分析
- 支持多種導航方向識別：
  - 基本方向：左轉、右轉、直行
  - 特殊直行：直行(接到下一個路)
  - 靠左/靠右行駛
  - 分岔路（靠左/靠右）
  - 下交流道
  - 圓環相關：
    - 圓環
    - 駛出圓環(4分之1)
    - 駛出圓環(2分之1)
  - 迴轉
  - 目的地相關：
    - 目的地在左方

## 技術實現

### 核心組件

1. **NotificationCatcherService**
   - 繼承自 `NotificationListenerService`
   - 監聽 Google Maps 的通知
   - 解析通知內容
   - 計算圖標哈希值

2. **NavigationViewModel**
   - 管理導航狀態
   - 存儲已知的圖標哈希值對應表
   - 處理未知哈希值的收集

3. **圖標哈希算法**
   ```kotlin
   fun simpleIconHash(bitmap: Bitmap): String {
       val resized = Bitmap.createScaledBitmap(bitmap, 32, 32, true)
       val grayscale = IntArray(32 * 32)
       resized.getPixels(grayscale, 0, 32, 0, 0, 32, 32)

       // 區域亮度平均值
       val parts = 4
       val avgByRegion = Array(parts * parts) { 0 }
       for (y in 0 until 32) {
           for (x in 0 until 32) {
               val gray = Color.red(grayscale[y * 32 + x])
               val regionX = x / (32 / parts)
               val regionY = y / (32 / parts)
               val index = regionY * parts + regionX
               avgByRegion[index] += gray
           }
       }

       return avgByRegion.joinToString("-") { (it / ((32 / parts) * (32 / parts))).toString() }
   }
   ```

### 已知的哈希值對應表

```kotlin
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
    
    // 圓環
    "0-143-143-0-0-167-167-0-0-139-139-0-0-39-39-0" to "Roundabout", // 圓環
    "23-123-119-11-115-51-171-75-91-127-119-0-0-63-0-0" to "Exit1st", // 駛出圓環(4分之1)
    "0-59-79-0-0-131-159-3-39-131-147-3-0-0-39-15" to "Exit2nd", // 駛出圓環(2分之1)
    
    // 迴轉
    "0-51-115-15-11-115-23-107-75-203-51-95-0-15-0-47" to "UTurnLeft", // 迴轉（左）
    
    // 目的地
    "99-131-11-0-119-111-39-11-67-167-203-143-0-55-91-103" to "DestinationLeft" // 目的地在左方
)
```

## 使用說明

1. 安裝應用程式
2. 授予通知存取權限
3. 開啟 Google Maps 導航
4. 應用程式會自動顯示：
   - 當前導航信息
   - 未知的導航圖標哈希值（如果有的話）
5. 點擊未知哈希值可以複製到剪貼簿

## 開發指南

### 添加新的哈希值

1. 在 `NavigationViewModel` 中的 `iconHashMap` 添加新的哈希值對應
2. 格式為：`"哈希值" to "方向描述"`

### 修改哈希算法

1. 在 `NotificationCatcherService` 中修改 `simpleIconHash` 方法
2. 確保新的算法能產生穩定的哈希值

### 擴展功能

- 可以添加更多導航信息的解析
- 可以實現哈希值的自動學習
- 可以添加導航歷史記錄
- 可以實現導航狀態的持久化存儲

## 注意事項

- 需要 Android 通知存取權限
- 只支持 Google Maps 的導航通知
- 哈希值對應表可能需要根據 Google Maps 的更新進行調整
- 不同裝置的 DPI 和顯示效果可能略有不同，系統已內建容錯機制

## 貢獻

歡迎提交 Pull Request 或 Issue 來改進這個專案。

## 授權

MIT License 



## 配置說明

### 配置包名一致性

對於正確的通訊，安卓應用程式和快應用必須保持包名一致：

1. 安卓應用程式包名已設定為：`com.andyching168.gmaps`
2. 快應用的 manifest.json 中的 package 字段也必須設為：`com.andyching168.gmaps`

### 簽名配置步驟

為了確保應用程式間的通訊，您需要使用相同的簽名：

1. **從 JKS 轉換為 P12 格式**
   ```shell
   keytool -importkeystore -srckeystore keystore.jks -destkeystore keystore.p12 -srcstoretype jks -deststoretype pkcs12
   ```

2. **從 P12 轉換為 PEM 格式**
   ```shell
   openssl pkcs12 -nodes -in keystore.p12 -out keystore.pem
   ```

3. **從 PEM 提取私鑰和證書**
   - 將 `-----BEGIN PRIVATE KEY-----` 到 `-----END PRIVATE KEY-----` 的內容複製到 `private.pem` 中
   - 將 `-----BEGIN CERTIFICATE-----` 到 `-----END CERTIFICATE-----` 的內容複製到 `certificate.pem` 中

4. **配置快應用簽名**
   - 將上述產生的 `private.pem` 和 `certificate.pem` 放在快應用根目錄的 `/sign/debug` 和 `/sign/release` 目錄下

### 安裝測試注意事項

在真機測試時，建議先卸載舊版本再安裝新版本：

```shell
adb uninstall com.andyching168.gmaps
```

然後再安裝新版本以確保完全替換。

## 功能說明

1. **通知捕獲**：捕獲 Google Maps 導航通知
2. **資訊解析**：解析導航方向、距離等資訊
3. **手環同步**：自動將資訊發送到小米手環快應用

## 手環操作

- 在手機上開啟應用程式
- 確保手環已連接
- 點擊「開啟手環應用」按鈕開啟快應用
- 開始 Google Maps 導航，資訊將自動同步到手環 
