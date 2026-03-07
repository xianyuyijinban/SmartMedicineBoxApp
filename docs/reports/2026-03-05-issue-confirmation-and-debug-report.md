# SmartMedicineBox Issue Confirmation and Debug Report (2026-03-05)

## Scope

- Firmware (active Keil build path): `MDK-ARM/code`
- Android app: `SmartMedicineBoxApp/app`

## Results by Issue

1. **Critical - ESP8266 init retry underflow**  
   - **Confirmed** in `MDK-ARM/code/esp8266.c` (`while (retry--)` + post-check).  
   - **Fixed** by replacing with bounded attempt loop and explicit success flag.

2. **Critical - Ring buffer written circularly but parsed via raw C string**  
   - **Confirmed** in `MDK-ARM/code/esp8266.c` (direct `strstr((char *)rx_buffer, ...)`).  
   - **Fixed** by adding snapshot-based parsing (`ESP8266_CopyRxSnapshot`) and buffer reset helper (`ESP8266_ClearRxBuffer`).

3. **High - Dual MQTT state sources can desynchronize**  
   - **Confirmed** in `MDK-ARM/code/app_tasks.c` (`mqtt_connected` local flag) and `esp8266.c` state transitions.  
   - **Fixed** by removing local `mqtt_connected` dependency in task loop and relying on driver state; driver now sets `MQTT_CONNECTING -> MQTT_CONNECTED` in `ESP8266_MQTT_ConnectToBroker`.

4. **High - SensorTask anti-accumulation semaphore logic swallows token**  
   - **Confirmed** in `MDK-ARM/code/app_tasks.c` (`osSemaphoreAcquire(...,0)` branch).  
   - **Fixed** by using non-consuming release-only signaling and ignoring full-queue return.

5. **High - Aliyun MQTT signature timestamp inconsistency**  
   - **Confirmed** in `AliyunMqttConfig.kt`/`AliyunMqttManager.kt` (different timestamp sources for clientId/password).  
   - **Fixed** by introducing `buildConnectionCredentials(...)` with one shared timestamp and using it in manager connect flow.

6. **Medium - Android timestamp formatting assumes seconds**  
   - **Confirmed** in `SensorData.kt`, `DeviceStatus.kt`, `CommandResponse.kt` (`timestamp * 1000`).  
   - **Fixed** by introducing `DeviceTimestampFormatter.formatUptime(...)` and switching model formatters to device-uptime milliseconds.

7. **Medium - Settings validation doesn't surface specific error messages**  
   - **Confirmed** in `SettingsScreen.kt` (`validateInputs` boolean only).  
   - **Fixed** by introducing structured validation result `validateSettingsInputs(...)` and binding field-level error text in UI actions.

8. **Medium - AGP/Gradle build-chain mismatch claim**  
   - **Not confirmed in this workspace**.  
   - With writable env override (`GRADLE_USER_HOME`, `ANDROID_USER_HOME`), both `testDebugUnitTest` and `assembleDebug` passed on AGP 8.2.2 + Gradle 8.13.
   - Observed failures were environment permission related (`.gradle` / `.android` / kotlin-daemon path), not reproducible as version incompatibility.

## Validation Evidence

- Android unit tests passed:
  - `AliyunMqttConfigTest` (`tests=1, failures=0`)
  - `DeviceTimestampFormatTest` (`tests=2, failures=0`)
  - `SettingsValidationTest` (`tests=3, failures=0`)
- Android build passed: `assembleDebug`
- Existing host-side firmware unit checks still pass:
  - `test_display_logic`
  - `test_st7789_color`
  - `test_display_ui_format`

## Notes

- Firmware full compile was not performed in this shell because project uses RVDS FreeRTOS port (`portable/RVDS/ARM_CM4F`), which is not directly checkable with GNU syntax checks here.
