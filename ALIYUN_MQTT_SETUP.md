# 阿里云IoT MQTT配置指南

## 📋 概述

智能药箱APP支持连接阿里云物联网平台，提供更安全、稳定的云端MQTT服务。

---

## 🚀 快速配置

### 步骤1: 创建阿里云IoT产品

1. 登录 [阿里云物联网平台](https://iot.console.aliyun.com/)
2. 点击 **"公共实例"** → **"设备管理"** → **"产品"**
3. 点击 **"创建产品"**
4. 填写产品信息：
   - **产品名称**: 智能药箱
   - **所属品类**: 自定义品类 → 直连设备
   - **节点类型**: 直连设备
   - **联网方式**: WiFi
   - **数据格式**: ICA标准数据格式
5. 点击 **"确认"**

### 步骤2: 添加设备

1. 在产品列表中点击刚创建的产品
2. 点击 **"设备"** → **"添加设备"**
3. 填写设备名称：`medicine_box_001`
4. 点击 **"确认"**
5. **保存设备证书**（非常重要！）
   - ProductKey (产品密钥)
   - DeviceName (设备名称)
   - DeviceSecret (设备密钥)

### 步骤3: 配置APP

打开 `MainActivity.kt`，替换阿里云配置：

```kotlin
companion object {
    // 本地MQTT配置（默认）
    const val DEFAULT_MQTT_BROKER = "tcp://192.168.1.100:1883"
    const val DEFAULT_DEVICE_ID = "medicine_box_001"
    
    // 阿里云IoT MQTT配置
    const val ALIYUN_PRODUCT_KEY = "你的ProductKey"
    const val ALIYUN_DEVICE_NAME = "medicine_box_001"
    const val ALIYUN_DEVICE_SECRET = "你的DeviceSecret"
    const val ALIYUN_REGION = "cn-shanghai"
}
```

### 步骤4: 选择连接方式

在 `MainViewModel.kt` 中修改 `connect()` 方法：

**方式1: 使用普通MQTT（默认）**
```kotlin
// 保持现有代码不变
mqttManager.connect(...)
```

**方式2: 使用阿里云MQTT**
```kotlin
// 导入阿里云MQTT管理器
import com.smartmedicine.mqtt.aliyun.AliyunMqttManager
import com.smartmedicine.mqtt.aliyun.AliyunDeviceAuth

// 使用阿里云MQTT连接
private val aliyunMqttManager = AliyunMqttManager()

fun connect() {
    val auth = AliyunDeviceAuth(
        productKey = MainActivity.ALIYUN_PRODUCT_KEY,
        deviceName = MainActivity.ALIYUN_DEVICE_NAME,
        deviceSecret = MainActivity.ALIYUN_DEVICE_SECRET,
        region = MainActivity.ALIYUN_REGION
    )
    
    aliyunMqttManager.connect(
        auth = auth,
        useSSL = false,  // true表示使用SSL加密
        onConnected = { /* ... */ },
        onDisconnected = { /* ... */ },
        onSensorDataReceived = { /* ... */ },
        onError = { /* ... */ }
    )
}
```

---

## 🔧 阿里云MQTT特性

### 认证方式

阿里云IoT使用**一机一密**认证方式：

| 参数 | 说明 | 示例 |
|------|------|------|
| ProductKey | 产品唯一标识 | `a1X2b3C4d5E` |
| DeviceName | 设备名称 | `medicine_box_001` |
| DeviceSecret | 设备密钥 | `ab12cd34ef56...` |

### Broker地址格式

```
${ProductKey}.iot-as-mqtt.${Region}.aliyuncs.com:1883

# 示例
cn-shanghai区域:
a1X2b3C4d5E.iot-as-mqtt.cn-shanghai.aliyuncs.com:1883
```

### 支持的地域

| 地域代码 | 地域名称 |
|----------|----------|
| cn-shanghai | 华东2（上海） |
| cn-beijing | 华北2（北京） |
| cn-shenzhen | 华南1（深圳） |
| cn-hangzhou | 华东1（杭州） |
| cn-hongkong | 香港 |
| ap-southeast-1 | 新加坡 |
| ap-northeast-1 | 日本（东京） |
| eu-central-1 | 德国（法兰克福） |
| us-west-1 | 美国（硅谷） |

---

## 📡 主题格式对比

### 普通MQTT主题

```
medicine/{device_id}/sensors      # 传感器数据
medicine/{device_id}/status       # 设备状态
medicine/{device_id}/control      # 控制命令
medicine/{device_id}/control/response  # 命令响应
```

### 阿里云官方主题

```
# 属性上报
/sys/{productKey}/{deviceName}/thing/event/property/post

# 属性设置
/sys/{productKey}/{deviceName}/thing/service/property/set

# 自定义Topic
/{productKey}/{deviceName}/user/update
/{productKey}/{deviceName}/user/get
```

---

## 🔐 安全建议

### 1. 使用SSL加密（推荐）

```kotlin
aliyunMqttManager.connect(
    auth = auth,
    useSSL = true,  // 启用SSL加密
    // ...
)
```

SSL连接端口：**443**

### 2. 保护设备密钥

**不要**将真实的 `DeviceSecret` 提交到Git仓库！

**推荐做法：**
1. 将密钥存储在 `local.properties` 中
2. 使用BuildConfig注入
3. 或使用Android Keystore加密存储

示例 `local.properties`:
```properties
# 在 local.properties 中定义（不要提交到git）
ALIBABA_IOT_PRODUCT_KEY=a1X2b3C4d5E
ALIBABA_IOT_DEVICE_SECRET=your_secret_here
```

在 `build.gradle` 中读取：
```gradle
android {
    defaultConfig {
        Properties localProps = new Properties()
        localProps.load(project.rootProject.file('local.properties').newDataInputStream())
        
        buildConfigField "String", "ALIYUN_PRODUCT_KEY", "\"${localProps['ALIBABA_IOT_PRODUCT_KEY']}\""
        buildConfigField "String", "ALIYUN_DEVICE_SECRET", "\"${localProps['ALIBABA_IOT_DEVICE_SECRET']}\""
    }
}
```

---

## 💰 费用说明

阿里云物联网平台计费方式：

| 计费项 | 免费额度 | 超出费用 |
|--------|----------|----------|
| 消息通信 | 100万条/月 | 1.0元/百万条 |
| 设备连接 | 100万分钟/月 | 0.4元/百万分钟 |
| 规则引擎 | 100万条/月 | 0.5元/百万条 |

> 个人使用通常不会超过免费额度

---

## 🆘 常见问题

### Q1: 连接失败，提示"认证失败"

**原因**: ProductKey/DeviceName/DeviceSecret 不正确

**解决**: 
1. 检查三个参数是否正确
2. 确认设备已在阿里云控制台激活
3. 检查地域(region)是否正确

### Q2: 无法订阅主题

**原因**: 没有在阿里云控制台定义Topic

**解决**:
1. 进入产品详情
2. 点击 **"Topic类列表"**
3. 添加自定义Topic：
   - Topic: `/medicine/${deviceName}/sensors`
   - 操作权限: 发布和订阅

### Q3: SSL连接失败

**原因**: 证书问题或端口错误

**解决**:
- SSL端口是 **443**，不是 1883
- 确保设备支持TLS 1.2+

### Q4: 消息收不到

**原因**: Topic格式错误或权限不足

**解决**:
1. 检查Topic格式是否正确
2. 确认设备有该Topic的订阅权限
3. 检查阿里云控制台的消息日志

---

## 📚 相关链接

- [阿里云物联网平台文档](https://help.aliyun.com/document_detail/30522.html)
- [阿里云MQTT连接说明](https://help.aliyun.com/document_detail/73742.html)
- [设备认证文档](https://help.aliyun.com/document_detail/42649.html)

---

## 🎯 下一步

配置完成后，在设置界面输入阿里云设备信息，即可享受云端MQTT服务！

如有问题，欢迎提交Issue讨论。
