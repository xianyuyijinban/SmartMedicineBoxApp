/**
  ******************************************************************************
  * @file    app_tasks.c
  * @brief   FreeRTOS任务实现
  ******************************************************************************
  */
#include "app_tasks.h"
#include "sensor_manager.h"
#include "esp8266.h"
#include "debug_log.h"
#include "display_logic.h"
#include "display_ui.h"
#include "mqtt_task_policy.h"
#include "medicine_timer_policy.h"
#include "rtc.h"
#include "tim.h"
#include "usart.h"
#include "spi.h"
#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

/* 任务句柄 */
osThreadId_t sensorTaskHandle = NULL;
osThreadId_t mqttTaskHandle = NULL;
osThreadId_t displayTaskHandle = NULL;
osThreadId_t buzzerTaskHandle = NULL;

/* 信号量/队列 */
static osSemaphoreId_t mqttPublishSem;
static osSemaphoreId_t buzzerAlertSem;

typedef struct {
    uint8_t active;
    uint8_t id;
    uint8_t mode;
    uint8_t target_hour;
    uint8_t target_minute;
    uint8_t target_second;
} MedicineTimerSlot_t;

/* 运行标志 */
static volatile uint8_t system_ready = 0;
static volatile uint8_t app_wifi_connected = 0U;
static volatile uint8_t env_alert_latched = 0U;
static volatile uint8_t env_alert_pending = 0U;
static volatile uint8_t env_recover_pending = 0U;
static volatile uint8_t env_alert_reason_flags = 0U;
static volatile uint8_t env_alert_last_reason_flags = 0U;
static volatile uint32_t env_alert_last_buzzer_tick = 0U;
static volatile uint8_t buzzer_feature_enabled = 1U;
static volatile uint32_t mqtt_publish_interval_ms = MQTT_PUBLISH_INTERVAL_MS;
static volatile uint8_t app_bus_fault_flags = 0U;
static volatile uint8_t drop_alert_latched = 0U;
static volatile uint8_t drop_alert_pending = 0U;
static volatile uint8_t drop_alarm_request_pending = 0U;
static volatile uint8_t drop_alarm_cancel_pending = 0U;
static volatile MedicineTimerSlot_t medicine_timer_slots[MEDICINE_TIMER_MAX_COUNT];
static volatile uint8_t medicine_timer_scheduled_id = 0U;
static volatile uint8_t medicine_timer_alarm_request_pending = 0U;
static volatile uint8_t medicine_timer_alarm_publish_flags = 0U;
static volatile uint8_t medicine_timer_cancel_publish_flags = 0U;
static volatile uint8_t medicine_timer_alarm_active = 0U;
static volatile uint8_t medicine_timer_current_alarm_id = 0U;
static volatile uint8_t medicine_timer_reschedule_pending = 0U;
static volatile uint32_t medicine_timer_alarm_timestamps[MEDICINE_TIMER_MAX_COUNT];
static volatile uint32_t medicine_timer_cancel_timestamps[MEDICINE_TIMER_MAX_COUNT];
static volatile uint32_t drop_alert_timestamp = 0U;
static volatile uint32_t drop_alarm_cancel_timestamp = 0U;
static volatile float drop_alert_accel_x = 0.0f;
static volatile float drop_alert_accel_y = 0.0f;
static volatile float drop_alert_accel_z = 0.0f;
static volatile float drop_alert_accel_magnitude = 0.0f;
static volatile uint8_t control_response_pending = 0U;
static volatile uint8_t mqtt_status_publish_pending = 0U;
static char control_response_payload[224];
extern volatile uint32_t g_uart3_last_rx_tick;

typedef struct {
    uint8_t key_armed;
    uint8_t key_pressed_latched;
    uint8_t key_idle_level;
    uint8_t key_idle_known;
    uint32_t key_press_tick;
} KeyDebounceState_t;

#define APP_BUS_FAULT_SENSOR   0x01U
#define APP_BUS_FAULT_UART3    0x02U
#define APP_BUS_FAULT_SPI1     0x04U
#define LED2_FAULT_BLINK_MS    200U
#define ENV_ALERT_REPEAT_MS    8000U
#define BUZZER_TONE_HZ         1200U
#define BUZZER_TONE_DUTY_PCT   90U
#define MEDICINE_TIMER_MODE_COUNTDOWN 1U
#define MEDICINE_TIMER_MODE_CLOCK     2U
#define MEDICINE_TIMER_SHORT_BEEP_ON_MS       100U
#define MEDICINE_TIMER_SHORT_BEEP_GAP_MS      300U
#define MEDICINE_TIMER_SHORT_BEEP_COUNT       4U
#define MEDICINE_TIMER_SHORT_ROUND_GAP_MS     1000U

static void Buzzer_StopTone(void);

static void App_SetBusFaultFlag(uint8_t mask, uint8_t active)
{
    if (active != 0U) {
        app_bus_fault_flags |= mask;
    } else {
        app_bus_fault_flags &= (uint8_t)(~mask);
    }
}

static uint8_t App_IsBuzzerFeatureEnabled(void)
{
    return buzzer_feature_enabled;
}

static void App_ToggleBuzzerFeature(void)
{
    buzzer_feature_enabled = (uint8_t)(buzzer_feature_enabled == 0U ? 1U : 0U);
    if (buzzer_feature_enabled == 0U) {
        Buzzer_StopTone();
    }
    mqtt_status_publish_pending = 1U;
    if (mqttPublishSem != NULL) {
        (void)osSemaphoreRelease(mqttPublishSem);
    }
}

static void App_BuildStatusPayload(char *out, uint16_t out_size)
{
    if ((out == NULL) || (out_size == 0U)) {
        return;
    }

    (void)snprintf(out,
                   out_size,
                   "{\"status\":\"online\",\"timestamp\":%lu,"
                   "\"device_id\":\"box001\",\"firmware_version\":\"1.0.0\","
                   "\"publish_interval\":%lu,\"buzzer_enabled\":%u}",
                   HAL_GetTick(),
                   (unsigned long)(mqtt_publish_interval_ms / 1000U),
                   (unsigned int)App_IsBuzzerFeatureEnabled());
}

static uint8_t App_ExtractJsonString(const char *payload, const char *key, char *out, uint16_t out_size)
{
    char pattern[32];
    const char *cursor;
    const char *value_start;
    const char *value_end;
    size_t value_len;

    if ((payload == NULL) || (key == NULL) || (out == NULL) || (out_size < 2U)) {
        return 1U;
    }

    snprintf(pattern, sizeof(pattern), "\"%s\"", key);
    cursor = strstr(payload, pattern);
    if (cursor == NULL) {
        return 1U;
    }

    cursor = strchr(cursor + strlen(pattern), ':');
    if (cursor == NULL) {
        return 1U;
    }
    cursor++;

    while ((*cursor == ' ') || (*cursor == '\t')) {
        cursor++;
    }

    if (*cursor != '"') {
        return 1U;
    }
    value_start = cursor + 1;
    value_end = strchr(value_start, '"');
    if (value_end == NULL) {
        return 1U;
    }

    value_len = (size_t)(value_end - value_start);
    if (value_len >= out_size) {
        return 1U;
    }

    memcpy(out, value_start, value_len);
    out[value_len] = '\0';
    return 0U;
}

static uint8_t App_ExtractJsonFloat(const char *payload, const char *key, float *out_value)
{
    char pattern[32];
    const char *cursor;
    char *value_end = NULL;
    float parsed_value;

    if ((payload == NULL) || (key == NULL) || (out_value == NULL)) {
        return 1U;
    }

    snprintf(pattern, sizeof(pattern), "\"%s\"", key);
    cursor = strstr(payload, pattern);
    if (cursor == NULL) {
        return 1U;
    }

    cursor = strchr(cursor + strlen(pattern), ':');
    if (cursor == NULL) {
        return 1U;
    }
    cursor++;

    while ((*cursor == ' ') || (*cursor == '\t')) {
        cursor++;
    }

    parsed_value = strtof(cursor, &value_end);
    if ((value_end == cursor) || (value_end == NULL)) {
        return 1U;
    }

    *out_value = parsed_value;
    return 0U;
}

static uint8_t App_ParseRatedValues(const char *payload, float *temperature, float *humidity)
{
    uint8_t temp_found = 0U;
    uint8_t hum_found = 0U;

    if ((payload == NULL) || (temperature == NULL) || (humidity == NULL)) {
        return 1U;
    }

    if (App_ExtractJsonFloat(payload, "temperature", temperature) == 0U) {
        temp_found = 1U;
    } else if (App_ExtractJsonFloat(payload, "temp", temperature) == 0U) {
        temp_found = 1U;
    } else if (App_ExtractJsonFloat(payload, "rated_temperature", temperature) == 0U) {
        temp_found = 1U;
    } else if (App_ExtractJsonFloat(payload, "temperature_rated", temperature) == 0U) {
        temp_found = 1U;
    }

    if (App_ExtractJsonFloat(payload, "humidity", humidity) == 0U) {
        hum_found = 1U;
    } else if (App_ExtractJsonFloat(payload, "hum", humidity) == 0U) {
        hum_found = 1U;
    } else if (App_ExtractJsonFloat(payload, "rated_humidity", humidity) == 0U) {
        hum_found = 1U;
    } else if (App_ExtractJsonFloat(payload, "humidity_rated", humidity) == 0U) {
        hum_found = 1U;
    }

    return (uint8_t)((temp_found != 0U) && (hum_found != 0U) ? 0U : 1U);
}

