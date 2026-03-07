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
#include "tim.h"
#include "usart.h"
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

/* 运行标志 */
static volatile uint8_t system_ready = 0;
static volatile uint8_t env_alert_latched = 0U;
static volatile uint8_t env_alert_pending = 0U;
static volatile uint8_t env_recover_pending = 0U;
static volatile uint8_t env_alert_reason_flags = 0U;
extern volatile uint32_t g_uart3_last_rx_tick;

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

static uint8_t App_PublishControlResponse(const char *payload)
{
    if (ESP8266_GetState() != ESP8266_STATE_MQTT_CONNECTED) {
        return 1U;
    }

    return ESP8266_MQTT_Publish(MQTT_TOPIC_CONTROL_RESPONSE, payload, 0, 0);
}

static void App_OnMqttMessage(const char *topic, const char *payload)
{
    char cmd[32];
    char response[192];
    float rated_temperature;
    float rated_humidity;
    float current_rated_temperature;
    float current_rated_humidity;
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
        (void)App_PublishControlResponse(response);
        return;
    }

    if (is_set_command || (has_cmd == 0U)) {
        if (App_ParseRatedValues(payload, &rated_temperature, &rated_humidity) != 0U) {
            if (has_cmd != 0U) {
                snprintf(response, sizeof(response),
                         "{\"cmd\":\"%s\",\"result\":\"error\",\"error_msg\":\"invalid rated values\"}",
                         cmd);
                (void)App_PublishControlResponse(response);
            }
            return;
        }

        if (SensorManager_SetRatedEnvironment(rated_temperature, rated_humidity) != 0U) {
            snprintf(response, sizeof(response),
                     "{\"cmd\":\"set_env_rated\",\"result\":\"error\",\"error_msg\":\"value out of range\"}");
            (void)App_PublishControlResponse(response);
            return;
        }

        SensorManager_GetRatedEnvironment(&current_rated_temperature, &current_rated_humidity);
        snprintf(response, sizeof(response),
                 "{\"cmd\":\"set_env_rated\",\"result\":\"ok\",\"rated_temperature\":%.2f,\"rated_humidity\":%.2f,\"timestamp\":%lu}",
                 current_rated_temperature, current_rated_humidity, HAL_GetTick());
        (void)App_PublishControlResponse(response);
        return;
    }

    snprintf(response, sizeof(response),
             "{\"cmd\":\"%s\",\"result\":\"error\",\"error_msg\":\"unsupported command\"}",
             cmd);
    (void)App_PublishControlResponse(response);
}

