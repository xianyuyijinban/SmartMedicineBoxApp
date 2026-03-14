# SmartMedicineBox Safety and UI Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Fix APP refresh instability, remove pressure from the firmware-to-APP contract, and deliver lid behavior detection, filtered drop confirmation, graded environment risk, and a home-style light-blue APP refresh.

**Architecture:** Split the work into a firmware protocol/logic track and an Android APP parsing/UI track. Introduce new `behavior` and `risk` protocol objects for clarity, keep `state` for coarse compatibility, and use TDD-style narrow tests around pure parsing and decision logic before changing production code.

**Tech Stack:** STM32 C with FreeRTOS and ESP8266 MQTT, Kotlin Android with Jetpack Compose, Gson, JUnit4, Gradle.

---

### Task 1: Write the approved design to disk

**Files:**
- Create: `C:\Users\xiangyu\SmartMedicineBox\.worktrees\safety-features-root\docs\plans\2026-03-14-smartmedicinebox-safety-ui-design.md`

**Step 1: Verify the design file exists**

Run: `Get-Item 'C:\Users\xiangyu\SmartMedicineBox\.worktrees\safety-features-root\docs\plans\2026-03-14-smartmedicinebox-safety-ui-design.md'`
Expected: file exists

**Step 2: Commit the design doc**

Run:

```bash
git -C C:\Users\xiangyu\SmartMedicineBox\.worktrees\safety-features-root add docs/plans/2026-03-14-smartmedicinebox-safety-ui-design.md
git -C C:\Users\xiangyu\SmartMedicineBox\.worktrees\safety-features-root commit -m "docs: add safety and UI design"
```

### Task 2: Add APP refresh-state regression tests

**Files:**
- Create: `C:\Users\xiangyu\.config\superpowers\worktrees\SmartMedicineBoxApp\safety-features-app\app\src\test\java\com\smartmedicine\ui\RefreshStateTest.kt`
- Modify: `C:\Users\xiangyu\.config\superpowers\worktrees\SmartMedicineBoxApp\safety-features-app\app\src\main\java\com\smartmedicine\ui\MainActivity.kt`

**Step 1: Write the failing test**

Cover:
- refresh is ignored when MQTT is disconnected
- refresh enters refreshing state when publish is allowed
- refresh clears on timeout or response

**Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests com.smartmedicine.ui.RefreshStateTest`
Expected: FAIL because refresh-state logic does not exist yet

**Step 3: Write minimal implementation**

Add explicit refresh state handling in `MainViewModel`.

**Step 4: Run test to verify it passes**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests com.smartmedicine.ui.RefreshStateTest`
Expected: PASS

### Task 3: Fix APP refresh crash path and split connection state

**Files:**
- Modify: `C:\Users\xiangyu\.config\superpowers\worktrees\SmartMedicineBoxApp\safety-features-app\app\src\main\java\com\smartmedicine\ui\MainActivity.kt`
- Modify: `C:\Users\xiangyu\.config\superpowers\worktrees\SmartMedicineBoxApp\safety-features-app\app\src\main\java\com\smartmedicine\mqtt\MqttManager.kt`
- Modify: `C:\Users\xiangyu\.config\superpowers\worktrees\SmartMedicineBoxApp\safety-features-app\app\src\main\java\com\smartmedicine\ui\screens\HomeScreen.kt`

**Step 1: Add a failing test for online-vs-connected separation**

Extend `RefreshStateTest` or add a dedicated UI-state test to prove broker connect alone must not mark the device as online.

**Step 2: Run the test to verify it fails**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests com.smartmedicine.ui.RefreshStateTest`
Expected: FAIL against current behavior

**Step 3: Implement the minimal fix**

- add `mqttConnected`, `deviceOnline`, `isRefreshing`, `refreshError`
- guard `publishNow()`
- stop setting device-online on broker connect alone
- finish refresh on command response or fresh sensor frame
- add refresh timeout path
- wrap parse/update entry points defensively

**Step 4: Run tests**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests com.smartmedicine.ui.RefreshStateTest`
Expected: PASS

### Task 4: Add APP parsing tests for new protocol objects

