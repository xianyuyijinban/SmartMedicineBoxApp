#include "display_ui.h"

#include <stdio.h>
#include <math.h>

static const char *DisplayUI_GetWifiStateText(uint8_t wifi_state) {
    switch (wifi_state) {
        case DISPLAY_WIFI_CONNECTED:
            return "connected";
        case DISPLAY_WIFI_DISCONNECTED:
            return "disconnected";
        case DISPLAY_WIFI_DISABLED:
        default:
            return "disable";
    }
}

static uint16_t DisplayUI_GetWifiStateColor(uint8_t wifi_state) {
    switch (wifi_state) {
        case DISPLAY_WIFI_CONNECTED:
            return 0x07E0U; /* green */
        case DISPLAY_WIFI_DISCONNECTED:
            return 0xF800U; /* red */
        case DISPLAY_WIFI_DISABLED:
        default:
            return 0xFFFFU; /* white */
    }
}

void DisplayUI_FormatWifiLine(const DisplayPage2Status_t *status, char *out, uint16_t out_len) {
    if (status == 0 || out == 0 || out_len == 0U) {
        return;
    }
    (void)snprintf(out, out_len, "WiFi:%s", DisplayUI_GetWifiStateText(status->wifi_state));
}

#ifndef UNIT_TEST

#include "st7789.h"
#include "esp8266.h"
#include <string.h>

static uint8_t g_active_page = 0U;
static float g_accel_baseline_g = 1.0f;
static float g_accel_baseline_sum = 0.0f;
static uint8_t g_accel_baseline_count = 0U;

#define ACCEL_BASELINE_SAMPLE_COUNT 20U
#define ACCEL_ZERO_CLAMP_G          0.05f

static void DisplayUI_DrawLine(uint16_t y, const char *text, uint16_t color) {
    ST7789_DrawString2x(4U, y, text, color, ST7789_COLOR_BLACK);
}

void DisplayUI_Init(void) {
    ST7789_Init();
    g_active_page = 0U;
    g_accel_baseline_g = 1.0f;
    g_accel_baseline_sum = 0.0f;
    g_accel_baseline_count = 0U;
}

void DisplayUI_RenderPage1(const MedicineBoxData_t *data,
                           uint8_t buzzer_enabled,
                           uint8_t key1_raw,
                           uint8_t key2_raw) {
    char line[48];
    uint8_t fault_mask;
    float accel_mag;
    float accel_dynamic;

    if (data == 0) {
        return;
    }

    (void)key1_raw;
    (void)key2_raw;

    if (g_active_page != 1U) {
        ST7789_FillScreen(ST7789_COLOR_BLACK);
        DisplayUI_DrawLine(6U, "P01 ENV BOX", ST7789_COLOR_CYAN);
        DisplayUI_DrawLine(206U, "KEY NXT", ST7789_COLOR_MAGENTA);
        g_active_page = 1U;
    }

    (void)snprintf(line, sizeof(line), "TMP:%5.1fC ", data->env.temperature);
    DisplayUI_DrawLine(34U, line, ST7789_COLOR_WHITE);

    (void)snprintf(line, sizeof(line), "HUM:%5.1f%%", data->env.humidity);
    DisplayUI_DrawLine(56U, line, ST7789_COLOR_WHITE);

    (void)snprintf(line, sizeof(line), "STA:%-8.8s", SensorManager_GetStateString(data->state));
    DisplayUI_DrawLine(78U, line, ST7789_COLOR_YELLOW);

    fault_mask = SensorManager_GetFaultMask();
    (void)snprintf(line, sizeof(line), "MPU:%-8s", ((fault_mask & 0x01U) == 0U) ? "OK" : "ERR");
    DisplayUI_DrawLine(100U, line, ((fault_mask & 0x01U) == 0U) ? ST7789_COLOR_GREEN : ST7789_COLOR_RED);

    accel_mag = sqrtf((data->motion.accel_x * data->motion.accel_x) +
                      (data->motion.accel_y * data->motion.accel_y) +
                      (data->motion.accel_z * data->motion.accel_z));

    if (((fault_mask & 0x01U) == 0U) && (g_accel_baseline_count < ACCEL_BASELINE_SAMPLE_COUNT)) {
        g_accel_baseline_sum += accel_mag;
        g_accel_baseline_count++;
        if (g_accel_baseline_count == ACCEL_BASELINE_SAMPLE_COUNT) {
            g_accel_baseline_g = g_accel_baseline_sum / (float)ACCEL_BASELINE_SAMPLE_COUNT;
        }
    }

    accel_dynamic = fabsf(accel_mag - g_accel_baseline_g);
    if (accel_dynamic < ACCEL_ZERO_CLAMP_G) {
        accel_dynamic = 0.0f;
    }

    (void)snprintf(line, sizeof(line), "BUZ:%-3s",
                   (buzzer_enabled != 0U) ? "ON" : "OFF");
    DisplayUI_DrawLine(122U, line, (buzzer_enabled != 0U) ? ST7789_COLOR_GREEN : ST7789_COLOR_RED);

    (void)snprintf(line, sizeof(line), "ACC:%4.1fg", accel_dynamic);
    DisplayUI_DrawLine(144U, line, ST7789_COLOR_CYAN);

    /* VAL行按需求隐藏 */
}

