#ifndef DISPLAY_LOGIC_H
#define DISPLAY_LOGIC_H

#include <stdint.h>

typedef enum {
    DISPLAY_PAGE_ENV = 0,
    DISPLAY_PAGE_SYSTEM = 1
} DisplayPage_t;

typedef struct {
    uint8_t key_armed;
    uint8_t key_pressed_latched;
    uint32_t key_press_tick;
} DisplayLogicState_t;

uint8_t DisplayLogic_UpdateKey(DisplayLogicState_t *state,
                               uint8_t key_raw_high,
                               uint32_t now_ms,
                               DisplayPage_t *page);

#endif /* DISPLAY_LOGIC_H */
