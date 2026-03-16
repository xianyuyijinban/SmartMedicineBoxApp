package com.smartmedicine.ui.components

import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

class EnvironmentCardSourceTest {

    private val environmentCardFile = File(
        "src/main/java/com/smartmedicine/ui/components/EnvironmentCard.kt"
    )

    @Test
    fun environmentCardSourceDoesNotRenderPressureMetric() {
        val source = environmentCardFile.readText()

        assertFalse("EnvironmentDataCard should not expose pressure in UI", source.contains("pressure: Double?"))
        assertFalse("Environment cards should not render pressure label", source.contains("label = \"气压\""))
        assertFalse("Environment cards should not render pressure icon", source.contains("icon = \"🌀\""))
    }
}
