/**
  ******************************************************************************
  * @file    system_monitor.h
  * @brief   系统状态监测
  ******************************************************************************
  */
#ifndef __SYSTEM_MONITOR_H
#define __SYSTEM_MONITOR_H

#include "main.h"

/* 系统状态 */
typedef struct {
    uint32_t uptime;            // 运行时间
    uint32_t free_heap;         // 剩余堆内存
    uint32_t cpu_usage;         // CPU使用率
    uint8_t sensor_status;      // 传感器状态
    uint8_t wifi_status;        // WiFi状态
    uint8_t mqtt_status;        // MQTT状态
    uint32_t last_sensor_read;  // 上次传感器读取时间
    uint32_t last_mqtt_publish; // 上次MQTT发布时间
    uint32_t error_count;       // 错误计数
} SystemStatus_t;

/* 函数声明 */
void SystemMonitor_Init(void);
void SystemMonitor_Update(void);
void SystemMonitor_GetStatus(SystemStatus_t *status);
void SystemMonitor_ReportError(const char *error_msg);
void SystemMonitor_PrintStatus(void);

#endif /* __SYSTEM_MONITOR_H */
