#ifndef BENCHMARK_MATRIX_PHYSICS_WORKLOAD_H
#define BENCHMARK_MATRIX_PHYSICS_WORKLOAD_H

#include "../harness.h"

namespace benchmark {

WorkloadResult run_matrix_physics_workload(const std::string& affinity_mode, uint64_t cpu_mask, int threads);

} // namespace benchmark

#endif // BENCHMARK_MATRIX_PHYSICS_WORKLOAD_H
