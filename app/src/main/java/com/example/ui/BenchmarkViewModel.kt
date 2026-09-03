package com.example.ui

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BenchmarkApplication
import com.example.benchmark.HardwareTopology
import com.example.benchmark.NativeBenchmarkBridge
import com.example.benchmark.ThermalSnapshot
import com.example.data.BenchmarkRepository
import com.example.data.BenchmarkRunEntity
import com.example.data.DriftStats
import com.example.service.BenchmarkExecutionService
import com.example.service.BenchmarkServiceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class BenchmarkTab {
    HARNESS,
    TOPOLOGY_THERMALS,
    RESULTS_DRIFT,
    DEVICE_OPTIMIZATIONS
}

enum class AffinityType(val label: String) {
    PRIME("Prime Core Only"),
    ALL("All Cores"),
    CUSTOM_MASK("Explicit Mask")
}

data class WorkloadDef(
    val key: String,
    val title: String,
    val subtitle: String,
    val description: String,
    val tag: String
)

class BenchmarkViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: BenchmarkRepository = (application as BenchmarkApplication).repository

    val allRuns: StateFlow<List<BenchmarkRunEntity>> = repository.allRuns
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val availableWorkloads = listOf(
        WorkloadDef(
            key = "matrix_physics",
            title = "Matrix & N-Body Physics",
            subtitle = "Dense FP64 MatMul + N-Body Gravity",
            description = "Evaluates floating-point throughput, register pressure, and L1/L2 cache hierarchy behavior using symplectic Verlet integration.",
            tag = "FP / SIMD"
        ),
        WorkloadDef(
            key = "chess",
            title = "Chess Search & Perft",
            subtitle = "0x88 Minimax & Perft Leaf Verification",
            description = "Branch-heavy and integer-heavy alpha-beta search. Rigorously verified against published perft counts (197,281 leaf nodes).",
            tag = "Branch / Int"
        ),
        WorkloadDef(
            key = "crypto",
            title = "Cryptographic Hashing",
            subtitle = "Fixed-Buffer SHA-256 Block Digest",
            description = "Measures cryptographic transformation rate and instruction pipeline saturation over a fixed 16 MB block buffer.",
            tag = "Crypto"
        ),
        WorkloadDef(
            key = "memory",
            title = "Memory Hierarchy & Latency",
            subtitle = "Streaming DRAM + Pointer-Chasing Latency",
            description = "Sequential bandwidth coupled with true Hamiltonian cycle pointer-chasing across L1/L2/L3/DRAM boundaries to expose prefetch limits.",
            tag = "Memory"
        ),
        WorkloadDef(
            key = "video",
            title = "Video Macroblock Transform",
            subtitle = "2D 8x8 DCT + Quantization + Zigzag",
            description = "Simulates real-world video encoding compute stages on raw macroblocks, stressing sustained thermal and instruction throughput.",
            tag = "Video / DSP"
        )
    )

    private val _selectedTab = MutableStateFlow(BenchmarkTab.HARNESS)
    val selectedTab: StateFlow<BenchmarkTab> = _selectedTab.asStateFlow()

    private val _selectedWorkloads = MutableStateFlow(setOf("matrix_physics", "chess", "crypto", "memory", "video"))
    val selectedWorkloads: StateFlow<Set<String>> = _selectedWorkloads.asStateFlow()

    private val _affinityMode = MutableStateFlow(AffinityType.PRIME)
    val affinityMode: StateFlow<AffinityType> = _affinityMode.asStateFlow()

    private val _customCpuMask = MutableStateFlow(0x80L) // Default core 7
    val customCpuMask: StateFlow<Long> = _customCpuMask.asStateFlow()

    private val _repetitions = MutableStateFlow(1)
    val repetitions: StateFlow<Int> = _repetitions.asStateFlow()

    private val _topology = MutableStateFlow<HardwareTopology?>(null)
    val topology: StateFlow<HardwareTopology?> = _topology.asStateFlow()

    private val _thermals = MutableStateFlow<ThermalSnapshot?>(null)
    val thermals: StateFlow<ThermalSnapshot?> = _thermals.asStateFlow()

    private val _serviceState = MutableStateFlow<BenchmarkServiceState>(BenchmarkServiceState.Idle)
    val serviceState: StateFlow<BenchmarkServiceState> = _serviceState.asStateFlow()

    private var benchmarkService: BenchmarkExecutionService? = null
    private var isBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as BenchmarkExecutionService.LocalBinder
            benchmarkService = binder.getService()
            isBound = true

            viewModelScope.launch {
                benchmarkService?.serviceState?.collect { state ->
                    _serviceState.value = state
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            benchmarkService = null
            isBound = false
        }
    }

    init {
        bindService()
        refreshHardwareInfo()
    }

    private fun bindService() {
        val intent = Intent(getApplication(), BenchmarkExecutionService::class.java)
        getApplication<Application>().startService(intent)
        getApplication<Application>().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    fun selectTab(tab: BenchmarkTab) {
        _selectedTab.value = tab
    }

    fun toggleWorkload(key: String) {
        val current = _selectedWorkloads.value.toMutableSet()
        if (current.contains(key)) {
            if (current.size > 1) { // Keep at least one
                current.remove(key)
            }
        } else {
            current.add(key)
        }
        _selectedWorkloads.value = current
    }

    fun selectAllWorkloads() {
        _selectedWorkloads.value = availableWorkloads.map { it.key }.toSet()
    }

    fun setAffinityMode(mode: AffinityType) {
        _affinityMode.value = mode
    }

    fun toggleCoreInMask(coreId: Int) {
        val mask = _customCpuMask.value
        val bit = 1L shl coreId
        val newMask = mask xor bit
        if (newMask != 0L) {
            _customCpuMask.value = newMask
        }
    }

    fun setRepetitions(count: Int) {
        _repetitions.value = count
    }

    fun refreshHardwareInfo() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val topoJson = NativeBenchmarkBridge.getHardwareTopology()
                val parsedTopo = HardwareTopology.fromJson(topoJson)
                _topology.value = parsedTopo

                // Automatically configure prime mask if not set
                if (parsedTopo.primeMask != 0L && _customCpuMask.value == 0x80L) {
                    _customCpuMask.value = parsedTopo.primeMask
                }

                val thermJson = NativeBenchmarkBridge.getLiveThermalZones()
                _thermals.value = ThermalSnapshot.fromJson(thermJson)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun startBenchmark() {
        val topo = _topology.value
        val mask = when (_affinityMode.value) {
            AffinityType.PRIME -> topo?.primeMask ?: 1L
            AffinityType.ALL -> topo?.allMask ?: 0xFFL
            AffinityType.CUSTOM_MASK -> _customCpuMask.value
        }

        val selectedDefs = availableWorkloads.filter { _selectedWorkloads.value.contains(it.key) }
            .map { it.key to it.title }

        val threads = when (_affinityMode.value) {
            AffinityType.ALL -> topo?.totalCores ?: 8
            else -> 1
        }

        benchmarkService?.startBenchmarkSuite(
            workloads = selectedDefs,
            affinityMode = _affinityMode.value.name,
            cpuMask = mask,
            threads = threads,
            repetitions = _repetitions.value
        )
    }

    fun cancelBenchmark() {
        benchmarkService?.cancelBenchmark()
    }

    fun deleteRun(id: Long) {
        viewModelScope.launch {
            repository.deleteRun(id)
        }
    }

    fun clearAllRuns() {
        viewModelScope.launch {
            repository.clearAll()
        }
    }

    fun generateCsvExport(runs: List<BenchmarkRunEntity>): String {
        val sb = StringBuilder()
        sb.append("ID,Timestamp,Device,Workload,Affinity,CPUMask,Threads,DurationMs,ThroughputOpsSec,Checksum,DeterministicMatch,StartTempC,PeakTempC,EndTempC,AvgTempC,ThrottlingDetected\n")
        for (r in runs) {
            sb.append("${r.id},${r.timestamp},\"${r.deviceModel}\",\"${r.workloadTitle}\",${r.affinityMode},0x${r.cpuMask.toString(16)},${r.threadCount},${r.durationMs},${r.throughputOpsSec},${r.determinismChecksum},${r.isDeterministicMatch},${r.startTempC},${r.peakTempC},${r.endTempC},${r.avgTempC},${r.throttlingDetected}\n")
        }
        return sb.toString()
    }

    override fun onCleared() {
        if (isBound) {
            getApplication<Application>().unbindService(serviceConnection)
            isBound = false
        }
        super.onCleared()
    }
}
