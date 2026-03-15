#include <assert.h>
#include <stdint.h>

#include "mqtt_task_policy.h"

int main(void) {
    assert(MQTTTask_ShouldStartWiFiConnect(0U, 0U, MQTT_TASK_STATE_INIT) == 0U);
    assert(MQTTTask_ShouldStartWiFiConnect(0U, 1U, MQTT_TASK_STATE_RESET) == 0U);
    assert(MQTTTask_ShouldStartWiFiConnect(1U, 1U, MQTT_TASK_STATE_INIT) == 0U);
    assert(MQTTTask_ShouldStartWiFiConnect(0U, 1U, MQTT_TASK_STATE_INIT) == 1U);

    return 0;
}
