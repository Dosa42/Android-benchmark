package com.example.benchmark

import org.json.JSONArray
import org.json.JSONObject

data class CoreTopology(
    val coreId: Int,
    val maxFreqKhz: Long,
    val minFreqKhz: Long,
    val curFreqKhz: Long,
    val clusterName: String,
    val clusterId: Int,
    val isOnline: Boolean
)

data class HardwareTopology(
    val totalCores: Int,
    val littleCount: Int,
    val midCount: Int,
    val primeCount: Int,
    val primeMask: Long,
    val allMask: Long,
    val midMask: Long,
    val littleMask: Long,
    val cores: List<CoreTopology>
) {
    companion object {
        fun fromJson(jsonStr: String): HardwareTopology {
            val root = JSONObject(jsonStr)
            val coresArray = root.optJSONArray("cores") ?: JSONArray()
            val cores = mutableListOf<CoreTopology>()
            for (i in 0 until coresArray.length()) {
                val obj = coresArray.getJSONObject(i)
                cores.add(
                    CoreTopology(
                        coreId = obj.optInt("coreId", i),
                        maxFreqKhz = obj.optLong("maxFreqKhz", 0),
                        minFreqKhz = obj.optLong("minFreqKhz", 0),
                        curFreqKhz = obj.optLong("curFreqKhz", 0),
                        clusterName = obj.optString("clusterName", "UNKNOWN"),
                        clusterId = obj.optInt("clusterId", 0),
                        isOnline = obj.optBoolean("isOnline", true)
                    )
                )
            }
            return HardwareTopology(
                totalCores = root.optInt("totalCores", cores.size),
                littleCount = root.optInt("littleCount", 0),
                midCount = root.optInt("midCount", 0),
                primeCount = root.optInt("primeCount", 0),
                primeMask = root.optLong("primeMask", 0),
                allMask = root.optLong("allMask", 0),
                midMask = root.optLong("midMask", 0),
                littleMask = root.optLong("littleMask", 0),
                cores = cores
            )
        }
    }
}

data class ThermalZone(
    val id: Int,
    val type: String,
    val tempC: Float
)

data class ThermalSnapshot(
    val batteryTempC: Float,
    val zones: List<ThermalZone>
) {
    companion object {
        fun fromJson(jsonStr: String): ThermalSnapshot {
            val root = JSONObject(jsonStr)
            val zonesArray = root.optJSONArray("zones") ?: JSONArray()
            val zones = mutableListOf<ThermalZone>()
            for (i in 0 until zonesArray.length()) {
                val obj = zonesArray.getJSONObject(i)
                zones.add(
                    ThermalZone(
                        id = obj.optInt("id", i),
                        type = obj.optString("type", "thermal_zone$i"),
                        tempC = obj.optDouble("tempC", 0.0).toFloat()
                    )
                )
            }
            return ThermalSnapshot(
                batteryTempC = root.optDouble("batteryTempC", -1.0).toFloat(),
                zones = zones
            )
        }
    }
}

data class TelemetrySample(
    val elapsedMs: Float,
    val maxTempC: Float,
    val battTempC: Float,
    val primaryFreqKhz: Long
)

data class NativeBenchmarkResult(
    val workload: String,
    val affinityMode: String,
    val cpuMask: Long,
    val threadCount: Int,
    val warmupNs: Long,
    val measureNs: Long,
    val cooldownNs: Long,
    val totalNs: Long,
    val operationsCount: Double,
    val throughputOpsSec: Double,
    val determinismChecksum: String,
    val checksumMatched: Boolean,
    val startTempC: Float,
    val peakTempC: Float,
    val endTempC: Float,
    val avgTempC: Float,
    val throttlingDetected: Boolean,
    val telemetry: List<TelemetrySample>,
    val errorMessage: String
) {
    val durationMs: Double get() = measureNs / 1_000_000.0

    companion object {
        fun fromJson(jsonStr: String): NativeBenchmarkResult {
            val root = JSONObject(jsonStr)
            val telArray = root.optJSONArray("telemetry") ?: JSONArray()
            val telemetryList = mutableListOf<TelemetrySample>()
            for (i in 0 until telArray.length()) {
                val obj = telArray.getJSONObject(i)
                telemetryList.add(
                    TelemetrySample(
                        elapsedMs = obj.optDouble("t", 0.0).toFloat(),
                        maxTempC = obj.optDouble("temp", 0.0).toFloat(),
                        battTempC = obj.optDouble("batt", -1.0).toFloat(),
                        primaryFreqKhz = obj.optLong("freq", 0)
                    )
                )
            }

            return NativeBenchmarkResult(
                workload = root.optString("workload", "Unknown"),
                affinityMode = root.optString("affinityMode", "PRIME"),
                cpuMask = root.optLong("cpuMask", 0),
                threadCount = root.optInt("threadCount", 1),
                warmupNs = root.optLong("warmupNs", 0),
                measureNs = root.optLong("measureNs", 0),
                cooldownNs = root.optLong("cooldownNs", 0),
                totalNs = root.optLong("totalNs", 0),
                operationsCount = root.optDouble("operationsCount", 0.0),
                throughputOpsSec = root.optDouble("throughputOpsSec", 0.0),
                determinismChecksum = root.optString("determinismChecksum", "0"),
                checksumMatched = root.optBoolean("checksumMatched", true),
                startTempC = root.optDouble("startTempC", 0.0).toFloat(),
                peakTempC = root.optDouble("peakTempC", 0.0).toFloat(),
                endTempC = root.optDouble("endTempC", 0.0).toFloat(),
                avgTempC = root.optDouble("avgTempC", 0.0).toFloat(),
                throttlingDetected = root.optBoolean("throttlingDetected", false),
                telemetry = telemetryList,
                errorMessage = root.optString("errorMessage", "")
            )
        }
    }
}
