#ifndef DISPLAY_UI_H
#define DISPLAY_UI_H

#include <stdint.h>

#ifndef UNIT_TEST
#include "sensor_manager.h"
#endif

typedef struct {
    uint8_t wifi_state;
    uint8_t mqtt_ok;
    uint8_t uart1_init;
    uint8_t uart3_init;
    uint8_t uart3_rx_recent;
    uint32_t uptime_s;
} DisplayPage2Status_t;

typedef enum {
    DISPLAY_WIFI_DISABLED = 0U,
    DISPLAY_WIFI_DISCONNECTED = 1U,
    DISPLAY_WIFI_CONNECTED = 2U
} DisplayWifiState_t;

void DisplayUI_FormatWifiLine(const DisplayPage2Status_t *status, char *out, uint16_t out_len);

#ifndef UNIT_TEST
void DisplayUI_Init(void);
void DisplayUI_RenderPage1(const MedicineBoxData_t *data,
                           uint8_t buzzer_enabled,
                           uint8_t key1_raw,
                           uint8_t key2_raw);
void DisplayUI_RenderPage2(const DisplayPage2Status_t *status);
#endif

#endif /* DISPLAY_UI_H */
