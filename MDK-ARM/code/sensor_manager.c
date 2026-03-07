/**
  ******************************************************************************
  * @file    sensor_manager.c
  * @brief   传感器数据管理器实现
  ******************************************************************************
  */
#include "sensor_manager.h"
#include "app_tasks.h"
#include <stdio.h>
#include <string.h>
#include <math.h>

/* 传感器数据实例 */
static MPU6050_Data_t mpu_data;
static AHT20_Data_t aht_data;
static BMP280_Data_t bmp_data;

/* 药箱综合数据 */
static MedicineBoxData_t box_data;

/* 振动检测阈值 */
#define VIBRATION_THRESHOLD     0.5f    // g
#define TILT_THRESHOLD          30.0f   // 度

/* 振动检测历史数据 */
#define VIBRATION_HISTORY_SIZE  10
static float accel_history[VIBRATION_HISTORY_SIZE][3];
static uint8_t history_index = 0;
static uint8_t history_count = 0;

#define ENV_ABNORMAL_RATIO      0.30f
#define ENV_RATED_TEMP_MIN      1.0f
#define ENV_RATED_TEMP_MAX      60.0f
#define ENV_RATED_HUM_MIN       1.0f
#define ENV_RATED_HUM_MAX       100.0f

static float rated_temperature = ENV_RATED_DEFAULT_TEMP_C;
static float rated_humidity = ENV_RATED_DEFAULT_HUMIDITY_PERCENT;
static EnvironmentAlertStatus_t env_alert_status;

/**
  * @brief  更新加速度历史数据
  * @param  accel_x: X轴加速度值 (单位: g)
  * @param  accel_y: Y轴加速度值 (单位: g)
  * @param  accel_z: Z轴加速度值 (单位: g)
  *
  * @note   使用循环缓冲区存储最近VIBRATION_HISTORY_SIZE个加速度样本
  *         用于后续振动强度计算。当缓冲区满时，新数据会覆盖最旧的数据。
  *         这种设计确保振动检测基于最近一段时间内的运动变化。
  */
static void UpdateAccelHistory(float accel_x, float accel_y, float accel_z)
{
    /* 将当前加速度值存入循环缓冲区当前位置 */
    accel_history[history_index][0] = accel_x;
    accel_history[history_index][1] = accel_y;
    accel_history[history_index][2] = accel_z;

    /* 更新索引位置，使用模运算实现循环 */
    history_index = (history_index + 1) % VIBRATION_HISTORY_SIZE;

    /* 增加有效数据计数，直到达到缓冲区大小 */
    if (history_count < VIBRATION_HISTORY_SIZE) {
        history_count++;
    }
}

/**
  * @brief  计算振动强度
  * @retval 振动强度值 (单位: g)，表示平均加速度变化率
  *
  * @note   算法原理:
  *         1. 计算相邻样本之间的加速度差值（欧几里得距离）
  *         2. 对所有差值求平均，得到平均振动强度
  *         3. 这种方法比简单的加速度合成更能准确反映运动变化
  *
  * @note   性能考量:
  *         - 时间复杂度: O(n)，n为历史数据数量
  *         - 空间复杂度: O(1)，只使用固定大小的缓冲区
  *         - 使用sqrtf计算欧几里得距离，在嵌入式系统中效率较高
  *
  * @note   当历史数据不足2个时返回0，避免计算错误
  */
static float CalculateVibration(void)
{
    /* 需要至少2个数据点才能计算振动 */
    if (history_count < 2) {
        return 0.0f;
    }

    float sum_diff = 0.0f;  /* 累计加速度差值 */
    uint8_t count = 0;      /* 有效差值计数 */

    /* 遍历历史数据，计算相邻样本的加速度差值 */
    for (uint8_t i = 1; i < history_count; i++) {
        /* 计算当前样本和前一样本的索引（处理循环缓冲区） */
        uint8_t curr_idx = (history_index + VIBRATION_HISTORY_SIZE - i) % VIBRATION_HISTORY_SIZE;
        uint8_t prev_idx = (history_index + VIBRATION_HISTORY_SIZE - i - 1) % VIBRATION_HISTORY_SIZE;

        /* 计算三个轴的加速度差值 */
        float diff_x = accel_history[curr_idx][0] - accel_history[prev_idx][0];
        float diff_y = accel_history[curr_idx][1] - accel_history[prev_idx][1];
        float diff_z = accel_history[curr_idx][2] - accel_history[prev_idx][2];

        /* 计算欧几里得距离（三维空间中的直线距离） */
        sum_diff += sqrtf(diff_x * diff_x + diff_y * diff_y + diff_z * diff_z);
        count++;
    }

    /* 返回平均振动强度，避免除零错误 */
    return (count > 0) ? (sum_diff / count) : 0.0f;
}

