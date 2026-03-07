# 智能药箱系统 - 技术文档

## 1. 项目概述

### 1.1 系统功能
智能药箱监控系统实时采集环境数据（温湿度、气压）和姿态数据（加速度、陀螺仪），通过WiFi上传到MQTT服务器，支持手机APP远程监控药箱状态。

### 1.2 硬件平台
- **MCU**: STM32G431RBT6 (170MHz, ARM Cortex-M4)
- **传感器**: MPU6050(姿态) + AHT20(温湿度) + BMP280(气压)
- **通信**: ESP-01S WiFi模块
- **调试**: CH340N USB转串口

---

## 2. 系统架构

### 2.1 硬件连接图

```
┌─────────────────────────────────────────────────────────────┐
│                    STM32G431RBT6                            │
│                                                              │
│  ┌──────────────┐        ┌──────────────────────────────┐  │
│  │    I2C2      │        │           I2C3               │  │
│  │  (PA8/PA9)   │        │       (PC8/PC9)              │  │
│  └──────┬───────┘        └───────────┬──────────────────┘  │
│         │                            │                     │
│         ▼                            ▼                     │
│   ┌───────────┐               ┌───────────┐               │
│   │  MPU6050  │               │   AHT20   │               │
│   │  0x68     │               │   0x38    │               │
│   │  姿态传感器│               │  温湿度    │               │
│   └───────────┘               └─────┬─────┘               │
│                                     │                     │
│                                     ▼                     │
│                              ┌───────────┐               │
│                              │  BMP280   │               │
│                              │  0x76     │               │
│                              │  气压传感器│               │
│                              └───────────┘               │
│                                                            │
│  ┌──────────────┐        ┌──────────────────────────────┐  │
│  │   USART1     │        │          USART3              │  │
│  │ (PB6/PB7)    │        │     (PB10/PB11)              │  │
│  └──────┬───────┘        └───────────┬──────────────────┘  │
│         │                            │                     │
│         ▼                            ▼                     │
│   ┌───────────┐               ┌───────────┐               │
│   │  CH340N   │               │ ESP-01S   │               │
│   │  调试串口  │               │ WiFi模块   │               │
│   │  115200   │               │  115200   │               │
│   └───────────┘               └─────┬─────┘               │
│                                     │                     │
│                         ┌───────────┴───────────┐         │
│                         ▼                       ▼         │
│                  ┌─────────────┐        ┌─────────────┐   │
│                  │  PD2:EN     │        │  PC12:RST   │   │
│                  │  PB3:IO0    │        │             │   │
│                  └─────────────┘        └─────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

### 2.2 软件架构图

```
┌──────────────────────────────────────────────────────────────┐
│                        应用层 (APP)                           │
├──────────────────────────────────────────────────────────────┤
│  ┌──────────────────────────────────────────────────────┐   │
│  │              FreeRTOS 任务调度层                       │   │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ │   │
│  │  │SensorTask│ │ MQTTTask │ │DisplayTask│ │ LEDTask │ │   │
│  │  │ (100ms)  │ │  (5s)    │ │  (500ms) │ │ (动态)   │ │   │
│  │  └────┬─────┘ └────┬─────┘ └────┬─────┘ └────┬─────┘ │   │
│  └───────┼────────────┼────────────┼────────────┼───────┘   │
├──────────┼────────────┼────────────┼────────────┼───────────┤
│          ▼            ▼            │            │           │
│  ┌─────────────────────────────┐   │            │           │
│  │     业务逻辑层               │   │            │           │
│  │  ┌─────────────────────┐   │   │            │           │
│  │  │   sensor_manager    │◄──┘   │            │           │
│  │  │  - 数据融合          │       │            │           │
│  │  │  - 状态检测          │       │            │           │
│  │  │  - JSON生成          │       │            │           │
│  │  └─────────────────────┘       │            │           │
│  │  ┌─────────────────────┐       │            │           │
│  │  │      esp8266        │◄──────┘            │           │
│  │  │  - AT指令           │                    │           │
│  │  │  - MQTT协议         │                    │           │
│  │  │  - WiFi管理         │                    │           │
│  │  └─────────────────────┘                    │           │
│  └─────────────────────────────┘                │           │
├────────────────────────────────────────────────┼───────────┤
│                    驱动层                       │           │
│  ┌─────────────┐ ┌─────────────┐ ┌────────────┐│          │
│  │   mpu6050   │ │    aht20    │ │   bmp280   ││          │
│  │  (I2C2)     │ │   (I2C3)    │ │  (I2C3)    ││          │
│  └─────────────┘ └─────────────┘ └────────────┘│          │
│  ┌─────────────────────────────────────────────┐│          │
│  │              HAL/LL库层                      ││          │
│  │  I2C2 | I2C3 | USART1 | USART3 | GPIO | RTC ││          │
│  └─────────────────────────────────────────────┘│          │
└─────────────────────────────────────────────────┴───────────┘
```

### 2.3 数据流向图

```
传感器数据采集流程:
┌──────────┐    ┌──────────┐    ┌──────────┐
│  MPU6050 │    │   AHT20  │    │  BMP280  │
└────┬─────┘    └────┬─────┘    └────┬─────┘
     │               │               │
     ▼               ▼               ▼
