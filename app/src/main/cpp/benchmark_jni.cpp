#include <jni.h>
#include <string>
#include <sstream>
#include <iomanip>

#include "harness.h"
#include "workloads/matrix_physics_workload.h"
#include "workloads/chess_workload.h"
#include "workloads/crypto_workload.h"
#include "workloads/memory_workload.h"
#include "workloads/video_workload.h"

extern "C" {

JNIEXPORT jstring JNICALL
Java_com_example_benchmark_NativeBenchmarkBridge_getHardwareTopology(JNIEnv* env, jobject /* this */) {
    benchmark::TopologyInfo topo = benchmark::detect_cpu_topology();

    std::ostringstream ss;
    ss << "{\n";
    ss << "  \"totalCores\": " << topo.total_cores << ",\n";
    ss << "  \"littleCount\": " << topo.little_count << ",\n";
    ss << "  \"midCount\": " << topo.mid_count << ",\n";
    ss << "  \"primeCount\": " << topo.prime_count << ",\n";
    ss << "  \"primeMask\": " << topo.prime_mask << ",\n";
    ss << "  \"allMask\": " << topo.all_mask << ",\n";
    ss << "  \"midMask\": " << topo.mid_mask << ",\n";
    ss << "  \"littleMask\": " << topo.little_mask << ",\n";
    ss << "  \"cores\": [\n";

    for (size_t i = 0; i < topo.cores.size(); ++i) {
        const auto& c = topo.cores[i];
        ss << "    {\n";
        ss << "      \"coreId\": " << c.core_id << ",\n";
        ss << "      \"maxFreqKhz\": " << c.max_freq_khz << ",\n";
        ss << "      \"minFreqKhz\": " << c.min_freq_khz << ",\n";
        ss << "      \"curFreqKhz\": " << c.cur_freq_khz << ",\n";
        ss << "      \"clusterName\": \"" << c.cluster_name << "\",\n";
        ss << "      \"clusterId\": " << c.cluster_id << ",\n";
        ss << "      \"isOnline\": " << (c.is_online ? "true" : "false") << "\n";
        ss << "    }" << (i + 1 < topo.cores.size() ? "," : "") << "\n";
    }

    ss << "  ]\n";
    ss << "}";

    std::string json_str = ss.str();
    return env->NewStringUTF(json_str.c_str());
}

JNIEXPORT jstring JNICALL
Java_com_example_benchmark_NativeBenchmarkBridge_getLiveThermalZones(JNIEnv* env, jobject /* this */) {
    auto zones = benchmark::read_thermal_zones();
    float batt = benchmark::read_battery_temp();

    std::ostringstream ss;
    ss << "{\n";
    ss << "  \"batteryTempC\": " << batt << ",\n";
    ss << "  \"zones\": [\n";
    for (size_t i = 0; i < zones.size(); ++i) {
        ss << "    {\"id\":" << zones[i].zone_id
           << ",\"type\":\"" << zones[i].type << "\""
           << ",\"tempC\":" << zones[i].temp_celsius << "}"
           << (i + 1 < zones.size() ? "," : "") << "\n";
    }
    ss << "  ]\n";
    ss << "}";

    std::string json_str = ss.str();
    return env->NewStringUTF(json_str.c_str());
}

JNIEXPORT jstring JNICALL
Java_com_example_benchmark_NativeBenchmarkBridge_executeBenchmark(
        JNIEnv* env,
        jobject /* this */,
        jstring jWorkload,
        jstring jAffinityMode,
        jlong jCpuMask,
        jint jThreads) {

    const char* workload_str = env->GetStringUTFChars(jWorkload, nullptr);
    const char* affinity_str = env->GetStringUTFChars(jAffinityMode, nullptr);
    uint64_t cpu_mask = static_cast<uint64_t>(jCpuMask);
    int threads = static_cast<int>(jThreads);

    std::string workload(workload_str);
    std::string affinity_mode(affinity_str);

    env->ReleaseStringUTFChars(jWorkload, workload_str);
    env->ReleaseStringUTFChars(jAffinityMode, affinity_str);

    benchmark::WorkloadResult result;

    if (workload == "matrix_physics") {
        result = benchmark::run_matrix_physics_workload(affinity_mode, cpu_mask, threads);
    } else if (workload == "chess") {
        result = benchmark::run_chess_workload(affinity_mode, cpu_mask, threads);
    } else if (workload == "crypto") {
        result = benchmark::run_crypto_workload(affinity_mode, cpu_mask, threads);
    } else if (workload == "memory") {
        result = benchmark::run_memory_workload(affinity_mode, cpu_mask, threads);
    } else if (workload == "video") {
        result = benchmark::run_video_workload(affinity_mode, cpu_mask, threads);
    } else {
        result.workload_name = workload;
        result.error_message = "Unknown workload: " + workload;
        result.is_valid = false;
    }

    std::string result_json = result.to_json();
    return env->NewStringUTF(result_json.c_str());
}

} // extern "C"