static void UpdateEnvAlertStatus(void)
{
    env_alert_status.rated_temperature = rated_temperature;
    env_alert_status.rated_humidity = rated_humidity;
    env_alert_status.temp_low_limit = rated_temperature * (1.0f - ENV_ABNORMAL_RATIO);
    env_alert_status.temp_high_limit = rated_temperature * (1.0f + ENV_ABNORMAL_RATIO);
    env_alert_status.humidity_low_limit = rated_humidity * (1.0f - ENV_ABNORMAL_RATIO);
    env_alert_status.humidity_high_limit = rated_humidity * (1.0f + ENV_ABNORMAL_RATIO);

    if (box_data.is_valid == 0U) {
        env_alert_status.temperature_abnormal = 0U;
        env_alert_status.humidity_abnormal = 0U;
        env_alert_status.is_abnormal = 0U;
        return;
    }

    env_alert_status.temperature_abnormal =
        ((box_data.env.temperature < env_alert_status.temp_low_limit) ||
         (box_data.env.temperature > env_alert_status.temp_high_limit)) ? 1U : 0U;
    env_alert_status.humidity_abnormal =
        ((box_data.env.humidity < env_alert_status.humidity_low_limit) ||
         (box_data.env.humidity > env_alert_status.humidity_high_limit)) ? 1U : 0U;
    env_alert_status.is_abnormal =
        (uint8_t)(env_alert_status.temperature_abnormal || env_alert_status.humidity_abnormal);
}

/**
  * @brief  初始化传感器管理器
  */
void SensorManager_Init(void)
{
    uint8_t retry;

    /* 初始化MPU6050 */
    retry = SENSOR_INIT_RETRY_COUNT;
    while (retry--) {
        if (MPU6050_Init() == 0) {
            break;
        }
        HAL_Delay(SENSOR_INIT_DELAY_MS);
    }

    /* 初始化AHT20 */
    retry = SENSOR_INIT_RETRY_COUNT;
    while (retry--) {
        if (AHT20_Init() == 0) {
            break;
        }
        HAL_Delay(SENSOR_INIT_DELAY_MS);
    }

    /* 初始化BMP280 */
    retry = SENSOR_INIT_RETRY_COUNT;
    while (retry--) {
        if (BMP280_Init() == 0) {
            break;
        }
        HAL_Delay(SENSOR_INIT_DELAY_MS);
    }
    
    /* 初始化振动检测历史数据 */
    memset(accel_history, 0, sizeof(accel_history));
    history_index = 0;
    history_count = 0;
    
    /* 初始化数据结构 */
    memset(&box_data, 0, sizeof(box_data));
    box_data.state = BOX_STATE_CLOSED;
    box_data.is_valid = 0U;
    rated_temperature = ENV_RATED_DEFAULT_TEMP_C;
    rated_humidity = ENV_RATED_DEFAULT_HUMIDITY_PERCENT;
    memset(&env_alert_status, 0, sizeof(env_alert_status));
    UpdateEnvAlertStatus();
}

/**
  * @brief  读取所有传感器数据
  * @retval 0:成功 1:失败
  */
