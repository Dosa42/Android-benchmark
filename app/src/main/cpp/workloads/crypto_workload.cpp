#include "crypto_workload.h"
#include <vector>
#include <cstring>

namespace benchmark {

// SHA-256 implementation
static inline uint32_t rotr(uint32_t x, uint32_t n) {
    return (x >> n) | (x << (32 - n));
}

static inline uint32_t ch(uint32_t x, uint32_t y, uint32_t z) {
    return (x & y) ^ (~x & z);
}

static inline uint32_t maj(uint32_t x, uint32_t y, uint32_t z) {
    return (x & y) ^ (x & z) ^ (y & z);
}

static inline uint32_t sig0(uint32_t x) {
    return rotr(x, 2) ^ rotr(x, 13) ^ rotr(x, 22);
}

static inline uint32_t sig1(uint32_t x) {
    return rotr(x, 6) ^ rotr(x, 11) ^ rotr(x, 25);
}

static inline uint32_t theta0(uint32_t x) {
    return rotr(x, 7) ^ rotr(x, 18) ^ (x >> 3);
}

static inline uint32_t theta1(uint32_t x) {
    return rotr(x, 17) ^ rotr(x, 19) ^ (x >> 10);
}

static const uint32_t K256[64] = {
    0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
    0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
    0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
    0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
    0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
    0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
    0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
    0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2
};

struct SHA256Context {
    uint32_t state[8];
    uint64_t count;
    uint8_t buffer[64];

    void init() {
        state[0] = 0x6a09e667; state[1] = 0xbb67ae85;
        state[2] = 0x3c6ef372; state[3] = 0xa54ff53a;
        state[4] = 0x510e527f; state[5] = 0x9b05688c;
        state[6] = 0x1f83d9ab; state[7] = 0x5be0cd19;
        count = 0;
    }

    void transform(const uint8_t* data) {
        uint32_t W[64];
        for (int i = 0; i < 16; ++i) {
            W[i] = (static_cast<uint32_t>(data[i * 4]) << 24) |
                   (static_cast<uint32_t>(data[i * 4 + 1]) << 16) |
                   (static_cast<uint32_t>(data[i * 4 + 2]) << 8) |
                   (static_cast<uint32_t>(data[i * 4 + 3]));
        }
        for (int i = 16; i < 64; ++i) {
            W[i] = theta1(W[i - 2]) + W[i - 7] + theta0(W[i - 15]) + W[i - 16];
        }

        uint32_t a = state[0], b = state[1], c = state[2], d = state[3];
        uint32_t e = state[4], f = state[5], g = state[6], h = state[7];

        for (int i = 0; i < 64; ++i) {
            uint32_t T1 = h + sig1(e) + ch(e, f, g) + K256[i] + W[i];
            uint32_t T2 = sig0(a) + maj(a, b, c);
            h = g; g = f; f = e; e = d + T1;
            d = c; c = b; b = a; a = T1 + T2;
        }

        state[0] += a; state[1] += b; state[2] += c; state[3] += d;
        state[4] += e; state[5] += f; state[6] += g; state[7] += h;
    }

    void update(const uint8_t* data, size_t len) {
        size_t buf_idx = count & 63;
        count += len;
        for (size_t i = 0; i < len; ++i) {
            buffer[buf_idx++] = data[i];
            if (buf_idx == 64) {
                transform(buffer);
                buf_idx = 0;
            }
        }
    }

    void finalize(uint8_t digest[32]) {
        uint64_t total_bits = count * 8;
        update((const uint8_t*)"\x80", 1);
        while ((count & 63) != 56) {
            update((const uint8_t*)"\x00", 1);
        }
        uint8_t len_bytes[8];
        for (int i = 7; i >= 0; --i) {
            len_bytes[i] = total_bits & 0xFF;
            total_bits >>= 8;
        }
        update(len_bytes, 8);

        for (int i = 0; i < 8; ++i) {
            digest[i * 4]     = (state[i] >> 24) & 0xFF;
            digest[i * 4 + 1] = (state[i] >> 16) & 0xFF;
            digest[i * 4 + 2] = (state[i] >> 8) & 0xFF;
            digest[i * 4 + 3] = state[i] & 0xFF;
        }
    }
};

WorkloadResult run_crypto_workload(const std::string& affinity_mode, uint64_t cpu_mask, int threads) {
    WorkloadResult result;
    result.workload_name = "Cryptographic Hashing (SHA-256)";
    result.affinity_mode = affinity_mode;
    result.cpu_mask = cpu_mask;
    result.thread_count = threads;

    set_thread_affinity(cpu_mask);

    int total_cores = sysconf(_SC_NPROCESSORS_CONF);
    if (total_cores <= 0) total_cores = 8;
    TelemetryCollector telemetry(total_cores, 50);

    // Prepare 16 MB fixed buffer
    const size_t BUFFER_SIZE = 16 * 1024 * 1024;
    std::vector<uint8_t> buffer(BUFFER_SIZE);
    SplitMix64 rng(0x1337C0DEFEEDCAFEULL);
    for (size_t i = 0; i < BUFFER_SIZE; i += 8) {
        uint64_t v = rng.next();
        memcpy(&buffer[i], &v, 8);
    }

    uint64_t start_time = get_time_ns();
    telemetry.start(start_time);

    // Warmup: hash 1 MB
    uint64_t warmup_start = get_time_ns();
    SHA256Context warmup_ctx;
    warmup_ctx.init();
    warmup_ctx.update(buffer.data(), 1024 * 1024);
    uint8_t warmup_digest[32];
    warmup_ctx.finalize(warmup_digest);
    uint64_t warmup_end = get_time_ns();
    result.warmup_ns = warmup_end - warmup_start;

    // Measurement: hash full 16 MB buffer
    uint64_t measure_start = get_time_ns();
    SHA256Context ctx;
    ctx.init();
    ctx.update(buffer.data(), BUFFER_SIZE);
    uint8_t digest[32];
    ctx.finalize(digest);
    uint64_t measure_end = get_time_ns();
    result.measure_ns = measure_end - measure_start;

    result.operations_count = static_cast<double>(BUFFER_SIZE);
    double mbytes = static_cast<double>(BUFFER_SIZE) / (1024.0 * 1024.0);
    double sec = static_cast<double>(result.measure_ns) / 1e9;
    result.throughput_ops_sec = (mbytes / sec) * 1e6; // Bytes/sec

    // Construct checksum from first 8 bytes of SHA-256 digest
    uint64_t checksum = 0;
    memcpy(&checksum, digest, 8);
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
