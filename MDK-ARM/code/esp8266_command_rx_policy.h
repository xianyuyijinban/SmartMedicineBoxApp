#ifndef ESP8266_COMMAND_RX_POLICY_H
#define ESP8266_COMMAND_RX_POLICY_H

#include <stdint.h>

static inline uint8_t ESP8266_ShouldSuspendInterruptRx(uint8_t interrupt_rx_active)
{
    return (uint8_t)(interrupt_rx_active != 0U ? 1U : 0U);
}

static inline uint8_t ESP8266_ShouldResumeInterruptRx(uint8_t interrupt_rx_was_active)
{
    return (uint8_t)(interrupt_rx_was_active != 0U ? 1U : 0U);
}

#endif
