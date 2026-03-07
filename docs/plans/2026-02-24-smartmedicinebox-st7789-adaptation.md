# SmartMedicineBox ST7789 Two-Page Display Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Integrate a 1.3-inch ST7789 (240x240) SPI display into SmartMedicineBox with two pages and KEY1 (PC6, active-high) page switching.

**Architecture:** Use a layered design: `st7789` driver for SPI/GPIO hardware access, `display_ui` for page rendering and key switching state machine, and `DisplayTask` for periodic data fetch + refresh orchestration. Keep business logic in existing managers (`sensor_manager`, `esp8266`) and avoid mixing hardware code with task logic.

**Tech Stack:** STM32G4 HAL, FreeRTOS/CMSIS-RTOS2, Keil MDK project (`.uvprojx`), host-side C unit tests via `gcc` for pure logic modules.

---

### Task 1: Add Page-Switch Logic Module (TDD first)

**Files:**
- Create: `MDK-ARM/code/display_logic.h`
- Create: `MDK-ARM/code/display_logic.c`
- Test: `MDK-ARM/code/tests/test_display_logic.c`

**Step 1: Write the failing test**

```c
// MDK-ARM/code/tests/test_display_logic.c
#include <assert.h>
#include <stdint.h>
#include "display_logic.h"

int main(void) {
    DisplayLogicState_t st = {0};
    DisplayPage_t page = DISPLAY_PAGE_ENV;

    // idle
    assert(DisplayLogic_UpdateKey(&st, 0, 0, &page) == 0);
    assert(page == DISPLAY_PAGE_ENV);

    // active-high press, debounce reached -> toggle
    assert(DisplayLogic_UpdateKey(&st, 1, 10, &page) == 0);
    assert(DisplayLogic_UpdateKey(&st, 1, 80, &page) == 1);
    assert(page == DISPLAY_PAGE_SYSTEM);

    // hold should not retrigger
    assert(DisplayLogic_UpdateKey(&st, 1, 120, &page) == 0);
    assert(page == DISPLAY_PAGE_SYSTEM);

    // release then press again -> toggle back
    assert(DisplayLogic_UpdateKey(&st, 0, 200, &page) == 0);
    assert(DisplayLogic_UpdateKey(&st, 1, 220, &page) == 0);
    assert(DisplayLogic_UpdateKey(&st, 1, 300, &page) == 1);
    assert(page == DISPLAY_PAGE_ENV);
    return 0;
}
```

**Step 2: Run test to verify it fails**

Run:

```powershell
gcc -std=c11 -Wall -Wextra -I MDK-ARM/code MDK-ARM/code/tests/test_display_logic.c MDK-ARM/code/display_logic.c -o build/test_display_logic.exe
```

Expected: FAIL with missing `display_logic.h` / `display_logic.c`.

**Step 3: Write minimal implementation**

```c
// MDK-ARM/code/display_logic.h
#ifndef DISPLAY_LOGIC_H
#define DISPLAY_LOGIC_H

#include <stdint.h>

typedef enum {
    DISPLAY_PAGE_ENV = 0,
    DISPLAY_PAGE_SYSTEM = 1
} DisplayPage_t;

typedef struct {
    uint8_t key_armed;
    uint8_t key_pressed_latched;
    uint32_t key_press_tick;
} DisplayLogicState_t;

uint8_t DisplayLogic_UpdateKey(DisplayLogicState_t *state,
                               uint8_t key_raw_high,
                               uint32_t now_ms,
                               DisplayPage_t *page);

#endif
```

```c
// MDK-ARM/code/display_logic.c
#include "display_logic.h"

#define DISPLAY_KEY_DEBOUNCE_MS 50U

uint8_t DisplayLogic_UpdateKey(DisplayLogicState_t *state,
                               uint8_t key_raw_high,
                               uint32_t now_ms,
                               DisplayPage_t *page) {
    if (!state || !page) return 0;

    if (!state->key_pressed_latched) {
        if (key_raw_high) {
            if (!state->key_armed) {
                state->key_armed = 1;
                state->key_press_tick = now_ms;
            } else if ((now_ms - state->key_press_tick) >= DISPLAY_KEY_DEBOUNCE_MS) {
                *page = (*page == DISPLAY_PAGE_ENV) ? DISPLAY_PAGE_SYSTEM : DISPLAY_PAGE_ENV;
                state->key_pressed_latched = 1;
                state->key_armed = 0;
                return 1;
            }
        } else {
            state->key_armed = 0;
        }
    } else if (!key_raw_high) {
        state->key_pressed_latched = 0;
    }
    return 0;
}
```

**Step 4: Run test to verify it passes**

Run:

