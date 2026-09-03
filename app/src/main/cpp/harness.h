#ifndef BENCHMARK_HARNESS_H
#define BENCHMARK_HARNESS_H

#include <string>
#include <vector>
#include <cstdint>
#include <chrono>
#include <atomic>
#include <thread>
#include <sched.h>
#include <unistd.h>
#include <android/log.h>

#define LOG_TAG "NativeBenchmark"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

namespace benchmark {

// Monotonic raw nanosecond timer
inline uint64_t get_time_ns() {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC_RAW, &ts);
    return static_cast<uint64_t>(ts.tv_sec) * 1000000000ULL + static_cast<uint64_t>(ts.tv_nsec);
}

// SplitMix64 deterministic PRNG
class SplitMix64 {
public:
    explicit SplitMix64(uint64_t seed = 0x853c49e6748fea9bULL) : state_(seed) {}
    uint64_t next() {
        uint64_t z = (state_ += 0x9e3779b97f4a7c15ULL);
        z = (z ^ (z >> 30)) * 0xbf58476d1ce4e5b9ULL;
        z = (z ^ (z >> 27)) * 0x94d049bb133111ebULL;
        return z ^ (z >> 31);
    }
    double next_double() {
        return (next() >> 11) * (1.0 / (1ULL << 53));
    }
private:
    uint64_t state_;
};

struct CoreInfo {
    int core_id = 0;
    long max_freq_khz = 0;
    long min_freq_khz = 0;
    long cur_freq_khz = 0;
    std::string cluster_name = "UNKNOWN"; // "LITTLE", "MID", "PRIME"
    int cluster_id = 0;
    bool is_online = true;
};

struct ThermalZoneInfo {
    int zone_id = 0;
    std::string type = "";
    float temp_celsius = 0.0f;
};

struct TopologyInfo {
    int total_cores = 0;
    std::vector<CoreInfo> cores;
    int little_count = 0;
    int mid_count = 0;
    int prime_count = 0;
    uint64_t prime_mask = 0;
    uint64_t all_mask = 0;
    uint64_t mid_mask = 0;
    uint64_t little_mask = 0;
};

struct TelemetryPoint {
    uint64_t timestamp_ns = 0;
    float elapsed_ms = 0.0f;
    float max_temp_celsius = 0.0f;
    float battery_temp_celsius = 0.0f;
    std::vector<float> zone_temps;
    std::vector<long> core_frequencies_khz;
};

struct WorkloadResult {
    std::string workload_name;
    std::string affinity_mode; // "PRIME", "ALL", "CUSTOM_MASK"
    uint64_t cpu_mask = 0;
    int thread_count = 1;
    
    uint64_t warmup_ns = 0;
    uint64_t measure_ns = 0;
    uint64_t cooldown_ns = 0;
    uint64_t total_ns = 0;
    
    double operations_count = 0.0;
    double throughput_ops_sec = 0.0;
    uint64_t determinism_checksum = 0;
    bool checksum_matched = true;
    
    float start_temp_c = 0.0f;
    float peak_temp_c = 0.0f;
    float end_temp_c = 0.0f;
    float avg_temp_c = 0.0f;
    bool throttling_detected = false;
    
    std::vector<TelemetryPoint> telemetry_samples;
    bool is_valid = true;
    std::string error_message = "";
    
    std::string to_json() const;
};

// Core hardware topology detector
TopologyInfo detect_cpu_topology();

// Thread affinity control
bool set_thread_affinity(uint64_t cpu_mask);

// Thermal and frequency query helpers
std::vector<ThermalZoneInfo> read_thermal_zones();
float read_battery_temp();
std::vector<long> read_core_frequencies(int num_cores);

// Telemetry background sampler
class TelemetryCollector {
public:
    explicit TelemetryCollector(int num_cores, int sample_interval_ms = 100);
    ~TelemetryCollector();
    
    void start(uint64_t benchmark_start_ns);
    void stop();
    
    const std::vector<TelemetryPoint>& get_samples() const { return samples_; }
    float get_start_temp() const { return start_temp_; }
    float get_peak_temp() const { return peak_temp_; }
    float get_end_temp() const { return end_temp_; }
    float get_avg_temp() const { return avg_temp_; }
    bool is_throttling_detected() const { return throttling_detected_; }

private:
    void sample_loop();

    int num_cores_;
    int interval_ms_;
    uint64_t start_ns_ = 0;
    std::atomic<bool> running_{false};
    std::thread worker_thread_;
    std::vector<TelemetryPoint> samples_;
    
    float start_temp_ = 0.0f;
    float peak_temp_ = 0.0f;
    float end_temp_ = 0.0f;
    float avg_temp_ = 0.0f;
    bool throttling_detected_ = false;
};

} // namespace benchmark

#endif // BENCHMARK_HARNESS_H