**Files:**
- Create: `C:\Users\xiangyu\.config\superpowers\worktrees\SmartMedicineBoxApp\safety-features-app\app\src\test\java\com\smartmedicine\data\model\BehaviorRiskParsingTest.kt`
- Modify: `C:\Users\xiangyu\.config\superpowers\worktrees\SmartMedicineBoxApp\safety-features-app\app\src\main\java\com\smartmedicine\data\model\SensorData.kt`
- Create: `C:\Users\xiangyu\.config\superpowers\worktrees\SmartMedicineBoxApp\safety-features-app\app\src\main\java\com\smartmedicine\data\model\BehaviorData.kt`
- Create: `C:\Users\xiangyu\.config\superpowers\worktrees\SmartMedicineBoxApp\safety-features-app\app\src\main\java\com\smartmedicine\data\model\RiskData.kt`

**Step 1: Write the failing test**

Cover:
- parse `behavior`
- parse `risk`
- tolerate missing legacy fields

**Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests com.smartmedicine.data.model.BehaviorRiskParsingTest`
Expected: FAIL because those classes/fields do not exist

**Step 3: Implement minimal model changes**

- add `BehaviorData`
- add `RiskData`
- wire into `SensorData`

**Step 4: Run test**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests com.smartmedicine.data.model.BehaviorRiskParsingTest`
Expected: PASS

### Task 5: Remove pressure from APP models and previews

**Files:**
- Modify: `C:\Users\xiangyu\.config\superpowers\worktrees\SmartMedicineBoxApp\safety-features-app\app\src\main\java\com\smartmedicine\data\model\EnvironmentData.kt`
- Modify: `C:\Users\xiangyu\.config\superpowers\worktrees\SmartMedicineBoxApp\safety-features-app\app\src\main\java\com\smartmedicine\data\db\SensorDataEntity.kt`
- Modify: `C:\Users\xiangyu\.config\superpowers\worktrees\SmartMedicineBoxApp\safety-features-app\app\src\main\java\com\smartmedicine\ui\preview\Previews.kt`
- Modify: `C:\Users\xiangyu\.config\superpowers\worktrees\SmartMedicineBoxApp\safety-features-app\app\src\test\java\com\smartmedicine\ui\components\EnvironmentCardSourceTest.kt`

**Step 1: Write/extend a failing test**

Prove APP source/model layer no longer requires `pressure`.

