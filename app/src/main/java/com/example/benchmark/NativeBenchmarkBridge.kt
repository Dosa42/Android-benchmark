package com.example.benchmark

object NativeBenchmarkBridge {
    init {
        try {
            System.loadLibrary("benchmark_native")
        } catch (e: UnsatisfiedLinkError) {
            e.printStackTrace()
        }
    }

    external fun getHardwareTopology(): String
    external fun getLiveThermalZones(): String
    external fun executeBenchmark(
        workload: String,
        affinityMode: String,
        cpuMask: Long,
        threads: Int
    ): String
}
