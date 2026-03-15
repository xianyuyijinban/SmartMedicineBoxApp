#include <assert.h>
#include <stdint.h>

#include "esp8266_delay_policy.h"

int main(void) {
    assert(ESP8266_ShouldUseTaskDelay(0U, 0U) == 0U);
    assert(ESP8266_ShouldUseTaskDelay(1U, 1U) == 0U);
    assert(ESP8266_ShouldUseTaskDelay(1U, 0U) == 1U);

    assert(ESP8266_DelayTicksFromMs(0U, 1U) == 1U);
    assert(ESP8266_DelayTicksFromMs(1U, 1U) == 1U);
    assert(ESP8266_DelayTicksFromMs(10U, 1U) == 10U);
    assert(ESP8266_DelayTicksFromMs(10U, 7U) == 2U);

    return 0;
}
