#include <assert.h>
#include <stdint.h>

#define UNIT_TEST 1
#include "display_ui.h"
#include "mqtt_task_policy.h"

int main(void) {
    assert(MQTTTask_ShouldStartWiFiConnect(0U, 0U, MQTT_TASK_STATE_INIT) == 0U);
    assert(MQTTTask_ShouldStartWiFiConnect(0U, 1U, MQTT_TASK_STATE_RESET) == 1U);
    assert(MQTTTask_ShouldStartWiFiConnect(1U, 1U, MQTT_TASK_STATE_INIT) == 0U);
    assert(MQTTTask_ShouldStartWiFiConnect(0U, 1U, MQTT_TASK_STATE_INIT) == 1U);

    assert(MQTTTask_GetDisplayWiFiState(0U) == DISPLAY_WIFI_DISCONNECTED);
    assert(MQTTTask_GetDisplayWiFiState(1U) == DISPLAY_WIFI_CONNECTED);

    return 0;
}
