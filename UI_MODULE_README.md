# 智能药箱APP - UI界面模块

## 模块概述

本模块使用 **Jetpack Compose** + **Material Design 3** 实现了智能药箱Android APP的完整UI界面。

## 技术栈

- **UI框架**: Jetpack Compose 1.6.x
- **设计系统**: Material Design 3
- **导航**: Navigation Compose
- **状态管理**: ViewModel + StateFlow
- **主题**: 动态颜色支持 (Android 12+)

## 文件结构

```
app/src/main/java/com/smartmedicine/ui/
├── MainActivity.kt              # 主Activity (Compose版本)
├── theme/                       # 主题配置
│   ├── Color.kt                # 颜色定义
│   ├── Theme.kt                # 主题配置
│   └── Type.kt                 # 字体配置
├── components/                  # 可复用UI组件
│   ├── StatusIndicator.kt      # 状态指示器组件
│   ├── EnvironmentCard.kt      # 环境数据卡片
│   └── ControlButtons.kt       # 控制按钮区域
├── screens/                     # 页面屏幕
│   ├── HomeScreen.kt           # 主屏幕
│   └── SettingsScreen.kt       # 设置屏幕
└── preview/                     # 预览文件
    └── Previews.kt             # 组件预览
```

## 界面预览

### 主界面 (HomeScreen)
```
┌─────────────────────────────┐
│    智能药箱监控              │
│    medicine_box_001         │
├─────────────────────────────┤
│  [●] 设备在线               │
├─────────────────────────────┤
│  🌡️ 环境数据                │
│  ─────────────────          │
│  🌡️ 25.3°C  💧 55.5%        │
│  🌀 1013.25 hPa             │
├─────────────────────────────┤
│  📦 药箱状态: 已关闭        │
│  📳 振动: 正常              │
│  📐 倾斜: 正常              │
├─────────────────────────────┤
│  📱 设备信息                │
│  固件: 1.0.0  信号: -65dBm  │
├─────────────────────────────┤
│  [刷新] [设置间隔] [重置]   │
├─────────────────────────────┤
│      最后更新: 14:30:25     │
└─────────────────────────────┘
```

### 设置界面 (SettingsScreen)
```
┌─────────────────────────────┐
│ ← 设置                      │
├─────────────────────────────┤
│ MQTT 连接设置               │
│ ┌───────────────────────┐   │
│ │ tcp://192.168.1.100   │   │
│ └───────────────────────┘   │
├─────────────────────────────┤
│ 设备设置                    │
│ ┌───────────────────────┐   │
│ │ medicine_box_001      │   │
│ └───────────────────────┘   │
├─────────────────────────────┤
│ 连接状态: ● 已连接 [断开]   │
├─────────────────────────────┤
│ [保存并返回] [取消]         │
└─────────────────────────────┘
```

## 状态颜色说明

### 连接状态
- 🟢 **绿色** - 设备在线
- 🔴 **红色** - 设备离线
- 🟡 **黄色** - 连接中

### 药箱状态
- 🟢 **绿色** - 已关闭 (closed)
- 🔵 **蓝色** - 已打开 (opened)
- 🟠 **橙色** - 移动中 (moving)
- 🔴 **红色** - 倾斜状态 (tilted)

### 环境数据状态
- 🟢 **正常** - 温度 10-30°C, 湿度 30-70%
- 🟠 **警告** - 超出正常范围

## 主要组件

### 1. ConnectionStatusIndicator
显示设备连接状态的指示器组件。

```kotlin
ConnectionStatusIndicator(
    isOnline = true,           // 是否在线
    isConnecting = false       // 是否正在连接
)
```

### 2. EnvironmentDataCard
显示环境数据（温度、湿度、气压）的卡片组件。

```kotlin
EnvironmentDataCard(
    temperature = 25.3,        // 温度 °C
    humidity = 55.5,           // 湿度 %
    pressure = 101325.0        // 气压 Pa（自动转换为hPa）
)
```

