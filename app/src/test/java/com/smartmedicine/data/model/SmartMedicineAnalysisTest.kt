package com.smartmedicine.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartMedicineAnalysisTest {
    @Test
    fun perfectDayKeepsHighScore() {
        val result = SmartMedicineAnalysis.analyze(
            baseInput(
                doses = listOf(
                    SmartDoseRecord(
                        scheduledAt = 1_000L,
                        status = SmartMedicineAnalysis.STATUS_TAKEN,
                        actualTakenAt = 2_000L
                    )
                )
            )
        )

        assertEquals(100, result.score)
        assertEquals(100, result.completedRate)
        assertEquals("good", result.advice.first().level)
    }

    @Test
    fun delayedAndMissedDosesReduceScoreButNotBelowSixty() {
        val result = SmartMedicineAnalysis.analyze(
            baseInput(
                doses = listOf(
                    SmartDoseRecord(1_000L, SmartMedicineAnalysis.STATUS_TAKEN, 12L * 60L * 1000L),
                    SmartDoseRecord(2_000L, SmartMedicineAnalysis.STATUS_MISSED),
                    SmartDoseRecord(3_000L, SmartMedicineAnalysis.STATUS_MISSED),
                    SmartDoseRecord(4_000L, SmartMedicineAnalysis.STATUS_MISSED),
                    SmartDoseRecord(5_000L, SmartMedicineAnalysis.STATUS_SKIPPED)
                )
            )
        )

        assertEquals(1, result.delayedCount)
        assertEquals(3, result.missedCount)
        assertTrue(result.score in 60..99)
        assertTrue(result.advice.any { it.title.contains("漏服") })
        assertTrue(result.advice.any { it.title.contains("延迟") })
    }

    @Test
    fun inactiveEmptyBoxesDoNotCreateStockAdvice() {
        val result = SmartMedicineAnalysis.analyze(
            baseInput(
                compartments = listOf(
                    SmartCompartmentRecord(1, "1 号药盒", stock = 0, dosePerUse = 1, lowStockThreshold = 3, active = false),
                    SmartCompartmentRecord(6, "降压药", stock = 0, dosePerUse = 1, lowStockThreshold = 3, active = true),
                    SmartCompartmentRecord(8, "维生素", stock = 2, dosePerUse = 1, lowStockThreshold = 3, active = true)
                )
            )
        )

        assertEquals(1, result.lowStockCount)
        assertEquals(1, result.zeroStockCount)
        assertFalse(result.advice.any { it.title.contains("1 号药盒需要补药") })
        assertTrue(result.advice.any { it.title.contains("6 号药盒需要补药") })
        assertTrue(result.advice.any { it.title.contains("8 号药盒库存偏低") })
    }

    @Test
    fun moreThanThreeStockRisksAreAllReported() {
        val result = SmartMedicineAnalysis.analyze(
            baseInput(
                compartments = (1..5).map {
                    SmartCompartmentRecord(it, "$it 号药", stock = 1, dosePerUse = 1, lowStockThreshold = 3, active = true)
                }
            )
        )

        assertEquals(5, result.lowStockCount)
        assertEquals(5, result.advice.count { it.title.contains("库存偏低") })
    }

    @Test
    fun offlineAndEnvironmentAbnormalAreReported() {
        val result = SmartMedicineAnalysis.analyze(
            baseInput(
                isOnline = false,
                environmentAbnormal = true,
                boxState = "tilted"
            )
        )

        assertTrue(result.advice.any { it.title.contains("设备未在线") })
        assertTrue(result.advice.any { it.title.contains("存放环境") })
        assertTrue(result.advice.any { it.title.contains("药箱状态") })
    }

    private fun baseInput(
        doses: List<SmartDoseRecord> = emptyList(),
        compartments: List<SmartCompartmentRecord> = emptyList(),
        isOnline: Boolean = true,
        environmentAbnormal: Boolean = false,
        boxState: String? = "closed"
    ): SmartAnalysisInput = SmartAnalysisInput(
        now = 0L,
        doses = doses,
        compartments = compartments,
        activePlanCount = 1,
        isOnline = isOnline,
        boxState = boxState,
        environmentAbnormal = environmentAbnormal
    )
}
