package com.smartmedicine.data.model

data class SmartDoseRecord(
    val scheduledAt: Long,
    val status: String,
    val actualTakenAt: Long? = null,
    val reminderCount: Int = 0
)

data class SmartCompartmentRecord(
    val boxId: Int,
    val name: String,
    val stock: Int,
    val dosePerUse: Int,
    val lowStockThreshold: Int,
    val active: Boolean = true
)

data class SmartAnalysisInput(
    val now: Long,
    val doses: List<SmartDoseRecord>,
    val compartments: List<SmartCompartmentRecord>,
    val activePlanCount: Int,
    val isOnline: Boolean,
    val boxState: String?,
    val environmentAbnormal: Boolean
)

data class SmartAdvice(
    val level: String,
    val title: String,
    val message: String
)

data class SmartAnalysisResult(
    val score: Int,
    val completedRate: Int,
    val totalDoses: Int,
    val takenCount: Int,
    val delayedCount: Int,
    val missedCount: Int,
    val skippedCount: Int,
    val lowStockCount: Int,
    val zeroStockCount: Int,
    val riskCount: Int,
    val advice: List<SmartAdvice>
)

object SmartMedicineAnalysis {
    const val STATUS_PENDING = "pending"
    const val STATUS_TAKEN = "taken"
    const val STATUS_SKIPPED = "skipped"
    const val STATUS_MISSED = "missed"
    const val STATUS_SNOOZED = "snoozed"

    private const val ON_TIME_WINDOW_MS = 10L * 60L * 1000L

    fun analyze(input: SmartAnalysisInput): SmartAnalysisResult {
        val activeCompartments = input.compartments.filter { it.active }
        val total = input.doses.size
        val taken = input.doses.count { it.status == STATUS_TAKEN }
        val skipped = input.doses.count { it.status == STATUS_SKIPPED }
        val missed = input.doses.count { it.status == STATUS_MISSED }
        val delayed = input.doses.count { dose ->
            dose.status == STATUS_TAKEN &&
                dose.actualTakenAt != null &&
                dose.actualTakenAt - dose.scheduledAt > ON_TIME_WINDOW_MS
        }
        val snoozed = input.doses.count { it.status == STATUS_SNOOZED || it.reminderCount > 0 }
        val lowStock = activeCompartments
            .filter { it.stock in 1..it.lowStockThreshold }
            .sortedWith(compareBy<SmartCompartmentRecord> { it.stock / it.dosePerUse.coerceAtLeast(1) }.thenBy { it.boxId })
        val zeroStock = activeCompartments
            .filter { it.stock <= 0 }
            .sortedBy { it.boxId }

        val completedRate = if (total == 0) 100 else ((taken * 100.0) / total).toInt().coerceIn(0, 100)
        val rawScore = 100 - missed * 10 - skipped * 8 - delayed * 5 - snoozed * 3 - lowStock.size * 3 - zeroStock.size * 6
        val score = rawScore.coerceIn(60, 100)

        val advice = buildList {
            if (input.activePlanCount == 0) {
                add(SmartAdvice("info", "还没有服药计划", "建立服药计划后，系统会自动同步最近 5 次提醒。"))
            }
            if (missed > 0) {
                add(SmartAdvice("warning", "存在可能漏服", "近 7 天有 $missed 次未按时确认，建议提前提醒或使用稍后提醒。"))
            }
            if (delayed > 0) {
                add(SmartAdvice("info", "服药略有延迟", "近 7 天有 $delayed 次超过 10 分钟才确认，可以适当提前提醒时间。"))
            }
            zeroStock.forEach {
                add(SmartAdvice("danger", "${it.boxId} 号药盒需要补药", "${it.name} 库存为 0，请补药或停用该药盒。"))
            }
            lowStock.forEach {
                val uses = it.stock / it.dosePerUse.coerceAtLeast(1)
                add(SmartAdvice("warning", "${it.boxId} 号药盒库存偏低", "${it.name} 预计还可服用 ${uses.coerceAtLeast(0)} 次。"))
            }
            if (!input.isOnline) {
                add(SmartAdvice("warning", "设备未在线", "App 会保留计划，但设备离线时无法同步 RTC 蜂鸣提醒。"))
            }
            if (input.boxState == "moving" || input.boxState == "tilted") {
                add(SmartAdvice("danger", "药箱状态异常", "检测到移动或倾斜，建议确认药箱是否放置平稳。"))
            }
            if (input.environmentAbnormal) {
                add(SmartAdvice("warning", "存放环境异常", "温湿度已超出设定范围，建议更换阴凉干燥位置。"))
            }
            if (isEmpty()) {
                add(SmartAdvice("good", "当前状态良好", "服药、库存、设备状态都比较稳定，继续保持。"))
            }
        }

        return SmartAnalysisResult(
            score = score,
            completedRate = completedRate,
            totalDoses = total,
            takenCount = taken,
            delayedCount = delayed,
            missedCount = missed,
            skippedCount = skipped,
            lowStockCount = lowStock.size,
            zeroStockCount = zeroStock.size,
            riskCount = advice.count { it.level == "warning" || it.level == "danger" },
            advice = advice
        )
    }
}
