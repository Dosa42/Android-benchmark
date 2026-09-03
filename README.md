# Native Android Deterministic Benchmark Suite ⚡

A high-precision, low-level Android benchmarking application engineered in **Modern C++17 (NDK)** and **Jetpack Compose (Material 3)**.

Designed for hardware architects, performance engineers, and enthusiasts who require mathematically deterministic, jitter-free performance telemetry across heterogeneous ARM big.LITTLE / DynamIQ CPU topologies.

---

## 🎯 Key Architectural Capabilities

- **Native C++ Execution Pipeline**:
  - Isolated background thread execution in native code (`libbenchmark_native.so`) bypassing JVM GC pauses and runtime JIT de-optimizations.
  - Nanosecond-precision timing via POSIX `clock_gettime(CLOCK_MONOTONIC_RAW)`.
  - True CPU core affinity pinning via Linux `sched_setaffinity()` (Prime / Big, Mid, Little, or custom bitmasks).
  - Bit-for-bit mathematical determinism using fixed-seed PRNGs and `-fno-fast-math`.
- **Comprehensive Deterministic Workloads**:
  1. **Matrix Multiplication & N-Body Gravitational Dynamics** (FP64 symplectic Verlet integration).
  2. **Chess Move Generation & Perft Leaf-Node Traversal** (0x88 branch-heavy tree exploration).
  3. **SHA-256 Cryptographic Block Hashing** (16 MB buffer digest).
  4. **Memory Hierarchy & Latency Pointer Chasing** (Sequential DRAM bandwidth vs. prefetcher-bypassing cyclic graphs).
  5. **Video Discrete Cosine Transform (DCT)** (2D 8x8 macroblock transformations & zigzag reordering).
- **Continuous Sysfs Hardware Telemetry**:
  - Direct real-time polling of `/sys/class/thermal/thermal_zone*` and `/sys/devices/system/cpu/cpu*/cpufreq/cpuinfo_cur_freq`.
  - Automated throttling detection and thermal drift analysis (Mean, Standard Deviation $\sigma$, and CV%).
- **System Interruption Mitigation**:
  - Foreground Service with CPU `PARTIAL_WAKE_LOCK` for non-preemptible sustained runs.
  - One-tap Android Doze mode unrestricted battery exemption request.
  - Vendor governor mitigation guide (Samsung Game Booster / GOS & Thermal Guardian).

---

## 🤖 Automated GitHub Actions Dynamic APK Builder

This repository comes pre-configured with a **fully automated, dynamic GitHub Actions CI/CD workflow** (`.github/workflows/build-apk.yml`).

### 🔄 What Triggers the Workflow?
1. **Push to branches**: Any push to `main`, `master`, `release/**`, or `feat/**`.
2. **Pull Requests**: Any PR targeting `main` or `master`.
3. **Git Release Tags**: Pushing a tag like `v1.0.0` automatically compiles the APK and attaches it directly to the GitHub Release.
4. **Manual Trigger (`workflow_dispatch`)**: Run on demand from the **Actions** tab on GitHub with custom parameters.

### ⚙️ How the Dynamic Pipeline Works
- **Integrity Validation**: Verifies that Gradle, NDK build definitions (`CMakeLists.txt`), and Kotlin dependencies are intact.
- **Dynamic Provisioning**:
  - Automatically provisions **JDK 17 (Temurin)** and configures the Android SDK.
  - Automatically provisions **Android NDK (`26.1.10909125`)** and **CMake (`3.22.1`)** via `sdkmanager` if not pre-installed on the runner, caching them for fast subsequent runs.
- **Dynamic Keystore & Secrets Recovery**:
  - Detects and decodes `debug.keystore.base64` into a valid `debug.keystore`.
  - Automatically falls back to generating a fresh Android debug keystore with `keytool` if absent.
  - Proactively creates `.env` from `.env.example` to satisfy Secrets Gradle Plugin requirements.
- **Parallel Compilation & Packaging**:
  - Compiles the native C++ library for target ABIs (`arm64-v8a`, `x86_64`).
  - Assembles the debug APK (`./gradlew assembleDebug`).
  - Calculates file size and SHA-256 checksum for binary integrity verification.
- **Artifact Publishing**:
  - Uploads the resulting `.apk` to GitHub Actions Artifacts (retained for 30 days).
  - Posts a formatted GitHub Step Summary table containing download instructions and hash checksums.

---

## 📥 How to Download the Built APK from GitHub

1. Push your changes to your GitHub repository:
   ```bash
   git add .
   git commit -m "Add benchmark suite & dynamic workflow"
   git push origin main
   ```
2. Navigate to your repository on GitHub and click the **Actions** tab.
3. Select the latest workflow run: **Build Android Debug APK**.
4. Scroll down to the **Artifacts** section at the bottom of the Summary page.
5. Download **`debug-apk-run-<number>`**.
6. Unzip the downloaded archive to extract the `.apk` file.
7. Install the APK on your device:
   ```bash
   adb install -r NativeBenchmark-debug-*.apk
   ```

---

## 💻 Building Locally

If building locally with Gradle:

```bash
# Decode debug keystore if building on a clean clone
base64 -d debug.keystore.base64 > debug.keystore

# Build Debug APK
./gradlew assembleDebug

# Run Tests
./gradlew testDebugUnitTest
```
