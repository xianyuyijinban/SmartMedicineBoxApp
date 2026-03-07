# SmartMedicineBox Code Review, Functional Test, and Debug Report

- Date: 2026-02-22
- Scope:
  - Firmware: `Core/`, `SmartMedicineBox/`
  - Android App: `SmartMedicineBoxApp/`
- Workspace analyzed: `C:/Users/xiangyu/SmartMedicineBox_work_20260222`
- Original source path (read-only in this session): `D:/STM32CubeMXProject/item/SmartMedicineBox`

## 0. Executive Summary

- 已完成全面审查、自动化功能测试、缺陷定位与修复。
- 共识别问题（含改进项）9项：
  - 已修复：3项（2个高优先级逻辑缺陷 + 1个中优先级输入校验缺陷）
  - 未修复但已给出方案：6项
- Android 自动化验证通过：
  - `testDebugUnitTest`: 9/9 通过，0 失败
  - `lintDebug`: 构建成功，195 条 warning（0 error）

## 1. Review Findings

### 1.1 Coding Standards

1. `esp8266.c` 使用 `atoi` 但缺少 `<stdlib.h>`，会导致隐式声明问题（已修复）。
   - 证据: `SmartMedicineBox/Drivers/ESP8266/esp8266.c:11`, `SmartMedicineBox/Drivers/ESP8266/esp8266.c:218`
2. Android 项目 lint 警告数量较高（195），可维护性风险较大。
   - 主要类型：`UnusedResources`、`HardcodedText`、`GradleDependency`、`StaticFieldLeak`
   - 证据: `SmartMedicineBoxApp/app/build/reports/lint-results-debug.html:180`

### 1.2 Logic and Correctness

1. [HIGH][已修复] MQTT 连接成功后状态未切换为 `MQTT_CONNECTED`，导致发布分支不可达。
   - 根因: `ESP8266_MQTT_ConnectToBroker` 返回成功前未更新状态机。
   - 影响: `MQTTTask` 的 `ESP8266_STATE_MQTT_CONNECTED` 分支无法稳定进入，数据上报可能长期不触发。
   - 修复: 在连接前置为 `MQTT_CONNECTING`，成功后置为 `MQTT_CONNECTED`，失败回退 `WIFI_CONNECTED`。
   - 证据: `SmartMedicineBox/Drivers/ESP8266/esp8266.c:294`, `SmartMedicineBox/Drivers/ESP8266/esp8266.c:304`

2. [HIGH][已修复] USART3 接收回调读取来源错误，存在错包/丢包风险。
   - 根因: 回调中直接读取 `RDR`，未使用 HAL 中断接收缓冲变量。
   - 影响: 高并发串口数据下可能读取到非预期字节，影响 MQTT 响应解析。
   - 修复: 引入统一的 `esp_rx_byte` 缓冲并在回调复用。
   - 证据: `Core/Src/main.c:57`, `Core/Src/main.c:111`, `Core/Src/main.c:196`, `Core/Src/main.c:199`

3. [MEDIUM][已修复] 设置页输入校验过弱，仅校验前缀。
   - 根因: 旧逻辑只检查 `tcp://` / `ssl://` 前缀，无法拦截无 host/port 等非法输入。
   - 影响: 用户可保存无效 Broker，触发连接失败与重试噪音。
   - 修复: 新增 `BrokerInputValidator`，对协议、host、port 范围、deviceId 格式做严格校验。
   - 证据: `SmartMedicineBoxApp/app/src/main/java/com/smartmedicine/ui/screens/BrokerInputValidator.kt:8`, `SmartMedicineBoxApp/app/src/main/java/com/smartmedicine/ui/screens/SettingsScreen.kt:385`

