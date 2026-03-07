# SmartMedicineBox ST7789 Adaptation Design

## 1. Scope

This design adapts a 1.3-inch ST7789 SPI display (240x240, 7-pin) into `SmartMedicineBox` and integrates it with the existing FreeRTOS task architecture.

Confirmed hardware and behavior:

- Display controller: `ST7789`
- Resolution: `240x240`
- Interface: `SPI`
- Backlight: `BLK low = off`
- Page switch key: `KEY1 (PC6)`, **pressed = high level**

Confirmed feature scope:

- Two display pages
- Page 1: environment and box state
- Page 2: system status (WiFi, MQTT, UART1, UART3, UART3 RX activity, uptime)
- KEY1 toggles page

## 2. Pin Mapping

Display wiring to MCU:

- `SCL -> PA5 (SPI1_SCK)`
- `SDA -> PA7 (SPI1_MOSI)`
- `DC  -> PC5`
- `RES -> PC4`
- `BLK -> PB0`
- `VCC -> 3.3V`
- `GND -> GND`

Existing project pin usage already matches this mapping except `RES`, which will use `PC4` as display reset output.

## 3. Architecture

Use a layered structure to keep extensibility:

- Driver layer: `st7789.[ch]`
  - SPI command/data transport
  - init sequence
  - primitive draw APIs (pixel/fill/rect/text)
- UI layer: `display_ui.[ch]`
  - page enum/state
  - page render functions
  - key debounce + page switching
- Task integration:
  - `DisplayTask` obtains runtime data and calls UI renderer
  - no business logic is moved into low-level display driver

Benefits:

- future UI expansion stays in UI layer
- display hardware changes stay in driver layer
- `app_tasks.c` remains task-focused

## 4. Page Definitions

### 4.1 Page 1: Environment + Box State

Fields:

- `Temp`: `data.env.temperature` (C)
- `Hum`: `data.env.humidity` (%RH)
- `Pres`: `data.env.pressure / 100.0` (hPa)
- `Box`: `SensorManager_GetStateString(data.state)`
- `Valid`: `data.is_valid` (OK/FAIL)

### 4.2 Page 2: System Status

Fields:

- `WiFi`: `ESP8266_GetState() >= ESP8266_STATE_WIFI_CONNECTED`
- `MQTT`: `ESP8266_GetState() == ESP8266_STATE_MQTT_CONNECTED`
- `UART1`: init status (initialized => INIT)
- `UART3`: init status (initialized => INIT)
- `UART3_RX`: has recent RX activity (timestamp window)
- `Uptime`: `HAL_GetTick()/1000`

`UART3_RX` is represented by recording last RX tick in UART callback and checking `now - last_rx_tick <= window_ms`.

## 5. Key Handling

Input: `KEY1 (PC6)`, active high.

Switch algorithm:

- Detect rising edge (unpressed -> pressed)
- Debounce delay
- Re-check pressed level
- Toggle page
- Wait until release to prevent repeated toggle

This avoids accidental multi-page jumps from contact bounce.

## 6. Refresh Strategy

Initial strategy:

- Refresh interval: `500ms` (aligned with current `DisplayTask` loop)
- Full-page redraw on first render and after page switch
- Periodic redraw on interval for correctness-first delivery

Future optimization point:

- Partial refresh for only changed fields if performance tuning is needed

## 7. Reliability and Risk Controls

Primary risks and mitigations:

- Wrong orientation:
  - keep `MADCTL` configurable (`0x00` / `0x70`)
- Display refresh blocking other tasks:
  - keep refresh interval moderate (500ms)
  - limit heavy operations in render path
- Key bounce causing repeated toggles:
  - edge-trigger + debounce + wait-release
- Misleading serial status:
  - use explicit UART init flag and RX activity window

## 8. Verification Plan (Manual Run-Level)

Post-flash checks:

1. Power-on display lights and shows page 1 data.
2. KEY1 press toggles page 1 <-> page 2.
3. Page 2 WiFi/MQTT reflects ESP state transitions.
4. UART3_RX transitions from idle to active when ESP data arrives.
5. Sensor sampling and MQTT behavior remain functional while display updates.

## 9. Files To Be Added/Modified

Planned additions:

- `MDK-ARM/code/st7789.h`
- `MDK-ARM/code/st7789.c`
- `MDK-ARM/code/display_ui.h`
- `MDK-ARM/code/display_ui.c`

Planned modifications:

- `MDK-ARM/code/app_tasks.c`
- `MDK-ARM/code/app_tasks.h`
- `Core/Inc/main.h` (add RES pin macro if needed)
- `Core/Src/gpio.c` (label/use PC4 as display RES output)
- `Core/Src/main.c` (record UART3 RX timestamp for status page)
- `MDK-ARM/Project with XiaoJunWei.uvprojx` (add new source/header files)

## 10. Out of Scope

Not included in this iteration:

- LVGL integration
- DMA accelerated display pipeline
- advanced UI animations/themes
- touch input support