uint8_t SensorManager_ReadAll(void)
{
    uint8_t result = 0;
    uint8_t retry_count = 0;
    uint8_t max_retries = SENSOR_READ_RETRY_COUNT;

    /* 读取MPU6050 */
    retry_count = 0;
    while (retry_count < max_retries) {
        if (MPU6050_ReadData(&mpu_data) == 0) {
            MPU6050_CalculateAngles(&mpu_data);

            box_data.motion.accel_x = mpu_data.accel_x_g;
            box_data.motion.accel_y = mpu_data.accel_y_g;
            box_data.motion.accel_z = mpu_data.accel_z_g;
            box_data.motion.gyro_x = mpu_data.gyro_x_dps;
            box_data.motion.gyro_y = mpu_data.gyro_y_dps;
            box_data.motion.gyro_z = mpu_data.gyro_z_dps;
            box_data.motion.pitch = mpu_data.pitch;
            box_data.motion.roll = mpu_data.roll;

            /* 更新历史数据 */
            UpdateAccelHistory(mpu_data.accel_x_g, mpu_data.accel_y_g, mpu_data.accel_z_g);

            /* 计算振动强度 */
            box_data.motion.vibration = CalculateVibration();
            break;
        } else {
            retry_count++;
            HAL_Delay(SENSOR_RETRY_DELAY_MS);
        }
    }
    if (retry_count >= max_retries) {
        result |= 0x01;
        printf("[Sensor] MPU6050 read failed after %d retries\r\n", max_retries);
    }

    /* 读取AHT20 */
    retry_count = 0;
    while (retry_count < max_retries) {
        if (AHT20_ReadDataBlocking(&aht_data, 200) == 0) {
            box_data.env.humidity = aht_data.humidity;
            /* 温度暂存，后续与BMP280取平均 */
            break;
        } else {
            retry_count++;
            HAL_Delay(SENSOR_RETRY_DELAY_MS);
        }
    }
    if (retry_count >= max_retries) {
        result |= 0x02;
        printf("[Sensor] AHT20 read failed after %d retries\r\n", max_retries);
    }

    /* 读取BMP280 */
    retry_count = 0;
    while (retry_count < max_retries) {
        if (BMP280_ReadData(&bmp_data) == 0) {
            box_data.env.pressure = bmp_data.pressure;
            box_data.env.altitude = bmp_data.altitude;

            /* 温度取AHT20和BMP280的平均值 */
            if ((result & 0x02) == 0) {
                box_data.env.temperature = (aht_data.temperature + bmp_data.temperature) / 2.0f;
            } else {
                box_data.env.temperature = bmp_data.temperature;
            }
            break;
        } else {
            retry_count++;
            HAL_Delay(SENSOR_RETRY_DELAY_MS);
        }
    }
    if (retry_count >= max_retries) {
        result |= 0x04;
        printf("[Sensor] BMP280 read failed after %d retries\r\n", max_retries);
        /* 如果BMP280失败，使用AHT20的温度 */
        if ((result & 0x02) == 0) {
            box_data.env.temperature = aht_data.temperature;
        }
    }
    
    /* 检测药箱状态 */
    box_data.state = SensorManager_DetectState();
    box_data.timestamp = HAL_GetTick();
    box_data.is_valid = (result == 0) ? 1 : 0;
    UpdateEnvAlertStatus();
    
    return result;
}

/**
  * @brief  获取药箱数据
  * @param  data: 数据指针
  */
void SensorManager_GetData(MedicineBoxData_t *data)
{
    if (data != NULL) {
        memcpy(data, &box_data, sizeof(MedicineBoxData_t));
    }
}

uint8_t SensorManager_SetRatedEnvironment(float temperature, float humidity)
{
    if ((temperature < ENV_RATED_TEMP_MIN) || (temperature > ENV_RATED_TEMP_MAX)) {
        return 1U;
    }

    if ((humidity < ENV_RATED_HUM_MIN) || (humidity > ENV_RATED_HUM_MAX)) {
        return 1U;
    }

    rated_temperature = temperature;
    rated_humidity = humidity;
    UpdateEnvAlertStatus();
    return 0U;
}

void SensorManager_GetRatedEnvironment(float *temperature, float *humidity)
{
    if (temperature != NULL) {
        *temperature = rated_temperature;
    }
    if (humidity != NULL) {
        *humidity = rated_humidity;
    }
}

void SensorManager_GetEnvAlertStatus(EnvironmentAlertStatus_t *status)
{
    if (status != NULL) {
        memcpy(status, &env_alert_status, sizeof(EnvironmentAlertStatus_t));
    }
}

/**
  * @brief  检测药箱状态
  * @retval BoxState_t: 药箱当前状态枚举
  *
  * @note   状态检测算法优先级（从高到低）：
  *         1. MOVING (移动): 振动强度超过阈值，表示药箱正在被移动
  *         2. TILTED (倾斜): 俯仰角或横滚角超过阈值，表示药箱倾斜
  *         3. OPENED (打开): Z轴加速度显著减小，表示药箱可能被打开
  *         4. CLOSED (关闭): 以上条件都不满足，药箱正常放置
  *
  * @note   算法原理:
  *         - 振动检测: 基于历史加速度数据的平均变化率
  *         - 倾斜检测: 使用MPU6050计算的姿态角（俯仰角和横滚角）
  *         - 开合检测: 通过Z轴加速度判断，正常放置时约1g，打开时会减小
  *
  * @note   阈值说明:
  *         - VIBRATION_THRESHOLD: 0.5g，振动强度超过此值判定为移动
  *         - TILT_THRESHOLD: 30度，倾斜角度超过此值判定为倾斜
  *         - OPENED阈值: 0.7g，Z轴加速度低于此值判定为打开
  *
  * @note   改进建议:
  *         - 开合检测目前仅基于加速度，建议增加霍尔传感器提高准确性
  *         - 可考虑添加状态持续时间判断，避免瞬时抖动导致状态频繁切换
  *         - 可添加状态转换历史记录，用于分析药箱使用模式
  */
