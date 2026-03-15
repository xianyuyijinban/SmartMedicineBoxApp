/**
  ******************************************************************************
  * @file    debug_log.c
  * @brief   调试日志系统实现
  ******************************************************************************
  */
#include "debug_log.h"
#include <stdarg.h>
#include <string.h>
#include <rt_sys.h>

extern UART_HandleTypeDef huart1;

/* 禁用 semihosting，避免运行时触发 BKPT 0xAB */
#pragma import(__use_no_semihosting_swi)

struct __FILE { int handle; };
FILE __stdout;
FILE __stdin;
FILE __stderr;

/* ArmCC full library retarget: 所有标准 I/O 走 USART1 */
const char __stdin_name[] = ":tt";
const char __stdout_name[] = ":tt";
const char __stderr_name[] = ":tt";

static void DebugLog_UartWrite(const uint8_t *data, uint16_t len)
{
    if ((huart1.Instance != NULL) && (len > 0U)) {
        HAL_UART_Transmit(&huart1, (uint8_t *)data, len, 20);
    }
}

FILEHANDLE _sys_open(const char *name, int openmode)
{
    (void)name;
    (void)openmode;
    return 1;
}

int _sys_close(FILEHANDLE fh)
{
    (void)fh;
    return 0;
}

int _sys_write(FILEHANDLE fh, const unsigned char *buf, unsigned len, int mode)
{
    (void)fh;
    (void)mode;
    DebugLog_UartWrite(buf, (uint16_t)len);
    return 0;
}

int _sys_read(FILEHANDLE fh, unsigned char *buf, unsigned len, int mode)
{
    (void)fh;
    (void)buf;
    (void)len;
    (void)mode;
    return 0;
}

int _sys_istty(FILEHANDLE fh)
{
    (void)fh;
    return 1;
}

int _sys_seek(FILEHANDLE fh, long pos)
{
    (void)fh;
    (void)pos;
    return -1;
}

int _sys_ensure(FILEHANDLE fh)
{
    (void)fh;
    return 0;
}

long _sys_flen(FILEHANDLE fh)
{
    (void)fh;
    return 0;
}

int fputc(int ch, FILE *f)
{
    uint8_t c = (uint8_t)ch;
    (void)f;
    DebugLog_UartWrite(&c, 1U);
    return ch;
}

int ferror(FILE *f)
{
    (void)f;
    return EOF;
}

void _ttywrch(int ch)
{
    uint8_t c = (uint8_t)ch;
    DebugLog_UartWrite(&c, 1U);
}

void _sys_exit(int return_code)
{
    (void)return_code;
    while (1) {
    }
}

char *_sys_command_string(char *cmd, int len)
{
    (void)cmd;
    (void)len;
    return NULL;
}

/* 运行时日志级别变量 */
LogLevel_t g_current_log_level = CURRENT_LOG_LEVEL;

/* 互斥锁，保护printf线程安全 */
osMutexId_t printfMutex = NULL;

static const char* level_strings[] = {
    "DEBUG", "INFO", "WARN", "ERROR"
};

static const char* module_strings[] = {
    "SYS", "SENSOR", "WIFI", "MQTT", "TASK"
};

void DebugLog_Init(void)
{
    /* 初始化完成 */
    g_current_log_level = CURRENT_LOG_LEVEL;
}

/**
  * @brief  初始化日志互斥锁
  * @note   在FreeRTOS启动后调用
  */
void DebugLog_InitMutex(void)
{
    if (printfMutex == NULL) {
        printfMutex = osMutexNew(NULL);
    }
}

void DebugLog_SetLevel(LogLevel_t level)
{
    if (level <= LOG_LEVEL_ERROR) {
        g_current_log_level = level;
    }
}

LogLevel_t DebugLog_GetLevel(void)
{
    return g_current_log_level;
}

const char* DebugLog_GetLevelString(LogLevel_t level)
{
    if (level <= LOG_LEVEL_ERROR) {
        return level_strings[level];
    }
    return "UNKNOWN";
}

const char* DebugLog_GetModuleString(LogModule_t module)
{
    if (module <= LOG_MODULE_TASK) {
        return module_strings[module];
    }
    return "UNKNOWN";
}

void DebugLog(LogLevel_t level, LogModule_t module, const char *fmt, ...)
{
    if (level < g_current_log_level) {
        return;
    }
    
    char buffer[256];
    int len = 0;
    
    /* 获取互斥锁，保护printf线程安全 */
    if (printfMutex != NULL) {
        osMutexAcquire(printfMutex, osWaitForever);
    }
    
    /* 添加时间戳 */
    #if ENABLE_LOG_TIMESTAMP
    len += snprintf(buffer + len, sizeof(buffer) - len, "[%lu]", HAL_GetTick());
    #endif
    
    /* 添加日志级别 */
    len += snprintf(buffer + len, sizeof(buffer) - len, "[%s]", DebugLog_GetLevelString(level));
    
    /* 添加模块 */
    #if ENABLE_LOG_MODULE
    len += snprintf(buffer + len, sizeof(buffer) - len, "[%s]", DebugLog_GetModuleString(module));
    #endif
    
    /* 添加分隔符 */
    len += snprintf(buffer + len, sizeof(buffer) - len, " ");
    
    /* 添加内容 */
    va_list args;
    va_start(args, fmt);
    int msg_len = vsnprintf(buffer + len, sizeof(buffer) - len - 3, fmt, args);  // 预留3字节给\r\n\0
    va_end(args);
    
    if (msg_len >= (int)(sizeof(buffer) - len - 3)) {
        // 消息被截断，添加标记
        strcat(buffer, "...");
        len = strlen(buffer);
    } else {
        len += msg_len;
    }
    
    /* 添加换行 */
    len += snprintf(buffer + len, sizeof(buffer) - len, "\r\n");

    /* 输出到串口 - 降低超时时间到 10ms 提高性能 */
    DebugLog_UartWrite((const uint8_t *)buffer, (uint16_t)len);
    
    /* 释放互斥锁 */
    if (printfMutex != NULL) {
        osMutexRelease(printfMutex);
    }
}
