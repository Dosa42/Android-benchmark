package com.example.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BenchmarkRepository
import com.example.data.BenchmarkRunEntity
import com.example.data.DriftStats
import com.example.ui.BenchmarkViewModel
import com.example.ui.components.MetricBadge
import com.example.ui.components.PrecisionCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.JadeGreen
import com.example.ui.theme.RedAlert
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ResultsScreen(
    viewModel: BenchmarkViewModel,
    modifier: Modifier = Modifier
) {
    val runs by viewModel.allRuns.collectAsState()
    val context = LocalContext.current

    val groupedByWorkload = runs.groupBy { it.workloadTitle }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))
            // Export & Control Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "BENCHMARK RESULTS & DRIFT",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyanAccent,
                    letterSpacing = 1.2.sp
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (runs.isNotEmpty()) {
                        Button(
                            onClick = {
                                val csv = viewModel.generateCsvExport(runs)
                                shareText(context, "Benchmark_Results.csv", csv)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Slate800,
                                contentColor = CyanAccent
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("export_csv_button")
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Export CSV", modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Export CSV", fontSize = 12.sp)
                        }

                        Button(
                            onClick = { viewModel.clearAllRuns() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Slate800,
                                contentColor = RedAlert
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("clear_all_runs_button")
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Clear All", modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Clear", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        if (runs.isEmpty()) {
            item {
                PrecisionCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No Benchmark Records Yet",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Execute the suite from the 'Harness' tab to collect deterministic execution and thermal drift records.",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }
            }
        } else {
            // Statistical Drift Breakdown for each workload group
            items(groupedByWorkload.entries.toList()) { (title, workloadRuns) ->
                val drift = BenchmarkRepository.calculateDrift(workloadRuns)
                if (drift != null) {
                    DriftAnalysisCard(title = title, stats = drift, runs = workloadRuns)
                }
            }

            item {
                Text(
                    text = "INDIVIDUAL RUN TIMELINE (${runs.size} RUNS)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyanAccent,
                    letterSpacing = 1.sp
                )
            }

            items(runs) { run ->
                RunDetailCard(
                    run = run,
                    onDelete = { viewModel.deleteRun(run.id) },
                    onShare = {
                        shareText(context, "Run_${run.id}_telemetry.json", run.telemetryJson)
                    }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun DriftAnalysisCard(
    title: String,
    stats: DriftStats,
    runs: List<BenchmarkRunEntity>
) {
    val cvColor = when {
        stats.cvPercent < 1.0 -> JadeGreen
        stats.cvPercent < 3.0 -> CyanAccent
        stats.cvPercent < 7.0 -> AmberWarning
        else -> RedAlert
    }

    PrecisionCard(
        modifier = Modifier.fillMaxWidth(),
        borderColor = if (stats.isDeterminismPerfect) JadeGreen.copy(alpha = 0.5f) else AmberWarning
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp,
                    color = TextPrimary
                )
                StatusPill(
                    text = if (stats.isDeterminismPerfect) "100% DETERMINISTIC MATCH" else "CHECKSUM DRIFT DETECTED",
                    color = if (stats.isDeterminismPerfect) JadeGreen else AmberWarning
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricBadge(
                    label = "Mean Time",
                    value = "${"%.2f".format(stats.meanDurationMs)} ms",
                    color = CyanAccent,
                    modifier = Modifier.weight(1f)
                )
                MetricBadge(
                    label = "Std Dev (σ)",
                    value = "±${"%.2f".format(stats.stdDevDurationMs)} ms",
                    color = cvColor,
                    modifier = Modifier.weight(1f)
                )
                MetricBadge(
                    label = "CV %",
                    value = "${"%.2f".format(stats.cvPercent)}%",
                    color = cvColor,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Runs: ${stats.runCount} • Min: ${"%.2f".format(stats.minDurationMs)} ms • Max: ${"%.2f".format(stats.maxDurationMs)} ms • Spread: ${"%.2f".format(stats.maxDurationMs - stats.minDurationMs)} ms",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = TextSecondary
            )
        }
    }
}

@Composable
fun RunDetailCard(
    run: BenchmarkRunEntity,
    onDelete: () -> Unit,
    onShare: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(run.timestamp))

    PrecisionCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .testTag("run_item_${run.id}"),
        borderColor = if (run.throttlingDetected) RedAlert.copy(alpha = 0.5f) else Slate800
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = run.workloadTitle,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = TextPrimary
                    )
                    Text(
                        text = "$dateStr • Affinity: ${run.affinityMode}",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${"%.2f".format(run.durationMs)} ms",
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 15.sp,
                        color = CyanAccent
                    )
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Slate400, modifier = Modifier.size(16.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                StatusPill(
                    text = if (run.isDeterministicMatch) "CHECKSUM OK" else "MISMATCH",
                    color = if (run.isDeterministicMatch) JadeGreen else RedAlert
                )
                StatusPill(
                    text = "${"%.1f".format(run.startTempC)}°C -> ${"%.1f".format(run.peakTempC)}°C",
                    color = if (run.throttlingDetected) RedAlert else AmberWarning
                )
                if (run.throttlingDetected) {
                    StatusPill(text = "THROTTLED", color = RedAlert)
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    Text(
                        text = "DETAILED NATIVE TELEMETRY",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyanAccent
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Device: ${run.deviceModel} (${run.chipsetInfo})\n" +
                                "CPU Mask: 0x${run.cpuMask.toString(16).uppercase()} (Threads: ${run.threadCount})\n" +
                                "Throughput: ${"%.2f".format(run.throughputOpsSec)} ops/sec\n" +
                                "Checksum: ${run.determinismChecksum}\n" +
                                "Thermal: Start ${"%.1f".format(run.startTempC)}°C, Peak ${"%.1f".format(run.peakTempC)}°C, End ${"%.1f".format(run.endTempC)}°C, Avg ${"%.1f".format(run.avgTempC)}°C",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TextSecondary,
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onShare,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share Raw JSON", modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Share Raw JSON Telemetry", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

private fun shareText(context: Context, title: String, content: String) {
    val sendIntent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_TEXT, content)
        putExtra(Intent.EXTRA_TITLE, title)
        type = "text/plain"
    }
    val shareIntent = Intent.createChooser(sendIntent, "Export Benchmark Data")
    context.startActivity(shareIntent)
}
