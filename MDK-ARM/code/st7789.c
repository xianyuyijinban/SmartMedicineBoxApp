#include "st7789.h"

uint16_t ST7789_Color565(uint8_t r, uint8_t g, uint8_t b) {
    return (uint16_t)(((uint16_t)(r & 0xF8U) << 8) |
                      ((uint16_t)(g & 0xFCU) << 3) |
                      ((uint16_t)b >> 3));
}

#ifndef UNIT_TEST

#include "main.h"
#include "spi.h"
#include "lcd_font.h"

#ifndef LCD_RES_GPIO_Port
#define LCD_RES_GPIO_Port GPIOC
#define LCD_RES_Pin GPIO_PIN_4
#endif

#define ST7789_SPI_TIMEOUT_MS 100U

static uint8_t g_madctl = 0x00U;

static void ST7789_WriteCommand(uint8_t cmd) {
    HAL_GPIO_WritePin(DC_GPIO_Port, DC_Pin, GPIO_PIN_RESET);
    (void)HAL_SPI_Transmit(&hspi1, &cmd, 1U, ST7789_SPI_TIMEOUT_MS);
}

static void ST7789_WriteData(const uint8_t *data, uint16_t size) {
    HAL_GPIO_WritePin(DC_GPIO_Port, DC_Pin, GPIO_PIN_SET);
    (void)HAL_SPI_Transmit(&hspi1, (uint8_t *)data, size, ST7789_SPI_TIMEOUT_MS);
}

static void ST7789_Reset(void) {
    HAL_GPIO_WritePin(LCD_RES_GPIO_Port, LCD_RES_Pin, GPIO_PIN_RESET);
    HAL_Delay(20U);
    HAL_GPIO_WritePin(LCD_RES_GPIO_Port, LCD_RES_Pin, GPIO_PIN_SET);
    HAL_Delay(20U);
}

static void ST7789_SetAddressWindow(uint16_t x0, uint16_t y0, uint16_t x1, uint16_t y1) {
    uint8_t data[4];

    ST7789_WriteCommand(0x2AU);
    data[0] = (uint8_t)(x0 >> 8);
    data[1] = (uint8_t)(x0 & 0xFFU);
    data[2] = (uint8_t)(x1 >> 8);
    data[3] = (uint8_t)(x1 & 0xFFU);
    ST7789_WriteData(data, 4U);

    ST7789_WriteCommand(0x2BU);
    data[0] = (uint8_t)(y0 >> 8);
    data[1] = (uint8_t)(y0 & 0xFFU);
    data[2] = (uint8_t)(y1 >> 8);
    data[3] = (uint8_t)(y1 & 0xFFU);
    ST7789_WriteData(data, 4U);

    ST7789_WriteCommand(0x2CU);
}

void ST7789_SetRotation(uint8_t madctl) {
    g_madctl = madctl;
    ST7789_WriteCommand(0x36U);
    ST7789_WriteData(&g_madctl, 1U);
}

void ST7789_Init(void) {
    static const uint8_t gamma_pos[] = {
        0xD0, 0x04, 0x0D, 0x11, 0x13, 0x2B, 0x3F, 0x54, 0x4C, 0x18, 0x0D, 0x0B, 0x1F, 0x23
    };
    static const uint8_t gamma_neg[] = {
        0xD0, 0x04, 0x0C, 0x11, 0x13, 0x2C, 0x3F, 0x44, 0x51, 0x2F, 0x1F, 0x1F, 0x20, 0x23
    };
    uint8_t data[5];

    /* BLK low = off on this module */
    HAL_GPIO_WritePin(BLK_GPIO_Port, BLK_Pin, GPIO_PIN_RESET);

    ST7789_Reset();

    ST7789_WriteCommand(0x36U);
    data[0] = g_madctl;
    ST7789_WriteData(data, 1U);

    ST7789_WriteCommand(0x3AU);
    data[0] = 0x05U;
    ST7789_WriteData(data, 1U);

    ST7789_WriteCommand(0xB2U);
    data[0] = 0x0CU;
    data[1] = 0x0CU;
    data[2] = 0x00U;
    data[3] = 0x33U;
    data[4] = 0x33U;
    ST7789_WriteData(data, 5U);

    ST7789_WriteCommand(0xB7U);
    data[0] = 0x35U;
    ST7789_WriteData(data, 1U);

    ST7789_WriteCommand(0xBBU);
    data[0] = 0x19U;
    ST7789_WriteData(data, 1U);

    ST7789_WriteCommand(0xC0U);
    data[0] = 0x2CU;
    ST7789_WriteData(data, 1U);

    ST7789_WriteCommand(0xC2U);
    data[0] = 0x01U;
    ST7789_WriteData(data, 1U);

    ST7789_WriteCommand(0xC3U);
    data[0] = 0x12U;
    ST7789_WriteData(data, 1U);

    ST7789_WriteCommand(0xC4U);
    data[0] = 0x20U;
    ST7789_WriteData(data, 1U);

    ST7789_WriteCommand(0xC6U);
    data[0] = 0x0FU;
    ST7789_WriteData(data, 1U);

    ST7789_WriteCommand(0xD0U);
    data[0] = 0xA4U;
    data[1] = 0xA1U;
    ST7789_WriteData(data, 2U);

    ST7789_WriteCommand(0xE0U);
    ST7789_WriteData(gamma_pos, (uint16_t)sizeof(gamma_pos));

    ST7789_WriteCommand(0xE1U);
    ST7789_WriteData(gamma_neg, (uint16_t)sizeof(gamma_neg));

    ST7789_WriteCommand(0x21U);
    ST7789_WriteCommand(0x11U);
    HAL_Delay(120U);
    ST7789_WriteCommand(0x29U);
    HAL_Delay(20U);

    ST7789_FillScreen(ST7789_COLOR_BLACK);

    /* BLK high = on */
    HAL_GPIO_WritePin(BLK_GPIO_Port, BLK_Pin, GPIO_PIN_SET);
}

