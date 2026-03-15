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

#ifndef LCD_SCK_GPIO_Port
#define LCD_SCK_GPIO_Port GPIOA
#define LCD_SCK_Pin GPIO_PIN_5
#endif

#ifndef LCD_MOSI_GPIO_Port
#define LCD_MOSI_GPIO_Port GPIOA
#define LCD_MOSI_Pin GPIO_PIN_7
#endif

#define ST7789_PIN_MASK(pin_number) ((uint16_t)(1U << (pin_number)))

#if ST7789_PANEL_HAS_SECONDARY_RESET
#ifndef ST7789_RESET2_GPIO_Port
#define ST7789_RESET2_GPIO_Port GPIOB
#define ST7789_RESET2_Pin GPIO_PIN_0
#endif
#endif

#if ST7789_PANEL_HAS_SECONDARY_DC
#ifndef ST7789_DC2_GPIO_Port
#define ST7789_DC2_GPIO_Port GPIOB
#define ST7789_DC2_Pin GPIO_PIN_1
#endif
#endif

#if ST7789_PANEL_HAS_SECONDARY_BACKLIGHT
#ifndef ST7789_BL2_GPIO_Port
#define ST7789_BL2_GPIO_Port GPIOA
#define ST7789_BL2_Pin GPIO_PIN_4
#endif
#endif

#define ST7789_SPI_TIMEOUT_MS 500U

#ifndef ST7789_USE_BITBANG
#define ST7789_USE_BITBANG 1U
#endif

#define ST7789_BULK_CHUNK_BYTES 512U

/* 可按屏幕模组实际方向覆盖该值 */
static uint8_t g_madctl = ST7789_DEFAULT_MADCTL;

volatile uint32_t g_st7789_cmd_count = 0U;
volatile uint32_t g_st7789_data_bytes = 0U;
volatile uint32_t g_st7789_spi_ok_count = 0U;
volatile uint32_t g_st7789_spi_err_count = 0U;
volatile uint32_t g_st7789_last_spi_status = 0U;
volatile uint32_t g_st7789_pixel_bytes = 0U;
volatile uint32_t g_st7789_pixel_err_count = 0U;

#define ST7789_CMD_SLPOUT  0x11U
#define ST7789_CMD_INVOFF  0x20U
#define ST7789_CMD_INVON   0x21U
#define ST7789_CMD_DISPON  0x29U
#define ST7789_CMD_CASET   0x2AU
#define ST7789_CMD_RASET   0x2BU
#define ST7789_CMD_RAMWR   0x2CU
#define ST7789_CMD_MADCTL  0x36U
#define ST7789_CMD_COLMOD  0x3AU

static uint16_t g_x_offset = ST7789_X_OFFSET;
static uint16_t g_y_offset = ST7789_Y_OFFSET;

static GPIO_TypeDef *ST7789_PortFromId(uint8_t port_id) {
    switch (port_id) {
        case ST7789_PORT_ID_A:
            return GPIOA;
        case ST7789_PORT_ID_B:
            return GPIOB;
        case ST7789_PORT_ID_C:
            return GPIOC;
        case ST7789_PORT_ID_D:
            return GPIOD;
        default:
            return GPIOC;
    }
}

static void ST7789_SetResetLevel(GPIO_PinState state) {
    HAL_GPIO_WritePin(ST7789_PortFromId(ST7789_BOARD_RESET_PORT_ID),
                      ST7789_PIN_MASK(ST7789_BOARD_RESET_PIN_NUMBER),
                      state);
#if ST7789_PANEL_HAS_SECONDARY_RESET
    HAL_GPIO_WritePin(ST7789_RESET2_GPIO_Port, ST7789_RESET2_Pin, state);
#endif
}

