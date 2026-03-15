#ifndef ESP8266_DELAY_POLICY_H
#define ESP8266_DELAY_POLICY_H

#include <stdint.h>

static inline uint8_t ESP8266_ShouldUseTaskDelay(uint8_t scheduler_running, uint32_t ipsr)
{
    return (uint8_t)(((scheduler_running != 0U) && (ipsr == 0U)) ? 1U : 0U);
}

static inline uint32_t ESP8266_DelayTicksFromMs(uint32_t delay_ms, uint32_t tick_period_ms)
{
    uint32_t ticks;

    if ((delay_ms == 0U) || (tick_period_ms == 0U)) {
        return 1U;
    }

    ticks = (delay_ms + tick_period_ms - 1U) / tick_period_ms;
    return (ticks == 0U) ? 1U : ticks;
}

#ifndef UNIT_TEST
#include "main.h"
#include "FreeRTOS.h"
#include "task.h"

static inline void ESP8266_TaskFriendlyDelay(uint32_t delay_ms)
{
    if (ESP8266_ShouldUseTaskDelay(
            (uint8_t)(xTaskGetSchedulerState() == taskSCHEDULER_RUNNING),
            (uint32_t)__get_IPSR()) != 0U) {
        vTaskDelay((TickType_t)ESP8266_DelayTicksFromMs(delay_ms, (uint32_t)portTICK_PERIOD_MS));
        return;
    }

    HAL_Delay(delay_ms);
}
#endif

#endif
