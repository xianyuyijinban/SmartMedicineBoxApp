#ifndef __MEDICINE_TIMER_POLICY_H
#define __MEDICINE_TIMER_POLICY_H

#include <stdint.h>

#define MEDICINE_TIMER_SECONDS_PER_DAY 86400UL
#define MEDICINE_TIMER_MAX_COUNT 5U

static inline uint8_t MedicineTimer_IsValidHms(int32_t hour, int32_t minute, int32_t second)
{
    return (uint8_t)((hour >= 0) && (hour <= 23) &&
                     (minute >= 0) && (minute <= 59) &&
                     (second >= 0) && (second <= 59));
}

static inline uint8_t MedicineTimer_IsValidId(int32_t timer_id)
{
    return (uint8_t)((timer_id >= 1) && (timer_id <= (int32_t)MEDICINE_TIMER_MAX_COUNT));
}

static inline uint32_t MedicineTimer_HmsToSeconds(uint8_t hour, uint8_t minute, uint8_t second)
{
    return ((uint32_t)hour * 3600UL) + ((uint32_t)minute * 60UL) + (uint32_t)second;
}

static inline void MedicineTimer_SecondsToHms(uint32_t seconds,
                                              uint8_t *hour,
                                              uint8_t *minute,
                                              uint8_t *second)
{
    seconds %= MEDICINE_TIMER_SECONDS_PER_DAY;
    if (hour != 0) {
        *hour = (uint8_t)(seconds / 3600UL);
    }
    if (minute != 0) {
        *minute = (uint8_t)((seconds % 3600UL) / 60UL);
    }
    if (second != 0) {
        *second = (uint8_t)(seconds % 60UL);
    }
}

static inline uint8_t MedicineTimer_ComputeCountdownTarget(uint8_t now_hour,
                                                           uint8_t now_minute,
                                                           uint8_t now_second,
                                                           uint8_t duration_hour,
                                                           uint8_t duration_minute,
                                                           uint8_t duration_second,
                                                           uint8_t *target_hour,
                                                           uint8_t *target_minute,
                                                           uint8_t *target_second,
                                                           uint32_t *remaining_seconds)
{
    uint32_t now = MedicineTimer_HmsToSeconds(now_hour, now_minute, now_second);
    uint32_t duration = MedicineTimer_HmsToSeconds(duration_hour, duration_minute, duration_second);
    uint32_t target;

    if (duration == 0UL) {
        return 1U;
    }

    target = (now + duration) % MEDICINE_TIMER_SECONDS_PER_DAY;
    MedicineTimer_SecondsToHms(target, target_hour, target_minute, target_second);
    if (remaining_seconds != 0) {
        *remaining_seconds = duration;
    }
    return 0U;
}

static inline uint32_t MedicineTimer_ComputeClockRemaining(uint8_t now_hour,
                                                           uint8_t now_minute,
                                                           uint8_t now_second,
                                                           uint8_t target_hour,
                                                           uint8_t target_minute,
                                                           uint8_t target_second)
{
    uint32_t now = MedicineTimer_HmsToSeconds(now_hour, now_minute, now_second);
    uint32_t target = MedicineTimer_HmsToSeconds(target_hour, target_minute, target_second);

    if (target <= now) {
        target += MEDICINE_TIMER_SECONDS_PER_DAY;
    }

    return target - now;
}

#endif /* __MEDICINE_TIMER_POLICY_H */