static uint8_t App_ExtractJsonInt(const char *payload, const char *key, int32_t *out_value)
{
    char pattern[32];
    const char *cursor;
    char *value_end = NULL;
    long parsed_value;

    if ((payload == NULL) || (key == NULL) || (out_value == NULL)) {
        return 1U;
    }

    snprintf(pattern, sizeof(pattern), "\"%s\"", key);
    cursor = strstr(payload, pattern);
    if (cursor == NULL) {
        return 1U;
    }

    cursor = strchr(cursor + strlen(pattern), ':');
    if (cursor == NULL) {
        return 1U;
    }
    cursor++;

    while ((*cursor == ' ') || (*cursor == '\t')) {
        cursor++;
    }

    parsed_value = strtol(cursor, &value_end, 10);
    if ((value_end == cursor) || (value_end == NULL)) {
        return 1U;
    }

    *out_value = (int32_t)parsed_value;
    return 0U;
}

static uint8_t App_GetRtcHms(uint8_t *hour, uint8_t *minute, uint8_t *second)
{
    RTC_TimeTypeDef time = {0};
    RTC_DateTypeDef date = {0};

    if ((hour == NULL) || (minute == NULL) || (second == NULL)) {
        return 1U;
    }

    if (HAL_RTC_GetTime(&hrtc, &time, RTC_FORMAT_BIN) != HAL_OK) {
        return 1U;
    }
    if (HAL_RTC_GetDate(&hrtc, &date, RTC_FORMAT_BIN) != HAL_OK) {
        return 1U;
    }

    *hour = time.Hours;
    *minute = time.Minutes;
    *second = time.Seconds;
    return 0U;
}

static uint8_t App_SetRtcHms(uint8_t hour, uint8_t minute, uint8_t second)
{
    RTC_TimeTypeDef time = {0};
    RTC_DateTypeDef date = {0};

    if (MedicineTimer_IsValidHms(hour, minute, second) == 0U) {
        return 1U;
    }

    time.Hours = hour;
    time.Minutes = minute;
    time.Seconds = second;
    time.DayLightSaving = RTC_DAYLIGHTSAVING_NONE;
    time.StoreOperation = RTC_STOREOPERATION_RESET;

    date.WeekDay = RTC_WEEKDAY_MONDAY;
    date.Month = RTC_MONTH_JANUARY;
    date.Date = 1U;
    date.Year = 0U;

    if (HAL_RTC_SetTime(&hrtc, &time, RTC_FORMAT_BIN) != HAL_OK) {
        return 1U;
    }
    if (HAL_RTC_SetDate(&hrtc, &date, RTC_FORMAT_BIN) != HAL_OK) {
        return 1U;
    }
    return 0U;
}

static const char *App_GetMedicineTimerModeString(uint8_t mode)
{
    return (mode == MEDICINE_TIMER_MODE_CLOCK) ? "clock" : "countdown";
}

static uint8_t App_GetMedicineTimerActiveCount(void)
{
    uint8_t index;
    uint8_t count = 0U;

    for (index = 0U; index < MEDICINE_TIMER_MAX_COUNT; index++) {
        if (medicine_timer_slots[index].active != 0U) {
            count++;
        }
    }
    return count;
}

static void App_ClearMedicineTimerSlot(uint8_t index)
{
    if (index < MEDICINE_TIMER_MAX_COUNT) {
        medicine_timer_slots[index].active = 0U;
    }
}

static void App_ClearMedicineTimer(void)
{
    uint8_t index;

    (void)HAL_RTC_DeactivateAlarm(&hrtc, RTC_ALARM_A);
    for (index = 0U; index < MEDICINE_TIMER_MAX_COUNT; index++) {
        App_ClearMedicineTimerSlot(index);
    }
    medicine_timer_scheduled_id = 0U;
    medicine_timer_alarm_request_pending = 0U;
    medicine_timer_alarm_publish_flags = 0U;
    medicine_timer_cancel_publish_flags = 0U;
    medicine_timer_alarm_active = 0U;
    medicine_timer_current_alarm_id = 0U;
}

static uint8_t App_SetRtcAlarmHms(uint8_t hour, uint8_t minute, uint8_t second)
{
    RTC_AlarmTypeDef alarm = {0};

    if (MedicineTimer_IsValidHms(hour, minute, second) == 0U) {
        return 1U;
    }

    (void)HAL_RTC_DeactivateAlarm(&hrtc, RTC_ALARM_A);

    alarm.AlarmTime.Hours = hour;
    alarm.AlarmTime.Minutes = minute;
    alarm.AlarmTime.Seconds = second;
    alarm.AlarmTime.SubSeconds = 0U;
    alarm.AlarmTime.DayLightSaving = RTC_DAYLIGHTSAVING_NONE;
    alarm.AlarmTime.StoreOperation = RTC_STOREOPERATION_RESET;
    alarm.AlarmMask = RTC_ALARMMASK_DATEWEEKDAY;
    alarm.AlarmSubSecondMask = RTC_ALARMSUBSECONDMASK_ALL;
    alarm.AlarmDateWeekDaySel = RTC_ALARMDATEWEEKDAYSEL_DATE;
    alarm.AlarmDateWeekDay = 1U;
    alarm.Alarm = RTC_ALARM_A;

    if (HAL_RTC_SetAlarm_IT(&hrtc, &alarm, RTC_FORMAT_BIN) != HAL_OK) {
        return 1U;
    }

    return 0U;
}

static uint8_t App_RescheduleMedicineTimerAlarm(void)
{
    uint8_t now_hour = 0U;
    uint8_t now_minute = 0U;
    uint8_t now_second = 0U;
    uint8_t index;
    uint8_t best_index = MEDICINE_TIMER_MAX_COUNT;
    uint32_t best_remaining = MEDICINE_TIMER_SECONDS_PER_DAY + 1UL;
    uint32_t remaining;

    (void)HAL_RTC_DeactivateAlarm(&hrtc, RTC_ALARM_A);
    medicine_timer_scheduled_id = 0U;

    if (App_GetRtcHms(&now_hour, &now_minute, &now_second) != 0U) {
        return 1U;
    }

    for (index = 0U; index < MEDICINE_TIMER_MAX_COUNT; index++) {
        if (medicine_timer_slots[index].active == 0U) {
            continue;
        }

        remaining = MedicineTimer_ComputeClockRemaining(now_hour,
                                                        now_minute,
                                                        now_second,
                                                        medicine_timer_slots[index].target_hour,
                                                        medicine_timer_slots[index].target_minute,
                                                        medicine_timer_slots[index].target_second);
        if (remaining < best_remaining) {
            best_remaining = remaining;
            best_index = index;
        }
    }

    if (best_index >= MEDICINE_TIMER_MAX_COUNT) {
        return 0U;
    }

    if (App_SetRtcAlarmHms(medicine_timer_slots[best_index].target_hour,
                           medicine_timer_slots[best_index].target_minute,
                           medicine_timer_slots[best_index].target_second) != 0U) {
        return 1U;
    }

    medicine_timer_scheduled_id = medicine_timer_slots[best_index].id;
    return 0U;
}

static void App_SetMedicineTimerSlot(uint8_t index,
                                     uint8_t mode,
                                     uint8_t hour,
                                     uint8_t minute,
                                     uint8_t second)
{
    if (index < MEDICINE_TIMER_MAX_COUNT) {
        medicine_timer_slots[index].active = 1U;
        medicine_timer_slots[index].id = (uint8_t)(index + 1U);
        medicine_timer_slots[index].mode = mode;
        medicine_timer_slots[index].target_hour = hour;
        medicine_timer_slots[index].target_minute = minute;
        medicine_timer_slots[index].target_second = second;
    }
}

static void App_QueueControlResponse(const char *payload)
{
    size_t payload_len;

    if (payload == NULL) {
        return;
    }

    payload_len = strlen(payload);
    if (payload_len >= sizeof(control_response_payload)) {
        payload_len = sizeof(control_response_payload) - 1U;
    }

    memcpy(control_response_payload, payload, payload_len);
    control_response_payload[payload_len] = '\0';
    control_response_pending = 1U;
}