```powershell
gcc -std=c11 -Wall -Wextra -I MDK-ARM/code MDK-ARM/code/tests/test_display_logic.c MDK-ARM/code/display_logic.c -o build/test_display_logic.exe
./build/test_display_logic.exe
```

Expected: PASS (exit code `0`).

**Step 5: Commit**

```bash
git add MDK-ARM/code/display_logic.h MDK-ARM/code/display_logic.c MDK-ARM/code/tests/test_display_logic.c
git commit -m "test: add TDD key debounce and page toggle logic"
```

If repository is not initialized as git, record a checkpoint in `docs/reports/` with modified file list.

### Task 2: Add ST7789 Driver Module

**Files:**
- Create: `MDK-ARM/code/st7789.h`
- Create: `MDK-ARM/code/st7789.c`
- Modify: `Core/Inc/main.h`
- Modify: `Core/Src/gpio.c`
- Test: `MDK-ARM/code/tests/test_st7789_color.c`

**Step 1: Write the failing test**

```c
// MDK-ARM/code/tests/test_st7789_color.c
#include <assert.h>
#include <stdint.h>
#include "st7789.h"

int main(void) {
    assert(ST7789_Color565(255, 0, 0) == 0xF800);
    assert(ST7789_Color565(0, 255, 0) == 0x07E0);
    assert(ST7789_Color565(0, 0, 255) == 0x001F);
    return 0;
}
```

**Step 2: Run test to verify it fails**

Run:

```powershell
gcc -std=c11 -Wall -Wextra -I MDK-ARM/code MDK-ARM/code/tests/test_st7789_color.c MDK-ARM/code/st7789.c -o build/test_st7789_color.exe
```

Expected: FAIL with missing `st7789` module.

**Step 3: Write minimal implementation**

```c
// MDK-ARM/code/st7789.h
#ifndef ST7789_H
#define ST7789_H

#include "main.h"
#include "spi.h"
#include <stdint.h>

#define ST7789_WIDTH  240
#define ST7789_HEIGHT 240

void ST7789_Init(void);
void ST7789_SetRotation(uint8_t madctl);
void ST7789_FillScreen(uint16_t color);
void ST7789_DrawString(uint16_t x, uint16_t y, const char *s, uint16_t fg, uint16_t bg);
uint16_t ST7789_Color565(uint8_t r, uint8_t g, uint8_t b);

#endif
```

```c
// MDK-ARM/code/st7789.c (minimal skeleton + tested helper)
#include "st7789.h"

uint16_t ST7789_Color565(uint8_t r, uint8_t g, uint8_t b) {
    return (uint16_t)(((r & 0xF8) << 8) | ((g & 0xFC) << 3) | (b >> 3));
}

void ST7789_Init(void) { /* TODO: full init sequence */ }
void ST7789_SetRotation(uint8_t madctl) { (void)madctl; }
void ST7789_FillScreen(uint16_t color) { (void)color; }
void ST7789_DrawString(uint16_t x, uint16_t y, const char *s, uint16_t fg, uint16_t bg) {
    (void)x; (void)y; (void)s; (void)fg; (void)bg;
}
```

Also add in `Core/Inc/main.h`:

```c
#define LCD_RES_Pin GPIO_PIN_4
#define LCD_RES_GPIO_Port GPIOC
```

**Step 4: Run test to verify it passes**

Run:

```powershell
gcc -std=c11 -Wall -Wextra -I MDK-ARM/code MDK-ARM/code/tests/test_st7789_color.c MDK-ARM/code/st7789.c -o build/test_st7789_color.exe
./build/test_st7789_color.exe
```

Expected: PASS (exit code `0`).

**Step 5: Commit**

```bash
git add MDK-ARM/code/st7789.h MDK-ARM/code/st7789.c MDK-ARM/code/tests/test_st7789_color.c Core/Inc/main.h
git commit -m "feat: add st7789 base driver and pin definitions"
```

### Task 3: Add UI Renderer Module For Two Pages

**Files:**
- Create: `MDK-ARM/code/display_ui.h`
- Create: `MDK-ARM/code/display_ui.c`
- Modify: `MDK-ARM/code/app_tasks.h`
- Modify: `MDK-ARM/code/app_tasks.c`
- Test: `MDK-ARM/code/tests/test_display_ui_format.c`

**Step 1: Write the failing test**

```c
// MDK-ARM/code/tests/test_display_ui_format.c
#include <assert.h>
#include <string.h>
#include "display_ui.h"

int main(void) {
    DisplayPage2Status_t s = {1, 0, 1, 1, 0, 123};
    char line[32];
    DisplayUI_FormatWifiLine(&s, line, sizeof(line));
    assert(strstr(line, "WiFi:OK") != 0);
    return 0;
}
```

**Step 2: Run test to verify it fails**

