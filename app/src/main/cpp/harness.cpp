#include "harness.h"

#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <dirent.h>
#include <unistd.h>
#include <sstream>
#include <iomanip>
#include <algorithm>
#include <set>

namespace benchmark {

static long read_sysfs_long(const std::string& path) {
    FILE* fp = fopen(path.c_str(), "r");
    if (!fp) return -1;
    long val = -1;
    if (fscanf(fp, "%ld", &val) != 1) {
        val = -1;
    }
    fclose(fp);
    return val;
}

static std::string read_sysfs_string(const std::string& path) {
    FILE* fp = fopen(path.c_str(), "r");
    if (!fp) return "";
    char buf[128];
    if (fgets(buf, sizeof(buf), fp)) {
        size_t len = strlen(buf);
        if (len > 0 && buf[len - 1] == '\n') buf[len - 1] = '\0';
        fclose(fp);
        return std::string(buf);
    }
    fclose(fp);
    return "";
}

TopologyInfo detect_cpu_topology() {
    TopologyInfo topo;
    int num_cores = sysconf(_SC_NPROCESSORS_CONF);
    if (num_cores <= 0) num_cores = 8;
    topo.total_cores = num_cores;

    std::set<long> distinct_freqs;
    std::vector<CoreInfo> raw_cores;

    for (int i = 0; i < num_cores; ++i) {
        CoreInfo info;
        info.core_id = i;
        
        char path[128];
        snprintf(path, sizeof(path), "/sys/devices/system/cpu/cpu%d/cpufreq/cpuinfo_max_freq", i);
        info.max_freq_khz = read_sysfs_long(path);
        
        if (info.max_freq_khz <= 0) {
            snprintf(path, sizeof(path), "/sys/devices/system/cpu/cpu%d/cpufreq/scaling_max_freq", i);
            info.max_freq_khz = read_sysfs_long(path);
        }

        snprintf(path, sizeof(path), "/sys/devices/system/cpu/cpu%d/cpufreq/cpuinfo_min_freq", i);
        info.min_freq_khz = read_sysfs_long(path);

        snprintf(path, sizeof(path), "/sys/devices/system/cpu/cpu%d/cpufreq/scaling_cur_freq", i);
        info.cur_freq_khz = read_sysfs_long(path);

        snprintf(path, sizeof(path), "/sys/devices/system/cpu/cpu%d/online", i);
        long online_val = read_sysfs_long(path);
        info.is_online = (online_val != 0); // Core 0 might not have online file (always online)

        if (info.max_freq_khz > 0) {
            distinct_freqs.insert(info.max_freq_khz);
        }
        raw_cores.push_back(info);
    }

    // Classify clusters based on sorted max frequencies
    std::vector<long> sorted_freqs(distinct_freqs.begin(), distinct_freqs.end());
    std::sort(sorted_freqs.begin(), sorted_freqs.end());

    for (auto& core : raw_cores) {
        topo.all_mask |= (1ULL << core.core_id);
        
        if (sorted_freqs.empty()) {
            core.cluster_name = "STANDARD";
            core.cluster_id = 0;
            topo.prime_mask |= (1ULL << core.core_id);
            continue;
        }

        if (sorted_freqs.size() == 1) {
            core.cluster_name = "UNIFIED";
            core.cluster_id = 0;
            topo.prime_mask |= (1ULL << core.core_id);
            topo.prime_count++;
        } else if (sorted_freqs.size() == 2) {
            if (core.max_freq_khz == sorted_freqs[0]) {
                core.cluster_name = "LITTLE";
                core.cluster_id = 0;
                topo.little_mask |= (1ULL << core.core_id);
                topo.little_count++;
            } else {
                core.cluster_name = "PRIME";
                core.cluster_id = 1;
                topo.prime_mask |= (1ULL << core.core_id);
                topo.prime_count++;
            }
        } else {
            // 3 or more clusters (e.g. Samsung Exynos 2400 / Snapdragon 8 Gen 3)
            if (core.max_freq_khz == sorted_freqs[0]) {
                core.cluster_name = "LITTLE";
                core.cluster_id = 0;
                topo.little_mask |= (1ULL << core.core_id);
                topo.little_count++;
            } else if (core.max_freq_khz == sorted_freqs.back()) {
                core.cluster_name = "PRIME";
                core.cluster_id = 2;
                topo.prime_mask |= (1ULL << core.core_id);
                topo.prime_count++;
            } else {
                core.cluster_name = "MID";
                core.cluster_id = 1;
                topo.mid_mask |= (1ULL << core.core_id);
                topo.mid_count++;
            }
        }
    }

    // Safety check: if prime mask is 0, use core 0 or highest core
    if (topo.prime_mask == 0 && !raw_cores.empty()) {
        topo.prime_mask = (1ULL << (raw_cores.size() - 1));
    }

    topo.cores = raw_cores;
    return topo;
}

bool set_thread_affinity(uint64_t cpu_mask) {
    if (cpu_mask == 0) return false;
    
    cpu_set_t cpuset;
    CPU_ZERO(&cpuset);
    for (int i = 0; i < 64; ++i) {
        if (cpu_mask & (1ULL << i)) {
            CPU_SET(i, &cpuset);
        }
    }

    int rc = sched_setaffinity(0, sizeof(cpu_set_t), &cpuset);
    if (rc != 0) {
        LOGE("sched_setaffinity failed with code: %d (mask: 0x%llx)", rc, (unsigned long long)cpu_mask);
        return false;
    }
    return true;
}

std::vector<ThermalZoneInfo> read_thermal_zones() {
    std::vector<ThermalZoneInfo> zones;
    for (int i = 0; i < 32; ++i) {
        char type_path[128], temp_path[128];
        snprintf(type_path, sizeof(type_path), "/sys/class/thermal/thermal_zone%d/type", i);
        snprintf(temp_path, sizeof(temp_path), "/sys/class/thermal/thermal_zone%d/temp", i);

        std::string type = read_sysfs_string(type_path);
        if (type.empty()) break; // No more zones

        long raw_temp = read_sysfs_long(temp_path);
        if (raw_temp <= 0) continue;

        float temp_c = (raw_temp > 1000) ? (static_cast<float>(raw_temp) / 1000.0f) : static_cast<float>(raw_temp);
        zones.push_back({i, type, temp_c});
    }
    return zones;
}

float read_battery_temp() {
    long raw_temp = read_sysfs_long("/sys/class/power_supply/battery/temp");
    if (raw_temp > 0) {
        return (raw_temp > 100) ? (static_cast<float>(raw_temp) / 10.0f) : static_cast<float>(raw_temp);
    }
    return -1.0f;
}

std::vector<long> read_core_frequencies(int num_cores) {
    std::vector<long> freqs(num_cores, 0);
    for (int i = 0; i < num_cores; ++i) {
        char path[128];
        snprintf(path, sizeof(path), "/sys/devices/system/cpu/cpu%d/cpufreq/scaling_cur_freq", i);
        long cur = read_sysfs_long(path);
        freqs[i] = (cur > 0) ? cur : 0;
    }
    return freqs;
}

TelemetryCollector::TelemetryCollector(int num_cores, int sample_interval_ms)
    : num_cores_(num_cores), interval_ms_(sample_interval_ms) {}

TelemetryCollector::~TelemetryCollector() {
    stop();
}

void TelemetryCollector::start(uint64_t benchmark_start_ns) {
    start_ns_ = benchmark_start_ns;
    running_ = true;
    samples_.clear();
    peak_temp_ = 0.0f;
    avg_temp_ = 0.0f;
    start_temp_ = 0.0f;
    end_temp_ = 0.0f;
    throttling_detected_ = false;

    worker_thread_ = std::thread(&TelemetryCollector::sample_loop, this);
}

void TelemetryCollector::stop() {
    if (running_.exchange(false)) {
        if (worker_thread_.joinable()) {
            worker_thread_.join();
        }
    }
}

void TelemetryCollector::sample_loop() {
    double temp_sum = 0.0;
    size_t temp_count = 0;
    long initial_peak_freq = 0;

    while (running_) {
        uint64_t now_ns = get_time_ns();
        float elapsed_ms = static_cast<float>(now_ns - start_ns_) / 1000000.0f;

        auto zones = read_thermal_zones();
        float max_zone_temp = 0.0f;
        std::vector<float> zone_temps;
        for (const auto& z : zones) {
            zone_temps.push_back(z.temp_celsius);
            if (z.temp_celsius > max_zone_temp) {
                max_zone_temp = z.temp_celsius;
            }
        }

        float batt_temp = read_battery_temp();
        auto freqs = read_core_frequencies(num_cores_);

        long max_freq_now = 0;
        for (long f : freqs) {
            if (f > max_freq_now) max_freq_now = f;
        }

        if (initial_peak_freq == 0 && max_freq_now > 0) {
            initial_peak_freq = max_freq_now;
        }

        // Check if frequency dropped drastically under high temperature
        if (initial_peak_freq > 0 && max_freq_now > 0 && max_zone_temp > 50.0f) {
            if (max_freq_now < (initial_peak_freq * 80 / 100)) {
                throttling_detected_ = true;
            }
        }

        if (start_temp_ == 0.0f && max_zone_temp > 0.0f) {
            start_temp_ = max_zone_temp;
        }
        if (max_zone_temp > peak_temp_) {
            peak_temp_ = max_zone_temp;
        }
        if (max_zone_temp > 0.0f) {
            temp_sum += max_zone_temp;
            temp_count++;
            end_temp_ = max_zone_temp;
        }

        TelemetryPoint pt;
        pt.timestamp_ns = now_ns;
        pt.elapsed_ms = elapsed_ms;
        pt.max_temp_celsius = max_zone_temp;
        pt.battery_temp_celsius = batt_temp;
        pt.zone_temps = zone_temps;
        pt.core_frequencies_khz = freqs;

        samples_.push_back(pt);

        usleep(interval_ms_ * 1000);
    }

    if (temp_count > 0) {
        avg_temp_ = static_cast<float>(temp_sum / temp_count);
    }
}

std::string WorkloadResult::to_json() const {
    std::ostringstream ss;
    ss << std::fixed << std::setprecision(3);
    ss << "{\n";
    ss << "  \"workload\": \"" << workload_name << "\",\n";
    ss << "  \"affinityMode\": \"" << affinity_mode << "\",\n";
    ss << "  \"cpuMask\": " << cpu_mask << ",\n";
    ss << "  \"threadCount\": " << thread_count << ",\n";
    ss << "  \"warmupNs\": " << warmup_ns << ",\n";
    ss << "  \"measureNs\": " << measure_ns << ",\n";
    ss << "  \"cooldownNs\": " << cooldown_ns << ",\n";
    ss << "  \"totalNs\": " << total_ns << ",\n";
    ss << "  \"operationsCount\": " << operations_count << ",\n";
    ss << "  \"throughputOpsSec\": " << throughput_ops_sec << ",\n";
    ss << "  \"determinismChecksum\": \"" << determinism_checksum << "\",\n";
    ss << "  \"checksumMatched\": " << (checksum_matched ? "true" : "false") << ",\n";
    ss << "  \"startTempC\": " << start_temp_c << ",\n";
    ss << "  \"peakTempC\": " << peak_temp_c << ",\n";
    ss << "  \"endTempC\": " << end_temp_c << ",\n";
    ss << "  \"avgTempC\": " << avg_temp_c << ",\n";
    ss << "  \"throttlingDetected\": " << (throttling_detected ? "true" : "false") << ",\n";
    ss << "  \"sampleCount\": " << telemetry_samples.size() << ",\n";
    
    // Include compact telemetry series for client visualization
    ss << "  \"telemetry\": [\n";
    for (size_t i = 0; i < telemetry_samples.size(); ++i) {
        const auto& s = telemetry_samples[i];
        ss << "    {\"t\":" << s.elapsed_ms << ",\"temp\":" << s.max_temp_celsius;
        if (s.battery_temp_celsius > 0) {
            ss << ",\"batt\":" << s.battery_temp_celsius;
        }
        if (!s.core_frequencies_khz.empty()) {
            ss << ",\"freq\":" << s.core_frequencies_khz[0];
        }
        ss << "}" << (i + 1 < telemetry_samples.size() ? "," : "") << "\n";
    }
    ss << "  ],\n";
    ss << "  \"errorMessage\": \"" << error_message << "\"\n";
    ss << "}";
    return ss.str();
}

} // namespace benchmark