┌─────────────────────────────────────────┐
│         SensorManager_ReadAll()         │
│         100Hz采样频率                    │
└──────────────────┬──────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────┐
│      MedicineBoxData_t 数据结构          │
│  ┌─────────────────────────────────┐    │
│  │  environment: 温度/湿度/气压/海拔 │    │
│  │  motion: 加速度/陀螺仪/姿态/振动  │    │
│  │  state: 药箱状态(关闭/打开/移动)  │    │
│  └─────────────────────────────────┘    │
└──────────────────┬──────────────────────┘
                   │
         ┌─────────┴─────────┐
         ▼                   ▼
┌─────────────────┐   ┌─────────────────┐
│   本地显示/LCD   │   │  MQTT发布(5s)   │
└─────────────────┘   └────────┬────────┘
                               │
                               ▼
                    ┌────────────────────┐
                    │  ESP8266 MQTT上传  │
                    │  medicine/box001/  │
                    └────────────────────┘
```

---

## 3. 目录结构详解

```
SmartMedicineBox/
│
├── 📁 Core/                          # 核心代码
│   ├── 📁 Inc/                       # 头文件
│   │   ├── 📄 app_tasks.h            # 任务配置和宏定义
│   │   └── 📄 sensor_manager.h       # 数据管理器接口
│   │
│   └── 📁 Src/                       # 源文件
│       ├── 📄 app_tasks.c            # FreeRTOS任务实现 (4个任务)
│       ├── 📄 sensor_manager.c       # 传感器数据融合与处理
│       └── 📄 main_user.c            # main.c修改参考模板
│
├── 📁 Drivers/                       # 驱动层
│   ├── 📁 Sensors/                   # 传感器驱动
│   │   ├── 📄 mpu6050.c/h            # MPU6050 6轴传感器
│   │   │                              #  - I2C地址: 0x68
│   │   │                              #  - 接口: I2C2 (PA8/PA9)
│   │   │                              #  - 功能: 加速度/陀螺仪/姿态角
│   │   │
│   │   ├── 📄 aht20.c/h              # AHT20 温湿度传感器
│   │   │                              #  - I2C地址: 0x38
│   │   │                              #  - 接口: I2C3 (PC8/PC9)
│   │   │                              #  - 功能: 温度/湿度
│   │   │
│   │   └── 📄 bmp280.c/h             # BMP280 气压传感器
│   │                                      #  - I2C地址: 0x76
│   │                                      #  - 接口: I2C3 (PC8/PC9)
│   │                                      #  - 功能: 气压/温度/海拔
│   │
│   └── 📁 ESP8266/                   # 通信模块驱动
│       ├── 📄 esp8266.c/h            # ESP-01S WiFi模块
│                                          #  - 接口: USART3 (PB10/PB11)
│                                          #  - 控制: PD2(EN), PC12(RST), PB3(IO0)
│                                          #  - 协议: MQTT over TCP
│
├── 📁 App/                           # 应用层文档
│   └── 📄 APP_INTERFACE.md           # 手机APP开发接口文档
│
└── 📄 README.md                      # 本文件 (技术文档)
```

---

## 4. 模块详细说明

### 4.1 传感器驱动层

#### 4.1.1 MPU6050 (mpu6050.c/h)
```c
// 主要API
uint8_t MPU6050_Init(void);                          // 初始化
uint8_t MPU6050_ReadData(MPU6050_Data_t *data);      // 读取原始数据
void MPU6050_CalculateAngles(MPU6050_Data_t *data);  // 计算姿态角

