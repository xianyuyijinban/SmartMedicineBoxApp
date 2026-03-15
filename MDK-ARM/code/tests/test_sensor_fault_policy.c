#include <assert.h>
#include <stdint.h>

#include "sensor_fault_policy.h"

int main(void) {
    assert(SensorFaultPolicy_ShouldReadBmp280(0U) == 0U);
    assert(SensorFaultPolicy_ShouldReadBmp280(1U) == 1U);

    assert(SensorFaultPolicy_GetCriticalFaultMask(0U) == 0U);
    assert(SensorFaultPolicy_GetCriticalFaultMask(SENSOR_FAULT_BMP280) == 0U);
    assert(SensorFaultPolicy_GetCriticalFaultMask(SENSOR_FAULT_MPU6050 | SENSOR_FAULT_BMP280) == SENSOR_FAULT_MPU6050);
    assert(SensorFaultPolicy_GetCriticalFaultMask(SENSOR_FAULT_AHT20 | SENSOR_FAULT_BMP280) == SENSOR_FAULT_AHT20);

    assert(SensorFaultPolicy_IsDataValid(0U) == 1U);
    assert(SensorFaultPolicy_IsDataValid(SENSOR_FAULT_BMP280) == 1U);
    assert(SensorFaultPolicy_IsDataValid(SENSOR_FAULT_MPU6050) == 0U);
    assert(SensorFaultPolicy_IsDataValid(SENSOR_FAULT_AHT20) == 0U);

    return 0;
}
