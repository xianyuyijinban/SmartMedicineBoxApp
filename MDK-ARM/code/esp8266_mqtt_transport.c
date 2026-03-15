#include "esp8266_mqtt_transport.h"

uint8_t ESP8266_MQTT_GetCloudAtCandidate(uint16_t requested_port,
                                         uint8_t attempt,
                                         ESP8266_MqttTransportCandidate_t *out_candidate)
{
    if (out_candidate == 0) {
        return 1U;
    }

    if (requested_port == 8883U) {
        if (attempt == 0U) {
            out_candidate->port = 8883U;
            out_candidate->scheme = 2U;
            out_candidate->path = "";
            return 0U;
        }
        if (attempt == 1U) {
            out_candidate->port = 8084U;
            out_candidate->scheme = 7U;
            out_candidate->path = "/mqtt";
            return 0U;
        }
        return 1U;
    }

    if (requested_port == 8084U) {
        if (attempt == 0U) {
            out_candidate->port = 8084U;
            out_candidate->scheme = 7U;
            out_candidate->path = "/mqtt";
            return 0U;
        }
        if (attempt == 1U) {
            out_candidate->port = 8883U;
            out_candidate->scheme = 2U;
            out_candidate->path = "";
            return 0U;
        }
        return 1U;
    }

    return 1U;
}