static void ST7789_SetDataCommandLevel(GPIO_PinState state) {
    HAL_GPIO_WritePin(ST7789_PortFromId(ST7789_BOARD_DC_PORT_ID),
                      ST7789_PIN_MASK(ST7789_BOARD_DC_PIN_NUMBER),
                      state);
#if ST7789_PANEL_HAS_SECONDARY_DC
    HAL_GPIO_WritePin(ST7789_DC2_GPIO_Port, ST7789_DC2_Pin, state);
#endif
}

static void ST7789_SetBacklightLevel(GPIO_PinState state) {
    HAL_GPIO_WritePin(ST7789_PortFromId(ST7789_BOARD_BL_PORT_ID),
                      ST7789_PIN_MASK(ST7789_BOARD_BL_PIN_NUMBER),
                      state);
#if ST7789_PANEL_HAS_SECONDARY_BACKLIGHT
    HAL_GPIO_WritePin(ST7789_BL2_GPIO_Port, ST7789_BL2_Pin, state);
#endif
}

static void ST7789_ControlPinsInit(void) {
    GPIO_InitTypeDef GPIO_InitStruct = {0};

    GPIO_InitStruct.Mode = GPIO_MODE_OUTPUT_PP;
    GPIO_InitStruct.Pull = GPIO_NOPULL;
    GPIO_InitStruct.Speed = GPIO_SPEED_FREQ_VERY_HIGH;

    GPIO_InitStruct.Pin = ST7789_PIN_MASK(ST7789_BOARD_RESET_PIN_NUMBER);
    HAL_GPIO_Init(ST7789_PortFromId(ST7789_BOARD_RESET_PORT_ID), &GPIO_InitStruct);
#if ST7789_PANEL_HAS_SECONDARY_RESET
    GPIO_InitStruct.Pin = ST7789_RESET2_Pin;
    HAL_GPIO_Init(ST7789_RESET2_GPIO_Port, &GPIO_InitStruct);
#endif

    GPIO_InitStruct.Pin = ST7789_PIN_MASK(ST7789_BOARD_DC_PIN_NUMBER);
    HAL_GPIO_Init(ST7789_PortFromId(ST7789_BOARD_DC_PORT_ID), &GPIO_InitStruct);
#if ST7789_PANEL_HAS_SECONDARY_DC
    GPIO_InitStruct.Pin = ST7789_DC2_Pin;
    HAL_GPIO_Init(ST7789_DC2_GPIO_Port, &GPIO_InitStruct);
#endif

    GPIO_InitStruct.Pin = ST7789_PIN_MASK(ST7789_BOARD_BL_PIN_NUMBER);
    HAL_GPIO_Init(ST7789_PortFromId(ST7789_BOARD_BL_PORT_ID), &GPIO_InitStruct);
#if ST7789_PANEL_HAS_SECONDARY_BACKLIGHT
    GPIO_InitStruct.Pin = ST7789_BL2_Pin;
    HAL_GPIO_Init(ST7789_BL2_GPIO_Port, &GPIO_InitStruct);
#endif
}

static HAL_StatusTypeDef ST7789_Transmit(const uint8_t *data, uint16_t size) {
#if ST7789_USE_BITBANG
    uint16_t i;
    uint8_t bit;
    uint8_t byte;

    if (data == 0) {
        return HAL_ERROR;
    }

    for (i = 0U; i < size; ++i) {
        byte = data[i];
        for (bit = 0U; bit < 8U; ++bit) {
            HAL_GPIO_WritePin(LCD_SCK_GPIO_Port, LCD_SCK_Pin, GPIO_PIN_RESET);
            HAL_GPIO_WritePin(LCD_MOSI_GPIO_Port,
                              LCD_MOSI_Pin,
                              ((byte & 0x80U) != 0U) ? GPIO_PIN_SET : GPIO_PIN_RESET);
            __NOP();
            HAL_GPIO_WritePin(LCD_SCK_GPIO_Port, LCD_SCK_Pin, GPIO_PIN_SET);
            __NOP();
            byte <<= 1;
        }
    }

    return HAL_OK;
#else
    return HAL_SPI_Transmit(&hspi1, (uint8_t *)data, size, ST7789_SPI_TIMEOUT_MS);
#endif
}

