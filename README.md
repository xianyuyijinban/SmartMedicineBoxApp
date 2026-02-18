# 智能药箱 Android APP 📱💊

[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.0-blue.svg)](https://kotlinlang.org)
[![Android](https://img.shields.io/badge/Android-24+-green.svg)](https://developer.android.com)
[![MQTT](https://img.shields.io/badge/MQTT-3.1.1-orange.svg)](https://mqtt.org)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

一款基于MQTT协议与智能硬件通信的Android应用，用于远程监控和控制智能药箱设备。

<p align="center">
  <img src="docs/screenshots/home_screen.png" width="280" alt="主界面">
  <img src="docs/screenshots/history_screen.png" width="280" alt="历史数据">
  <img src="docs/screenshots/settings_screen.png" width="280" alt="设置界面">
</p>

## ✨ 功能特性

- 🔗 **MQTT协议通信** - 实时连接智能药箱设备
- 📊 **传感器数据监控** - 温度、湿度、气压实时显示
- 📱 **设备状态监测** - 在线状态、电量、WiFi信号
- 🎮 **远程控制** - 开关盖、LED控制、上报间隔设置
- 🔔 **智能告警推送** - 离线、温度/湿度异常、倾斜检测
- 📈 **历史数据图表** - 24小时趋势图和统计分析
- 🔄 **自动重连机制** - 网络波动自动恢复连接
- 🌙 **深色主题支持** - Material Design 3 动态主题

## 🏗️ 架构概览

```
┌─────────────────────────────────────────────────────────────┐
│                          UI Layer                           │
│         Jetpack Compose + Material Design 3                 │
└────────────────────────┬────────────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────────────┐
│                     ViewModel Layer                         │
│              StateFlow + Coroutines                         │
└────────────────────────┬────────────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────────────┐
│                   Repository Layer                          │
│        HistoryRepository + NotificationManager              │
└────────────────────────┬────────────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────────────┐
│                     Data Layer                              │
│    Room Database    │    MQTT (Paho)    │   Notification   │
└─────────────────────────────────────────────────────────────┘
```

## 🚀 快速开始

### 环境要求

- Android Studio Hedgehog (2023.1.1) 或更新版本
- JDK 17+
- Android SDK 24+ (Android 7.0)
- Kotlin 1.9.0+

### 安装步骤

1. **克隆仓库**
   ```bash
   git clone https://github.com/YOUR_USERNAME/SmartMedicineBoxApp.git
   cd SmartMedicineBoxApp
   ```

2. **打开项目**
   使用Android Studio打开项目目录

3. **同步Gradle**
   点击 "Sync Project with Gradle Files"

4. **运行应用**
   连接设备或启动模拟器，点击运行按钮

### 配置MQTT Broker

在应用设置界面中配置：
- **MQTT Broker**: `tcp://192.168.1.100:1883`
- **设备ID**: `medicine_box_001`

## 📡 MQTT通信协议

### 主题规范

| 主题 | 类型 | 说明 |
|------|------|------|
| `medicine/{device_id}/sensors` | 订阅 | 传感器数据 |
| `medicine/{device_id}/status` | 订阅 | 设备状态 |
| `medicine/{device_id}/control` | 发布 | 控制命令 |
| `medicine/{device_id}/control/response` | 订阅 | 命令响应 |

### 数据格式

```json
{
    "timestamp": 1234567890,
    "device_id": "medicine_box_001",
    "state": "closed",
    "environment": {
        "temperature": 25.30,
        "humidity": 55.50,
        "pressure": 101325.00,
        "altitude": 0.00
    },
    "motion": {
        "accel_x": 0.015,
        "accel_y": -0.008,
        "accel_z": 0.995,
        "gyro_x": 0.50,
        "gyro_y": -0.30,
        "gyro_z": 0.10,
        "pitch": 2.15,
        "roll": -1.02,
        "vibration": 0.005
    },
    "valid": 1
}
```

## 🔔 告警条件

| 条件 | 级别 | 通知方式 |
|------|------|----------|
| 设备离线15秒+ | 🔴 Critical | 振动+声音 |
| 温度 > 30°C 或 < 10°C | 🟠 Warning | 振动+声音 |
| 湿度 > 70% 或 < 30% | 🟠 Warning | 振动+声音 |
| 药箱倾斜 | 🟠 Warning | 振动+声音 |
| 药箱打开 | 🟢 Info | 静默通知 |

## 📸 界面预览

### 主界面
- 设备连接状态指示
- 实时环境数据显示
- 药箱状态监控
- 快速控制按钮

### 历史数据
- 温度/湿度趋势图
- 统计分析（最小/平均/最大）
- 时间范围筛选（1小时~3天）

### 设置界面
- MQTT Broker配置
- 设备ID设置
- 连接状态管理

## 🛠️ 技术栈

- **UI**: Jetpack Compose 1.6.x, Material Design 3
- **架构**: MVVM, StateFlow, Repository Pattern
- **数据库**: Room 2.6.1
- **网络**: Eclipse Paho MQTT Client
- **异步**: Kotlin Coroutines, Flow
- **日志**: Timber
- **JSON**: Gson

## 📁 项目结构

```
app/src/main/java/com/smartmedicine/
├── mqtt/              # MQTT连接管理
├── data/              # 数据模型和数据库
│   ├── model/        # 数据类
│   └── db/           # Room数据库
├── repository/        # 数据仓库
├── notification/      # 系统通知
├── box/              # 业务逻辑
│   ├── manager/      # 管理器
│   └── service/      # 后台服务
└── ui/               # UI层
    ├── screens/      # 页面
    ├── components/   # 组件
    └── theme/        # 主题
```

## 📝 开发文档

详细开发文档请查看 [DEVELOPMENT.md](./DEVELOPMENT.md)

包含内容：
- 架构设计详解
- API接口文档
- 数据库设计
- 代码规范
- 构建指南

## 🤝 贡献

欢迎贡献代码！请遵循以下步骤：

1. Fork 本项目
2. 创建功能分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'feat: Add some AmazingFeature'`)
4. 推送分支 (`git push origin feature/AmazingFeature`)
5. 创建 Pull Request

## 📄 许可证

本项目采用 MIT 许可证 - 查看 [LICENSE](LICENSE) 文件了解详情

## 👨‍💻 开发者

- **XiaoJunWei** - 硬件开发
- **SmartMedicine Team** - APP开发

## 🙏 致谢

- [Eclipse Paho](https://www.eclipse.org/paho/) - MQTT客户端
- [Jetpack Compose](https://developer.android.com/jetpack/compose) - UI框架
- [Material Design](https://m3.material.io/) - 设计系统

---

<p align="center">
  Made with ❤️ for Smart Healthcare
</p>