**Step 2: Run the test to verify it fails**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests com.smartmedicine.ui.components.EnvironmentCardSourceTest`
Expected: FAIL if any model/UI source still exposes pressure

**Step 3: Implement minimal removal**

- remove pressure field usage from `EnvironmentData`
- remove pressure from entity
- clean previews

**Step 4: Run tests**

Run:

```bash
.\gradlew.bat :app:testDebugUnitTest --tests com.smartmedicine.ui.components.EnvironmentCardSourceTest
.\gradlew.bat :app:testDebugUnitTest --tests com.smartmedicine.data.model.BehaviorRiskParsingTest
```

Expected: PASS

### Task 6: Add firmware host-side tests for environment grading

**Files:**
- Create: `C:\Users\xiangyu\SmartMedicineBox\.worktrees\safety-features-root\MDK-ARM\code\tests\test_sensor_env_risk.c`
- Modify: `C:\Users\xiangyu\SmartMedicineBox\.worktrees\safety-features-root\MDK-ARM\code\sensor_manager.c`
- Modify: `C:\Users\xiangyu\SmartMedicineBox\.worktrees\safety-features-root\MDK-ARM\code\sensor_manager.h`

**Step 1: Write the failing test**

Cover:
- normal
- warning after threshold exceed
- critical by hard exceed
- recovery hysteresis

**Step 2: Run test to verify it fails**

Run: compile and execute the host test with the same local compiler flow used for other small C tests
Expected: FAIL because grading helper does not exist

**Step 3: Implement minimal pure helper logic**

Extract a pure environment-risk helper callable from a host test.

**Step 4: Run test**

Expected: PASS

### Task 7: Add firmware host-side tests for lid behavior transitions

**Files:**
- Create: `C:\Users\xiangyu\SmartMedicineBox\.worktrees\safety-features-root\MDK-ARM\code\tests\test_lid_behavior.c`
- Modify: `C:\Users\xiangyu\SmartMedicineBox\.worktrees\safety-features-root\MDK-ARM\code\sensor_manager.c`
- Modify: `C:\Users\xiangyu\SmartMedicineBox\.worktrees\safety-features-root\MDK-ARM\code\sensor_manager.h`

**Step 1: Write the failing test**

Cover:
- closed -> opening -> opened
- opened -> closing -> closed
- abnormal open by vibration
- open timeout

**Step 2: Run test to verify it fails**

Expected: FAIL before helper/state machine exists

**Step 3: Implement minimal lid behavior state machine**

**Step 4: Run test**

Expected: PASS

### Task 8: Add firmware host-side tests for confirmed drop filtering

**Files:**
- Create: `C:\Users\xiangyu\SmartMedicineBox\.worktrees\safety-features-root\MDK-ARM\code\tests\test_drop_filter.c`
- Modify: `C:\Users\xiangyu\SmartMedicineBox\.worktrees\safety-features-root\MDK-ARM\code\app_tasks.c`
- Modify: `C:\Users\xiangyu\SmartMedicineBox\.worktrees\safety-features-root\MDK-ARM\code\app_tasks.h`

**Step 1: Write the failing test**

Cover:
- single spike rejected
- sustained `>= 6G` becomes suspected
- suspected plus posture/vibration confirmation becomes confirmed

**Step 2: Run test to verify it fails**

Expected: FAIL before helper exists

**Step 3: Implement minimal helper logic**

**Step 4: Run test**

Expected: PASS

### Task 9: Remove pressure from firmware JSON and docs-facing data model

**Files:**
- Modify: `C:\Users\xiangyu\SmartMedicineBox\.worktrees\safety-features-root\MDK-ARM\code\sensor_manager.c`
- Modify: `C:\Users\xiangyu\SmartMedicineBox\.worktrees\safety-features-root\MDK-ARM\code\sensor_manager.h`
- Modify: `C:\Users\xiangyu\SmartMedicineBox\.worktrees\safety-features-root\SmartMedicineBox\Core\Src\app_tasks.c`
- Modify: `C:\Users\xiangyu\SmartMedicineBox\.worktrees\safety-features-root\SmartMedicineBox\Core\Inc\app_tasks.h`

**Step 1: Implement after tests exist**

- remove `pressure/altitude` from published JSON
- keep local optional BMP280 reads only if still needed for temperature fusion, otherwise simplify

**Step 2: Verify**

Check generated JSON output path and build

### Task 10: Implement firmware behavior/risk protocol fields

**Files:**
- Modify: `C:\Users\xiangyu\SmartMedicineBox\.worktrees\safety-features-root\MDK-ARM\code\sensor_manager.c`
- Modify: `C:\Users\xiangyu\SmartMedicineBox\.worktrees\safety-features-root\MDK-ARM\code\sensor_manager.h`

**Step 1: Add fields to box data/status structures**

**Step 2: Populate `behavior` and `risk`**

**Step 3: Append them to JSON**

**Step 4: Re-run host tests**

Expected: PASS

### Task 11: Correct KEY2 cancel handling and confirmed-drop alarm flow

**Files:**
- Modify: `C:\Users\xiangyu\SmartMedicineBox\.worktrees\safety-features-root\MDK-ARM\code\app_tasks.c`
- Modify: `C:\Users\xiangyu\SmartMedicineBox\.worktrees\safety-features-root\README.md`

**Step 1: Write or extend a failing test if helperized**

At minimum, add a host-test helper around button-source selection if practical.

**Step 2: Implement**

- read `KEY2(PB15)` for cancel
- keep `KEY3` for buzzer feature toggle only
- only alarm on confirmed drop

**Step 3: Verify**

Re-run host tests and inspect code paths

### Task 12: Extend APP alert-event parsing for new events and levels

**Files:**
- Modify: `C:\Users\xiangyu\.config\superpowers\worktrees\SmartMedicineBoxApp\safety-features-app\app\src\main\java\com\smartmedicine\data\model\AlertEvent.kt`
- Modify: `C:\Users\xiangyu\.config\superpowers\worktrees\SmartMedicineBoxApp\safety-features-app\app\src\main\java\com\smartmedicine\ui\MainActivity.kt`
- Modify: `C:\Users\xiangyu\.config\superpowers\worktrees\SmartMedicineBoxApp\safety-features-app\app\src\main\java\com\smartmedicine\notification\NotificationManager.kt`
- Create: `C:\Users\xiangyu\.config\superpowers\worktrees\SmartMedicineBoxApp\safety-features-app\app\src\test\java\com\smartmedicine\data\model\AlertEventAdvancedParsingTest.kt`

**Step 1: Write the failing test**

Cover:
- `env_abnormal` with `level`
- `lid_abnormal_opened`
- `lid_open_timeout`
- `lid_closed`
- enhanced `drop_detected`

**Step 2: Run test**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests com.smartmedicine.data.model.AlertEventAdvancedParsingTest`
Expected: FAIL

