#ifndef MQTT_TASK_POLICY_H
#define MQTT_TASK_POLICY_H

#include <stdint.h>
#include "display_ui.h"

#define MQTT_TASK_STATE_RESET            0U
#define MQTT_TASK_STATE_INIT             1U
#define MQTT_TASK_STATE_WIFI_CONNECTING  2U

static inline uint8_t MQTTTask_ShouldStartWiFiConnect(uint8_t wifi_connected,
                                                      uint8_t wifi_init_ready,
                                                      uint8_t esp_state)
{
    return (uint8_t)(((wifi_connected == 0U) &&
                      (wifi_init_ready != 0U) &&
                      ((esp_state == MQTT_TASK_STATE_RESET) ||
                       (esp_state == MQTT_TASK_STATE_INIT))) ? 1U : 0U);
}

static inline uint8_t MQTTTask_GetDisplayWiFiState(uint8_t wifi_connected)
{
    return (uint8_t)((wifi_connected != 0U) ? DISPLAY_WIFI_CONNECTED
                                            : DISPLAY_WIFI_DISCONNECTED);
}

#endif
