#include <assert.h>
#include <string.h>

#include "esp8266_mqtt_transport.h"

int main(void) {
    ESP8266_MqttTransportCandidate_t candidate;

    assert(ESP8266_MQTT_GetCloudAtCandidate(8883U, 0U, &candidate) == 0U);
    assert(candidate.port == 8883U);
    assert(candidate.scheme == 2U);
    assert(strcmp(candidate.path, "") == 0);

    assert(ESP8266_MQTT_GetCloudAtCandidate(8883U, 1U, &candidate) == 0U);
    assert(candidate.port == 8084U);
    assert(candidate.scheme == 7U);
    assert(strcmp(candidate.path, "/mqtt") == 0);

    assert(ESP8266_MQTT_GetCloudAtCandidate(8084U, 0U, &candidate) == 0U);
    assert(candidate.port == 8084U);
    assert(candidate.scheme == 7U);
    assert(strcmp(candidate.path, "/mqtt") == 0);

    assert(ESP8266_MQTT_GetCloudAtCandidate(8084U, 1U, &candidate) == 0U);
    assert(candidate.port == 8883U);
    assert(candidate.scheme == 2U);
    assert(strcmp(candidate.path, "") == 0);

    assert(ESP8266_MQTT_GetCloudAtCandidate(8883U, 2U, &candidate) != 0U);
    assert(ESP8266_MQTT_GetCloudAtCandidate(1883U, 0U, &candidate) != 0U);

    return 0;
}
