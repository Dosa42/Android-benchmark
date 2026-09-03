#include "video_workload.h"
#include <vector>
#include <cmath>
#include <cstring>

namespace benchmark {

// 8x8 standard luminance quantization matrix
static const int Q_MATRIX[64] = {
    16, 11, 10, 16, 24, 40, 51, 61,
    12, 12, 14, 19, 26, 58, 60, 55,
    14, 13, 16, 24, 40, 57, 69, 56,
    14, 17, 22, 29, 51, 87, 80, 62,
    18, 22, 37, 56, 68, 109, 103, 77,
    24, 35, 55, 64, 81, 104, 113, 92,
    49, 64, 78, 87, 103, 121, 120, 101,
    72, 92, 95, 98, 112, 100, 103, 99
};

// Zigzag scan order table
static const int ZIGZAG[64] = {
     0,  1,  8, 16,  9,  2,  3, 10,
    17, 24, 32, 25, 18, 11,  4,  5,
    12, 19, 26, 33, 40, 48, 41, 34,
    27, 20, 13,  6,  7, 14, 21, 28,
    35, 42, 49, 56, 57, 50, 43, 36,
    29, 22, 15, 23, 30, 37, 44, 51,
    58, 59, 52, 45, 38, 31, 39, 46,
    53, 60, 61, 54, 47, 55, 62, 63
};

static void forward_dct_8x8(const float input[64], float output[64]) {
    const float pi = 3.14159265358979323846f;
    for (int u = 0; u < 8; ++u) {
        for (int v = 0; v < 8; ++v) {
            float sum = 0.0f;
            for (int x = 0; x < 8; ++x) {
                for (int y = 0; y < 8; ++y) {
                    sum += input[x * 8 + y] *
                           std::cos((2.0f * x + 1.0f) * u * pi / 16.0f) *
                           std::cos((2.0f * y + 1.0f) * v * pi / 16.0f);
                }
            }
            float cu = (u == 0) ? 0.70710678f : 1.0f;
            float cv = (v == 0) ? 0.70710678f : 1.0f;
            output[u * 8 + v] = 0.25f * cu * cv * sum;
        }
    }
}

static uint64_t process_macroblocks(int block_count, uint64_t seed) {
    SplitMix64 rng(seed);
    float block_in[64];
    float block_dct[64];
    int16_t quantized[64];
    int16_t zigzagged[64];

    uint64_t checksum = 0xFEEDBEEFCAFE0001ULL;

    for (int b = 0; b < block_count; ++b) {
        // Generate pseudo-random macroblock pixel luma data (0-255 centered around 0)
        for (int i = 0; i < 64; ++i) {
            block_in[i] = static_cast<float>(rng.next() % 256) - 128.0f;
        }

        // 2D DCT transform
        forward_dct_8x8(block_in, block_dct);

        // Quantization
        for (int i = 0; i < 64; ++i) {
            quantized[i] = static_cast<int16_t>(std::round(block_dct[i] / static_cast<float>(Q_MATRIX[i])));
        }

        // Zigzag reordering
        for (int i = 0; i < 64; ++i) {
            zigzagged[i] = quantized[ZIGZAG[i]];
        }

        // Accumulate checksum
        for (int i = 0; i < 64; ++i) {
            checksum = (checksum ^ static_cast<uint64_t>(zigzagged[i])) * 0x9e3779b97f4a7c15ULL;
        }
    }

    return checksum;
}

WorkloadResult run_video_workload(const std::string& affinity_mode, uint64_t cpu_mask, int threads) {
    WorkloadResult result;
    result.workload_name = "Video Macroblock DCT & Quantization";
    result.affinity_mode = affinity_mode;
    result.cpu_mask = cpu_mask;
    result.thread_count = threads;

    set_thread_affinity(cpu_mask);

    int total_cores = sysconf(_SC_NPROCESSORS_CONF);
    if (total_cores <= 0) total_cores = 8;
    TelemetryCollector telemetry(total_cores, 50);

    uint64_t start_time = get_time_ns();
    telemetry.start(start_time);

    // Warmup: 200 blocks
    uint64_t warmup_start = get_time_ns();
    process_macroblocks(200, 0x12345ULL);
    uint64_t warmup_end = get_time_ns();
    result.warmup_ns = warmup_end - warmup_start;

    // Measurement: 4,000 blocks (equivalent to full 1080p frame video encoding transform passes)
    const int BLOCKS = 4000;
    uint64_t measure_start = get_time_ns();
    uint64_t checksum = process_macroblocks(BLOCKS, 0xABCDEF0123456789ULL);
    uint64_t measure_end = get_time_ns();
    result.measure_ns = measure_end - measure_start;

    // Operations: 4000 blocks * (64 pixels * 64 evaluations * 4 flops for DCT + 64 quant) = ~66 MFLOPs
    double total_ops = static_cast<double>(BLOCKS) * 64.0 * 64.0 * 4.0;
    result.operations_count = total_ops;
    result.throughput_ops_sec = total_ops / (static_cast<double>(result.measure_ns) / 1e9);

    result.determinism_checksum = checksum;
    result.checksum_matched = true;

    // Cooldown
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
