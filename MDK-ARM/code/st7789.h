#ifndef ST7789_H
#define ST7789_H

#include <stdint.h>

#define ST7789_WIDTH 240U
#define ST7789_HEIGHT 240U

#define ST7789_COLOR_BLACK   0x0000U
#define ST7789_COLOR_BLUE    0x001FU
#define ST7789_COLOR_RED     0xF800U
#define ST7789_COLOR_GREEN   0x07E0U
#define ST7789_COLOR_CYAN    0x07FFU
#define ST7789_COLOR_MAGENTA 0xF81FU
#define ST7789_COLOR_YELLOW  0xFFE0U
#define ST7789_COLOR_WHITE   0xFFFFU

uint16_t ST7789_Color565(uint8_t r, uint8_t g, uint8_t b);

#ifndef UNIT_TEST
void ST7789_Init(void);
void ST7789_SetRotation(uint8_t madctl);
void ST7789_FillScreen(uint16_t color);
void ST7789_DrawPixel(uint16_t x, uint16_t y, uint16_t color);
void ST7789_DrawChar(uint16_t x, uint16_t y, char ch, uint16_t fg, uint16_t bg);
void ST7789_DrawString(uint16_t x, uint16_t y, const char *s, uint16_t fg, uint16_t bg);
#endif

#endif /* ST7789_H */
