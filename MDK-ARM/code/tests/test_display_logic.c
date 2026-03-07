#include <assert.h>
#include <stdint.h>

#include "display_logic.h"

int main(void) {
    DisplayLogicState_t st = {0};
    DisplayPage_t page = DISPLAY_PAGE_ENV;

    assert(DisplayLogic_UpdateKey(&st, 0, 0, &page) == 0);
    assert(page == DISPLAY_PAGE_ENV);

    assert(DisplayLogic_UpdateKey(&st, 1, 10, &page) == 0);
    assert(DisplayLogic_UpdateKey(&st, 1, 80, &page) == 1);
    assert(page == DISPLAY_PAGE_SYSTEM);

    assert(DisplayLogic_UpdateKey(&st, 1, 120, &page) == 0);
    assert(page == DISPLAY_PAGE_SYSTEM);

    assert(DisplayLogic_UpdateKey(&st, 0, 200, &page) == 0);
    assert(DisplayLogic_UpdateKey(&st, 1, 220, &page) == 0);
    assert(DisplayLogic_UpdateKey(&st, 1, 300, &page) == 1);
    assert(page == DISPLAY_PAGE_ENV);

    return 0;
}
