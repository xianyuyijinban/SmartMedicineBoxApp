/**
  ******************************************************************************
  * @file    debug_log.h
  * @brief   调试日志系统
  ******************************************************************************
  */
#ifndef __DEBUG_LOG_H
#define __DEBUG_LOG_H

#include "main.h"
#include "cmsis_os.h"
#include <stdio.h>

/* 日志级别 */
typedef enum {
    LOG_LEVEL_DEBUG = 0,
    LOG_LEVEL_INFO,
    LOG_LEVEL_WARN,
    LOG_LEVEL_ERROR
} LogLevel_t;

/* 日志模块 */
typedef enum {
    LOG_MODULE_SYSTEM = 0,
    LOG_MODULE_SENSOR,
    LOG_MODULE_WIFI,
    LOG_MODULE_MQTT,
    LOG_MODULE_TASK
} LogModule_t;

/* 配置 - 可通过编译选项覆盖 */
#ifndef CURRENT_LOG_LEVEL
    #ifdef RELEASE_BUILD
        #define CURRENT_LOG_LEVEL   LOG_LEVEL_ERROR  // 发布版本只显示错误
    #else
        #define CURRENT_LOG_LEVEL   LOG_LEVEL_DEBUG  // 调试版本显示所有日志
    #endif
#endif

#define ENABLE_LOG_TIMESTAMP 1
#define ENABLE_LOG_MODULE    1

/* 运行时日志级别控制 */
extern LogLevel_t g_current_log_level;
void DebugLog_SetLevel(LogLevel_t level);
LogLevel_t DebugLog_GetLevel(void);

/* 添加互斥锁保护printf */
extern osMutexId_t printfMutex;
void DebugLog_InitMutex(void);

/* 日志宏 */
#define LOG_DEBUG(module, fmt, ...) DebugLog(LOG_LEVEL_DEBUG, module, fmt, ##__VA_ARGS__)
#define LOG_INFO(module, fmt, ...)  DebugLog(LOG_LEVEL_INFO, module, fmt, ##__VA_ARGS__)
#define LOG_WARN(module, fmt, ...)  DebugLog(LOG_LEVEL_WARN, module, fmt, ##__VA_ARGS__)
#define LOG_ERROR(module, fmt, ...) DebugLog(LOG_LEVEL_ERROR, module, fmt, ##__VA_ARGS__)

/* 函数声明 */
void DebugLog_Init(void);
void DebugLog(LogLevel_t level, LogModule_t module, const char *fmt, ...);
const char* DebugLog_GetLevelString(LogLevel_t level);
const char* DebugLog_GetModuleString(LogModule_t module);

#endif /* __DEBUG_LOG_H */