static void App_OnMqttMessage(const char *topic, const char *payload)
{
    char cmd[32];
    char mode[16];
    char response[256];
    int32_t value = 0;
    int32_t hour = 0;
    int32_t minute = 0;
    int32_t second = 0;
    int32_t now_hour = 0;
    int32_t now_minute = 0;
    int32_t now_second = 0;
    int32_t timer_id = 0;
    float rated_temperature;
    float rated_humidity;
    float current_rated_temperature;
    float current_rated_humidity;
    uint8_t rtc_hour = 0U;
    uint8_t rtc_minute = 0U;
    uint8_t rtc_second = 0U;
    uint8_t target_hour = 0U;
    uint8_t target_minute = 0U;
    uint8_t target_second = 0U;
    uint32_t remaining_seconds = 0UL;
    uint8_t timer_id_present = 0U;
    uint8_t slot_index = 0U;
    uint8_t active_count = 0U;
    uint8_t has_cmd;
    uint8_t is_set_command;

    if ((topic == NULL) || (payload == NULL)) {
        return;
    }

    if (strcmp(topic, MQTT_TOPIC_CONTROL) != 0) {
        return;
    }

    has_cmd = (uint8_t)(App_ExtractJsonString(payload, "cmd", cmd, sizeof(cmd)) == 0U);
    is_set_command = (uint8_t)(has_cmd &&
                               ((strcmp(cmd, "set_env_rated") == 0) ||
                                (strcmp(cmd, "set_rated_env") == 0) ||
                                (strcmp(cmd, "set_threshold") == 0)));

    if (has_cmd && (strcmp(cmd, "publish_now") == 0)) {
        (void)osSemaphoreRelease(mqttPublishSem);
        snprintf(response, sizeof(response),
                 "{\"cmd\":\"publish_now\",\"result\":\"ok\",\"timestamp\":%lu}",
                 HAL_GetTick());
        App_QueueControlResponse(response);
        return;
    }

    if (has_cmd && (strcmp(cmd, "set_interval") == 0)) {
        if ((App_ExtractJsonInt(payload, "value", &value) != 0U) || (value < 1) || (value > 300)) {
            snprintf(response, sizeof(response),
                     "{\"cmd\":\"set_interval\",\"result\":\"error\",\"error_msg\":\"invalid interval\"}");
            App_QueueControlResponse(response);
            return;
        }

        mqtt_publish_interval_ms = (uint32_t)value * 1000U;
        snprintf(response, sizeof(response),
                 "{\"cmd\":\"set_interval\",\"result\":\"ok\",\"interval\":%ld,\"timestamp\":%lu}",
                 (long)value, HAL_GetTick());
        App_QueueControlResponse(response);
        return;
    }

    if (has_cmd && (strcmp(cmd, "set_buzzer_enable") == 0)) {
        if ((App_ExtractJsonInt(payload, "value", &value) != 0U) || ((value != 0) && (value != 1))) {
            snprintf(response, sizeof(response),
                     "{\"cmd\":\"set_buzzer_enable\",\"result\":\"error\",\"error_msg\":\"invalid value\"}");
            App_QueueControlResponse(response);
            return;
        }

        buzzer_feature_enabled = (uint8_t)value;
        if (buzzer_feature_enabled == 0U) {
            Buzzer_StopTone();
        }
        mqtt_status_publish_pending = 1U;
        if (mqttPublishSem != NULL) {
            (void)osSemaphoreRelease(mqttPublishSem);
        }

        snprintf(response, sizeof(response),
                 "{\"cmd\":\"set_buzzer_enable\",\"result\":\"ok\",\"value\":%ld,\"timestamp\":%lu}",
                 (long)value, HAL_GetTick());
        App_QueueControlResponse(response);
        return;
    }

    if (has_cmd && (strcmp(cmd, "set_medicine_timer") == 0)) {
        timer_id_present = (uint8_t)(App_ExtractJsonInt(payload, "timer_id", &timer_id) == 0U);
        if ((timer_id_present != 0U) && (MedicineTimer_IsValidId(timer_id) == 0U)) {
            snprintf(response, sizeof(response),
                     "{\"cmd\":\"set_medicine_timer\",\"result\":\"error\",\"error_msg\":\"invalid timer_id\"}");
            App_QueueControlResponse(response);
            return;
        }

        if (timer_id_present != 0U) {
            slot_index = (uint8_t)(timer_id - 1);
        } else {
            for (slot_index = 0U; slot_index < MEDICINE_TIMER_MAX_COUNT; slot_index++) {
                if (medicine_timer_slots[slot_index].active == 0U) {
                    break;
                }
            }
            if (slot_index >= MEDICINE_TIMER_MAX_COUNT) {
                snprintf(response, sizeof(response),
                         "{\"cmd\":\"set_medicine_timer\",\"result\":\"error\",\"error_msg\":\"timer full\"}");
                App_QueueControlResponse(response);
                return;
            }
        }

        if ((App_ExtractJsonString(payload, "mode", mode, sizeof(mode)) != 0U) ||
            (App_ExtractJsonInt(payload, "hour", &hour) != 0U) ||
            (App_ExtractJsonInt(payload, "minute", &minute) != 0U) ||
            (App_ExtractJsonInt(payload, "second", &second) != 0U) ||
            (MedicineTimer_IsValidHms(hour, minute, second) == 0U)) {
            snprintf(response, sizeof(response),
                     "{\"cmd\":\"set_medicine_timer\",\"result\":\"error\",\"error_msg\":\"invalid timer\"}");
            App_QueueControlResponse(response);
            return;
        }

        if (strcmp(mode, "countdown") == 0) {
            if (App_GetRtcHms(&rtc_hour, &rtc_minute, &rtc_second) != 0U) {
                snprintf(response, sizeof(response),
                         "{\"cmd\":\"set_medicine_timer\",\"result\":\"error\",\"error_msg\":\"rtc unavailable\"}");
                App_QueueControlResponse(response);
                return;
            }
            if (MedicineTimer_ComputeCountdownTarget(rtc_hour, rtc_minute, rtc_second,
                                                     (uint8_t)hour, (uint8_t)minute, (uint8_t)second,
                                                     &target_hour, &target_minute, &target_second,
                                                     &remaining_seconds) != 0U) {
                snprintf(response, sizeof(response),
                         "{\"cmd\":\"set_medicine_timer\",\"result\":\"error\",\"error_msg\":\"countdown must be greater than zero\"}");
                App_QueueControlResponse(response);
                return;
            }
            App_SetMedicineTimerSlot(slot_index,
                                     MEDICINE_TIMER_MODE_COUNTDOWN,
                                     target_hour,
                                     target_minute,
                                     target_second);
            if (App_RescheduleMedicineTimerAlarm() != 0U) {
                App_ClearMedicineTimerSlot(slot_index);
                (void)App_RescheduleMedicineTimerAlarm();
                snprintf(response, sizeof(response),
                         "{\"cmd\":\"set_medicine_timer\",\"result\":\"error\",\"error_msg\":\"alarm set failed\"}");
                App_QueueControlResponse(response);
                return;
            }
        } else if (strcmp(mode, "clock") == 0) {
            if ((App_ExtractJsonInt(payload, "now_hour", &now_hour) != 0U) ||
                (App_ExtractJsonInt(payload, "now_minute", &now_minute) != 0U) ||
                (App_ExtractJsonInt(payload, "now_second", &now_second) != 0U) ||
                (MedicineTimer_IsValidHms(now_hour, now_minute, now_second) == 0U)) {
                snprintf(response, sizeof(response),
                         "{\"cmd\":\"set_medicine_timer\",\"result\":\"error\",\"error_msg\":\"invalid rtc sync time\"}");
                App_QueueControlResponse(response);
                return;
            }
            if (App_SetRtcHms((uint8_t)now_hour, (uint8_t)now_minute, (uint8_t)now_second) != 0U) {
                snprintf(response, sizeof(response),
                         "{\"cmd\":\"set_medicine_timer\",\"result\":\"error\",\"error_msg\":\"rtc sync failed\"}");
                App_QueueControlResponse(response);
                return;
            }
            remaining_seconds = MedicineTimer_ComputeClockRemaining((uint8_t)now_hour,
                                                                    (uint8_t)now_minute,
                                                                    (uint8_t)now_second,
                                                                    (uint8_t)hour,
                                                                    (uint8_t)minute,
                                                                    (uint8_t)second);
            target_hour = (uint8_t)hour;
            target_minute = (uint8_t)minute;
            target_second = (uint8_t)second;
            App_SetMedicineTimerSlot(slot_index,
                                     MEDICINE_TIMER_MODE_CLOCK,
                                     target_hour,
                                     target_minute,
                                     target_second);
            if (App_RescheduleMedicineTimerAlarm() != 0U) {
                App_ClearMedicineTimerSlot(slot_index);
                (void)App_RescheduleMedicineTimerAlarm();
                snprintf(response, sizeof(response),
                         "{\"cmd\":\"set_medicine_timer\",\"result\":\"error\",\"error_msg\":\"alarm set failed\"}");
                App_QueueControlResponse(response);
                return;
            }
        } else {
            snprintf(response, sizeof(response),
                     "{\"cmd\":\"set_medicine_timer\",\"result\":\"error\",\"error_msg\":\"unsupported timer mode\"}");
            App_QueueControlResponse(response);
            return;
        }

        active_count = App_GetMedicineTimerActiveCount();
        snprintf(response, sizeof(response),
                 "{\"cmd\":\"set_medicine_timer\",\"result\":\"ok\",\"mode\":\"%s\","
                 "\"timer_id\":%u,\"active_count\":%u,\"remaining_seconds\":%lu,"
                 "\"target_hour\":%u,\"target_minute\":%u,"
                 "\"target_second\":%u,\"timestamp\":%lu}",
                 mode,
                 (unsigned int)(slot_index + 1U),
                 (unsigned int)active_count,
                 (unsigned long)remaining_seconds,
                 (unsigned int)target_hour,
                 (unsigned int)target_minute,
                 (unsigned int)target_second,
                 HAL_GetTick());
        App_QueueControlResponse(response);
        return;
    }

    if (has_cmd && (strcmp(cmd, "cancel_medicine_timer") == 0)) {
        timer_id_present = (uint8_t)(App_ExtractJsonInt(payload, "timer_id", &timer_id) == 0U);
        if (timer_id_present != 0U) {
            if (MedicineTimer_IsValidId(timer_id) == 0U) {
                snprintf(response, sizeof(response),
                         "{\"cmd\":\"cancel_medicine_timer\",\"result\":\"error\",\"error_msg\":\"invalid timer_id\"}");
                App_QueueControlResponse(response);
                return;
            }
            slot_index = (uint8_t)(timer_id - 1);
            App_ClearMedicineTimerSlot(slot_index);
            if (medicine_timer_current_alarm_id == (uint8_t)timer_id) {
                medicine_timer_alarm_active = 0U;
                medicine_timer_alarm_request_pending = 0U;
                medicine_timer_current_alarm_id = 0U;
            }
            (void)App_RescheduleMedicineTimerAlarm();
            snprintf(response, sizeof(response),
                     "{\"cmd\":\"cancel_medicine_timer\",\"result\":\"ok\",\"timer_id\":%ld,"
                     "\"active_count\":%u,\"timestamp\":%lu}",
                     (long)timer_id,
                     (unsigned int)App_GetMedicineTimerActiveCount(),
                     HAL_GetTick());
        } else {
            App_ClearMedicineTimer();
            snprintf(response, sizeof(response),
                     "{\"cmd\":\"cancel_medicine_timer\",\"result\":\"ok\",\"active_count\":0,\"timestamp\":%lu}",
                     HAL_GetTick());
        }
        App_QueueControlResponse(response);
        return;
    }

    if (is_set_command || (has_cmd == 0U)) {
        if (App_ParseRatedValues(payload, &rated_temperature, &rated_humidity) != 0U) {
            if (has_cmd != 0U) {
                snprintf(response, sizeof(response),
                         "{\"cmd\":\"%s\",\"result\":\"error\",\"error_msg\":\"invalid rated values\"}",
                         cmd);
                App_QueueControlResponse(response);
            }
            return;
        }

        if (SensorManager_SetRatedEnvironment(rated_temperature, rated_humidity) != 0U) {
            snprintf(response, sizeof(response),
                     "{\"cmd\":\"set_env_rated\",\"result\":\"error\",\"error_msg\":\"value out of range\"}");
            App_QueueControlResponse(response);
            return;
        }

        SensorManager_GetRatedEnvironment(&current_rated_temperature, &current_rated_humidity);
        snprintf(response, sizeof(response),
                 "{\"cmd\":\"set_env_rated\",\"result\":\"ok\",\"rated_temperature\":%.2f,\"rated_humidity\":%.2f,\"timestamp\":%lu}",
                 current_rated_temperature, current_rated_humidity, HAL_GetTick());
        App_QueueControlResponse(response);
        return;
    }

    snprintf(response, sizeof(response),
             "{\"cmd\":\"%s\",\"result\":\"error\",\"error_msg\":\"unsupported command\"}",
             cmd);
    App_QueueControlResponse(response);
}

