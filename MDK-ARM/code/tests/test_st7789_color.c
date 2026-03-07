#include <assert.h>
#include <stdint.h>

#include "st7789.h"

int main(void) {
    assert(ST7789_Color565(255, 0, 0) == 0xF800U);
    assert(ST7789_Color565(0, 255, 0) == 0x07E0U);
    assert(ST7789_Color565(0, 0, 255) == 0x001FU);
    return 0;
}