### 3. BoxStatusCard
显示药箱状态（状态、振动、倾斜）的卡片组件。

```kotlin
BoxStatusCard(
    state = "closed",          // 状态: closed/opened/moving/tilted
    vibration = 0.005,         // 振动强度
    pitch = 2.15,              // 俯仰角
    roll = -1.02               // 横滚角
)
```

### 4. ControlButtonsSection
控制按钮区域，包含刷新、设置间隔、重置功能。

```kotlin
ControlButtonsSection(
    onRefresh = { /* 立即上报 */ },
    onReset = { /* 重置设备 */ },
    onSetInterval = { interval -> /* 设置间隔 */ },
    isConnected = true
)
```

### 5. AlertCard
告警提示卡片。

```kotlin
AlertCard(
    message = "温度异常",
    level = AlertLevel.WARNING,  // ERROR/WARNING/INFO
    onDismiss = { /* 关闭 */ }
)
```

## 使用说明

### 1. 设置MQTT连接
1. 点击主界面右上角设置图标
2. 输入MQTT Broker地址（如: tcp://192.168.1.100:1883）
3. 输入设备ID（如: medicine_box_001）
4. 点击"连接"按钮
5. 连接成功后点击"保存并返回"

### 2. 查看环境数据
- 主界面显示实时温度、湿度、气压
- 气压自动从Pa转换为hPa显示
- 温度和湿度异常时会显示警告

### 3. 查看药箱状态
- 显示当前药箱状态（已关闭/已打开/移动中/倾斜）
- 振动状态：正常/轻微/异常
- 倾斜状态：正常/倾斜（超过30度时警告）

### 4. 设备控制
- **立即上报**: 命令设备立即发送一次传感器数据
- **设置间隔**: 修改设备自动上报的时间间隔
- **重置设备**: 重置设备配置（需要确认）

## 数据单位转换

### 气压转换
```kotlin
// 原始数据: 101325 Pa
// 显示数据: 1013.25 hPa (除以100)
```

### 温度/湿度格式化
```kotlin
// 温度: 25.3 → "25.3°C"
// 湿度: 55.5 → "55.5%"
```

## 响应式适配

界面已针对不同屏幕尺寸进行适配：

- **手机竖屏**: 单列布局，全宽卡片
- **手机横屏**: 双列布局（环境数据并排）
- **平板**: 侧边导航，多列布局
- **折叠屏**: 自适应布局

## 主题切换

支持浅色/深色主题自动切换：

```kotlin
// 跟随系统
SmartMedicineBoxTheme {
    // 应用内容
}

// 强制深色主题
SmartMedicineBoxTheme(darkTheme = true) {
    // 应用内容
}
```

## 开发预览

在Android Studio中查看UI预览：

1. 打开 `ui/preview/Previews.kt`
2. 使用预览功能查看各组件效果
3. 支持不同设备尺寸预览

## 依赖配置

确保 `build.gradle` 包含以下依赖：

```gradle
dependencies {
    // Compose BOM
    implementation platform('androidx.compose:compose-bom:2024.02.00')
    
    // Material Design 3
    implementation 'androidx.compose.material3:material3'
    
    // Compose UI
    implementation 'androidx.compose.ui:ui'
    implementation 'androidx.compose.ui:ui-graphics'
    
    // Navigation
    implementation 'androidx.navigation:navigation-compose:2.7.7'
    
    // ViewModel
    implementation 'androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0'
}
```

## 更新日志

### v1.0.0
- 初始版本发布
- 实现主界面、设置界面
- 支持MQTT连接和数据显示
- 添加Material Design 3主题
- 支持浅色/深色主题

## 待优化项

1. 添加数据图表展示（温度趋势、湿度趋势）
2. 添加历史数据记录功能
3. 添加推送通知功能
4. 支持多个设备管理
5. 添加数据导出功能