**Step 3: Implement parsing and notification handling**

**Step 4: Run test**

Expected: PASS

### Task 13: Update APP home UI for behavior/risk and visual refresh

**Files:**
- Modify: `C:\Users\xiangyu\.config\superpowers\worktrees\SmartMedicineBoxApp\safety-features-app\app\src\main\java\com\smartmedicine\ui\components\EnvironmentCard.kt`
- Modify: `C:\Users\xiangyu\.config\superpowers\worktrees\SmartMedicineBoxApp\safety-features-app\app\src\main\java\com\smartmedicine\ui\components\StatusIndicator.kt`
- Modify: `C:\Users\xiangyu\.config\superpowers\worktrees\SmartMedicineBoxApp\safety-features-app\app\src\main\java\com\smartmedicine\ui\screens\HomeScreen.kt`
- Modify: `C:\Users\xiangyu\.config\superpowers\worktrees\SmartMedicineBoxApp\safety-features-app\app\src\main\java\com\smartmedicine\ui\theme\Color.kt`
- Modify: `C:\Users\xiangyu\.config\superpowers\worktrees\SmartMedicineBoxApp\safety-features-app\app\src\main\res\values\themes.xml`

**Step 1: Add/extend a failing source-level or state test where practical**

At minimum, lock down that pressure is absent and new behavior/risk labels exist.

**Step 2: Implement**

- light-blue/white palette
- cleaner status badges
- behavior/risk rows
- refresh-state messaging
- reduce emoji emphasis

**Step 3: Run tests/build**

Run:

```bash
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
```

Expected: PASS

### Task 14: Update launcher icon assets

**Files:**
- Modify/Create under: `C:\Users\xiangyu\.config\superpowers\worktrees\SmartMedicineBoxApp\safety-features-app\app\src\main\res\mipmap-*`
- Modify: `C:\Users\xiangyu\.config\superpowers\worktrees\SmartMedicineBoxApp\safety-features-app\app\src\main\res\mipmap-anydpi-v26\*`

**Step 1: Add icon assets**

Use a simple home-medicine-box visual in blue/white.

**Step 2: Build APK**

Run: `.\gradlew.bat :app:assembleDebug`
Expected: PASS

### Task 15: Sync documentation

**Files:**
- Modify: `C:\Users\xiangyu\SmartMedicineBox\.worktrees\safety-features-root\README.md`
- Modify: `C:\Users\xiangyu\SmartMedicineBox\.worktrees\safety-features-root\APP_INTERFACE.md`
- Modify: `C:\Users\xiangyu\.config\superpowers\worktrees\SmartMedicineBoxApp\safety-features-app\README.md`

**Step 1: Update protocol and UI docs**

- remove pressure/altitude
- add `behavior` and `risk`
- document refresh behavior
- document visual refresh and new notifications

**Step 2: Verify docs**

Search for stale `pressure` protocol text and outdated drop logic.

### Task 16: Final verification

**Files:**
- Verify whole diff only

**Step 1: Run APP verification**

Run:

```bash
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
```

Expected: PASS

**Step 2: Run firmware verification**

Run the host-side C regression tests added in `MDK-ARM\code\tests` and, if available in the environment, the Keil/UV build used by the repository.

Expected: tests pass, firmware build succeeds or any limitation is reported explicitly.

**Step 3: Review requirement checklist**

Confirm:
- refresh no longer whitescreens
- pressure removed end-to-end
- lid abnormal/open-timeout behavior works
- drop filtering works with KEY2 cancel
- environment grading works
- APP UI is light-blue/white and less cheap-looking
- READMEs updated