Run:

```powershell
gcc -std=c11 -Wall -Wextra -I MDK-ARM/code MDK-ARM/code/tests/test_display_ui_format.c MDK-ARM/code/display_ui.c -o build/test_display_ui_format.exe
```

Expected: FAIL because `display_ui` module does not exist.

**Step 3: Write minimal implementation**

```c
// MDK-ARM/code/display_ui.h
#ifndef DISPLAY_UI_H
#define DISPLAY_UI_H

#include "sensor_manager.h"
#include "display_logic.h"
#include <stdint.h>

typedef struct {
    uint8_t wifi_ok;
    uint8_t mqtt_ok;
    uint8_t uart1_init;
    uint8_t uart3_init;
    uint8_t uart3_rx_recent;
    uint32_t uptime_s;
} DisplayPage2Status_t;

void DisplayUI_Init(void);
void DisplayUI_RenderPage1(const MedicineBoxData_t *data);
void DisplayUI_RenderPage2(const DisplayPage2Status_t *status);
void DisplayUI_FormatWifiLine(const DisplayPage2Status_t *status, char *out, uint16_t out_len);

#endif
```

```c
// MDK-ARM/code/display_ui.c (minimal)
#include "display_ui.h"
#include "st7789.h"
#include <stdio.h>

void DisplayUI_Init(void) {
    ST7789_Init();
}

void DisplayUI_FormatWifiLine(const DisplayPage2Status_t *status, char *out, uint16_t out_len) {
    snprintf(out, out_len, "WiFi:%s", status->wifi_ok ? "OK" : "NO");
}

void DisplayUI_RenderPage1(const MedicineBoxData_t *data) {
    (void)data;
    ST7789_FillScreen(0x0000);
    ST7789_DrawString(8, 8, "Page1 Env", 0xFFFF, 0x0000);
}

void DisplayUI_RenderPage2(const DisplayPage2Status_t *status) {
    char line[32];
    ST7789_FillScreen(0x0000);
    ST7789_DrawString(8, 8, "Page2 System", 0xFFFF, 0x0000);
    DisplayUI_FormatWifiLine(status, line, sizeof(line));
    ST7789_DrawString(8, 30, line, 0xFFE0, 0x0000);
}
```

**Step 4: Run test to verify it passes**

Run:

```powershell
gcc -std=c11 -Wall -Wextra -I MDK-ARM/code MDK-ARM/code/tests/test_display_ui_format.c MDK-ARM/code/display_ui.c -o build/test_display_ui_format.exe
./build/test_display_ui_format.exe
```

Expected: PASS (exit code `0`).

**Step 5: Commit**

```bash
git add MDK-ARM/code/display_ui.h MDK-ARM/code/display_ui.c MDK-ARM/code/tests/test_display_ui_format.c
git commit -m "feat: add two-page display ui module"
```

### Task 4: Integrate With DisplayTask + KEY1 + UART RX Activity

**Files:**
- Modify: `MDK-ARM/code/app_tasks.c`
- Modify: `MDK-ARM/code/app_tasks.h`
- Modify: `Core/Src/main.c`

**Step 1: Write the failing integration test (behavior spec)**

Add a behavior checklist in test comments first:

```c
// EXPECTED:
// - KEY1 high-level press toggles page once per press cycle
// - Page1 shows env/state fields
// - Page2 shows WiFi/MQTT/UART1/UART3/UART3_RX/Uptime
// - UART3_RX becomes active when HAL_UART_RxCpltCallback receives data
```

**Step 2: Run build to verify current behavior is missing**

Run (Keil CLI):

```powershell
UV4 -b "MDK-ARM/Project with XiaoJunWei.uvprojx" -j0
```

Expected: Build succeeds, but runtime does not have required display pages/switching yet.

**Step 3: Write minimal implementation**

Core integration points:

```c
// app_tasks.c (DisplayTask loop pseudo)
static DisplayLogicState_t g_disp_logic;
static DisplayPage_t g_page = DISPLAY_PAGE_ENV;
extern volatile uint32_t g_uart3_last_rx_tick;

void DisplayTask(void *argument) {
    MedicineBoxData_t data;
    DisplayPage2Status_t status;
    DisplayUI_Init();

    for (;;) {
        uint8_t key_high = (HAL_GPIO_ReadPin(KEY1_GPIO_Port, KEY1_Pin) == GPIO_PIN_SET);
        if (DisplayLogic_UpdateKey(&g_disp_logic, key_high, HAL_GetTick(), &g_page)) {
            // force redraw after page switch
        }

        SensorManager_GetData(&data);

        if (g_page == DISPLAY_PAGE_ENV) {
            DisplayUI_RenderPage1(&data);
        } else {
            ESP8266_State_t s = ESP8266_GetState();
            status.wifi_ok = (s >= ESP8266_STATE_WIFI_CONNECTED);
            status.mqtt_ok = (s == ESP8266_STATE_MQTT_CONNECTED);
            status.uart1_init = 1;
            status.uart3_init = 1;
            status.uart3_rx_recent = ((HAL_GetTick() - g_uart3_last_rx_tick) <= 3000U) ? 1U : 0U;
            status.uptime_s = HAL_GetTick() / 1000U;
            DisplayUI_RenderPage2(&status);
        }
        osDelay(500);
    }
}
```