// 数据结构
typedef struct {
    int16_t accel_x, accel_y, accel_z;    // 原始值
    int16_t gyro_x, gyro_y, gyro_z;       // 原始值
    float accel_x_g, accel_y_g, accel_z_g; // 转换后(g)
    float gyro_x_dps, gyro_y_dps, gyro_z_dps; // 转换后(°/s)
    float pitch, roll;                    // 姿态角
    float temp_c;                         // 温度
} MPU6050_Data_t;
```

**关键配置**:
- 采样率: 100Hz (SMPLRT_DIV = 99)
- 加速度量程: ±2g
- 陀螺仪量程: ±250°/s
- 低通滤波: 41Hz带宽

#### 4.1.2 AHT20 (aht20.c/h)
```c
// 主要API
uint8_t AHT20_Init(void);
uint8_t AHT20_ReadDataBlocking(AHT20_Data_t *data, uint32_t timeout_ms);

// 数据结构
typedef struct {
    float humidity;      // 湿度 %RH
    float temperature;   // 温度 °C
} AHT20_Data_t;
```

**注意事项**:
- 测量时间: 约80ms
- 必须等待BUSY位清零后读取
- 首次使用需要校准

#### 4.1.3 BMP280 (bmp280.c/h)
```c
// 主要API
uint8_t BMP280_Init(void);
uint8_t BMP280_ReadData(BMP280_Data_t *data);

// 数据结构
typedef struct {
    float temperature;   // 温度 °C
    float pressure;      // 气压 Pa
    float altitude;      // 海拔 m
} BMP280_Data_t;
```

**关键配置**:
- 温度过采样: x2
- 气压过采样: x16
- IIR滤波: x4
- 工作模式: Normal (连续测量)

### 4.2 数据管理层 (sensor_manager.c)

```c
// 核心功能
void SensorManager_Init(void);                    // 初始化所有传感器
uint8_t SensorManager_ReadAll(void);              // 读取并融合数据
void SensorManager_GetData(MedicineBoxData_t *data);  // 获取数据
BoxState_t SensorManager_DetectState(void);       // 检测药箱状态
void SensorManager_CreateJSON(char *buf, uint16_t size); // 生成JSON

// 状态检测逻辑
if (vibration > 0.5g)      -> BOX_STATE_MOVING
else if (|pitch| > 30°)    -> BOX_STATE_TILTED
else if (accel_z < 0.7g)   -> BOX_STATE_OPENED
else                       -> BOX_STATE_CLOSED
```

**数据融合策略**:
- 温度: AHT20和BMP280取平均值
- 湿度: AHT20
- 气压/海拔: BMP280
- 姿态: MPU6050

### 4.3 通信层 (esp8266.c)

```c
// 初始化
void ESP8266_Init(void);

// WiFi功能
uint8_t ESP8266_WiFi_Connect(const char *ssid, const char *pwd);
uint8_t ESP8266_WiFi_IsConnected(void);

// MQTT功能
uint8_t ESP8266_MQTT_ConnectToBroker(const char *ip, uint16_t port);
uint8_t ESP8266_MQTT_Publish(const char *topic, const char *payload, uint8_t qos, uint8_t retain);
uint8_t ESP8266_MQTT_Subscribe(const char *topic, uint8_t qos);