BoxState_t SensorManager_DetectState(void)
{
    /* 优先级1: 检测移动/振动
     * 原理: 当药箱被移动时，加速度会发生快速变化
     * 通过振动强度算法可以检测到这种变化
     */
    if (box_data.motion.vibration > VIBRATION_THRESHOLD) {
        return BOX_STATE_MOVING;
    }

    /* 优先级2: 检测倾斜
     * 原理: 使用MPU6050融合算法计算的姿态角
     * 俯仰角(pitch)或横滚角(roll)超过阈值表示药箱倾斜
     */
    if (fabsf(box_data.motion.pitch) > TILT_THRESHOLD ||
        fabsf(box_data.motion.roll) > TILT_THRESHOLD) {
        return BOX_STATE_TILTED;
    }

    /* 优先级3: 检测开合状态
     * 原理: 正常放置时Z轴加速度约1g（重力加速度）
     * 当药箱打开时，Z轴方向可能改变，导致加速度减小
     *
     * 注意: 这种方法有一定局限性，建议配合霍尔传感器使用
     */
    if (box_data.motion.accel_z < 0.7f) {
        return BOX_STATE_OPENED;
    }

    /* 默认状态: 药箱正常关闭放置 */
    return BOX_STATE_CLOSED;
}

/**
  * @brief  获取状态字符串
  * @param  state: 状态枚举
  * @retval 状态描述
  */
const char* SensorManager_GetStateString(BoxState_t state)
{
    switch (state) {
        case BOX_STATE_CLOSED:   return "closed";
        case BOX_STATE_OPENED:   return "opened";
        case BOX_STATE_MOVING:   return "moving";
        case BOX_STATE_TILTED:   return "tilted";
        default:                 return "unknown";
    }
}

/**
  * @brief  生成JSON格式的传感器数据
  * @param  json_buf: JSON缓冲区
  * @param  buf_size: 缓冲区大小
  */
void SensorManager_CreateJSON(char *json_buf, uint16_t buf_size)
{
    snprintf(json_buf, buf_size,
        "{"
        "\"timestamp\":%lu,"
        "\"device_id\":\"medicine_box_001\","
        "\"state\":\"%s\","
        "\"environment\":{"
            "\"temperature\":%.2f,"
            "\"humidity\":%.2f,"
            "\"pressure\":%.2f,"
            "\"altitude\":%.2f"
        "},"
        "\"environment_limits\":{"
            "\"temperature_rated\":%.2f,"
            "\"temperature_low\":%.2f,"
            "\"temperature_high\":%.2f,"
            "\"humidity_rated\":%.2f,"
            "\"humidity_low\":%.2f,"
            "\"humidity_high\":%.2f"
        "},"
        "\"motion\":{"
            "\"accel_x\":%.3f,"
            "\"accel_y\":%.3f,"
            "\"accel_z\":%.3f,"
            "\"gyro_x\":%.2f,"
            "\"gyro_y\":%.2f,"
            "\"gyro_z\":%.2f,"
            "\"pitch\":%.2f,"
            "\"roll\":%.2f,"
            "\"vibration\":%.3f"
        "},"
        "\"alerts\":{"
            "\"env_abnormal\":%d,"
            "\"temperature_abnormal\":%d,"
            "\"humidity_abnormal\":%d"
        "},"
        "\"valid\":%d"
        "}",
        box_data.timestamp,
        SensorManager_GetStateString(box_data.state),
        box_data.env.temperature,
        box_data.env.humidity,
        box_data.env.pressure,
        box_data.env.altitude,
        env_alert_status.rated_temperature,
        env_alert_status.temp_low_limit,
        env_alert_status.temp_high_limit,
        env_alert_status.rated_humidity,
        env_alert_status.humidity_low_limit,
        env_alert_status.humidity_high_limit,
        box_data.motion.accel_x,
        box_data.motion.accel_y,
        box_data.motion.accel_z,
        box_data.motion.gyro_x,
        box_data.motion.gyro_y,
        box_data.motion.gyro_z,
        box_data.motion.pitch,
        box_data.motion.roll,
        box_data.motion.vibration,
        env_alert_status.is_abnormal,
        env_alert_status.temperature_abnormal,
        env_alert_status.humidity_abnormal,
        box_data.is_valid
    );
}
