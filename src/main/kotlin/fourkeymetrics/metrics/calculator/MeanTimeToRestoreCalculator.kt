package fourkeymetrics.metrics.calculator

import fourkeymetrics.common.model.Build
import fourkeymetrics.common.model.BuildStatus
import fourkeymetrics.common.model.Stage
import fourkeymetrics.metrics.model.LEVEL
import fourkeymetrics.metrics.model.MetricsUnit
import org.springframework.stereotype.Component
import java.math.RoundingMode

@Component
class MeanTimeToRestoreCalculator : MetricsCalculator {

    companion object {
        private const val MILLISECOND_TO_HOURS: Double = 3600000.0
        private const val NO_VALUE: Double = Double.NaN
    }

    override fun calculateValue(allBuilds: List<Build>, startTimestamp: Long,
                                endTimestamp: Long, targetStage: String): Double {
        val buildOrderByTimestampAscending = allBuilds.sortedBy { it.timestamp }
        val selectedStages = findSelectedStages(buildOrderByTimestampAscending,
            startTimestamp, endTimestamp, targetStage)

        return calculateMTTR(selectedStages)
    }

    override fun calculateLevel(value: Double, unit: MetricsUnit?): LEVEL {
        TODO("Not yet implemented")
    }

    private fun findSelectedStages(allBuilds: List<Build>, startTimestamp: Long, endTimestamp: Long,
                                   targetStage: String): List<Stage> {
        val targetStages = allBuilds.flatMap { build -> build.stages }.filter { stage -> stage.name == targetStage }
        val lastSuccessfulDeploymentBuildBeforeGivenTime = allBuilds.findLast {
            it.containsGivenDeploymentBeforeGivenTimestamp(
                targetStage,
                BuildStatus.SUCCESS,
                startTimestamp
            )
        } ?: return targetStages.filter { it.getStageDoneTime() <= endTimestamp }

        val lastSuccessfulDeploymentTimestamp = lastSuccessfulDeploymentBuildBeforeGivenTime
            .stages.find { it.name == targetStage }!!.getStageDoneTime()

        return targetStages.filter {
            it.getStageDoneTime() in
                (lastSuccessfulDeploymentTimestamp.plus(1)).rangeTo(endTimestamp)
        }
    }

    private fun calculateMTTR(selectedStages: List<Stage>): Double {
        var firstFailedStage: Stage? = null
        var totalTime = 0.0
        var restoredTimes = 0
        for (stage in selectedStages) {
            if (stage.status == BuildStatus.FAILED && firstFailedStage == null) {
                firstFailedStage = stage
                continue
            }
            if (stage.status == BuildStatus.SUCCESS && firstFailedStage != null) {
                totalTime += calculateRestoredTimeInHours(firstFailedStage.getStageDoneTime(),
                    stage.getStageDoneTime())
                restoredTimes++
                firstFailedStage = null
            }
        }
        if (restoredTimes > 0) {
            return (totalTime.div(restoredTimes)).toBigDecimal().setScale(2, RoundingMode.HALF_UP).toDouble()
        }
        return NO_VALUE
    }

    private fun calculateRestoredTimeInHours(failedTime: Long, restoredTime: Long) =
        restoredTime.minus(failedTime).div(MILLISECOND_TO_HOURS)
}