#if ST7789_USE_BITBANG
static void ST7789_BitBangInit(void) {
    GPIO_InitTypeDef GPIO_InitStruct = {0};

    GPIO_InitStruct.Pin = LCD_SCK_Pin;
    GPIO_InitStruct.Mode = GPIO_MODE_OUTPUT_PP;
    GPIO_InitStruct.Pull = GPIO_NOPULL;
    GPIO_InitStruct.Speed = GPIO_SPEED_FREQ_VERY_HIGH;
    HAL_GPIO_Init(LCD_SCK_GPIO_Port, &GPIO_InitStruct);

    GPIO_InitStruct.Pin = LCD_MOSI_Pin;
    HAL_GPIO_Init(LCD_MOSI_GPIO_Port, &GPIO_InitStruct);

    HAL_GPIO_WritePin(LCD_SCK_GPIO_Port, LCD_SCK_Pin, GPIO_PIN_SET);
    HAL_GPIO_WritePin(LCD_MOSI_GPIO_Port, LCD_MOSI_Pin, GPIO_PIN_SET);
}
#endif

static void ST7789_WriteCommand(uint8_t cmd) {
    HAL_StatusTypeDef st;

    ST7789_SetDataCommandLevel(GPIO_PIN_RESET);
    st = ST7789_Transmit(&cmd, 1U);
    g_st7789_cmd_count++;
    g_st7789_last_spi_status = (uint32_t)st;
    if (st == HAL_OK) {
        g_st7789_spi_ok_count++;
    } else {
        g_st7789_spi_err_count++;
    }
}

static void ST7789_WriteData(const uint8_t *data, uint16_t size) {
    HAL_StatusTypeDef st;

    ST7789_SetDataCommandLevel(GPIO_PIN_SET);
    st = ST7789_Transmit(data, size);
    g_st7789_data_bytes += size;
    g_st7789_last_spi_status = (uint32_t)st;
    if (st == HAL_OK) {
        g_st7789_spi_ok_count++;
    } else {
        g_st7789_spi_err_count++;
    }
}

static void ST7789_Reset(void) {
    ST7789_SetResetLevel(GPIO_PIN_RESET);
    HAL_Delay(20U);
    ST7789_SetResetLevel(GPIO_PIN_SET);
    HAL_Delay(20U);
}

static void ST7789_SetAddressWindow(uint16_t x0, uint16_t y0, uint16_t x1, uint16_t y1) {
    uint8_t data[4];
    uint16_t xs0 = (uint16_t)(x0 + g_x_offset);
    uint16_t xs1 = (uint16_t)(x1 + g_x_offset);
    uint16_t ys0 = (uint16_t)(y0 + g_y_offset);
    uint16_t ys1 = (uint16_t)(y1 + g_y_offset);

    ST7789_WriteCommand(ST7789_CMD_CASET);
    data[0] = (uint8_t)(xs0 >> 8);
    data[1] = (uint8_t)(xs0 & 0xFFU);
    data[2] = (uint8_t)(xs1 >> 8);
    data[3] = (uint8_t)(xs1 & 0xFFU);
    ST7789_WriteData(data, 4U);

    ST7789_WriteCommand(ST7789_CMD_RASET);
    data[0] = (uint8_t)(ys0 >> 8);
    data[1] = (uint8_t)(ys0 & 0xFFU);
    data[2] = (uint8_t)(ys1 >> 8);
    data[3] = (uint8_t)(ys1 & 0xFFU);
    ST7789_WriteData(data, 4U);

    ST7789_WriteCommand(ST7789_CMD_RAMWR);
}