static uint8_t App_PublishPendingAlerts(void)
{
    char payload[320];
    EnvironmentAlertStatus_t env_status;
    MedicineBoxData_t data;
    uint8_t timer_index;
    uint8_t timer_flag;

    if (medicine_timer_reschedule_pending != 0U) {
        (void)App_RescheduleMedicineTimerAlarm();
        medicine_timer_reschedule_pending = 0U;
    }

    if (ESP8266_GetState() != ESP8266_STATE_MQTT_CONNECTED) {
        return 1U;
    }

    if (control_response_pending != 0U) {
        if (ESP8266_MQTT_Publish(MQTT_TOPIC_CONTROL_RESPONSE, control_response_payload, 0, 0) == 0U) {
            control_response_pending = 0U;
        }
    }

    if (env_alert_pending != 0U) {
        SensorManager_GetEnvAlertStatus(&env_status);
        SensorManager_GetData(&data);

        snprintf(payload, sizeof(payload),
                 "{\"event\":\"env_abnormal\",\"timestamp\":%lu,\"temperature\":%.2f,\"humidity\":%.2f,"
                 "\"rated_temperature\":%.2f,\"rated_humidity\":%.2f,"
                 "\"temperature_abnormal\":%d,\"humidity_abnormal\":%d}",
                 data.timestamp,
                 data.env.temperature,
                 data.env.humidity,
                 env_status.rated_temperature,
                 env_status.rated_humidity,
                 (env_alert_reason_flags & 0x01U) ? 1 : 0,
                 (env_alert_reason_flags & 0x02U) ? 1 : 0);

        if (ESP8266_MQTT_Publish(MQTT_TOPIC_ALERT, payload, 0, 0) == 0U) {
            env_alert_pending = 0U;
        }
    }

    if (env_recover_pending != 0U) {
        SensorManager_GetEnvAlertStatus(&env_status);
        SensorManager_GetData(&data);

        snprintf(payload, sizeof(payload),
                 "{\"event\":\"env_recovered\",\"timestamp\":%lu,\"temperature\":%.2f,\"humidity\":%.2f,"
                 "\"rated_temperature\":%.2f,\"rated_humidity\":%.2f}",
                 data.timestamp,
                 data.env.temperature,
                 data.env.humidity,
                 env_status.rated_temperature,
                 env_status.rated_humidity);

        if (ESP8266_MQTT_Publish(MQTT_TOPIC_ALERT, payload, 0, 0) == 0U) {
            env_recover_pending = 0U;
        }
    }

    if (drop_alert_pending != 0U) {
        snprintf(payload, sizeof(payload),
                 "{\"event\":\"drop_detected\",\"timestamp\":%lu,"
                 "\"accel_x\":%.3f,\"accel_y\":%.3f,\"accel_z\":%.3f,"
                 "\"accel_magnitude\":%.3f,\"threshold_g\":%.1f,\"duration_ms\":%u,"
                 "\"freefall_threshold_g\":%.2f}",
                 drop_alert_timestamp,
                 drop_alert_accel_x,
                 drop_alert_accel_y,
                 drop_alert_accel_z,
                 drop_alert_accel_magnitude,
                 DROP_IMPACT_THRESHOLD_G,
                 (unsigned int)DROP_IMPACT_WINDOW_MS,
                 DROP_FREEFALL_THRESHOLD_G);

        if (ESP8266_MQTT_Publish(MQTT_TOPIC_ALERT, payload, 0, 0) == 0U) {
            drop_alert_pending = 0U;
        }
    }

    if (drop_alarm_cancel_pending != 0U) {
        snprintf(payload, sizeof(payload),
                 "{\"event\":\"drop_alarm_cancelled\",\"timestamp\":%lu,"
                 "\"source\":\"key2\",\"stop_push\":1}",
                 drop_alarm_cancel_timestamp);

        if (ESP8266_MQTT_Publish(MQTT_TOPIC_ALERT, payload, 0, 0) == 0U) {
            drop_alarm_cancel_pending = 0U;
        }
    }

    for (timer_index = 0U; timer_index < MEDICINE_TIMER_MAX_COUNT; timer_index++) {
        timer_flag = (uint8_t)(1U << timer_index);

        if ((medicine_timer_alarm_publish_flags & timer_flag) != 0U) {
            snprintf(payload, sizeof(payload),
                     "{\"event\":\"medicine_timer_alarm\",\"timestamp\":%lu,"
                     "\"timer_id\":%u,\"mode\":\"%s\",\"target_hour\":%u,"
                     "\"target_minute\":%u,\"target_second\":%u}",
                     medicine_timer_alarm_timestamps[timer_index],
                     (unsigned int)(timer_index + 1U),
                     App_GetMedicineTimerModeString(medicine_timer_slots[timer_index].mode),
                     (unsigned int)medicine_timer_slots[timer_index].target_hour,
                     (unsigned int)medicine_timer_slots[timer_index].target_minute,
                     (unsigned int)medicine_timer_slots[timer_index].target_second);

            if (ESP8266_MQTT_Publish(MQTT_TOPIC_ALERT, payload, 0, 0) == 0U) {
                medicine_timer_alarm_publish_flags &= (uint8_t)~timer_flag;
            }
        }

        if ((medicine_timer_cancel_publish_flags & timer_flag) != 0U) {
            snprintf(payload, sizeof(payload),
                     "{\"event\":\"medicine_timer_cancelled\",\"timestamp\":%lu,"
                     "\"timer_id\":%u,\"source\":\"key2\",\"stop_push\":1}",
                     medicine_timer_cancel_timestamps[timer_index],
                     (unsigned int)(timer_index + 1U));

            if (ESP8266_MQTT_Publish(MQTT_TOPIC_ALERT, payload, 0, 0) == 0U) {
                medicine_timer_cancel_publish_flags &= (uint8_t)~timer_flag;
            }
        }
    }

    return 0U;
}