// 状态机
ESP8266_State_t ESP8266_GetState(void);
// 状态: RESET -> INIT -> WIFI_CONNECTING -> WIFI_CONNECTED -> MQTT_CONNECTING -> MQTT_CONNECTED
```

### 4.4 任务层 (app_tasks.c)

| 任务名 | 优先级 | 周期 | 功能 |
|--------|--------|------|------|
| SensorTask | Normal | 100ms | 采集传感器数据，触发MQTT发布(每50次) |
| MQTTTask | AboveNormal | 动态 | WiFi/MQTT连接管理，数据发布 |
| DisplayTask | BelowNormal | 500ms | LCD显示更新(如有) |
| LEDTask | Low | 动态 | LED状态指示 |

**任务间通信**:
- 信号量: `mqttPublishSem` (SensorTask -> MQTTTask)
- 共享数据: `MedicineBoxData_t` (通过SensorManager_GetData访问)

---

## 5. 配置说明

### 5.1 网络配置 (app_tasks.h)
```c
// WiFi配置
#define WIFI_SSID           "YourWiFiSSID"
#define WIFI_PASSWORD       "YourWiFiPassword"

// MQTT服务器配置
#define MQTT_BROKER_IP      "192.168.1.100"
#define MQTT_BROKER_PORT    1883
#define MQTT_CLIENT_ID      "medicine_box_001"
#define MQTT_USERNAME       ""  // 如需要认证请填写
#define MQTT_PASSWORD       ""

// 主题配置
#define MQTT_TOPIC_DATA     "medicine/box001/sensors"
#define MQTT_TOPIC_STATUS   "medicine/box001/status"
#define MQTT_TOPIC_CONTROL  "medicine/box001/control"

// 采样间隔
#define SENSOR_SAMPLE_INTERVAL_MS   100     // 100ms = 10Hz
#define MQTT_PUBLISH_INTERVAL_MS    5000    // 5s
```

### 5.2 阈值配置 (sensor_manager.c)
```c
#define VIBRATION_THRESHOLD     0.5f    // 振动检测阈值 (g)
#define TILT_THRESHOLD          30.0f   // 倾斜检测阈值 (°)
```

---

## 6. 集成步骤

### 6.1 修改CubeMX配置
你的配置已包含大部分外设，确认以下配置：

```
I2C2: 
  - Mode: I2C
  - GPIO: PA8(SDA), PA9(SCL)
  - Timing: 0x40B285C2

I2C3:
  - Mode: I2C  
  - GPIO: PC8(SCL), PC9(SDA)
  - Timing: 0x40B285C2

USART1:
  - Mode: Asynchronous
  - GPIO: PB6(TX), PB7(RX)
  - Baud Rate: 115200

USART3:
  - Mode: Asynchronous
  - GPIO: PB10(TX), PB11(RX)
  - Baud Rate: 115200
  - NVIC: 启用USART3全局中断

GPIO:
  - PD2: Output (WIFIEN)
  - PC12: Output (WIFIRST)
  - PB3: Output (IO0)
  - PA3: Output (LED1)

FreeRTOS:
  - Version: CMSIS-RTOS V2
  - Memory: Dynamic
