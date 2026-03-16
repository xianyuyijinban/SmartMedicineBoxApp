#include <assert.h>
#include <stdint.h>

#include "esp8266_command_rx_policy.h"

int main(void) {
    assert(ESP8266_ShouldSuspendInterruptRx(0U) == 0U);
    assert(ESP8266_ShouldSuspendInterruptRx(1U) == 0U);

    assert(ESP8266_ShouldResumeInterruptRx(0U) == 0U);
    assert(ESP8266_ShouldResumeInterruptRx(1U) == 0U);

    return 0;
}
