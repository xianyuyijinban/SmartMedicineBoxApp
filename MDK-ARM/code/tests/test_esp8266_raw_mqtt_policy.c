#include <assert.h>
#include <stdint.h>

#include "esp8266_raw_mqtt_policy.h"

int main(void)
{
    static const uint8_t accepted[] = {
        '+','I','P','D',',','4',':', 0x20U, 0x02U, 0x00U, 0x00U
    };
    static const uint8_t rejected[] = {
        '\r','\n','+','I','P','D',',','4',':', 0x20U, 0x02U, 0x00U, 0x05U
    };
    static const uint8_t partial[] = {
        '+','I','P','D',',','4',':', 0x20U, 0x02U
    };
    uint8_t reason = 0xFFU;

    assert(ESP8266_RawMqttClassifyConnAck(accepted, (uint16_t)sizeof(accepted), &reason) ==
           ESP8266_RAW_MQTT_CONNACK_ACCEPTED);
    assert(reason == 0x00U);

    reason = 0xFFU;
    assert(ESP8266_RawMqttClassifyConnAck(rejected, (uint16_t)sizeof(rejected), &reason) ==
           ESP8266_RAW_MQTT_CONNACK_REJECTED);
    assert(reason == 0x05U);

    reason = 0xFFU;
    assert(ESP8266_RawMqttClassifyConnAck(partial, (uint16_t)sizeof(partial), &reason) ==
           ESP8266_RAW_MQTT_CONNACK_PENDING);
    assert(reason == 0xFFU);

    return 0;
}
