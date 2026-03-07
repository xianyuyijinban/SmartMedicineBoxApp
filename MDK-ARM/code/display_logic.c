#include "display_logic.h"

#define DISPLAY_KEY_DEBOUNCE_MS 50U

uint8_t DisplayLogic_UpdateKey(DisplayLogicState_t *state,
                               uint8_t key_raw_high,
                               uint32_t now_ms,
                               DisplayPage_t *page) {
    if (state == 0 || page == 0) {
        return 0;
    }

    if (state->key_pressed_latched == 0U) {
        if (key_raw_high != 0U) {
            if (state->key_armed == 0U) {
                state->key_armed = 1U;
                state->key_press_tick = now_ms;
            } else if ((uint32_t)(now_ms - state->key_press_tick) >= DISPLAY_KEY_DEBOUNCE_MS) {
                *page = (*page == DISPLAY_PAGE_ENV) ? DISPLAY_PAGE_SYSTEM : DISPLAY_PAGE_ENV;
                state->key_pressed_latched = 1U;
                state->key_armed = 0U;
                return 1U;
            }
        } else {
            state->key_armed = 0U;
        }
    } else if (key_raw_high == 0U) {
        state->key_pressed_latched = 0U;
    }

    return 0U;
}
