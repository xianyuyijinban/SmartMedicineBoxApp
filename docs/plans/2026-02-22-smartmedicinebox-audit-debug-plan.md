# SmartMedicineBox Audit and Debug Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 对 SmartMedicineBox 固件与 Android App 完成代码审查、功能测试、缺陷修复，并形成可追溯报告。

**Architecture:** 采用“双轨执行”：固件侧做静态审查 + 可执行验证，App 侧做构建/测试 + 关键流程验证。对每个缺陷严格执行“复现 -> 失败证据 -> 最小修复 -> 回归验证”。

**Tech Stack:** STM32G4 + HAL + FreeRTOS (C), Android + Kotlin + Gradle, MQTT (Aliyun/Paho)

---

### Task 1: Baseline and Scope Lock

**Files:**
- Read: `README.md`
- Read: `APP_INTERFACE.md`
- Read: `SmartMedicineBox/README.md`
- Create: `docs/reports/2026-02-22-audit-debug-report.md`

**Step 1: Capture baseline structure and entry points**

Run: `rg --files Core SmartMedicineBox SmartMedicineBoxApp`
Expected: 输出固件/驱动/App 的关键文件清单。

**Step 2: Define audit checklist and test matrix**

Write checklist in report file:
- 代码规范
- 逻辑正确性
- 安全性
- 性能优化点
- 核心流程测试
- 边界测试

**Step 3: Verify baseline command works**

Run: `Get-ChildItem docs/reports`
Expected: 报告目录存在。

### Task 2: Firmware Static Review and Risk Catalog

**Files:**
- Read: `SmartMedicineBox/Core/Src/app_tasks.c`
- Read: `SmartMedicineBox/Core/Src/sensor_manager.c`
- Read: `SmartMedicineBox/Drivers/ESP8266/esp8266.c`
- Read: `SmartMedicineBox/Drivers/Sensors/*.c`
- Update: `docs/reports/2026-02-22-audit-debug-report.md`

**Step 1: Identify deterministic defects and weak points**

Run: `rg -n "sprintf|strcpy|strcat|while \(1\)|HAL_.*Delay|malloc|free|TODO|FIXME" SmartMedicineBox Core`
Expected: 捕获风险模式和候选缺陷位置。

**Step 2: Validate each issue with code trace**

Run: `rg -n "<symbol>" <path>`
Expected: 形成“触发条件/影响范围/根因”三元证据。

**Step 3: Record firmware findings by severity**

Update report with CRITICAL/HIGH/MEDIUM/LOW 分级。

### Task 3: Android App Static Review and Security Checks

**Files:**
- Read: `SmartMedicineBoxApp/app/src/main/java/com/smartmedicine/**/*.kt`
- Read: `SmartMedicineBoxApp/app/build.gradle`
- Read: `SmartMedicineBoxApp/app/src/main/AndroidManifest.xml`
- Update: `docs/reports/2026-02-22-audit-debug-report.md`

**Step 1: Check secrets and credential handling**

Run: `rg -n "password|secret|token|clientId|username|deviceSecret|mqtt" SmartMedicineBoxApp/app/src/main/java SmartMedicineBoxApp/app`
Expected: 找到明文凭据、日志泄漏、弱校验。

**Step 2: Check lifecycle, threading and data correctness**

Run: `rg -n "GlobalScope|CoroutineScope|launch|collect|remember|mutableStateOf|Room|Dao" SmartMedicineBoxApp/app/src/main/java`
Expected: 捕获潜在资源泄漏与数据一致性问题。

**Step 3: Record app findings with remediation suggestion**

Update report with优先级、影响、修复建议。

### Task 4: Functional Testing (Core + Boundaries)

**Files:**
- Read: `APP_INTERFACE.md`
- Read: `SmartMedicineBox/App/APP_INTERFACE.md`
- Update: `docs/reports/2026-02-22-audit-debug-report.md`

**Step 1: Build test matrix from documented requirements**

覆盖：传感采集、状态识别、MQTT 发布、App 连接显示、异常断连恢复。

**Step 2: Execute app-side automated checks**

Run: `./gradlew.bat testDebugUnitTest lintDebug` (in `SmartMedicineBoxApp`)
Expected: 收集通过/失败明细。

**Step 3: Execute firmware-side verifiable checks**

Run available build/consistency command; if toolchain unavailable, run deterministic static checks and document limitation with evidence.

### Task 5: Bug Fixing with Root-Cause + TDD Loop

**Files:**
- Modify: `SmartMedicineBox/...` or `SmartMedicineBoxApp/...` (issue-dependent)
- Add/Modify test files under `SmartMedicineBoxApp/app/src/test/...` when applicable
- Update: `docs/reports/2026-02-22-audit-debug-report.md`

**Step 1: Reproduce one defect with failing evidence**

Run the smallest command/test proving failure.

**Step 2: Implement minimal fix for root cause**

One issue per patch; no bundled refactor.

**Step 3: Re-run targeted and full checks**

Run issue-specific test + `./gradlew.bat testDebugUnitTest` (if App issue) or equivalent firmware validation.

**Step 4: Record fix item**

记录：问题现象、根因、修复点、验证证据、回归风险。

### Task 6: Final Verification and Delivery

**Files:**
- Finalize: `docs/reports/2026-02-22-audit-debug-report.md`

**Step 1: Run final verification commands fresh**

- `./gradlew.bat testDebugUnitTest lintDebug` (if executable)
- Any targeted regression checks used during fixes

**Step 2: Validate report completeness**

必须包含：
- 审查报告（规范/逻辑/安全/性能）
- 功能测试结果（核心 + 边界）
- 修复清单（根因 + 方案 + 证据）
- 未完成项与阻塞说明

**Step 3: Deliver concise execution summary + artifacts paths**

输出主结论、风险排序、建议优先级。
