#include <assert.h>
#include <string.h>

#include "display_ui.h"

int main(void) {
    DisplayPage2Status_t status = {DISPLAY_WIFI_CONNECTED, 0, 1, 1, 0, 123U};
    char line[32];

    DisplayUI_FormatWifiLine(&status, line, (uint16_t)sizeof(line));
    assert(strstr(line, "WiFi:connected") != 0);

    status.wifi_state = DISPLAY_WIFI_DISCONNECTED;
    DisplayUI_FormatWifiLine(&status, line, (uint16_t)sizeof(line));
    assert(strstr(line, "WiFi:disconnected") != 0);

    status.wifi_state = DISPLAY_WIFI_DISABLED;
    DisplayUI_FormatWifiLine(&status, line, (uint16_t)sizeof(line));
    assert(strstr(line, "WiFi:disable") != 0);

    return 0;
}
