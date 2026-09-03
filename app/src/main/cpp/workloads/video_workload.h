#ifndef BENCHMARK_VIDEO_WORKLOAD_H
#define BENCHMARK_VIDEO_WORKLOAD_H

#include "../harness.h"

namespace benchmark {

WorkloadResult run_video_workload(const std::string& affinity_mode, uint64_t cpu_mask, int threads);

} // namespace benchmark

#endif // BENCHMARK_VIDEO_WORKLOAD_H