void ST7789_SetRotation(uint8_t madctl) {
    g_madctl = madctl;
    ST7789_WriteCommand(ST7789_CMD_MADCTL);
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

    ST7789_ControlPinsInit();

    /* BLK low = off on this module */
    ST7789_SetBacklightLevel(GPIO_PIN_RESET);

#if ST7789_USE_BITBANG
    ST7789_BitBangInit();
#endif

    ST7789_Reset();

    ST7789_WriteCommand(ST7789_CMD_MADCTL);
    data[0] = g_madctl;
    ST7789_WriteData(data, 1U);

    ST7789_WriteCommand(ST7789_CMD_COLMOD);
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

    ST7789_WriteCommand(ST7789_CMD_INVON);
    ST7789_WriteCommand(ST7789_CMD_SLPOUT);
    HAL_Delay(120U);
    ST7789_WriteCommand(ST7789_CMD_DISPON);
    HAL_Delay(20U);

#if ST7789_BACKLIGHT_ENABLE_BEFORE_SELF_TEST
    /* 背光先打开，否则启动纯色自检对用户不可见。 */
    ST7789_SetBacklightLevel(GPIO_PIN_SET);
    HAL_Delay(20U);
#endif

#if ST7789_STARTUP_SELF_TEST
    ST7789_FillScreen(ST7789_COLOR_RED);
    HAL_Delay(ST7789_STARTUP_SELF_TEST_STEP_MS);
    ST7789_FillScreen(ST7789_COLOR_GREEN);
    HAL_Delay(ST7789_STARTUP_SELF_TEST_STEP_MS);
    ST7789_FillScreen(ST7789_COLOR_BLUE);
    HAL_Delay(ST7789_STARTUP_SELF_TEST_STEP_MS);
    ST7789_FillScreen(ST7789_COLOR_WHITE);
    HAL_Delay(ST7789_STARTUP_SELF_TEST_STEP_MS);
#endif

    ST7789_FillScreen(ST7789_COLOR_BLACK);

#if !ST7789_BACKLIGHT_ENABLE_BEFORE_SELF_TEST
    /* BLK high = on */
    ST7789_SetBacklightLevel(GPIO_PIN_SET);
#endif
}

