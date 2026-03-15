#ifndef ESP8266_RX_POLICY_H
#define ESP8266_RX_POLICY_H

#include <stdint.h>

#define ESP8266_RX_POLL_OK      0U
#define ESP8266_RX_POLL_BUSY    1U
#define ESP8266_RX_POLL_TIMEOUT 2U
#define ESP8266_RX_POLL_ERROR   3U

static inline uint8_t ESP8266_ShouldPollRxFallback(uint8_t interrupt_rx_active)
{
    return (uint8_t)(interrupt_rx_active == 0U ? 1U : 0U);
}

static inline uint8_t ESP8266_ShouldRefreshRxState(uint8_t poll_status)
{
    return (uint8_t)(((poll_status == ESP8266_RX_POLL_TIMEOUT) ||
                      (poll_status == ESP8266_RX_POLL_ERROR)) ? 1U : 0U);
}

#endif