static void Buzzer_StartTone(void)
{
    uint32_t pclk2_hz = HAL_RCC_GetPCLK2Freq();
    uint32_t ppre2_bits = (RCC->CFGR & RCC_CFGR_PPRE2);
    uint32_t timer_clk_hz = (ppre2_bits == RCC_CFGR_PPRE2_DIV1) ? pclk2_hz : (pclk2_hz * 2U);
    uint32_t timer_tick_hz = timer_clk_hz / (htim1.Init.Prescaler + 1U);
    uint32_t auto_reload = timer_tick_hz / BUZZER_TONE_HZ;
    uint32_t pulse;

    if (App_IsBuzzerFeatureEnabled() == 0U) {
        return;
    }

    if (auto_reload < 2U) {
        auto_reload = 2U;
    } else if (auto_reload > 0x10000U) {
        auto_reload = 0x10000U;
    }

    __HAL_TIM_SET_AUTORELOAD(&htim1, auto_reload - 1U);
    __HAL_TIM_SET_COUNTER(&htim1, 0U);
    htim1.Instance->EGR = TIM_EGR_UG;

    pulse = (auto_reload * BUZZER_TONE_DUTY_PCT) / 100U;
    if (pulse == 0U) {
        pulse = 1U;
    } else if (pulse >= auto_reload) {
        pulse = auto_reload - 1U;
    }
    __HAL_TIM_SET_COMPARE(&htim1, TIM_CHANNEL_1, pulse);
    (void)HAL_TIM_PWM_Start(&htim1, TIM_CHANNEL_1);
}

static void Buzzer_StopTone(void)
{
    (void)HAL_TIM_PWM_Stop(&htim1, TIM_CHANNEL_1);
    __HAL_TIM_SET_COMPARE(&htim1, TIM_CHANNEL_1, 0U);
}

