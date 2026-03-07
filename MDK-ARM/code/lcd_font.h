#ifndef LCD_FONT_H
#define LCD_FONT_H

#include <stdint.h>

typedef struct {
    const uint8_t *table;
    uint16_t Width;
    uint16_t Height;
} sFONT;

extern sFONT Font8;

#endif /* LCD_FONT_H */
