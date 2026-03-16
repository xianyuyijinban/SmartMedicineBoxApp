# 智能药箱 Android APP

## 1. 项目概述

这是智能药箱配套 Android APP，负责完成以下工作：
- 连接 EMQX / 通用 MQTT Broker
- 订阅药箱的实时数据、状态和告警事件
- 下发控制命令（立即上报、上报间隔、额定值、蜂鸣器开关、复位）
- 在通知栏推送环境异常、跌落、箱盖行为等事件

当前 UI 已按“家用药箱”方向调整：
- 主色调为浅蓝 + 白色
- 图标更新为蓝白药箱风格
- 首页状态卡片、控制卡片、风险卡片统一改为更清晰的家用风格布局

## 2. 当前已适配功能

### 2.1 MQTT 连接与状态管理
- 支持手动配置 Broker、设备 ID、用户名、密码。
- 已移除阿里云 IoT 专用接入代码和文档，只保留 EMQX / 通用 MQTT 流程。
- APP 内部区分：
  - `MQTT 已连接`
  - `设备已在线`
- 仅 Broker 连接成功时不会误显示“设备正常”；在真正收到 `status` 或 `sensors` 数据前，首页显示 `尚未连接`。
- APP 挂后台后 MQTT 不因 Activity 销毁主动断开，可由 Paho 自动重连维持连接。

### 2.2 首页显示
- 环境数据：温度、湿度、环境风险等级
- 安全状态：箱盖状态、打开时长、跌落风险
- 箱体状态：`closed/opened/moving/tilted`
- 设备控制区：
  - 第一行三个模块：`立即上报`、`上报间隔`、`额定值`
  - 第二行：`蜂鸣器开关`
  - 第三行：`重置设备`
- 已彻底删除气压/海拔显示

### 2.3 通知联动
- `env_abnormal`：通知栏推送环境异常
- `env_recovered`：提示恢复正常
- `drop_detected`：立即推送跌落异常
- `drop_alarm_cancelled(stop_push=1)`：停止继续推送该次跌落告警
- `lid_abnormal_opened` / `lid_open_timeout` / `lid_closed`：同步箱盖行为事件

### 2.4 刷新与异常处理
- 左上角刷新按钮仅在 MQTT 已连接且当前不处于连接中/刷新中时可点击，未连接时不再触发白屏卡死。
- 刷新中重复点击会被拒绝。
- 收到控制应答或新一帧设备数据后，会自动结束刷新态。
- 解析失败、连接失败、刷新超时都会给出明确提示。

## 3. 当前默认联调参数

默认值定义在 `app/src/main/java/com/smartmedicine/ui/MainActivity.kt`：

```kotlin
const val DEFAULT_MQTT_BROKER = "ssl://jaf12a6c.ala.cn-hangzhou.emqxsl.cn:8883"
const val DEFAULT_DEVICE_ID = "box001"
const val DEFAULT_MQTT_USERNAME = "yunmenglin"
const val DEFAULT_MQTT_PASSWORD = "12345678y"
```

如果你的板端仍走本机 `mqtt_tls_proxy.py`，APP 侧仍然直接连 EMQX 即可；APP 和板端不要求使用同一个入口地址，但主题与设备 ID 要一致。

## 4. 协议摘要

APP 当前适配的核心字段如下：

```json
{
  "timestamp": 123456789,
  "device_id": "medicine_box_001",
  "state": "closed",
  "environment": {
    "temperature": 25.30,
    "humidity": 55.50
  },
  "environment_limits": {
    "temperature_rated": 15.00,
    "temperature_low": 10.50,
    "temperature_high": 19.50,
    "humidity_rated": 50.00,
    "humidity_low": 35.00,
    "humidity_high": 65.00
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
  "behavior": {
    "lid_state": "closed",
    "open_duration_ms": 0,
    "open_abnormal": 0,
    "open_reason": "normal"
  },
  "risk": {
    "env_level": "normal",
    "drop_state": "none"
  },
  "alerts": {
    "env_abnormal": 0,
    "temperature_abnormal": 0,
    "humidity_abnormal": 0
  },
  "valid": 1
}
```

说明：
- APP 不再依赖 `pressure` / `altitude`。
- `behavior` 用于箱盖状态和异常行为展示。
- `risk` 用于环境风险与跌落风险展示。

更完整的协议说明见固件仓库中的 `APP_INTERFACE.md`。

## 5. 主要代码位置

```text
app/src/main/java/com/smartmedicine/
├── data/
│   ├── db/                         # Room 数据库
│   └── model/                      # SensorData / AlertEvent / BehaviorData / RiskData 等
├── mqtt/
│   └── MqttManager.kt              # MQTT 连接、订阅、发布、错误回调
├── notification/
│   └── NotificationManager.kt      # 通知栏推送
└── ui/
    ├── MainActivity.kt             # 页面导航 + ViewModel 主逻辑
    ├── MainUiStateLogic.kt         # 刷新态/在线态逻辑
    ├── components/                 # 首页组件与对话框
    ├── screens/                    # Home / History / Settings
    └── theme/                      # 浅蓝白主题与图标风格
```

## 6. 构建与安装

### 6.1 单元测试

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

### 6.2 构建 Debug APK

```powershell
.\gradlew.bat :app:assembleDebug
```

### 6.3 APK 输出位置

```text
app/build/outputs/apk/debug/app-debug.apk
```

本次同步后，该 APK 已重新构建通过。

## 7. 已验证结果

本次修改后已验证：
- `:app:testDebugUnitTest` 通过
- `:app:assembleDebug` 通过

覆盖到的关键问题包括：
- MQTT 连接失败不再静默无提示
- 未连接设备时不再误显示“正常”
- 刷新按钮不再导致白屏卡死
- 控制卡片标题与模块文字恢复可见
- pressure/altitude 已从 UI、模型和资源层清除
- APP 后台保持连接，不因界面销毁主动断联

## 8. 测试建议

实际联调时，建议按以下顺序检查：
1. 在设置页确认 Broker、设备 ID、用户名、密码正确。
2. 先建立 MQTT 连接，再观察首页是否从 `尚未连接` 切到在线。
3. 测试 `立即上报`，确认刷新状态能正确开始和结束。
4. 修改额定值，观察设备是否回传 `control/response`。
5. 人为触发环境异常、跌落、箱盖事件，确认首页与通知栏同步变化。
