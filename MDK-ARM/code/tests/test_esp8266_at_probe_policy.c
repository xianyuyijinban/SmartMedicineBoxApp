#include <assert.h>
#include <stdint.h>

#include "esp8266_at_probe_policy.h"

int main(void) {
    uint32_t baud = 0U;

    assert(ESP8266_AT_GetProbeBaud(0U, &baud) == 0U);
    assert(baud == 115200U);

    assert(ESP8266_AT_GetProbeBaud(1U, &baud) == 0U);
    assert(baud == 230400U);

    assert(ESP8266_AT_GetProbeBaud(2U, &baud) == 0U);
    assert(baud == 460800U);

    assert(ESP8266_AT_GetProbeBaud(3U, &baud) == 0U);
    assert(baud == 921600U);

    assert(ESP8266_AT_GetProbeBaud(4U, &baud) == 0U);
    assert(baud == 9600U);

    assert(ESP8266_AT_GetProbeBaud(5U, &baud) == 0U);
    assert(baud == 57600U);

    assert(ESP8266_AT_GetProbeBaud(6U, &baud) == 0U);
    assert(baud == 38400U);

    assert(ESP8266_AT_GetProbeBaud(7U, &baud) == 0U);
    assert(baud == 74880U);

    assert(ESP8266_AT_GetProbeBaud(8U, &baud) != 0U);
    assert(ESP8266_AT_GetProbeBaud(0U, NULL) != 0U);

    assert(ESP8266_AT_GetProbeAttemptsPerBaud() == 4U);
    assert(ESP8266_AT_GetProbeTimeoutMs() == 250U);
    assert(ESP8266_AT_GetProbeRetryDelayMs() == 100U);
    assert(ESP8266_AT_GetBootReadyDelayMs() == 1500U);

    return 0;
}
