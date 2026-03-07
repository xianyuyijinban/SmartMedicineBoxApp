#include <assert.h>
#include <string.h>

#include "display_ui.h"

int main(void) {
    DisplayPage2Status_t status = {1, 0, 1, 1, 0, 123U};
    char line[32];

    DisplayUI_FormatWifiLine(&status, line, (uint16_t)sizeof(line));
    assert(strstr(line, "WiFi:OK") != 0);

    status.wifi_ok = 0;
    DisplayUI_FormatWifiLine(&status, line, (uint16_t)sizeof(line));
    assert(strstr(line, "WiFi:NO") != 0);

    return 0;
}
