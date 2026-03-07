#include "display_ui.h"

#include <stdio.h>

void DisplayUI_FormatWifiLine(const DisplayPage2Status_t *status, char *out, uint16_t out_len) {
    if (status == 0 || out == 0 || out_len == 0U) {
        return;
    }
    (void)snprintf(out, out_len, "WiFi:%s", status->wifi_ok ? "OK" : "NO");
}

#ifndef UNIT_TEST

#include "st7789.h"
#include "esp8266.h"
#include <string.h>

static void DisplayUI_DrawLine(uint16_t y, const char *text, uint16_t color) {
    ST7789_DrawString(8U, y, text, color, ST7789_COLOR_BLACK);
}

void DisplayUI_Init(void) {
    ST7789_Init();
}

void DisplayUI_RenderPage1(const MedicineBoxData_t *data) {
    char line[48];

    if (data == 0) {
        return;
    }

    ST7789_FillScreen(ST7789_COLOR_BLACK);

    DisplayUI_DrawLine(8U, "Page1 ENV/BOX", ST7789_COLOR_CYAN);

    (void)snprintf(line, sizeof(line), "Temp : %.1f C", data->env.temperature);
    DisplayUI_DrawLine(30U, line, ST7789_COLOR_WHITE);

    (void)snprintf(line, sizeof(line), "Hum  : %.1f %%", data->env.humidity);
    DisplayUI_DrawLine(50U, line, ST7789_COLOR_WHITE);

    (void)snprintf(line, sizeof(line), "Pres : %.1f hPa", data->env.pressure / 100.0f);
    DisplayUI_DrawLine(70U, line, ST7789_COLOR_WHITE);

    (void)snprintf(line, sizeof(line), "State: %s", SensorManager_GetStateString(data->state));
    DisplayUI_DrawLine(90U, line, ST7789_COLOR_YELLOW);

    (void)snprintf(line, sizeof(line), "Valid: %s", data->is_valid ? "OK" : "FAIL");
    DisplayUI_DrawLine(110U, line, data->is_valid ? ST7789_COLOR_GREEN : ST7789_COLOR_RED);

    DisplayUI_DrawLine(220U, "KEY1 -> Next Page", ST7789_COLOR_MAGENTA);
}

void DisplayUI_RenderPage2(const DisplayPage2Status_t *status) {
    char line[48];

    if (status == 0) {
        return;
    }

    ST7789_FillScreen(ST7789_COLOR_BLACK);

    DisplayUI_DrawLine(8U, "Page2 SYS", ST7789_COLOR_CYAN);

    DisplayUI_FormatWifiLine(status, line, (uint16_t)sizeof(line));
    DisplayUI_DrawLine(30U, line, status->wifi_ok ? ST7789_COLOR_GREEN : ST7789_COLOR_RED);

    (void)snprintf(line, sizeof(line), "MQTT:%s", status->mqtt_ok ? "OK" : "NO");
    DisplayUI_DrawLine(50U, line, status->mqtt_ok ? ST7789_COLOR_GREEN : ST7789_COLOR_RED);

    (void)snprintf(line, sizeof(line), "UART1:%s", status->uart1_init ? "INIT" : "NO");
    DisplayUI_DrawLine(70U, line, status->uart1_init ? ST7789_COLOR_GREEN : ST7789_COLOR_RED);

    (void)snprintf(line, sizeof(line), "UART3:%s", status->uart3_init ? "INIT" : "NO");
    DisplayUI_DrawLine(90U, line, status->uart3_init ? ST7789_COLOR_GREEN : ST7789_COLOR_RED);

    (void)snprintf(line, sizeof(line), "U3RX :%s", status->uart3_rx_recent ? "ACTIVE" : "IDLE");
    DisplayUI_DrawLine(110U, line, status->uart3_rx_recent ? ST7789_COLOR_YELLOW : ST7789_COLOR_WHITE);

    (void)snprintf(line, sizeof(line), "Up   :%lus", (unsigned long)status->uptime_s);
    DisplayUI_DrawLine(130U, line, ST7789_COLOR_WHITE);

    DisplayUI_DrawLine(160U, ESP8266_GetStateString(), ST7789_COLOR_MAGENTA);
    DisplayUI_DrawLine(220U, "KEY1 -> Next Page", ST7789_COLOR_MAGENTA);
}

#endif /* UNIT_TEST */
