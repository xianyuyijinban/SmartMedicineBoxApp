#include <assert.h>
#include <stdint.h>

#include "esp8266_rx_policy.h"

int main(void) {
    assert(ESP8266_ShouldPollRxFallback(0U) == 1U);
    assert(ESP8266_ShouldPollRxFallback(1U) == 0U);

    assert(ESP8266_ShouldRefreshRxState(ESP8266_RX_POLL_OK) == 0U);
    assert(ESP8266_ShouldRefreshRxState(ESP8266_RX_POLL_BUSY) == 0U);
    assert(ESP8266_ShouldRefreshRxState(ESP8266_RX_POLL_TIMEOUT) == 1U);
    assert(ESP8266_ShouldRefreshRxState(ESP8266_RX_POLL_ERROR) == 1U);

    return 0;
}
