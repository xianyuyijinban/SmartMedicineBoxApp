/**
  ******************************************************************************
  * @file    app_tasks.h
  * @brief   FreeRTOS任务定义
  ******************************************************************************
  */
#ifndef __APP_TASKS_H
#define __APP_TASKS_H

#include "main.h"
#include "cmsis_os.h"

/* 任务优先级 */
#define SENSOR_TASK_PRIORITY        osPriorityNormal
#define MQTT_TASK_PRIORITY          osPriorityAboveNormal
#define DISPLAY_TASK_PRIORITY       osPriorityBelowNormal
#define LED_TASK_PRIORITY           osPriorityLow
#define BUZZER_TASK_PRIORITY        osPriorityLow

/* 任务堆栈大小 */
#define SENSOR_TASK_STACK_SIZE      512   // 增加以容纳振动检测历史数据
#define MQTT_TASK_STACK_SIZE        2048  // 云端AT MQTT路径保留较大串口/JSON缓冲
#define DISPLAY_TASK_STACK_SIZE     512   // 增加以容纳 LCD 显示缓冲区
#define LED_TASK_STACK_SIZE         256   // 保持较小
#define BUZZER_TASK_STACK_SIZE      256

/* 任务句柄 (可选，用于外部控制) */
extern osThreadId_t sensorTaskHandle;
extern osThreadId_t mqttTaskHandle;
extern osThreadId_t displayTaskHandle;
extern osThreadId_t buzzerTaskHandle;

/* 任务函数声明 */
void SensorTask(void *argument);
void MQTTTask(void *argument);
void DisplayTask(void *argument);
void LEDTask(void *argument);
void BuzzerTask(void *argument);

/* 应用初始化 */
void App_Init(void);
void App_StartTasks(void);

/* 网络配置 (根据实际情况修改) */
#define WIFI_SSID           "Galaxy Note 7 Ultra"
#define WIFI_PASSWORD       "12345678y"
#define MQTT_BROKER_IP      "jaf12a6c.ala.cn-hangzhou.emqxsl.cn"
#define MQTT_BROKER_PORT    8883
#define MQTT_CLIENT_ID      "SmartBox"
#define MQTT_USERNAME       "yunmenglin"
#define MQTT_PASSWORD       "12345678y"

/* MQTT主题 */
#define MQTT_TOPIC_DATA     "medicine/box001/sensors"
#define MQTT_TOPIC_STATUS   "medicine/box001/status"
#define MQTT_TOPIC_CONTROL  "medicine/box001/control"
#define MQTT_TOPIC_ALERT    "medicine/box001/alert"
#define MQTT_TOPIC_CONTROL_RESPONSE "medicine/box001/control/response"

/* 采样间隔 */
#define SENSOR_SAMPLE_INTERVAL_MS   100     // 传感器采样间隔 100ms
#define MQTT_PUBLISH_INTERVAL_MS    5000    // MQTT发布间隔 5s

/* 延时配置（单位：毫秒） */
#define WIFI_INIT_RETRY_DELAY_MS    2000
#define WIFI_CONNECT_RETRY_DELAY_MS 3000
#define MQTT_RETRY_DELAY_MS         3000
#define ERROR_RESET_DELAY_MS        2000
#define STATE_CHECK_INTERVAL_MS     100
#define WIFI_CONNECT_WAIT_MS        500
#define WIFI_HEALTH_CHECK_INTERVAL_MS 5000
#define WIFI_DISCONNECT_CONFIRM_COUNT 3U

/* 传感器配置 */
#define SENSOR_RETRY_DELAY_MS       10
#define SENSOR_INIT_DELAY_MS        100
#define SENSOR_INIT_RETRY_COUNT     3
#define SENSOR_READ_RETRY_COUNT     3

/* MQTT 配置 */
#define MQTT_FORCE_PUBLISH_MS       30000
#define MQTT_PUBLISH_RETRY_COUNT    3
#define MQTT_CONNECT_RETRY_COUNT    5
#define WIFI_CONNECT_RETRY_COUNT    5

/* JSON缓冲区大小（完整上报字段需要 >512，避免截断成无效JSON） */
#define JSON_BUFFER_SIZE            1024

#define BUZZER_BEEP_COUNT           3
#define BUZZER_BEEP_ON_MS           200
#define BUZZER_BEEP_INTERVAL_MS     1000

/* 跌落检测与报警配置 */
#define DROP_FREEFALL_THRESHOLD_G   0.45f
#define DROP_FREEFALL_THRESHOLD_SQ  (DROP_FREEFALL_THRESHOLD_G * DROP_FREEFALL_THRESHOLD_G)
#define DROP_IMPACT_THRESHOLD_G     2.20f
#define DROP_IMPACT_THRESHOLD_SQ    (DROP_IMPACT_THRESHOLD_G * DROP_IMPACT_THRESHOLD_G)
#define DROP_IMPACT_WINDOW_MS       700U
#define DROP_ALARM_ON_MS            5000U
#define DROP_ALARM_GAP_MS           3000U
#define DROP_ALARM_REPEAT_COUNT     3U
#define KEY2_DEBOUNCE_MS            50U
#define BUZZER_POLL_INTERVAL_MS     20U

#endif /* __APP_TASKS_H */
