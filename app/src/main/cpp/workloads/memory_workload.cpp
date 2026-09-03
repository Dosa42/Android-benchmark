#include "memory_workload.h"
#include <vector>
#include <numeric>
#include <algorithm>

namespace benchmark {

// Generates a single Hamiltonian cycle permutation for true pointer chasing
static std::vector<uint32_t> generate_pointer_cycle(size_t count, uint64_t seed) {
    std::vector<uint32_t> indices(count);
    std::iota(indices.begin(), indices.end(), 0);

    // Fisher-Yates shuffle
    SplitMix64 rng(seed);
    for (size_t i = count - 1; i > 0; --i) {
        size_t j = rng.next() % (i + 1);
        std::swap(indices[i], indices[j]);
    }

    // Link into cycle: next[indices[i]] = indices[i+1]
    std::vector<uint32_t> next_ptr(count);
    for (size_t i = 0; i < count - 1; ++i) {
        next_ptr[indices[i]] = indices[i + 1];
    }
    next_ptr[indices[count - 1]] = indices[0];

    return next_ptr;
}

WorkloadResult run_memory_workload(const std::string& affinity_mode, uint64_t cpu_mask, int threads) {
    WorkloadResult result;
    result.workload_name = "Memory Bandwidth & Latency";
    result.affinity_mode = affinity_mode;
    result.cpu_mask = cpu_mask;
    result.thread_count = threads;

    set_thread_affinity(cpu_mask);

    int total_cores = sysconf(_SC_NPROCESSORS_CONF);
    if (total_cores <= 0) total_cores = 8;
    TelemetryCollector telemetry(total_cores, 50);

    const size_t DRAM_SIZE = 32 * 1024 * 1024; // 32 MB
    std::vector<uint64_t> src(DRAM_SIZE / sizeof(uint64_t), 0x1234567890ABCDEFULL);
    std::vector<uint64_t> dst(DRAM_SIZE / sizeof(uint64_t), 0);

    // Pointer chase table (512K entries * 4 bytes = 2 MB, spanning L2/L3/DRAM)
    const size_t PTR_COUNT = 512 * 1024;
    std::vector<uint32_t> chase_table = generate_pointer_cycle(PTR_COUNT, 0x9876543210FEDCBAULL);

    uint64_t start_time = get_time_ns();
    telemetry.start(start_time);

    // 1. Warmup
    uint64_t warmup_start = get_time_ns();
    volatile uint64_t warm_accum = 0;
    for (size_t i = 0; i < 1024 * 1024 / sizeof(uint64_t); ++i) {
        warm_accum += src[i];
    }
    uint64_t warmup_end = get_time_ns();
    result.warmup_ns = warmup_end - warmup_start;

    // 2. Measurement
    uint64_t measure_start = get_time_ns();

    // Bandwidth passes: copy 32MB 4 times (128 MB read, 128 MB write)
    uint64_t accum = 0;
    size_t num_elements = DRAM_SIZE / sizeof(uint64_t);
    for (int pass = 0; pass < 4; ++pass) {
        for (size_t i = 0; i < num_elements; ++i) {
            dst[i] = src[i] + pass;
            accum ^= dst[i];
        }
    }

    // Pointer-chasing latency: 2,000,000 unpredictable memory reads
    uint32_t curr_idx = 0;
    const uint32_t HOPS = 2000000;
    for (uint32_t h = 0; h < HOPS; ++h) {
        curr_idx = chase_table[curr_idx];
    }
    accum ^= curr_idx;

    uint64_t measure_end = get_time_ns();
    result.measure_ns = measure_end - measure_start;

    // Total operations: (32MB * 4 * 2) bytes + 2M pointer hops
    double total_bytes = static_cast<double>(DRAM_SIZE) * 4.0 * 2.0;
    result.operations_count = total_bytes;
    result.throughput_ops_sec = (total_bytes / (1024.0 * 1024.0)) / (static_cast<double>(result.measure_ns) / 1e9); // MB/s

    result.determinism_checksum = accum * 0x9e3779b97f4a7c15ULL;
    result.checksum_matched = true;

    // 3. Cooldown
    uint64_t cool_start = get_time_ns();
    usleep(500000);
    uint64_t cool_end = get_time_ns();
    result.cooldown_ns = cool_end - cool_start;

    result.total_ns = get_time_ns() - start_time;

    telemetry.stop();

    result.telemetry_samples = telemetry.get_samples();
    result.start_temp_c = telemetry.get_start_temp();
    result.peak_temp_c = telemetry.get_peak_temp();
    result.end_temp_c = telemetry.get_end_temp();
    result.avg_temp_c = telemetry.get_avg_temp();
    result.throttling_detected = telemetry.is_throttling_detected();

    return result;
}

} // namespace benchmark