void DisplayUI_RenderPage2(const DisplayPage2Status_t *status) {
    char line[48];

    if (status == 0) {
        return;
    }

    if (g_active_page != 2U) {
        ST7789_FillScreen(ST7789_COLOR_BLACK);
        DisplayUI_DrawLine(6U, "P02 SYSTEM", ST7789_COLOR_CYAN);
        DisplayUI_DrawLine(206U, "KEY NXT", ST7789_COLOR_MAGENTA);
        g_active_page = 2U;
    }

    (void)snprintf(line, sizeof(line), "WIF:%-12.12s", DisplayUI_GetWifiStateText(status->wifi_state));
    DisplayUI_DrawLine(34U, line, DisplayUI_GetWifiStateColor(status->wifi_state));

    (void)snprintf(line, sizeof(line), "MQT:%-7s", status->mqtt_ok ? "OK" : "NO");
    DisplayUI_DrawLine(56U, line, status->mqtt_ok ? ST7789_COLOR_GREEN : ST7789_COLOR_RED);

    (void)snprintf(line, sizeof(line), "U01:%-6s", status->uart1_init ? "INI" : "NO");
    DisplayUI_DrawLine(78U, line, status->uart1_init ? ST7789_COLOR_GREEN : ST7789_COLOR_RED);

    (void)snprintf(line, sizeof(line), "U03:%-6s", status->uart3_init ? "INI" : "NO");
    DisplayUI_DrawLine(100U, line, status->uart3_init ? ST7789_COLOR_GREEN : ST7789_COLOR_RED);

    (void)snprintf(line, sizeof(line), "RX3:%-6s", status->uart3_rx_recent ? "ACT" : "IDL");
    DisplayUI_DrawLine(122U, line, status->uart3_rx_recent ? ST7789_COLOR_YELLOW : ST7789_COLOR_WHITE);

    (void)snprintf(line, sizeof(line), "UPT:%6lus", (unsigned long)status->uptime_s);
    DisplayUI_DrawLine(144U, line, ST7789_COLOR_WHITE);

    (void)snprintf(line, sizeof(line), "DEV:%-10.10s", ESP8266_DEVICE_NAME);
    DisplayUI_DrawLine(166U, line, ST7789_COLOR_MAGENTA);

    (void)snprintf(line, sizeof(line), "MQD:%-7.7s:%4u", ESP8266_GetMqttDiag(),
                   (unsigned int)ESP8266_GetMqttActivePort());
    DisplayUI_DrawLine(188U, line, ST7789_COLOR_WHITE);
}

#endif /* UNIT_TEST */