```c
// Core/Src/main.c
volatile uint32_t g_uart3_last_rx_tick = 0;

void HAL_UART_RxCpltCallback(UART_HandleTypeDef *huart) {
    if (huart->Instance == USART3) {
        g_uart3_last_rx_tick = HAL_GetTick();
        ESP8266_UART_RxCallback(esp_rx_byte);
        HAL_UART_Receive_IT(&huart3, &esp_rx_byte, 1);
    }
}
```

**Step 4: Run verification**

Run:

```powershell
UV4 -b "MDK-ARM/Project with XiaoJunWei.uvprojx" -j0
```

Expected: PASS build, no undefined symbols.

Target runtime check:

1. Page 1 visible after boot.
2. KEY1 press toggles to page 2.
3. Page 2 values change with WiFi/MQTT state and UART3 traffic.

**Step 5: Commit**

```bash
git add MDK-ARM/code/app_tasks.c MDK-ARM/code/app_tasks.h Core/Src/main.c
git commit -m "feat: integrate two-page st7789 ui with key switch and uart rx status"
```

### Task 5: Register New Files In Keil Project + Full Verification

**Files:**
- Modify: `MDK-ARM/Project with XiaoJunWei.uvprojx`
- Create: `docs/reports/2026-02-24-st7789-adaptation-verification.md`

**Step 1: Write failing project inclusion check**

Attempt a build before adding new files to `.uvprojx`.

**Step 2: Run build to verify it fails**

Run:

```powershell
UV4 -b "MDK-ARM/Project with XiaoJunWei.uvprojx" -j0
```

Expected: FAIL with missing symbols (`DisplayUI_*`, `ST7789_*`, `DisplayLogic_*`) if files not included.

**Step 3: Add new file entries to `.uvprojx`**

Add under `code` group:

- `.\code\st7789.c`
- `.\code\st7789.h`
- `.\code\display_ui.c`
- `.\code\display_ui.h`
- `.\code\display_logic.c`
- `.\code\display_logic.h`

**Step 4: Run full verification**

Run:

```powershell
UV4 -b "MDK-ARM/Project with XiaoJunWei.uvprojx" -j0
```

Expected: PASS build.

Then flash and verify:

1. Boot on page 1.
2. KEY1 toggles pages.
3. Page 1 shows temp/hum/pres/state.
4. Page 2 shows WiFi/MQTT/UART1/UART3/UART3_RX/Uptime.

Write verification evidence:

```markdown
# ST7789 Adaptation Verification
- build command and result
- page switch result
- page 1 values
- page 2 values
- known limits
```

**Step 5: Commit**

```bash
git add MDK-ARM/Project\ with\ XiaoJunWei.uvprojx docs/reports/2026-02-24-st7789-adaptation-verification.md
git commit -m "build: register st7789 ui modules and capture verification report"
```

### Task 6: Final Cleanup and Documentation Sync

**Files:**
- Modify: `README.md` (display section)
- Modify: `docs/plans/2026-02-24-smartmedicinebox-st7789-adaptation-design.md`

**Step 1: Write failing doc check**

List required missing docs before update:

- no pin mapping summary in README
- no usage note for KEY1 page switching

**Step 2: Run doc check**

Run:

```powershell
rg -n "ST7789|KEY1|Display Page|WiFi|MQTT|UART3_RX" README.md docs/plans/2026-02-24-smartmedicinebox-st7789-adaptation-design.md
```

Expected: Missing one or more required strings before update.

**Step 3: Update docs minimally**

Add:

- ST7789 pin map
- page definitions
- KEY1 active-high behavior
- known limitation: full-page redraw every 500ms

**Step 4: Re-run doc check**

Run:

```powershell
rg -n "ST7789|KEY1|Display Page|WiFi|MQTT|UART3_RX" README.md docs/plans/2026-02-24-smartmedicinebox-st7789-adaptation-design.md
```

Expected: Required keywords all present.

**Step 5: Commit**

```bash
git add README.md docs/plans/2026-02-24-smartmedicinebox-st7789-adaptation-design.md
git commit -m "docs: document st7789 two-page ui usage and constraints"
```
