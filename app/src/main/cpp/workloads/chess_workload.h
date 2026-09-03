#ifndef BENCHMARK_CHESS_WORKLOAD_H
#define BENCHMARK_CHESS_WORKLOAD_H

#include "../harness.h"

namespace benchmark {

WorkloadResult run_chess_workload(const std::string& affinity_mode, uint64_t cpu_mask, int threads);

} // namespace benchmark

#endif // BENCHMARK_CHESS_WORKLOAD_H
