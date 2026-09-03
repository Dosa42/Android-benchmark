package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "benchmark_runs")
data class BenchmarkRunEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val deviceModel: String,
    val chipsetInfo: String,
    val workloadKey: String,
    val workloadTitle: String,
    val affinityMode: String,
    val cpuMask: Long,
    val threadCount: Int,
    val durationMs: Double,
    val throughputOpsSec: Double,
    val determinismChecksum: String,
    val isDeterministicMatch: Boolean,
    val startTempC: Float,
    val peakTempC: Float,
    val endTempC: Float,
    val avgTempC: Float,
    val throttlingDetected: Boolean,
    val telemetryJson: String = ""
)
