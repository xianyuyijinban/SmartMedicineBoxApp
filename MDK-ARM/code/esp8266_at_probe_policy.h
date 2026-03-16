#ifndef ESP8266_AT_PROBE_POLICY_H
#define ESP8266_AT_PROBE_POLICY_H

#include <stdint.h>

static inline uint8_t ESP8266_AT_GetProbeBaud(uint8_t index, uint32_t *out_baud)
{
    static const uint32_t probe_bauds[] = {
        115200U,
        230400U,
        460800U,
        921600U,
        9600U,
        57600U,
        38400U,
        74880U
    };

    if (out_baud == NULL) {
        return 1U;
    }

    if (index >= (uint8_t)(sizeof(probe_bauds) / sizeof(probe_bauds[0]))) {
        return 1U;
    }

    *out_baud = probe_bauds[index];
    return 0U;
}

static inline uint8_t ESP8266_AT_GetProbeAttemptsPerBaud(void)
{
    return 4U;
}

static inline uint32_t ESP8266_AT_GetProbeTimeoutMs(void)
{
    return 250U;
}

static inline uint32_t ESP8266_AT_GetProbeRetryDelayMs(void)
{
    return 100U;
}

static inline uint32_t ESP8266_AT_GetBootReadyDelayMs(void)
{
    return 1500U;
}

#endif