```

### 6.2 文件整合

1. **复制驱动文件**
   ```
   Drivers/Sensors/*.c -> Core/Src/
   Drivers/Sensors/*.h -> Core/Inc/
   Drivers/ESP8266/*.c -> Core/Src/
   Drivers/ESP8266/*.h -> Core/Inc/
   Core/Src/*.c -> Core/Src/
   Core/Inc/*.h -> Core/Inc/
   ```

2. **修改main.c**

   在 `/* USER CODE BEGIN Includes */` 区域添加：
   ```c
   /* USER CODE BEGIN Includes */
   #include "mpu6050.h"
   #include "aht20.h"
   #include "bmp280.h"
   #include "sensor_manager.h"
   #include "esp8266.h"
   #include "app_tasks.h"
   /* USER CODE END Includes */
   ```

   在 `/* USER CODE BEGIN 2 */` 区域添加（在osKernelInitialize之前）：
   ```c
   /* USER CODE BEGIN 2 */
   // 启动USART3接收中断
   static uint8_t esp_rx_byte;
   HAL_UART_Receive_IT(&huart3, &esp_rx_byte, 1);
   
   // 初始化应用
   App_Init();
   /* USER CODE END 2 */
   ```

   在 `MX_FREERTOS_Init()` 调用处改为：
   ```c
   // 注释掉自动生成的初始化
   // MX_FREERTOS_Init();
   
   // 启动自定义任务
   App_StartTasks();
   ```

3. **添加中断处理**

   在 `stm32g4xx_it.c` 中添加：
   ```c
   void USART3_IRQHandler(void)
   {
       HAL_UART_IRQHandler(&huart3);
   }
   ```

   在 `main.c` 的 `HAL_UART_RxCpltCallback` 中添加：
   ```c
   void HAL_UART_RxCpltCallback(UART_HandleTypeDef *huart)
   {
       static uint8_t esp_rx_byte;
       
       if (huart->Instance == USART3)
       {
           ESP8266_UART_RxCallback(esp_rx_byte);
           HAL_UART_Receive_IT(&huart3, &esp_rx_byte, 1);
       }
   }
   ```

---

## 7. 调试与维护

### 7.1 调试串口输出
USART1 (PB6/PB7) 输出调试信息，波特率115200。

### 7.2 LED状态指示
| LED状态 | 系统状态 |
|---------|----------|
| 慢闪(1Hz) | MQTT已连接，正常工作 |
| 快闪(5Hz) | WiFi已连，MQTT未连 |
| 常亮 | 错误状态 |
| 熄灭 | 初始化中 |

### 7.3 常见问题

**Q: 传感器读取失败**
- 检查I2C地址是否正确
- 检查I2C线是否接上拉电阻(4.7kΩ)
- 用示波器检查I2C波形

**Q: ESP8266无法连接WiFi**
- 检查USART3连接
- 确认WiFi名称和密码正确
- 检查ESP-01S固件是否支持MQTT AT指令

**Q: MQTT连接失败**
- 确认MQTT服务器IP和端口可访问
- 检查防火墙设置
- 使用MQTT.fx等工具测试服务器

### 7.4 日志级别
在 `app_tasks.c` 中定义：
```c
#define LOG_LEVEL_DEBUG   0
#define LOG_LEVEL_INFO    1
#define LOG_LEVEL_WARN    2
#define LOG_LEVEL_ERROR   3

#define CURRENT_LOG_LEVEL LOG_LEVEL_INFO
```

---

## 8. 扩展开发

### 8.1 添加新传感器
1. 在 `Drivers/Sensors/` 创建驱动文件
2. 在 `sensor_manager.c` 中添加读取逻辑
3. 更新 `MedicineBoxData_t` 结构体
4. 修改 `SensorManager_CreateJSON()` 函数

### 8.2 添加新任务
1. 在 `app_tasks.h` 定义任务参数
2. 在 `app_tasks.c` 实现任务函数
3. 在 `App_StartTasks()` 中创建任务

### 8.3 修改数据上报频率
修改 `app_tasks.h`:
```c
#define SENSOR_SAMPLE_INTERVAL_MS   100     // 传感器采样间隔
#define MQTT_PUBLISH_INTERVAL_MS    5000    // MQTT上报间隔
```

---

## 9. 版本历史

| 版本 | 日期 | 修改内容 |
|------|------|----------|
| v1.0 | 2026-02-17 | 初始版本，基础功能完成 |

---

## 10. 参考文档

- [APP开发接口](App/APP_INTERFACE.md)
- [STM32G4参考手册](https://www.st.com/)
- [MPU6050数据手册](https://invensense.tdk.com/)
- [ESP-AT指令集](https://docs.espressif.com/)
