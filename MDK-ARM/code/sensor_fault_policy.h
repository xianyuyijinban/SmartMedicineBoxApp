#ifndef SENSOR_FAULT_POLICY_H
#define SENSOR_FAULT_POLICY_H

#include <stdint.h>

#define SENSOR_FAULT_MPU6050 0x01U
#define SENSOR_FAULT_AHT20   0x02U
#define SENSOR_FAULT_BMP280  0x04U

static inline uint8_t SensorFaultPolicy_ShouldReadBmp280(uint8_t bmp280_available)
{
    return (uint8_t)(bmp280_available != 0U ? 1U : 0U);
}

static inline uint8_t SensorFaultPolicy_GetCriticalFaultMask(uint8_t fault_mask)
{
    return (uint8_t)(fault_mask & (uint8_t)(SENSOR_FAULT_MPU6050 | SENSOR_FAULT_AHT20));
}

static inline uint8_t SensorFaultPolicy_IsDataValid(uint8_t fault_mask)
{
    return (uint8_t)(SensorFaultPolicy_GetCriticalFaultMask(fault_mask) == 0U ? 1U : 0U);
}

#endif