void ST7789_FillScreen(uint16_t color) {
    uint8_t chunk[ST7789_BULK_CHUNK_BYTES];
    uint32_t pixels = ST7789_WIDTH * ST7789_HEIGHT;
    uint32_t i;
    uint16_t pix_chunk;
    HAL_StatusTypeDef st;

    for (i = 0U; i < sizeof(chunk); i += 2U) {
        chunk[i] = (uint8_t)(color >> 8);
        chunk[i + 1U] = (uint8_t)(color & 0xFFU);
    }

    ST7789_SetAddressWindow(0U, 0U, ST7789_WIDTH - 1U, ST7789_HEIGHT - 1U);
    ST7789_SetDataCommandLevel(GPIO_PIN_SET);

    while (pixels > 0U) {
        pix_chunk = (pixels > (sizeof(chunk) / 2U)) ? (uint16_t)(sizeof(chunk) / 2U) : (uint16_t)pixels;
        st = ST7789_Transmit(chunk, (uint16_t)(pix_chunk * 2U));
        if (st == HAL_OK) {
            g_st7789_pixel_bytes += (uint32_t)(pix_chunk * 2U);
            g_st7789_spi_ok_count++;
        } else {
            g_st7789_pixel_err_count++;
            g_st7789_spi_err_count++;
            g_st7789_last_spi_status = (uint32_t)st;
        }
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

void ST7789_DrawChar2x(uint16_t x, uint16_t y, char ch, uint16_t fg, uint16_t bg) {
    uint32_t row;
    uint32_t col;
    uint32_t sy;
    uint8_t glyph_row;
    uint8_t row5;
    uint32_t glyph_index;
    const uint8_t *table = Font8.table;
    uint8_t pixel_data[12U * 16U * 2U];
    uint32_t idx = 0U;
    uint16_t color;
    static const uint8_t custom_digits_5x8[10][8] = {
        { 0x0EU, 0x11U, 0x13U, 0x15U, 0x19U, 0x11U, 0x0EU, 0x00U }, /* 0 */
        { 0x04U, 0x0CU, 0x04U, 0x04U, 0x04U, 0x04U, 0x0EU, 0x00U }, /* 1 */
        { 0x0EU, 0x11U, 0x01U, 0x02U, 0x04U, 0x08U, 0x1FU, 0x00U }, /* 2 */
        { 0x1EU, 0x01U, 0x01U, 0x0EU, 0x01U, 0x01U, 0x1EU, 0x00U }, /* 3 */
        { 0x02U, 0x06U, 0x0AU, 0x12U, 0x1FU, 0x02U, 0x02U, 0x00U }, /* 4 */
        { 0x1FU, 0x10U, 0x1EU, 0x01U, 0x01U, 0x11U, 0x0EU, 0x00U }, /* 5 */
        { 0x06U, 0x08U, 0x10U, 0x1EU, 0x11U, 0x11U, 0x0EU, 0x00U }, /* 6 */
        { 0x1FU, 0x01U, 0x02U, 0x04U, 0x08U, 0x08U, 0x08U, 0x00U }, /* 7 */
        { 0x0EU, 0x11U, 0x11U, 0x0EU, 0x11U, 0x11U, 0x0EU, 0x00U }, /* 8 */
        { 0x0EU, 0x11U, 0x11U, 0x0FU, 0x01U, 0x02U, 0x0CU, 0x00U }  /* 9 */
    };

    if (ch < 32 || ch > 126) {
        ch = '?';
    }

    if (x >= ST7789_WIDTH || y >= ST7789_HEIGHT) {
        return;
    }
    if ((uint16_t)(x + 11U) >= ST7789_WIDTH || (uint16_t)(y + 15U) >= ST7789_HEIGHT) {
        return;
    }

    glyph_index = (uint32_t)(ch - 32) * 8U;

    for (row = 0U; row < 8U; ++row) {
        glyph_row = table[glyph_index + row];
        row5 = (uint8_t)((glyph_row >> 3U) & 0x1FU);

        if (ch >= '0' && ch <= '9') {
            row5 = custom_digits_5x8[(uint8_t)(ch - '0')][row];
        }

        for (sy = 0U; sy < 2U; ++sy) {
            for (col = 0U; col < 6U; ++col) {
                if (col < 5U) {
                    color = ((row5 & (uint8_t)(1U << (4U - col))) != 0U) ? fg : bg;
                } else {
                    color = bg;
                }

                pixel_data[idx++] = (uint8_t)(color >> 8);
                pixel_data[idx++] = (uint8_t)(color & 0xFFU);
                pixel_data[idx++] = (uint8_t)(color >> 8);
                pixel_data[idx++] = (uint8_t)(color & 0xFFU);
            }
        }
    }

    ST7789_SetAddressWindow(x, y, (uint16_t)(x + 11U), (uint16_t)(y + 15U));
    ST7789_WriteData(pixel_data, (uint16_t)sizeof(pixel_data));
}

void ST7789_DrawString2x(uint16_t x, uint16_t y, const char *s, uint16_t fg, uint16_t bg) {
    uint16_t cursor_x = x;
    uint16_t cursor_y = y;

    if (s == 0) {
        return;
    }

    while (*s != '\0') {
        if (*s == '\n') {
            cursor_x = x;
            cursor_y = (uint16_t)(cursor_y + 20U);
        } else {
            ST7789_DrawChar2x(cursor_x, cursor_y, *s, fg, bg);
            cursor_x = (uint16_t)(cursor_x + 12U);
            if ((uint16_t)(cursor_x + 11U) >= ST7789_WIDTH) {
                cursor_x = x;
                cursor_y = (uint16_t)(cursor_y + 20U);
            }
        }

        if ((uint16_t)(cursor_y + 16U) >= ST7789_HEIGHT) {
            break;
        }
        ++s;
    }
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
