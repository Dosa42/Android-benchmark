package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.BenchmarkApplication
import com.example.MainActivity
import com.example.R
import com.example.benchmark.NativeBenchmarkBridge
import com.example.benchmark.NativeBenchmarkResult
import com.example.data.BenchmarkRunEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class BenchmarkServiceState {
    object Idle : BenchmarkServiceState()
    data class Running(
        val workloadKey: String,
        val workloadTitle: String,
        val affinityMode: String,
        val currentIteration: Int,
        val totalIterations: Int,
        val statusMessage: String
    ) : BenchmarkServiceState()
    data class Completed(val lastResult: NativeBenchmarkResult) : BenchmarkServiceState()
    data class Failed(val reason: String) : BenchmarkServiceState()
}

class BenchmarkExecutionService : Service() {

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private var benchmarkJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private val _serviceState = MutableStateFlow<BenchmarkServiceState>(BenchmarkServiceState.Idle)
    val serviceState: StateFlow<BenchmarkServiceState> = _serviceState.asStateFlow()

    inner class LocalBinder : Binder() {
        fun getService(): BenchmarkExecutionService = this@BenchmarkExecutionService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "NativeBenchmark::ExecutionWakeLock"
            ).apply {
                setReferenceCounted(false)
            }
        }
        wakeLock?.acquire(24 * 60 * 60 * 1000L) // up to 24h as per spec "Individual tests may extend for several hours"
    }

    private fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
    }

    fun startBenchmarkSuite(
        workloads: List<Pair<String, String>>, // (key, title)
        affinityMode: String,
        cpuMask: Long,
        threads: Int,
        repetitions: Int
    ) {
        if (_serviceState.value is BenchmarkServiceState.Running) return

        acquireWakeLock()
        val notification = buildNotification("Preparing Deterministic Benchmark Suite...")
        startForeground(NOTIFICATION_ID, notification)

        benchmarkJob = serviceScope.launch {
            val app = application as BenchmarkApplication
            val repository = app.repository
            val deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}"
            val chipset = Build.HARDWARE

            try {
                var lastResult: NativeBenchmarkResult? = null

                for (rep in 1..repetitions) {
                    for ((workloadKey, workloadTitle) in workloads) {
                        _serviceState.value = BenchmarkServiceState.Running(
                            workloadKey = workloadKey,
                            workloadTitle = workloadTitle,
                            affinityMode = affinityMode,
                            currentIteration = rep,
                            totalIterations = repetitions,
                            statusMessage = "Executing: $workloadTitle (Run $rep of $repetitions)..."
                        )
                        updateNotification("Running $workloadTitle ($rep/$repetitions)")

                        // Native benchmark execution occurs completely uninterrupted on native thread
                        val jsonResult = withContext(Dispatchers.Default) {
                            NativeBenchmarkBridge.executeBenchmark(
                                workload = workloadKey,
                                affinityMode = affinityMode,
                                cpuMask = cpuMask,
                                threads = threads
                            )
                        }

                        val parsedResult = NativeBenchmarkResult.fromJson(jsonResult)
                        lastResult = parsedResult

                        // Store in local SQLite database via Room
                        val entity = BenchmarkRunEntity(
                            deviceModel = deviceModel,
                            chipsetInfo = chipset,
                            workloadKey = workloadKey,
                            workloadTitle = workloadTitle,
                            affinityMode = affinityMode,
                            cpuMask = cpuMask,
                            threadCount = threads,
                            durationMs = parsedResult.durationMs,
                            throughputOpsSec = parsedResult.throughputOpsSec,
                            determinismChecksum = parsedResult.determinismChecksum,
                            isDeterministicMatch = parsedResult.checksumMatched,
                            startTempC = parsedResult.startTempC,
                            peakTempC = parsedResult.peakTempC,
                            endTempC = parsedResult.endTempC,
                            avgTempC = parsedResult.avgTempC,
                            throttlingDetected = parsedResult.throttlingDetected,
                            telemetryJson = jsonResult
                        )
                        repository.insertRun(entity)
                    }
                }

                if (lastResult != null) {
                    _serviceState.value = BenchmarkServiceState.Completed(lastResult)
                } else {
                    _serviceState.value = BenchmarkServiceState.Idle
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _serviceState.value = BenchmarkServiceState.Failed(e.message ?: "Unknown error")
            } finally {
                releaseWakeLock()
                stopForeground(STOP_FOREGROUND_REMOVE)
            }
        }
    }

    fun cancelBenchmark() {
        benchmarkJob?.cancel()
        releaseWakeLock()
        _serviceState.value = BenchmarkServiceState.Idle
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Benchmark Execution Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors active native deterministic benchmark runs"
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Deterministic Benchmark Active")
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm?.notify(NOTIFICATION_ID, buildNotification(text))
    }

    override fun onDestroy() {
        releaseWakeLock()
        super.onDestroy()
    }

    companion object {
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "benchmark_service_channel"
    }
}
