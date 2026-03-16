#ifndef ESP8266_RAW_MQTT_POLICY_H
#define ESP8266_RAW_MQTT_POLICY_H

#include <stdint.h>
#include <ctype.h>
#include <string.h>

#define ESP8266_RAW_MQTT_CONNACK_PENDING   0U
#define ESP8266_RAW_MQTT_CONNACK_ACCEPTED  1U
#define ESP8266_RAW_MQTT_CONNACK_REJECTED  2U

static inline uint8_t ESP8266_RawMqttClassifyConnAck(const uint8_t *buf,
                                                     uint16_t len,
                                                     uint8_t *out_reason)
{
    const char prefix[] = "+IPD,";
    uint16_t i;

    if (out_reason != NULL) {
        *out_reason = 0xFFU;
    }
    if ((buf == NULL) || (len == 0U)) {
        return ESP8266_RAW_MQTT_CONNACK_PENDING;
    }

    for (i = 0U; i + (uint16_t)sizeof(prefix) - 1U < len; i++) {
        uint16_t cursor;
        uint32_t ipd_len = 0U;
        uint8_t has_digit = 0U;

        if (memcmp(&buf[i], prefix, sizeof(prefix) - 1U) != 0) {
            continue;
        }

        cursor = (uint16_t)(i + (sizeof(prefix) - 1U));
        while ((cursor < len) && isdigit((unsigned char)buf[cursor])) {
            has_digit = 1U;
            ipd_len = (ipd_len * 10U) + (uint32_t)(buf[cursor] - '0');
            cursor++;
        }

        if ((has_digit == 0U) || (cursor >= len) || (buf[cursor] != ':')) {
            continue;
        }

        cursor++;
        if ((uint32_t)(len - cursor) < ipd_len) {
            return ESP8266_RAW_MQTT_CONNACK_PENDING;
        }

        if ((ipd_len >= 4U) &&
            (buf[cursor] == 0x20U) &&
            (buf[cursor + 1U] == 0x02U) &&
            (buf[cursor + 2U] == 0x00U)) {
            uint8_t reason = buf[cursor + 3U];
            if (out_reason != NULL) {
                *out_reason = reason;
            }
            return (uint8_t)((reason == 0U) ? ESP8266_RAW_MQTT_CONNACK_ACCEPTED
                                            : ESP8266_RAW_MQTT_CONNACK_REJECTED);
        }
    }

    return ESP8266_RAW_MQTT_CONNACK_PENDING;
}

#endif
