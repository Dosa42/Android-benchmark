cmake_minimum_required(VERSION 3.22.1)
project("benchmark_native")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Enforce build determinism: -O2, disable fast-math drift
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O2 -fno-fast-math -fno-omit-frame-pointer -Wall")

add_library(
    benchmark_native
    SHARED
    benchmark_jni.cpp
    harness.cpp
    workloads/matrix_physics_workload.cpp
    workloads/chess_workload.cpp
    workloads/crypto_workload.cpp
    workloads/memory_workload.cpp
    workloads/video_workload.cpp
)

find_library(log-lib log)

target_link_libraries(
    benchmark_native
    ${log-lib}
)
