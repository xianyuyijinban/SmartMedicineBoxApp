/**
  ******************************************************************************
  * @file    system_monitor.c
  * @brief   系统状态监测实现
  ******************************************************************************
  */
#include "system_monitor.h"
#include "sensor_manager.h"
#include "esp8266.h"
#include <stdio.h>
#include <string.h>

/* FreeRTOS头文件 */
#include "FreeRTOS.h"
#include "task.h"

static SystemStatus_t sys_status;
static uint32_t start_tick;

void SystemMonitor_Init(void)
{
    memset(&sys_status, 0, sizeof(sys_status));
    start_tick = HAL_GetTick();

    /* 检查FreeRTOS内核是否已初始化 */
    if (xTaskGetSchedulerState() == taskSCHEDULER_NOT_STARTED) {
        printf("[Monitor] Error: FreeRTOS scheduler not started\r\n");
        sys_status.error_count++;
    }

    /* 检查堆内存是否可用 */
    size_t free_heap = xPortGetFreeHeapSize();
    if (free_heap == 0) {
        printf("[Monitor] Error: FreeRTOS heap not initialized\r\n");
        sys_status.error_count++;
    }

    printf("[Monitor] System monitor initialized, free heap: %lu bytes\r\n", (uint32_t)free_heap);
}

void SystemMonitor_Update(void)
{
    sys_status.uptime = (HAL_GetTick() - start_tick) / 1000;
    
    /* 获取剩余堆内存 */
    sys_status.free_heap = xPortGetFreeHeapSize();
    
    /* 获取传感器状态 */
    MedicineBoxData_t data;
    SensorManager_GetData(&data);
    sys_status.sensor_status = data.is_valid;
    sys_status.last_sensor_read = data.timestamp;
    
    /* 获取WiFi状态 */
    sys_status.wifi_status = (ESP8266_GetState() >= ESP8266_STATE_WIFI_CONNECTED) ? 1 : 0;
    
    /* 获取MQTT状态 */
    sys_status.mqtt_status = (ESP8266_GetState() == ESP8266_STATE_MQTT_CONNECTED) ? 1 : 0;
}

void SystemMonitor_GetStatus(SystemStatus_t *status)
{
    if (status != NULL) {
        memcpy(status, &sys_status, sizeof(SystemStatus_t));
    }
}

void SystemMonitor_ReportError(const char *error_msg)
{
    sys_status.error_count++;
    printf("[Monitor] Error #%lu: %s\r\n", sys_status.error_count, error_msg);
}

void SystemMonitor_PrintStatus(void)
{
    printf("\r\n========== System Status ==========\r\n");
    printf("Uptime: %lu seconds\r\n", sys_status.uptime);
    printf("Sensor: %s\r\n", sys_status.sensor_status ? "OK" : "FAIL");
    printf("WiFi: %s\r\n", sys_status.wifi_status ? "Connected" : "Disconnected");
    printf("MQTT: %s\r\n", sys_status.mqtt_status ? "Connected" : "Disconnected");
    printf("Errors: %lu\r\n", sys_status.error_count);
    printf("====================================\r\n\r\n");
}
