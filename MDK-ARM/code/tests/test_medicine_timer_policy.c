#include "../medicine_timer_policy.h"
#include <assert.h>

int main(void)
{
    uint8_t hour = 0;
    uint8_t minute = 0;
    uint8_t second = 0;
    uint32_t remaining = 0;

    assert(MedicineTimer_IsValidHms(23, 59, 59) == 1U);
    assert(MedicineTimer_IsValidHms(24, 0, 0) == 0U);
    assert(MedicineTimer_IsValidHms(0, 60, 0) == 0U);
    assert(MedicineTimer_IsValidHms(0, 0, 60) == 0U);
    assert(MEDICINE_TIMER_MAX_COUNT == 5U);
    assert(MedicineTimer_IsValidId(1) == 1U);
    assert(MedicineTimer_IsValidId(5) == 1U);
    assert(MedicineTimer_IsValidId(0) == 0U);
    assert(MedicineTimer_IsValidId(6) == 0U);

    assert(MedicineTimer_HmsToSeconds(1, 2, 3) == 3723UL);
    MedicineTimer_SecondsToHms(90061UL, &hour, &minute, &second);
    assert(hour == 1U);
    assert(minute == 1U);
    assert(second == 1U);

    assert(MedicineTimer_ComputeCountdownTarget(23, 59, 50, 0, 0, 15,
                                                &hour, &minute, &second,
                                                &remaining) == 0U);
    assert(hour == 0U);
    assert(minute == 0U);
    assert(second == 5U);
    assert(remaining == 15UL);

    assert(MedicineTimer_ComputeCountdownTarget(1, 0, 0, 0, 0, 0,
                                                &hour, &minute, &second,
                                                &remaining) != 0U);

    assert(MedicineTimer_ComputeClockRemaining(8, 0, 0, 8, 0, 10) == 10UL);
    assert(MedicineTimer_ComputeClockRemaining(23, 59, 50, 0, 0, 5) == 15UL);
    assert(MedicineTimer_ComputeClockRemaining(8, 0, 0, 8, 0, 0) == 86400UL);

    return 0;
}