4. [MEDIUM][未修复] ESP8266 接收缓冲在 ISR 与任务侧并发访问，无互斥保护。
   - 风险位置: `rx_buffer/rx_write_idx/rx_read_idx` 在 `ESP8266_UART_RxCallback` 与 `ESP8266_SendATCommand`/`ESP8266_ProcessRxData` 间共享。
   - 证据: `SmartMedicineBox/Drivers/ESP8266/esp8266.c:16`, `SmartMedicineBox/Drivers/ESP8266/esp8266.c:85`, `SmartMedicineBox/Drivers/ESP8266/esp8266.c:414`, `SmartMedicineBox/Drivers/ESP8266/esp8266.c:429`
   - 建议: 使用环形缓冲 + 原子索引/临界区，或消息队列拆分 ISR/任务解析职责。

### 1.3 Security

1. [MEDIUM][未修复] 固件网络参数硬编码在头文件，存在泄漏与环境切换风险。
   - 证据: `SmartMedicineBox/Core/Inc/app_tasks.h:41`, `SmartMedicineBox/Core/Inc/app_tasks.h:42`, `SmartMedicineBox/Core/Inc/app_tasks.h:43`, `SmartMedicineBox/Core/Inc/app_tasks.h:44`
   - 建议: 改为编译时注入或 NVM 配置下发。

2. [MEDIUM][未修复] Android 清单启用明文流量与备份。
   - 证据: `SmartMedicineBoxApp/app/src/main/AndroidManifest.xml:42`, `SmartMedicineBoxApp/app/src/main/AndroidManifest.xml:50`
   - 风险: 明文 MQTT 可能被中间人窃听；备份策略可能带来配置泄露面。
   - 建议: 生产环境关闭 `usesCleartextTraffic`，优先 TLS；按需收紧 `allowBackup`。

### 1.4 Performance

1. [MEDIUM][未修复] 多处 `HAL_Delay`/轮询等待，实时性与功耗受影响。
   - 证据: `SmartMedicineBox/Drivers/ESP8266/esp8266.c:103`, `SmartMedicineBox/Drivers/ESP8266/esp8266.c:116`, `SmartMedicineBox/Core/Src/sensor_manager.c:37`
   - 建议: 改事件驱动（中断/队列/超时状态机），避免阻塞延时。

2. [LOW][未修复] 高频任务路径存在大量 `printf`，串口 IO 可能影响时序。
   - 证据: `SmartMedicineBox/Core/Src/app_tasks.c:128`, `SmartMedicineBox/Core/Src/app_tasks.c:180`, `SmartMedicineBox/Core/Src/app_tasks.c:205`
   - 建议: 使用等级日志 + 条件编译，默认关闭实时路径日志。

3. [LOW][未修复] lint 报告存在 `StaticFieldLeak` 与长向量路径告警。
   - 证据: `SmartMedicineBoxApp/app/build/reports/lint-results-debug.xml:283`, `SmartMedicineBoxApp/app/build/reports/lint-results-debug.xml:299`

## 2. Functional Testing

### 2.1 Core Flow Coverage

| 编号 | 测试对象 | 用例 | 结果 |
|---|---|---|---|
| T1 | `SensorData` 解析 | 解析完整传感 JSON（状态/环境/运动）并验证业务判定 | PASS |
| T2 | `ControlCommand` | 上报间隔边界（1, 3600） | PASS |
| T3 | `DeviceStatus` | WiFi RSSI 分级边界（-50/-60/-70/-80/-81） | PASS |
| T4 | 设置输入校验 | 合法与非法 Broker/deviceId 场景 | PASS |

### 2.2 Boundary and Error Cases

| 编号 | 场景 | 预期 | 结果 |
|---|---|---|---|
| B1 | interval = 0 | 抛出异常拒绝 | PASS |
| B2 | interval = 3601 | 抛出异常拒绝 | PASS |
| B3 | broker 无 host/port/越界端口/错误协议 | 校验失败 | PASS |
| B4 | deviceId 含空格或非法字符 | 校验失败 | PASS |

### 2.3 Automated Command Evidence