static uint8_t App_UpdateKey2Pressed(KeyDebounceState_t *state)
{
    uint32_t now;
    uint8_t key_raw_high;

    if (state == NULL) {
        return 0U;
    }

    now = HAL_GetTick();
    key_raw_high = (HAL_GPIO_ReadPin(KEY3_GPIO_Port, KEY3_Pin) == GPIO_PIN_SET) ? 1U : 0U;

    if (state->key_pressed_latched == 0U) {
        if (key_raw_high != 0U) {
            if (state->key_armed == 0U) {
                state->key_armed = 1U;
                state->key_press_tick = now;
            } else if ((uint32_t)(now - state->key_press_tick) >= KEY2_DEBOUNCE_MS) {
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

static uint8_t Buzzer_WaitWithCancel(uint32_t wait_ms, KeyDebounceState_t *key_state)
{
    uint32_t start_tick;

    if (key_state == NULL) {
        return 0U;
    }

    start_tick = HAL_GetTick();
    while ((uint32_t)(HAL_GetTick() - start_tick) < wait_ms) {
        uint32_t elapsed = (uint32_t)(HAL_GetTick() - start_tick);
        uint32_t remaining = (wait_ms > elapsed) ? (wait_ms - elapsed) : 0U;
        uint32_t delay_ms = (remaining > BUZZER_POLL_INTERVAL_MS) ? BUZZER_POLL_INTERVAL_MS : remaining;

        if (App_IsBuzzerFeatureEnabled() == 0U) {
            return 1U;
        }
        if (App_UpdateKey2Pressed(key_state) != 0U) {
            return 1U;
        }
        if (delay_ms > 0U) {
            osDelay(delay_ms);
        }
    }

    return 0U;
}

static uint8_t Buzzer_WaitMedicineTimerDelay(uint32_t wait_ms, KeyDebounceState_t *key_state)
{
    uint32_t start_tick;
    uint32_t elapsed;
    uint32_t remaining;
    uint32_t delay_ms;

    if (key_state == NULL) {
        return 0U;
    }

    start_tick = HAL_GetTick();
    while (((uint32_t)(HAL_GetTick() - start_tick) < wait_ms) &&
           (medicine_timer_alarm_active != 0U)) {
        elapsed = (uint32_t)(HAL_GetTick() - start_tick);
        remaining = (wait_ms > elapsed) ? (wait_ms - elapsed) : 0U;
        delay_ms = (remaining > BUZZER_POLL_INTERVAL_MS) ? BUZZER_POLL_INTERVAL_MS : remaining;

        if (App_IsBuzzerFeatureEnabled() == 0U) {
            return 1U;
        }
        if (App_UpdateKey2Pressed(key_state) != 0U) {
            return 1U;
        }
        if (delay_ms > 0U) {
            osDelay(delay_ms);
        }
    }

    return 0U;
}

void HAL_RTC_AlarmAEventCallback(RTC_HandleTypeDef *hrtc_arg)
{
    uint8_t scheduled_index;
    uint8_t index;
    uint8_t target_hour;
    uint8_t target_minute;
    uint8_t target_second;
    uint32_t now_tick;

    if ((hrtc_arg != NULL) && (hrtc_arg->Instance == RTC) && (medicine_timer_scheduled_id != 0U)) {
        scheduled_index = (uint8_t)(medicine_timer_scheduled_id - 1U);
        if ((scheduled_index >= MEDICINE_TIMER_MAX_COUNT) ||
            (medicine_timer_slots[scheduled_index].active == 0U)) {
            medicine_timer_reschedule_pending = 1U;
            (void)osSemaphoreRelease(mqttPublishSem);
            return;
        }

        target_hour = medicine_timer_slots[scheduled_index].target_hour;
        target_minute = medicine_timer_slots[scheduled_index].target_minute;
        target_second = medicine_timer_slots[scheduled_index].target_second;
        now_tick = HAL_GetTick();

        medicine_timer_current_alarm_id = medicine_timer_slots[scheduled_index].id;
        for (index = 0U; index < MEDICINE_TIMER_MAX_COUNT; index++) {
            if ((medicine_timer_slots[index].active != 0U) &&
                (medicine_timer_slots[index].target_hour == target_hour) &&
                (medicine_timer_slots[index].target_minute == target_minute) &&
                (medicine_timer_slots[index].target_second == target_second)) {
                medicine_timer_slots[index].active = 0U;
                medicine_timer_alarm_timestamps[index] = now_tick;
                medicine_timer_alarm_publish_flags |= (uint8_t)(1U << index);
            }
        }

        medicine_timer_alarm_active = 1U;
        medicine_timer_alarm_request_pending = 1U;
        medicine_timer_scheduled_id = 0U;
        medicine_timer_reschedule_pending = 1U;
        (void)osSemaphoreRelease(mqttPublishSem);
        if (App_IsBuzzerFeatureEnabled() != 0U) {
            (void)osSemaphoreRelease(buzzerAlertSem);
        } else {
            medicine_timer_alarm_request_pending = 0U;
            medicine_timer_alarm_active = 0U;
        }
    }
}

/**
  * @brief  应用初始化
  */
void App_Init(void)
{
    printf("[APP] System Initializing...\r\n");

    /* 初始化传感器 */
    SensorManager_Init();
    printf("[APP] Sensors Initialized\r\n");
    
    /* 初始化ESP8266 */
    ESP8266_Init();
    printf("[APP] ESP8266 Initialized\r\n");

    /* 初始化日志互斥锁和RTOS对象
     * 注意：必须放在需要HAL_Delay的初始化之后，
     * 防止在内核临界态下阻塞Tick导致HAL_Delay卡死。 */
    DebugLog_InitMutex();
    
    mqttPublishSem = osSemaphoreNew(1, 0, NULL);
    buzzerAlertSem = osSemaphoreNew(1, 0, NULL);
    ESP8266_RegisterMQTTMessageCallback(App_OnMqttMessage);
    Buzzer_StopTone();
    HAL_NVIC_SetPriority(RTC_Alarm_IRQn, 5, 0);
    HAL_NVIC_EnableIRQ(RTC_Alarm_IRQn);
    
    system_ready = 1;
    printf("[APP] System Ready\r\n");
}

/**
  * @brief  启动所有任务
  */
void App_StartTasks(void)
{
    const osThreadAttr_t sensorTask_attr = {
        .name = "SensorTask",
        .priority = SENSOR_TASK_PRIORITY,
        .stack_size = SENSOR_TASK_STACK_SIZE * 4,
    };
    
    const osThreadAttr_t mqttTask_attr = {
        .name = "MQTTTask",
        .priority = MQTT_TASK_PRIORITY,
        .stack_size = MQTT_TASK_STACK_SIZE * 4,
    };
    
    const osThreadAttr_t displayTask_attr = {
        .name = "DisplayTask",
        .priority = DISPLAY_TASK_PRIORITY,
        .stack_size = DISPLAY_TASK_STACK_SIZE * 4,
    };
    
    const osThreadAttr_t ledTask_attr = {
        .name = "LEDTask",
        .priority = LED_TASK_PRIORITY,
        .stack_size = LED_TASK_STACK_SIZE * 4,
    };

    const osThreadAttr_t buzzerTask_attr = {
        .name = "BuzzerTask",
        .priority = BUZZER_TASK_PRIORITY,
        .stack_size = BUZZER_TASK_STACK_SIZE * 4,
    };
    
    sensorTaskHandle = osThreadNew(SensorTask, NULL, &sensorTask_attr);
    mqttTaskHandle = osThreadNew(MQTTTask, NULL, &mqttTask_attr);
    displayTaskHandle = osThreadNew(DisplayTask, NULL, &displayTask_attr);
    buzzerTaskHandle = osThreadNew(BuzzerTask, NULL, &buzzerTask_attr);
    osThreadNew(LEDTask, NULL, &ledTask_attr);
}

/**
  * @brief  传感器采集任务
  * @param  argument: 任务参数
  * @note   修复: 添加MQTT状态检查，防止信号量累积
  */
void SensorTask(void *argument)
{
    EnvironmentAlertStatus_t env_status;
    MedicineBoxData_t sensor_data;
    uint8_t sensor_read_result;
    uint32_t last_mqtt_tick = 0;
    uint8_t drop_freefall_armed = 0U;
    uint32_t drop_freefall_tick = 0U;
    
    printf("[SensorTask] Started\r\n");
    
    for (;;) {
        sensor_read_result = SensorManager_ReadAll();
        App_SetBusFaultFlag(APP_BUS_FAULT_SENSOR, (sensor_read_result != 0U) ? 1U : 0U);
        SensorManager_GetEnvAlertStatus(&env_status);
        SensorManager_GetData(&sensor_data);

        if (env_status.is_abnormal != 0U) {
            uint8_t reason_flags = 0U;
            uint8_t should_buzz = 0U;
            uint32_t now_tick = HAL_GetTick();
            if (env_status.temperature_abnormal != 0U) {
                reason_flags |= 0x01U;
            }
            if (env_status.humidity_abnormal != 0U) {
                reason_flags |= 0x02U;
            }
            env_alert_reason_flags = reason_flags;

            if (env_alert_latched == 0U) {
                env_alert_latched = 1U;
                env_alert_pending = 1U;
                should_buzz = 1U;
            } else if (reason_flags != env_alert_last_reason_flags) {
                /* 异常类型变化（如新增湿度异常）时立即提示一次。 */
                should_buzz = 1U;
            } else if ((uint32_t)(now_tick - env_alert_last_buzzer_tick) >= ENV_ALERT_REPEAT_MS) {
                /* 异常持续期间按固定周期重复蜂鸣，避免首次提示被错过。 */
                should_buzz = 1U;
            }

            env_alert_last_reason_flags = reason_flags;

            if ((should_buzz != 0U) && (App_IsBuzzerFeatureEnabled() != 0U)) {
                env_alert_last_buzzer_tick = now_tick;
                (void)osSemaphoreRelease(buzzerAlertSem);
            }
        } else if (env_alert_latched != 0U) {
            env_alert_latched = 0U;
            env_alert_reason_flags = 0U;
            env_alert_last_reason_flags = 0U;
            env_alert_last_buzzer_tick = 0U;
            env_recover_pending = 1U;
        }

        if ((sensor_data.is_valid != 0U) && (drop_alert_latched == 0U)) {
            float accel_sq = (sensor_data.motion.accel_x * sensor_data.motion.accel_x) +
                             (sensor_data.motion.accel_y * sensor_data.motion.accel_y) +
                             (sensor_data.motion.accel_z * sensor_data.motion.accel_z);

            if (accel_sq <= DROP_FREEFALL_THRESHOLD_SQ) {
                drop_freefall_armed = 1U;
                drop_freefall_tick = sensor_data.timestamp;
            } else if ((drop_freefall_armed != 0U) &&
                       ((uint32_t)(sensor_data.timestamp - drop_freefall_tick) > DROP_IMPACT_WINDOW_MS)) {
                drop_freefall_armed = 0U;
            }

            if ((drop_freefall_armed != 0U) && (accel_sq >= DROP_IMPACT_THRESHOLD_SQ)) {
                drop_alert_latched = 1U;
                drop_alert_pending = 1U;
                drop_alarm_request_pending = 1U;
                drop_alert_timestamp = sensor_data.timestamp;
                drop_alert_accel_x = sensor_data.motion.accel_x;
                drop_alert_accel_y = sensor_data.motion.accel_y;
                drop_alert_accel_z = sensor_data.motion.accel_z;
                drop_alert_accel_magnitude = sqrtf(accel_sq);
                drop_freefall_armed = 0U;
                (void)osSemaphoreRelease(mqttPublishSem);
                if (App_IsBuzzerFeatureEnabled() != 0U) {
                    (void)osSemaphoreRelease(buzzerAlertSem);
                }
            }
        } else {
            drop_freefall_armed = 0U;
        }

        if ((HAL_GetTick() - last_mqtt_tick) >= mqtt_publish_interval_ms) {
            if (ESP8266_GetState() == ESP8266_STATE_MQTT_CONNECTED) {
                (void)osSemaphoreRelease(mqttPublishSem);
            }
            last_mqtt_tick = HAL_GetTick();
        }
        
        osDelay(SENSOR_SAMPLE_INTERVAL_MS);
    }
}

/**
  * @brief  MQTT通信任务
  * @param  argument: 任务参数
  */
void MQTTTask(void *argument)
{
    uint8_t wifi_init_ready = 0U;
    uint8_t retry_count = 0;
    uint8_t max_retries = MQTT_CONNECT_RETRY_COUNT;
    uint8_t wifi_disconnect_suspect_count = 0U;
    char json_buffer[JSON_BUFFER_SIZE];
    char status_buffer[192];
    char runtime_client_id[64];
    uint32_t last_publish_tick = 0;
    uint32_t last_publish_log_tick = 0U;
    uint32_t stack_check_tick = 0;  // 添加堆栈检查时间戳
    uint32_t wifi_health_check_tick = 0U;

    printf("[MQTTTask] Started\r\n");

    /* 等待系统就绪 */
    osDelay(1000);
    (void)snprintf(runtime_client_id, sizeof(runtime_client_id), "%s-%08lX",
                   MQTT_CLIENT_ID, (unsigned long)HAL_GetUIDw0());

    for (;;) {
        /* 每 10 秒检查一次堆栈 */
        if ((int32_t)(HAL_GetTick() - stack_check_tick) > 10000) {
            UBaseType_t uxHighWaterMark = uxTaskGetStackHighWaterMark(NULL);
            if (uxHighWaterMark < 100) {  // 剩余少于 100 words
                printf("[MQTTTask] Warning: Low stack %lu words\r\n", uxHighWaterMark);
            }
            stack_check_tick = HAL_GetTick();
        }
        /* 状态机处理 */
        switch (ESP8266_GetState()) {
            case ESP8266_STATE_RESET:
            case ESP8266_STATE_INIT:
                /* 初始化WiFi */
                app_wifi_connected = 0U;
                printf("[MQTT] Initializing WiFi...\r\n");
                if (ESP8266_WiFi_Init() == 0) {
                    printf("[MQTT] WiFi AT OK\r\n");
                    wifi_init_ready = 1U;
                } else {
                    printf("[MQTT] WiFi Init failed (%s), retry...\r\n", ESP8266_GetMqttDiag());
                    wifi_init_ready = 0U;
                    ESP8266_Reset();
                    osDelay(WIFI_INIT_RETRY_DELAY_MS);
                }
                osDelay(WIFI_CONNECT_WAIT_MS);
                break;
                
            case ESP8266_STATE_WIFI_CONNECTING:
                /* 等待连接完成 */
                osDelay(WIFI_CONNECT_WAIT_MS);
                break;
                
            case ESP8266_STATE_WIFI_CONNECTED:
                /* WiFi已连接，连接MQTT */
                printf("[MQTT] Connecting to broker...\r\n");

                if (ESP8266_MQTT_Init(MQTT_BROKER_IP, MQTT_BROKER_PORT) == 0) {
                    if ((ESP8266_MQTT_Connect(runtime_client_id, MQTT_USERNAME, MQTT_PASSWORD) == 0) &&
                        (ESP8266_MQTT_ConnectToBroker(MQTT_BROKER_IP, 0U) == 0)) {
                        printf("[MQTT] Connected to %s:%d\r\n", MQTT_BROKER_IP, MQTT_BROKER_PORT);

                        /* 订阅控制主题 */
                        ESP8266_MQTT_Subscribe(MQTT_TOPIC_CONTROL, 0);

                        /* 发布上线消息 */
                        App_BuildStatusPayload(status_buffer, sizeof(status_buffer));
                        ESP8266_MQTT_Publish(MQTT_TOPIC_STATUS, status_buffer, 0, 1);

                        retry_count = 0;
                    } else {
                        retry_count++;
                        printf("[MQTT] Connect failed, retry %d/%d\r\n", retry_count, max_retries);
                    }
                } else {
                    retry_count++;
                    printf("[MQTT] Init failed, retry %d/%d\r\n", retry_count, max_retries);
                }

                if (ESP8266_GetState() != ESP8266_STATE_MQTT_CONNECTED) {
                    if (retry_count >= max_retries) {
                        printf("[MQTT] Broker unavailable after %d retries, keep WiFi and retry later...\r\n", max_retries);
                        retry_count = 0;
                    }
                    osDelay(MQTT_RETRY_DELAY_MS);
                }
                break;
                
            case ESP8266_STATE_MQTT_CONNECTED:
                ESP8266_ProcessRxData();
                (void)App_PublishPendingAlerts();

                if (mqtt_status_publish_pending != 0U) {
                    App_BuildStatusPayload(status_buffer, sizeof(status_buffer));
                    if (ESP8266_MQTT_Publish(MQTT_TOPIC_STATUS, status_buffer, 0, 1) == 0) {
                        mqtt_status_publish_pending = 0U;
                    }
                }

                if (osSemaphoreAcquire(mqttPublishSem, 100) == osOK) {
                    SensorManager_CreateJSON(json_buffer, sizeof(json_buffer));
                    
                    uint8_t pub_retry = 0;
                    while (pub_retry < MQTT_PUBLISH_RETRY_COUNT) {
                        if (ESP8266_MQTT_Publish(MQTT_TOPIC_DATA, json_buffer, 0, 0) == 0) {
                            /* 避免每5秒打印整包JSON导致串口阻塞和长期运行卡顿。 */
                            if ((uint32_t)(HAL_GetTick() - last_publish_log_tick) >= 60000U) {
                                printf("[MQTT] Published OK\r\n");
                                last_publish_log_tick = HAL_GetTick();
                            }
                            last_publish_tick = HAL_GetTick();
                            break;
                        } else {
                            pub_retry++;
                            printf("[MQTT] Publish failed, retry %d/%d\r\n", pub_retry, MQTT_PUBLISH_RETRY_COUNT);
                            osDelay(500);
                        }
                    }

                    if (pub_retry >= MQTT_PUBLISH_RETRY_COUNT) {
                        printf("[MQTT] Publish failed after %d retries\r\n", MQTT_PUBLISH_RETRY_COUNT);
                        (void)ESP8266_MQTT_Disconnect();
                    }
                }

                if ((int32_t)(HAL_GetTick() - last_publish_tick) > MQTT_FORCE_PUBLISH_MS) {
                    (void)osSemaphoreRelease(mqttPublishSem);
                }
                break;
                
            case ESP8266_STATE_ERROR:
                printf("[MQTT] Error state, resetting...\r\n");
                ESP8266_Reset();
                app_wifi_connected = 0U;
                wifi_init_ready = 0U;
                wifi_disconnect_suspect_count = 0U;
                retry_count = 0;
                osDelay(ERROR_RESET_DELAY_MS);
                break;
                
            default:
                break;
        }
        
        /* WiFi连接管理 */
        if (MQTTTask_ShouldStartWiFiConnect(app_wifi_connected,
                                            wifi_init_ready,
                                            (uint8_t)ESP8266_GetState()) != 0U) {
            printf("[MQTT] Connecting to WiFi: %s\r\n", WIFI_SSID);
            if (ESP8266_WiFi_Connect(WIFI_SSID, WIFI_PASSWORD) == 0) {
                printf("[MQTT] WiFi Connected\r\n");
                app_wifi_connected = 1U;
                wifi_disconnect_suspect_count = 0U;
                wifi_health_check_tick = HAL_GetTick();
                retry_count = 0;
            } else {
                retry_count++;
                printf("[MQTT] WiFi Connect failed (%s), retry %d/%d\r\n",
                       ESP8266_GetMqttDiag(), retry_count, max_retries);
                if (retry_count >= max_retries) {
                    printf("[MQTT] WiFi connection failed after %d retries, reset ESP8266...\r\n", max_retries);
                    ESP8266_Reset();
                    app_wifi_connected = 0U;
                    wifi_init_ready = 0U;
                    retry_count = 0;
                }
                osDelay(WIFI_CONNECT_RETRY_DELAY_MS);
            }
        }
        
        /* WiFi健康检查：降低频率并做连续失败确认，避免瞬时AT超时引发误判重连。 */
        if ((app_wifi_connected != 0U) &&
            ((uint32_t)(HAL_GetTick() - wifi_health_check_tick) >= WIFI_HEALTH_CHECK_INTERVAL_MS)) {
            ESP8266_State_t check_state = ESP8266_GetState();
            wifi_health_check_tick = HAL_GetTick();

            if (check_state == ESP8266_STATE_MQTT_CONNECTED) {
                /* MQTT活跃阶段依赖URC状态切换，不主动高频轮询CIPSTATUS。 */
                wifi_disconnect_suspect_count = 0U;
            } else if ((check_state == ESP8266_STATE_WIFI_CONNECTED) && (ESP8266_WiFi_IsConnected() == 0U)) {
                wifi_disconnect_suspect_count++;
                if (wifi_disconnect_suspect_count >= WIFI_DISCONNECT_CONFIRM_COUNT) {
                    printf("[MQTT] WiFi disconnected (confirmed)\r\n");
                    app_wifi_connected = 0U;
                    retry_count = 0;
                    wifi_disconnect_suspect_count = 0U;
                }
            } else {
                wifi_disconnect_suspect_count = 0U;
            }
        }

        {
            ESP8266_State_t current_state = ESP8266_GetState();
            uint8_t uart_fault_active = (uint8_t)((current_state == ESP8266_STATE_ERROR) ||
                                                  (huart3.gState == HAL_UART_STATE_RESET));
            uint8_t spi_fault_active = (uint8_t)(hspi1.State == HAL_SPI_STATE_RESET);
            App_SetBusFaultFlag(APP_BUS_FAULT_UART3, uart_fault_active);
            App_SetBusFaultFlag(APP_BUS_FAULT_SPI1, spi_fault_active);
        }
        
        osDelay(STATE_CHECK_INTERVAL_MS);
    }
}

/**
  * @brief  显示任务
  * @param  argument: 任务参数
  */
void DisplayTask(void *argument)
{
    MedicineBoxData_t data;
    DisplayPage2Status_t sys_status;
    KeyDebounceState_t key2_toggle_state = {0};
    DisplayLogicState_t logic_state = {0};
    DisplayPage_t current_page = DISPLAY_PAGE_ENV;
    uint32_t last_render_tick = 0U;
    uint8_t key_last_raw = 0U;
    uint8_t key_stable_level = 0U;
    uint8_t key_stable_count = 0U;
    uint8_t key_idle_level = 0U;
    uint8_t key_idle_known = 0U;
    uint8_t key_pressed = 0U;
    uint8_t key2_raw = 0U;
    uint8_t force_render = 1U;
    
    printf("[DisplayTask] Started\r\n");
    DisplayUI_Init();
    
    for (;;) {
        uint32_t now = HAL_GetTick();
        uint8_t key_raw_high = (HAL_GPIO_ReadPin(KEY1_GPIO_Port, KEY1_Pin) == GPIO_PIN_SET) ? 1U : 0U;

        if (key_raw_high == key_last_raw) {
            if (key_stable_count < 3U) {
                key_stable_count++;
            }
        } else {
            key_last_raw = key_raw_high;
            key_stable_count = 0U;
        }

        if (key_stable_count >= 2U) {
            key_stable_level = key_last_raw;
        }

        /* 启动后学习按键空闲电平，兼容高/低有效接法。 */
        if ((key_idle_known == 0U) && (now >= 1000U)) {
            key_idle_level = key_stable_level;
            key_idle_known = 1U;
        }

        if (key_idle_known != 0U) {
            key_pressed = (uint8_t)((key_stable_level != key_idle_level) ? 1U : 0U);
        } else {
            key_pressed = 0U;
        }

        if (DisplayLogic_UpdateKey(&logic_state, key_pressed, now, &current_page) != 0U) {
            force_render = 1U;
        }

        if (App_UpdateKey2Pressed(&key2_toggle_state) != 0U) {
            App_ToggleBuzzerFeature();
            force_render = 1U;
        }

        if (force_render != 0U || (uint32_t)(now - last_render_tick) >= 200U) {
            SensorManager_GetData(&data);
            key2_raw = (HAL_GPIO_ReadPin(KEY3_GPIO_Port, KEY3_Pin) == GPIO_PIN_SET) ? 1U : 0U;

            if (current_page == DISPLAY_PAGE_ENV) {
                DisplayUI_RenderPage1(&data, App_IsBuzzerFeatureEnabled(), key_stable_level, key2_raw);
            } else {
                ESP8266_State_t state = ESP8266_GetState();
                sys_status.wifi_state = MQTTTask_GetDisplayWiFiState(app_wifi_connected);
                sys_status.mqtt_ok = (state == ESP8266_STATE_MQTT_CONNECTED) ? 1U : 0U;
                sys_status.uart1_init = (huart1.gState != HAL_UART_STATE_RESET) ? 1U : 0U;
                sys_status.uart3_init = (huart3.gState != HAL_UART_STATE_RESET) ? 1U : 0U;
                sys_status.uart3_rx_recent = ((g_uart3_last_rx_tick != 0U) &&
                                              ((uint32_t)(now - g_uart3_last_rx_tick) <= 3000U)) ? 1U : 0U;
                sys_status.uptime_s = now / 1000U;
                DisplayUI_RenderPage2(&sys_status);
            }

            last_render_tick = now;
            force_render = 0U;
        }

        osDelay(20);
    }
}

void BuzzerTask(void *argument)
{
    uint8_t beep_index;
    uint8_t alarm_round;
    uint8_t cancelled;
    uint8_t cancel_index;
    uint32_t off_delay_ms;
    KeyDebounceState_t key2_state = {0};

    printf("[BuzzerTask] Started\r\n");

    off_delay_ms = (BUZZER_BEEP_INTERVAL_MS > BUZZER_BEEP_ON_MS) ?
                   (BUZZER_BEEP_INTERVAL_MS - BUZZER_BEEP_ON_MS) : 0U;

    for (;;) {
        if (osSemaphoreAcquire(buzzerAlertSem, osWaitForever) == osOK) {
            if (App_IsBuzzerFeatureEnabled() == 0U) {
                medicine_timer_alarm_request_pending = 0U;
                medicine_timer_alarm_active = 0U;
                medicine_timer_current_alarm_id = 0U;
                Buzzer_StopTone();
                continue;
            }

            if (drop_alarm_request_pending != 0U) {
                drop_alarm_request_pending = 0U;
                cancelled = 0U;

                for (alarm_round = 0U; alarm_round < DROP_ALARM_REPEAT_COUNT; alarm_round++) {
                    Buzzer_StartTone();
                    if (Buzzer_WaitWithCancel(DROP_ALARM_ON_MS, &key2_state) != 0U) {
                        cancelled = 1U;
                        Buzzer_StopTone();
                        break;
                    }
                    Buzzer_StopTone();

                    if ((alarm_round + 1U) < DROP_ALARM_REPEAT_COUNT) {
                        if (Buzzer_WaitWithCancel(DROP_ALARM_GAP_MS, &key2_state) != 0U) {
                            cancelled = 1U;
                            break;
                        }
                    }
                }

                Buzzer_StopTone();
                if (cancelled != 0U) {
                    drop_alarm_cancel_timestamp = HAL_GetTick();
                    drop_alarm_cancel_pending = 1U;
                    (void)osSemaphoreRelease(mqttPublishSem);
                }
                drop_alert_latched = 0U;
                continue;
            }

            if (medicine_timer_alarm_request_pending != 0U) {
                medicine_timer_alarm_request_pending = 0U;
                medicine_timer_alarm_active = 1U;
                cancelled = 0U;

                while (medicine_timer_alarm_active != 0U) {
                    for (beep_index = 0U;
                         (beep_index < MEDICINE_TIMER_SHORT_BEEP_COUNT) &&
                         (medicine_timer_alarm_active != 0U);
                         beep_index++) {
                        Buzzer_StartTone();
                        if (Buzzer_WaitMedicineTimerDelay(MEDICINE_TIMER_SHORT_BEEP_ON_MS, &key2_state) != 0U) {
                            cancelled = 1U;
                            break;
                        }
                        Buzzer_StopTone();
                        if (Buzzer_WaitMedicineTimerDelay(MEDICINE_TIMER_SHORT_BEEP_GAP_MS, &key2_state) != 0U) {
                            cancelled = 1U;
                            break;
                        }
                    }

                    Buzzer_StopTone();
                    if ((cancelled != 0U) || (medicine_timer_alarm_active == 0U)) {
                        break;
                    }
                    if (Buzzer_WaitMedicineTimerDelay(MEDICINE_TIMER_SHORT_ROUND_GAP_MS, &key2_state) != 0U) {
                        cancelled = 1U;
                        break;
                    }
                }
                Buzzer_StopTone();

                medicine_timer_alarm_active = 0U;
                if (cancelled != 0U) {
                    if ((medicine_timer_current_alarm_id >= 1U) &&
                        (medicine_timer_current_alarm_id <= MEDICINE_TIMER_MAX_COUNT)) {
                        cancel_index = (uint8_t)(medicine_timer_current_alarm_id - 1U);
                        medicine_timer_cancel_timestamps[cancel_index] = HAL_GetTick();
                        medicine_timer_cancel_publish_flags |= (uint8_t)(1U << cancel_index);
                    }
                    (void)osSemaphoreRelease(mqttPublishSem);
                }
                medicine_timer_current_alarm_id = 0U;
                continue;
            }

            for (beep_index = 0U; beep_index < BUZZER_BEEP_COUNT; beep_index++) {
                Buzzer_StartTone();
                osDelay(BUZZER_BEEP_ON_MS);
                Buzzer_StopTone();
                if ((beep_index + 1U) < BUZZER_BEEP_COUNT) {
                    osDelay(off_delay_ms);
                }
            }
        }
    }
}

/**
  * @brief  LED指示任务
  * @param  argument: 任务参数
  */
void LEDTask(void *argument)
{
    uint32_t now;
    uint32_t led1_last_toggle_tick = 0U;
    uint32_t led2_last_toggle_tick = 0U;
    uint32_t led1_toggle_interval = 500U;
    ESP8266_State_t led1_mode = ESP8266_STATE_RESET;
    uint8_t led1_output = 0U;
    uint8_t led2_output = 1U;

    printf("[LEDTask] Started\r\n");

    HAL_GPIO_WritePin(LED1_GPIO_Port, LED1_Pin, GPIO_PIN_RESET);
    HAL_GPIO_WritePin(LED2_GPIO_Port, LED2_Pin, GPIO_PIN_SET);

    for (;;) {
        ESP8266_State_t state = ESP8266_GetState();
        now = HAL_GetTick();

        if (state == ESP8266_STATE_MQTT_CONNECTED) {
            if (led1_mode != ESP8266_STATE_MQTT_CONNECTED) {
                led1_mode = ESP8266_STATE_MQTT_CONNECTED;
                led1_toggle_interval = 500U;
                led1_last_toggle_tick = now;
                led1_output = 1U;
            } else if ((uint32_t)(now - led1_last_toggle_tick) >= led1_toggle_interval) {
                led1_last_toggle_tick = now;
                led1_output = (uint8_t)((led1_output == 0U) ? 1U : 0U);
            }
        } else if (state == ESP8266_STATE_WIFI_CONNECTED) {
            if (led1_mode != ESP8266_STATE_WIFI_CONNECTED) {
                led1_mode = ESP8266_STATE_WIFI_CONNECTED;
                led1_toggle_interval = 100U;
                led1_last_toggle_tick = now;
                led1_output = 1U;
            } else if ((uint32_t)(now - led1_last_toggle_tick) >= led1_toggle_interval) {
                led1_last_toggle_tick = now;
                led1_output = (uint8_t)((led1_output == 0U) ? 1U : 0U);
            }
        } else if (state == ESP8266_STATE_ERROR) {
            led1_mode = ESP8266_STATE_ERROR;
            led1_output = 1U;
            led1_last_toggle_tick = now;
        } else {
            led1_mode = ESP8266_STATE_RESET;
            led1_output = 0U;
            led1_last_toggle_tick = now;
        }

        HAL_GPIO_WritePin(LED1_GPIO_Port, LED1_Pin, (led1_output != 0U) ? GPIO_PIN_SET : GPIO_PIN_RESET);

        if (app_bus_fault_flags != 0U) {
            if ((uint32_t)(now - led2_last_toggle_tick) >= LED2_FAULT_BLINK_MS) {
                led2_last_toggle_tick = now;
                led2_output = (uint8_t)((led2_output == 0U) ? 1U : 0U);
            }
        } else {
            led2_output = 1U;
            led2_last_toggle_tick = now;
        }

        HAL_GPIO_WritePin(LED2_GPIO_Port, LED2_Pin, (led2_output != 0U) ? GPIO_PIN_SET : GPIO_PIN_RESET);
        osDelay(20);
    }
}
