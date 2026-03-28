# 🚗 CarConnect - 车机无感连接 APK

适配 Android 5.1（API 22）及以上版本，专为 **vivo 手机 + 亿联车机** 无感自动连接设计。

---

## ✨ 功能特性

### 1. 蓝牙自动连接
- ✅ 通过**系统广播**发现目标蓝牙设备
- ✅ 先检查已配对设备列表，命中则直接连接
- ✅ 自动配对 + A2DP（音频）+ HFP（免提）双 Profile 连接
- ✅ 可配置**最大重试次数**（默认5次）
- ✅ 可配置**连接超时时间**（默认30秒）
- ✅ 支持设备名模糊匹配 或 MAC 地址精确匹配

### 2. Wi-Fi 自动连接（车机热点）
- ✅ 蓝牙连接成功后**自动触发** Wi-Fi 连接
- ✅ 连接成功后显示详细信息（IP / 信号强度 / MAC / 链路速率）
- ✅ 支持 WPA/WPA2 密码热点 和 开放热点
- ✅ 可配置重试次数和超时时间
- ✅ 支持热点模式（车机作为 Wi-Fi 热点）

### 3. 日志系统
- ✅ 实时显示系统运行日志
- ✅ 所有按钮点击事件均有日志记录
- ✅ 按级别彩色显示（Debug/Info/Warn/Error）
- ✅ 支持清空日志、滚动到底部

### 4. 应用列表
- ✅ 获取系统全部已安装应用
- ✅ 支持按应用名/包名实时搜索
- ✅ 支持筛选用户应用 / 全部应用（含系统）
- ✅ 点击即可直接启动对应应用

### 5. 后台自启动服务
- ✅ 开机广播接收（支持 vivo 快启）
- ✅ 应用启动自动运行后台服务
- ✅ **每30秒**循环检测蓝牙/Wi-Fi状态
- ✅ Service 设置 `START_STICKY`，崩溃自动重启
- ✅ 蓝牙/Wi-Fi 断开后自动重连

### 6. vivo 无感连接车机（亿联）
- ✅ 适配 vivo FuntouchOS 后台策略
- ✅ 使用 Wi-Fi Lock 防止系统断开 Wi-Fi
- ✅ 通过反射设置蓝牙设备最高优先级
- ✅ 亿联设备名自动识别（YEALINK/YEA-）
- ✅ 智能推断车机热点 SSID 候选列表
- ✅ 提供 vivo 系统权限引导提示

---

## 📱 使用说明

### 首次配置
1. 安装 APK 后授予所有权限（位置权限为蓝牙扫描必需）
2. 点击**蓝牙设置** → 扫描 → 点击选择目标车机设备 → 保存
3. 点击**Wi-Fi设置** → 填写车机热点 SSID 和密码 → 保存
4. 点击**启动**按钮或重启手机后自动运行

### 亿联车机配置示例
```
蓝牙设备名: YEALINK   (模糊匹配)
Wi-Fi SSID: YEALINK_T46G (精确填写)
Wi-Fi 密码: (车机热点密码)
```

### vivo 手机额外设置
为保证后台持续运行，需要在系统设置中：
1. **设置 → 电池 → 后台高耗电** → 允许 CarConnect
2. **设置 → 更多设置 → 应用权限 → 自启动** → 开启 CarConnect
3. **设置 → 更多设置 → 辅助功能 → 自启动管理** → 允许

---

## 🔨 编译构建

### 环境要求
- Android Studio 4.2+ 或 命令行 Gradle 7.3+
- Android SDK API 22-30
- JDK 8+

### 命令行构建
```bash
# 设置 SDK 路径
echo "sdk.dir=$ANDROID_HOME" > local.properties

# 构建 Debug APK
./gradlew assembleDebug

# APK 路径
app/build/outputs/apk/debug/app-debug.apk
```

或使用便捷脚本：
```bash
bash build.sh
```

---

## 📁 项目结构

```
CarConnectApp/
├── app/src/main/
│   ├── AndroidManifest.xml          # 权限、组件声明
│   ├── java/com/carconnect/app/
│   │   ├── app/
│   │   │   └── CarConnectApplication.java   # Application 入口
│   │   ├── activity/
│   │   │   ├── MainActivity.java            # 主界面
│   │   │   ├── BluetoothSettingsActivity.java # 蓝牙设置
│   │   │   ├── WifiSettingsActivity.java     # Wi-Fi设置
│   │   │   ├── AppListActivity.java          # 应用列表
│   │   │   └── LogActivity.java             # 日志界面
│   │   ├── service/
│   │   │   ├── CarConnectService.java        # 主后台服务
│   │   │   ├── BluetoothService.java         # 蓝牙连接服务
│   │   │   └── WifiService.java             # Wi-Fi连接服务
│   │   ├── receiver/
│   │   │   ├── BootReceiver.java            # 开机自启广播
│   │   │   ├── BluetoothReceiver.java        # 蓝牙状态广播
│   │   │   └── WifiReceiver.java            # Wi-Fi状态广播
│   │   ├── adapter/
│   │   │   ├── LogAdapter.java              # 日志 RecyclerView
│   │   │   └── AppListAdapter.java          # 应用列表 RecyclerView
│   │   ├── model/
│   │   │   ├── BluetoothConfig.java         # 蓝牙配置模型
│   │   │   ├── WifiConfig.java              # Wi-Fi配置模型
│   │   │   ├── LogEntry.java                # 日志条目模型
│   │   │   └── AppInfo.java                 # 应用信息模型
│   │   └── utils/
│   │       ├── LogManager.java              # 全局日志管理
│   │       ├── SharedPrefsManager.java      # 配置持久化
│   │       └── VivoCarConnectHelper.java    # vivo 专用工具
│   └── res/
│       ├── layout/                          # 界面布局
│       ├── drawable/                        # 图形资源
│       └── values/                          # 字符串/颜色/主题
└── build.gradle                             # 构建配置
```

---

## ⚠️ 注意事项

1. **蓝牙扫描**需要 `ACCESS_FINE_LOCATION` 权限（Android 系统要求）
2. **Android 11+** 需要额外申请 `BLUETOOTH_SCAN` + `BLUETOOTH_CONNECT` 权限
3. vivo 手机需要手动关闭电池优化才能保证后台持续运行
4. 亿联车机蓝牙/热点名称请以实际设备为准，建议扫描后选择
5. Wi-Fi 连接功能在 Android 10+ 行为有变化，此 App 兼容处理

---

## 📋 权限列表

| 权限 | 用途 |
|------|------|
| BLUETOOTH / BLUETOOTH_ADMIN | 蓝牙基础操作 |
| BLUETOOTH_SCAN / CONNECT | Android 12+ 蓝牙扫描/连接 |
| ACCESS_FINE/COARSE_LOCATION | 蓝牙扫描必需 |
| ACCESS_WIFI_STATE / CHANGE_WIFI_STATE | Wi-Fi连接 |
| RECEIVE_BOOT_COMPLETED | 开机自启 |
| FOREGROUND_SERVICE | 前台服务 |
| WAKE_LOCK | Wi-Fi Lock |
| QUERY_ALL_PACKAGES | 获取应用列表 |