- Android:
  - 执行: `./gradlew.bat testDebugUnitTest`
  - 结果: BUILD SUCCESS
  - 证据: 
    - `SmartMedicineBoxApp/app/build/test-results/testDebugUnitTest/TEST-com.smartmedicine.ui.screens.BrokerInputValidatorTest.xml`（`tests="4" failures="0" errors="0"`）
    - `SmartMedicineBoxApp/app/build/test-results/testDebugUnitTest/TEST-com.smartmedicine.data.model.ModelBehaviorTest.xml`（`tests="5" failures="0" errors="0"`）
- 质量扫描:
  - 执行: `./gradlew.bat lintDebug`
  - 结果: BUILD SUCCESS
  - 证据: `SmartMedicineBoxApp/app/build/reports/lint-results-debug.html:180`（195 warnings）

## 3. Debug and Fixes

### 3.1 Fixed Defects List

1. **FIX-001: MQTT 状态机修复（HIGH）**
   - 文件: `SmartMedicineBox/Drivers/ESP8266/esp8266.c`
   - 根因: `ConnectToBroker` 未推进状态机
   - 解决: 显式设置 `MQTT_CONNECTING -> MQTT_CONNECTED`，失败回退 `WIFI_CONNECTED`
   - 关键行: `SmartMedicineBox/Drivers/ESP8266/esp8266.c:294`, `SmartMedicineBox/Drivers/ESP8266/esp8266.c:304`

2. **FIX-002: USART3 回调字节源修复（HIGH）**
   - 文件: `Core/Src/main.c`
   - 根因: 回调直接读 `RDR` 而不是 HAL 接收缓冲
   - 解决: 统一使用 `esp_rx_byte` 并持续重启 `HAL_UART_Receive_IT`
   - 关键行: `Core/Src/main.c:57`, `Core/Src/main.c:111`, `Core/Src/main.c:196`, `Core/Src/main.c:199`

3. **FIX-003: 输入校验器增强（MEDIUM）**
   - 文件:
     - `SmartMedicineBoxApp/app/src/main/java/com/smartmedicine/ui/screens/BrokerInputValidator.kt`
     - `SmartMedicineBoxApp/app/src/main/java/com/smartmedicine/ui/screens/SettingsScreen.kt`
   - 根因: 设置页校验逻辑过宽
   - 解决: 引入 URI + 端口范围 + deviceId 正则校验
   - 回归测试:
     - `SmartMedicineBoxApp/app/src/test/java/com/smartmedicine/ui/screens/BrokerInputValidatorTest.kt`
     - `SmartMedicineBoxApp/app/src/test/java/com/smartmedicine/data/model/ModelBehaviorTest.kt`

## 4. Remaining Risks and Constraints

1. 本次会话无法直接写入原始目录 `D:/STM32CubeMXProject/item/SmartMedicineBox`，修复已在可写副本实施。
2. 固件完整工程构建受工具链限制：
   - `esp8266.c` 已完成 `arm-none-eabi-gcc` 语法检查通过；
   - `main.c` 依赖工程内 RVDS FreeRTOS 端口，无法用当前 GNU 前端完成等价编译验证。
3. 未进行硬件在环测试（传感器实采、ESP8266 实链路、RTOS 时序），建议在板级环境补充。

## 5. Verification Evidence

- 计划文档: `docs/plans/2026-02-22-smartmedicinebox-audit-debug-plan.md`
- 审查报告: `docs/reports/2026-02-22-audit-debug-report.md`
- 关键修复代码位置:
  - `Core/Src/main.c:57`
  - `Core/Src/main.c:196`
  - `SmartMedicineBox/Drivers/ESP8266/esp8266.c:294`
  - `SmartMedicineBox/Drivers/ESP8266/esp8266.c:304`
  - `SmartMedicineBoxApp/app/src/main/java/com/smartmedicine/ui/screens/BrokerInputValidator.kt:8`