void ST7789_FillScreen(uint16_t color) {
    uint8_t chunk[128];
    uint32_t pixels = ST7789_WIDTH * ST7789_HEIGHT;
    uint32_t i;
    uint16_t pix_chunk;

    for (i = 0U; i < sizeof(chunk); i += 2U) {
        chunk[i] = (uint8_t)(color >> 8);
        chunk[i + 1U] = (uint8_t)(color & 0xFFU);
    }

    ST7789_SetAddressWindow(0U, 0U, ST7789_WIDTH - 1U, ST7789_HEIGHT - 1U);
    HAL_GPIO_WritePin(DC_GPIO_Port, DC_Pin, GPIO_PIN_SET);

    while (pixels > 0U) {
        pix_chunk = (pixels > (sizeof(chunk) / 2U)) ? (uint16_t)(sizeof(chunk) / 2U) : (uint16_t)pixels;
        (void)HAL_SPI_Transmit(&hspi1, chunk, (uint16_t)(pix_chunk * 2U), ST7789_SPI_TIMEOUT_MS);
        pixels -= pix_chunk;
    }
}

void ST7789_DrawPixel(uint16_t x, uint16_t y, uint16_t color) {
    uint8_t data[2];
    if (x >= ST7789_WIDTH || y >= ST7789_HEIGHT) {
        return;
    }
    ST7789_SetAddressWindow(x, y, x, y);
    data[0] = (uint8_t)(color >> 8);
    data[1] = (uint8_t)(color & 0xFFU);
    ST7789_WriteData(data, 2U);
}

void ST7789_DrawChar(uint16_t x, uint16_t y, char ch, uint16_t fg, uint16_t bg) {
    uint32_t row;
    uint32_t col;
    uint8_t glyph_row;
    uint32_t glyph_index;
    const uint8_t *table = Font8.table;
    uint8_t pixel_data[6U * 8U * 2U];
    uint32_t idx = 0U;

    if (ch < 32 || ch > 126) {
        ch = '?';
    }

    if (x >= ST7789_WIDTH || y >= ST7789_HEIGHT) {
        return;
    }
    if ((uint16_t)(x + 5U) >= ST7789_WIDTH || (uint16_t)(y + 7U) >= ST7789_HEIGHT) {
        return;
    }

    glyph_index = (uint32_t)(ch - 32) * 8U;

    for (row = 0U; row < 8U; ++row) {
        glyph_row = table[glyph_index + row];
        for (col = 0U; col < 5U; ++col) {
            uint16_t color = ((glyph_row & (uint8_t)(1U << (7U - col))) != 0U) ? fg : bg;
            pixel_data[idx++] = (uint8_t)(color >> 8);
            pixel_data[idx++] = (uint8_t)(color & 0xFFU);
        }
        pixel_data[idx++] = (uint8_t)(bg >> 8);
        pixel_data[idx++] = (uint8_t)(bg & 0xFFU);
    }

    ST7789_SetAddressWindow(x, y, (uint16_t)(x + 5U), (uint16_t)(y + 7U));
    ST7789_WriteData(pixel_data, (uint16_t)sizeof(pixel_data));
}

void ST7789_DrawString(uint16_t x, uint16_t y, const char *s, uint16_t fg, uint16_t bg) {
    uint16_t cursor_x = x;
    uint16_t cursor_y = y;

    if (s == 0) {
        return;
    }

    while (*s != '\0') {
        if (*s == '\n') {
            cursor_x = x;
            cursor_y = (uint16_t)(cursor_y + 10U);
        } else {
            ST7789_DrawChar(cursor_x, cursor_y, *s, fg, bg);
            cursor_x = (uint16_t)(cursor_x + 6U);
            if ((uint16_t)(cursor_x + 5U) >= ST7789_WIDTH) {
                cursor_x = x;
                cursor_y = (uint16_t)(cursor_y + 10U);
            }
        }

        if ((uint16_t)(cursor_y + 8U) >= ST7789_HEIGHT) {
            break;
        }
        ++s;
    }
}

#endif /* UNIT_TEST */
