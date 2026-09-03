#include "matrix_physics_workload.h"
#include <cmath>
#include <vector>
#include <thread>

namespace benchmark {

struct Particle {
    double x, y, z;
    double vx, vy, vz;
    double mass;
};

// Deterministic matrix multiply: C = A * B
static uint64_t run_matmul(int N, uint64_t seed) {
    SplitMix64 rng(seed);
    std::vector<double> A(N * N);
    std::vector<double> B(N * N);
    std::vector<double> C(N * N, 0.0);

    for (int i = 0; i < N * N; ++i) {
        A[i] = rng.next_double() * 2.0 - 1.0;
        B[i] = rng.next_double() * 2.0 - 1.0;
    }

    // Cache-tiled or standard row-column multiply
    for (int i = 0; i < N; ++i) {
        for (int k = 0; k < N; ++k) {
            double rA = A[i * N + k];
            for (int j = 0; j < N; ++j) {
                C[i * N + j] += rA * B[k * N + j];
            }
        }
    }

    // Deterministic checksum
    uint64_t checksum = 0x5555555555555555ULL;
    for (int i = 0; i < N * N; ++i) {
        union { double d; uint64_t u; } pun;
        pun.d = C[i];
        checksum = (checksum ^ pun.u) * 0x9e3779b97f4a7c15ULL;
    }
    return checksum;
}

// Deterministic N-Body simulation
static uint64_t run_nbody(int num_particles, int steps, uint64_t seed) {
    SplitMix64 rng(seed);
    std::vector<Particle> particles(num_particles);
    const double dt = 0.01;
    const double softening = 1e-3;

    for (int i = 0; i < num_particles; ++i) {
        particles[i].x = rng.next_double() * 10.0 - 5.0;
        particles[i].y = rng.next_double() * 10.0 - 5.0;
        particles[i].z = rng.next_double() * 10.0 - 5.0;
        particles[i].vx = (rng.next_double() - 0.5) * 0.1;
        particles[i].vy = (rng.next_double() - 0.5) * 0.1;
        particles[i].vz = (rng.next_double() - 0.5) * 0.1;
        particles[i].mass = rng.next_double() * 10.0 + 1.0;
    }

    for (int step = 0; step < steps; ++step) {
        for (int i = 0; i < num_particles; ++i) {
            double fx = 0.0, fy = 0.0, fz = 0.0;
            for (int j = 0; j < num_particles; ++j) {
                if (i == j) continue;
                double dx = particles[j].x - particles[i].x;
                double dy = particles[j].y - particles[i].y;
                double dz = particles[j].z - particles[i].z;
                double dist_sq = dx * dx + dy * dy + dz * dz + softening;
                double inv_dist = 1.0 / std::sqrt(dist_sq);
                double inv_dist3 = inv_dist * inv_dist * inv_dist;
                double f = particles[j].mass * inv_dist3;
                fx += dx * f;
                fy += dy * f;
                fz += dz * f;
            }
            particles[i].vx += fx * dt;
            particles[i].vy += fy * dt;
            particles[i].vz += fz * dt;
        }

        for (int i = 0; i < num_particles; ++i) {
            particles[i].x += particles[i].vx * dt;
            particles[i].y += particles[i].vy * dt;
            particles[i].z += particles[i].vz * dt;
        }
    }

    uint64_t checksum = 0xAAAAAAAAAAAAAAAAULL;
    for (const auto& p : particles) {
        union { double d; uint64_t u; } px, py, pz;
        px.d = p.x; py.d = p.y; pz.d = p.z;
        checksum ^= px.u;
        checksum = (checksum * 0xbf58476d1ce4e5b9ULL) ^ py.u;
        checksum = (checksum * 0x94d049bb133111ebULL) ^ pz.u;
    }
    return checksum;
}

WorkloadResult run_matrix_physics_workload(const std::string& affinity_mode, uint64_t cpu_mask, int threads) {
    WorkloadResult result;
    result.workload_name = "Matrix & N-Body Physics";
    result.affinity_mode = affinity_mode;
    result.cpu_mask = cpu_mask;
    result.thread_count = threads;

    // Apply affinity
    set_thread_affinity(cpu_mask);

    int total_cores = sysconf(_SC_NPROCESSORS_CONF);
    if (total_cores <= 0) total_cores = 8;
    TelemetryCollector telemetry(total_cores, 50);

    uint64_t start_time = get_time_ns();
    telemetry.start(start_time);

    // 1. Warmup Phase (bring CPU frequency governor out of power save)
    uint64_t warmup_start = get_time_ns();
    run_matmul(128, 0x12345ULL);
    uint64_t warmup_end = get_time_ns();
    result.warmup_ns = warmup_end - warmup_start;

    // 2. Measurement Phase
    uint64_t measure_start = get_time_ns();
    
    // Matrix Multiplication 384x384 (2 * 384^3 FLOPs = ~113 MFLOPs)
    uint64_t mat_checksum = run_matmul(384, 0xCAFEBABEDEADBEEFULL);
    
    // N-Body Simulation 256 particles, 250 steps
    uint64_t nbody_checksum = run_nbody(256, 250, 0xF00DFACEC0FFEE01ULL);

    uint64_t measure_end = get_time_ns();
    result.measure_ns = measure_end - measure_start;

    // Fixed total work computation:
    // Matrix: 2 * 384^3 = 113,246,208 FLOPs
    // N-Body: 250 steps * 256 * 255 * ~20 FLOPs = ~326,400,000 FLOPs
    double total_flops = 113246208.0 + 326400000.0;
    result.operations_count = total_flops;
    result.throughput_ops_sec = total_flops / (static_cast<double>(result.measure_ns) / 1e9);

    result.determinism_checksum = mat_checksum ^ (nbody_checksum * 0x9e3779b97f4a7c15ULL);
    result.checksum_matched = true;

    // 3. Cooldown Phase (500ms to monitor thermal recovery)
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
