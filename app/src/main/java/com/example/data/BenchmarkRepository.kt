package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlin.math.sqrt

data class DriftStats(
    val runCount: Int,
    val meanDurationMs: Double,
    val stdDevDurationMs: Double,
    val cvPercent: Double, // Coefficient of Variation: % standard deviation / mean
    val minDurationMs: Double,
    val maxDurationMs: Double,
    val isDeterminismPerfect: Boolean,
    val identicalChecksumsCount: Int
)

class BenchmarkRepository(private val dao: BenchmarkDao) {
    val allRuns: Flow<List<BenchmarkRunEntity>> = dao.getAllRuns()

    fun getRunsForWorkload(workloadKey: String): Flow<List<BenchmarkRunEntity>> {
        return dao.getRunsForWorkload(workloadKey)
    }

    suspend fun insertRun(run: BenchmarkRunEntity): Long {
        return dao.insertRun(run)
    }

    suspend fun deleteRun(id: Long) {
        dao.deleteRunById(id)
    }

    suspend fun clearAll() {
        dao.clearAllRuns()
    }

    companion object {
        fun calculateDrift(runs: List<BenchmarkRunEntity>): DriftStats? {
            if (runs.isEmpty()) return null
            val durations = runs.map { it.durationMs }
            val n = durations.size
            val mean = durations.average()
            val variance = if (n > 1) {
                durations.sumOf { (it - mean) * (it - mean) } / (n - 1)
            } else 0.0
            val stdDev = sqrt(variance)
            val cv = if (mean > 0) (stdDev / mean) * 100.0 else 0.0

            val firstChecksum = runs.first().determinismChecksum
            val identicalCount = runs.count { it.determinismChecksum == firstChecksum }
            val isPerfect = (identicalCount == n)

            return DriftStats(
                runCount = n,
                meanDurationMs = mean,
                stdDevDurationMs = stdDev,
                cvPercent = cv,
                minDurationMs = durations.minOrNull() ?: 0.0,
                maxDurationMs = durations.maxOrNull() ?: 0.0,
                isDeterminismPerfect = isPerfect,
                identicalChecksumsCount = identicalCount
            )
        }
    }
}