static uint8_t App_PublishPendingEnvAlert(void)
{
    char payload[288];
    EnvironmentAlertStatus_t env_status;
    MedicineBoxData_t data;

    if (ESP8266_GetState() != ESP8266_STATE_MQTT_CONNECTED) {
        return 1U;
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

    return 0U;
}

static void Buzzer_StartTone(void)
{
    uint32_t pulse = (htim1.Init.Period + 1U) / 2U;
    if (pulse == 0U) {
        pulse = 1U;
    }
    __HAL_TIM_SET_COMPARE(&htim1, TIM_CHANNEL_1, pulse);
    (void)HAL_TIM_PWM_Start(&htim1, TIM_CHANNEL_1);
}

static void Buzzer_StopTone(void)
{
    (void)HAL_TIM_PWM_Stop(&htim1, TIM_CHANNEL_1);
    __HAL_TIM_SET_COMPARE(&htim1, TIM_CHANNEL_1, 0U);
}

/**
  * @brief  应用初始化
  */
void App_Init(void)
{
    printf("[APP] System Initializing...\r\n");
    
    /* 初始化日志互斥锁，保护printf线程安全 */
    DebugLog_InitMutex();
    
    /* 初始化传感器 */
    SensorManager_Init();
    printf("[APP] Sensors Initialized\r\n");
    
    /* 初始化ESP8266 */
    ESP8266_Init();
    printf("[APP] ESP8266 Initialized\r\n");
    
    mqttPublishSem = osSemaphoreNew(1, 0, NULL);
    buzzerAlertSem = osSemaphoreNew(1, 0, NULL);
    ESP8266_RegisterMQTTMessageCallback(App_OnMqttMessage);
    Buzzer_StopTone();
    
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
    uint32_t last_mqtt_tick = 0;
    
    printf("[SensorTask] Started\r\n");
    
    for (;;) {
        SensorManager_ReadAll();
        SensorManager_GetEnvAlertStatus(&env_status);

        if (env_status.is_abnormal != 0U) {
            uint8_t reason_flags = 0U;
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
                (void)osSemaphoreRelease(buzzerAlertSem);
            }
        } else if (env_alert_latched != 0U) {
            env_alert_latched = 0U;
            env_alert_reason_flags = 0U;
            env_recover_pending = 1U;
        }

        if ((HAL_GetTick() - last_mqtt_tick) >= MQTT_PUBLISH_INTERVAL_MS) {
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
    uint8_t wifi_connected = 0;
    uint8_t retry_count = 0;
    uint8_t max_retries = MQTT_CONNECT_RETRY_COUNT;
    char json_buffer[JSON_BUFFER_SIZE];
    uint32_t last_publish_tick = 0;
    uint32_t stack_check_tick = 0;  // 添加堆栈检查时间戳

    printf("[MQTTTask] Started\r\n");

    /* 等待系统就绪 */
    osDelay(1000);

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
                printf("[MQTT] Initializing WiFi...\r\n");
                if (ESP8266_WiFi_Init() == 0) {
                    printf("[MQTT] WiFi AT OK\r\n");
                } else {
                    printf("[MQTT] WiFi Init failed, retry...\r\n");
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
                    if (ESP8266_MQTT_ConnectToBroker(MQTT_BROKER_IP, MQTT_BROKER_PORT) == 0) {
                        printf("[MQTT] Connected to %s:%d\r\n", MQTT_BROKER_IP, MQTT_BROKER_PORT);

                        /* 订阅控制主题 */
                        ESP8266_MQTT_Subscribe(MQTT_TOPIC_CONTROL, 0);

                        /* 发布上线消息 */
                        ESP8266_MQTT_Publish(MQTT_TOPIC_STATUS, "online", 0, 1);

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
                        printf("[MQTT] Connection failed after %d retries, reset...\r\n", max_retries);
                        ESP8266_Reset();
                        retry_count = 0;
                        wifi_connected = 0;
                    }
                    osDelay(MQTT_RETRY_DELAY_MS);
                }
                break;
                
            case ESP8266_STATE_MQTT_CONNECTED:
                ESP8266_ProcessRxData();
                (void)App_PublishPendingEnvAlert();

                if (osSemaphoreAcquire(mqttPublishSem, 100) == osOK) {
                    SensorManager_CreateJSON(json_buffer, sizeof(json_buffer));
                    
                    uint8_t pub_retry = 0;
                    while (pub_retry < MQTT_PUBLISH_RETRY_COUNT) {
                        if (ESP8266_MQTT_Publish(MQTT_TOPIC_DATA, json_buffer, 0, 0) == 0) {
                            printf("[MQTT] Published: %s\r\n", json_buffer);
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
                wifi_connected = 0;
                retry_count = 0;
                osDelay(ERROR_RESET_DELAY_MS);
                break;
                
            default:
                break;
        }
        
        /* WiFi连接管理 */
        if (!wifi_connected && ESP8266_GetState() < ESP8266_STATE_WIFI_CONNECTING) {
            printf("[MQTT] Connecting to WiFi: %s\r\n", WIFI_SSID);
            if (ESP8266_WiFi_Connect(WIFI_SSID, WIFI_PASSWORD) == 0) {
                printf("[MQTT] WiFi Connected\r\n");
                wifi_connected = 1;
                retry_count = 0;
            } else {
                retry_count++;
                printf("[MQTT] WiFi Connect failed, retry %d/%d\r\n", retry_count, max_retries);
                if (retry_count >= max_retries) {
                    printf("[MQTT] WiFi connection failed after %d retries, reset ESP8266...\r\n", max_retries);
                    ESP8266_Reset();
                    retry_count = 0;
                }
                osDelay(WIFI_CONNECT_RETRY_DELAY_MS);
            }
        }
        
        /* 如果WiFi断开，重置状态 */
        if (wifi_connected && ESP8266_WiFi_IsConnected() == 0) {
            if (ESP8266_GetState() == ESP8266_STATE_WIFI_CONNECTED ||
                ESP8266_GetState() == ESP8266_STATE_MQTT_CONNECTED) {
                printf("[MQTT] WiFi disconnected\r\n");
                wifi_connected = 0;
                retry_count = 0;
            }
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
    DisplayLogicState_t logic_state = {0};
    DisplayPage_t current_page = DISPLAY_PAGE_ENV;
    uint32_t last_render_tick = 0U;
    uint8_t force_render = 1U;
    
    printf("[DisplayTask] Started\r\n");
    DisplayUI_Init();
    
    for (;;) {
        uint32_t now = HAL_GetTick();
        uint8_t key_high = (HAL_GPIO_ReadPin(KEY1_GPIO_Port, KEY1_Pin) == GPIO_PIN_SET) ? 1U : 0U;

        if (DisplayLogic_UpdateKey(&logic_state, key_high, now, &current_page) != 0U) {
            force_render = 1U;
        }

        if (force_render != 0U || (uint32_t)(now - last_render_tick) >= 500U) {
            SensorManager_GetData(&data);

            if (current_page == DISPLAY_PAGE_ENV) {
                DisplayUI_RenderPage1(&data);
            } else {
                ESP8266_State_t state = ESP8266_GetState();
                sys_status.wifi_ok = (state >= ESP8266_STATE_WIFI_CONNECTED) ? 1U : 0U;
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
    uint32_t off_delay_ms;

    printf("[BuzzerTask] Started\r\n");

    off_delay_ms = (BUZZER_BEEP_INTERVAL_MS > BUZZER_BEEP_ON_MS) ?
                   (BUZZER_BEEP_INTERVAL_MS - BUZZER_BEEP_ON_MS) : 0U;

    for (;;) {
        if (osSemaphoreAcquire(buzzerAlertSem, osWaitForever) == osOK) {
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
    printf("[LEDTask] Started\r\n");
    
    for (;;) {
        /* 根据系统状态指示LED */
        switch (ESP8266_GetState()) {
            case ESP8266_STATE_MQTT_CONNECTED:
                /* 正常工作：慢闪 */
                HAL_GPIO_WritePin(LED1_GPIO_Port, LED1_Pin, GPIO_PIN_SET);
                osDelay(500);
                HAL_GPIO_WritePin(LED1_GPIO_Port, LED1_Pin, GPIO_PIN_RESET);
                osDelay(500);
                break;
                
            case ESP8266_STATE_WIFI_CONNECTED:
                /* WiFi已连，MQTT未连：快闪 */
                HAL_GPIO_WritePin(LED1_GPIO_Port, LED1_Pin, GPIO_PIN_SET);
                osDelay(100);
                HAL_GPIO_WritePin(LED1_GPIO_Port, LED1_Pin, GPIO_PIN_RESET);
                osDelay(100);
                break;
                
            case ESP8266_STATE_ERROR:
                /* 错误：常亮 */
                HAL_GPIO_WritePin(LED1_GPIO_Port, LED1_Pin, GPIO_PIN_SET);
                osDelay(1000);
                break;
                
            default:
                /* 其他状态：熄灭 */
                HAL_GPIO_WritePin(LED1_GPIO_Port, LED1_Pin, GPIO_PIN_RESET);
                osDelay(500);
                break;
        }
    }
}
