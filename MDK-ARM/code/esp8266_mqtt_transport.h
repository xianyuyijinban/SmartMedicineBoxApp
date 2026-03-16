#ifndef __ESP8266_MQTT_TRANSPORT_H
#define __ESP8266_MQTT_TRANSPORT_H

#include <stdint.h>

typedef struct {
    uint16_t port;
    uint8_t scheme;
    const char *path;
} ESP8266_MqttTransportCandidate_t;

uint8_t ESP8266_MQTT_GetCloudAtCandidate(uint16_t requested_port,
                                         uint8_t attempt,
                                         ESP8266_MqttTransportCandidate_t *out_candidate);
uint8_t ESP8266_MQTT_ShouldUseRawForCloud(uint16_t requested_port);
uint16_t ESP8266_MQTT_GetCloudRawPort(uint16_t requested_port);

#endif /* __ESP8266_MQTT_TRANSPORT_H */
