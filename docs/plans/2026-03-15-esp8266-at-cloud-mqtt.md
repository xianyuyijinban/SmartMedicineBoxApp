# ESP8266 AT Cloud MQTT Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Switch ESP8266 cloud MQTT connectivity back to AT-command mode and prefer direct EMQX cloud connection over the current raw socket path.

**Architecture:** Add a small testable transport-policy helper that defines the AT candidate order for EMQX cloud ports. Update `esp8266.c` to use that helper so cloud connections try AT MQTT TLS/WSS candidates instead of activating raw mode. Keep the existing raw implementation in tree but stop selecting it for the cloud path.

**Tech Stack:** C, ESP8266 AT commands, Keil MDK-ARM, host-side gcc unit test

---

### Task 1: Define cloud AT transport candidates

**Files:**
- Create: `C:/Users/xiangyu/SmartMedicineBox/MDK-ARM/code/tests/test_esp8266_mqtt_transport.c`
- Create: `C:/Users/xiangyu/SmartMedicineBox/MDK-ARM/code/esp8266_mqtt_transport.h`
- Create: `C:/Users/xiangyu/SmartMedicineBox/MDK-ARM/code/esp8266_mqtt_transport.c`

**Step 1: Write the failing test**

Add a host test that expects:
- requested port `8883` -> attempt `0` selects `port=8883`, `scheme=2`, `path=""`
- requested port `8883` -> attempt `1` selects `port=8084`, `scheme=7`, `path="/mqtt"`
- requested port `8084` -> attempt `0` selects `port=8084`, `scheme=7`, `path="/mqtt"`
- requested port `8084` -> attempt `1` selects `port=8883`, `scheme=2`, `path=""`

**Step 2: Run test to verify it fails**

Run:
`gcc -I C:/Users/xiangyu/SmartMedicineBox/MDK-ARM/code C:/Users/xiangyu/SmartMedicineBox/MDK-ARM/code/tests/test_esp8266_mqtt_transport.c -o C:/Users/xiangyu/SmartMedicineBox/MDK-ARM/code/tests/test_esp8266_mqtt_transport.exe`

Expected: compile or link failure because the helper does not exist yet.

**Step 3: Write minimal implementation**

Implement a tiny helper returning the EMQX cloud AT candidate sequence.

**Step 4: Run test to verify it passes**

Run:
`gcc -I C:/Users/xiangyu/SmartMedicineBox/MDK-ARM/code C:/Users/xiangyu/SmartMedicineBox/MDK-ARM/code/tests/test_esp8266_mqtt_transport.c C:/Users/xiangyu/SmartMedicineBox/MDK-ARM/code/esp8266_mqtt_transport.c -o C:/Users/xiangyu/SmartMedicineBox/MDK-ARM/code/tests/test_esp8266_mqtt_transport.exe`

Then run:
`C:/Users/xiangyu/SmartMedicineBox/MDK-ARM/code/tests/test_esp8266_mqtt_transport.exe`

Expected: PASS with exit code `0`.

### Task 2: Switch cloud MQTT path back to AT mode

**Files:**
- Modify: `C:/Users/xiangyu/SmartMedicineBox/MDK-ARM/code/esp8266.c`

**Step 1: Use the helper in cloud initialization**

Update cloud-port handling so `8883` and `8084` no longer activate raw mode by default.

**Step 2: Probe AT candidates during connect**

For cloud ports:
- try AT candidate 0
- if config/connect fails, try candidate 1
- preserve a diagnostic code showing the current failure stage

**Step 3: Keep non-cloud behavior stable**

Do not change the existing non-cloud 1883 AT flow.

### Task 3: Verify and document

**Files:**
- Modify: `C:/Users/xiangyu/SmartMedicineBox/README.md`
- Modify: `C:/Users/xiangyu/SmartMedicineBox/SmartMedicineBox/README.md`

**Step 1: Update docs**

Document that the board now prefers AT MQTT cloud direct connect with candidate order:
- `8883/TLS`
- `8084/WSS`

**Step 2: Run verification**

Run host test, then build firmware:
`C:/Keil_v5/UV4/UV4.exe -r C:/Users/xiangyu/SmartMedicineBox/MDK-ARM/Smartbox.uvprojx -j0`

**Step 3: Flash hardware**

Run:
`python -m pyocd load C:/Users/xiangyu/SmartMedicineBox/MDK-ARM/Smartbox/Smartbox.axf -u 0001A0000001 -t stm32g431rbtx -e chip`
