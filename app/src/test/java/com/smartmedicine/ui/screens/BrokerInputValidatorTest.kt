package com.smartmedicine.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrokerInputValidatorTest {

    @Test
    fun validBrokerAccepted() {
        assertTrue(BrokerInputValidator.isValidBroker("tcp://192.168.1.100:1883"))
        assertTrue(BrokerInputValidator.isValidBroker("ssl://mqtt.example.com:8883"))
    }

    @Test
    fun invalidBrokerRejected() {
        assertFalse(BrokerInputValidator.isValidBroker("tcp://:1883"))
        assertFalse(BrokerInputValidator.isValidBroker("tcp://mqtt.example.com"))
        assertFalse(BrokerInputValidator.isValidBroker("tcp://mqtt.example.com:0"))
        assertFalse(BrokerInputValidator.isValidBroker("tcp://mqtt.example.com:70000"))
        assertFalse(BrokerInputValidator.isValidBroker("http://mqtt.example.com:1883"))
        assertFalse(BrokerInputValidator.isValidBroker(""))
    }

    @Test
    fun validDeviceIdAccepted() {
        assertTrue(BrokerInputValidator.isValidDeviceId("medicine_box_001"))
        assertTrue(BrokerInputValidator.isValidDeviceId("box-01"))
    }

    @Test
    fun invalidDeviceIdRejected() {
        assertFalse(BrokerInputValidator.isValidDeviceId(""))
        assertFalse(BrokerInputValidator.isValidDeviceId("box 01"))
        assertFalse(BrokerInputValidator.isValidDeviceId("box@01"))
    }
}
