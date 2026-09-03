package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.service.BenchmarkServiceState
import com.example.ui.AffinityType
import com.example.ui.BenchmarkViewModel
import com.example.ui.WorkloadDef
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
import com.example.ui.theme.Slate850
import com.example.ui.theme.Slate900
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HarnessScreen(
    viewModel: BenchmarkViewModel,
    modifier: Modifier = Modifier
) {
    val serviceState by viewModel.serviceState.collectAsState()
    val selectedWorkloads by viewModel.selectedWorkloads.collectAsState()
    val affinityMode by viewModel.affinityMode.collectAsState()
    val customMask by viewModel.customCpuMask.collectAsState()
    val repetitions by viewModel.repetitions.collectAsState()
    val topology by viewModel.topology.collectAsState()

    val isRunning = serviceState is BenchmarkServiceState.Running

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))
            // Active Execution Panel
            LiveExecutionCard(
                state = serviceState,
                onCancel = { viewModel.cancelBenchmark() }
            )
        }

        item {
            // Section Header: Workload Selection
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "WORKLOAD MODULES",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyanAccent,
                    letterSpacing = 1.2.sp
                )
                Text(
                    text = "Select All",
                    fontSize = 12.sp,
                    color = Slate400,
                    modifier = Modifier
                        .clickable(enabled = !isRunning) { viewModel.selectAllWorkloads() }
                        .padding(4.dp)
                        .testTag("select_all_workloads_button")
                )
            }
        }

        items(viewModel.availableWorkloads) { workload ->
            WorkloadSelectionCard(
                workload = workload,
                isSelected = selectedWorkloads.contains(workload.key),
                enabled = !isRunning,
                onToggle = { viewModel.toggleWorkload(workload.key) }
            )
        }

        item {
            // CPU Affinity & Thread Assignment
            PrecisionCard(
                modifier = Modifier.fillMaxWidth(),
                borderColor = Slate700
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "THREAD AFFINITY (sched_setaffinity)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyanAccent,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Pins native execution thread to exact hardware core cluster.",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AffinityType.values().forEach { type ->
                            FilterChip(
                                selected = affinityMode == type,
                                onClick = { if (!isRunning) viewModel.setAffinityMode(type) },
                                label = { Text(type.label, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = CyanAccent.copy(alpha = 0.2f),
                                    selectedLabelColor = CyanAccent
                                ),
                                modifier = Modifier.testTag("affinity_chip_${type.name}")
                            )
                        }
                    }

                    // Interactive Custom Mask Core Toggles
                    if (affinityMode == AffinityType.CUSTOM_MASK) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "ACTIVE CORE MASK: 0x${customMask.toString(16).uppercase().padStart(2, '0')}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = JadeGreen
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        val totalCores = topology?.totalCores ?: 8
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            for (coreId in 0 until totalCores) {
                                val isChecked = (customMask and (1L shl coreId)) != 0L
                                val coreInfo = topology?.cores?.find { it.coreId == coreId }
                                val clusterBadge = coreInfo?.clusterName ?: "CORE"

                                CoreToggleButton(
                                    coreId = coreId,
                                    clusterName = clusterBadge,
                                    isSelected = isChecked,
                                    enabled = !isRunning,
                                    onToggle = { viewModel.toggleCoreInMask(coreId) }
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            // Repetition Configuration for Drift Evaluation
            PrecisionCard(
                modifier = Modifier.fillMaxWidth(),
                borderColor = Slate700
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "REPETITIONS & DRIFT EVALUATION",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyanAccent,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Consecutive cycles measure thermal throttling curve & coefficient of variation.",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf(1, 3, 5, 10).forEach { count ->
                            FilterChip(
                                selected = repetitions == count,
                                onClick = { if (!isRunning) viewModel.setRepetitions(count) },
                                label = { Text("${count}x", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = CyanAccent.copy(alpha = 0.2f),
                                    selectedLabelColor = CyanAccent
                                ),
                                modifier = Modifier.testTag("repetition_chip_${count}x")
                            )
                        }
                    }
                }
            }
        }

        item {
            // Execution Trigger Button
            Button(
                onClick = {
                    if (isRunning) {
                        viewModel.cancelBenchmark()
                    } else {
                        viewModel.startBenchmark()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("benchmark_action_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRunning) RedAlert else CyanAccent,
                    contentColor = Slate900
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = if (isRunning) "Stop" else "Start",
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isRunning) "ABORT BENCHMARK SUITE" else "EXECUTE DETERMINISTIC SUITE",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun WorkloadSelectionCard(
    workload: WorkloadDef,
    isSelected: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit
) {
    PrecisionCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onToggle() }
            .testTag("workload_card_${workload.key}"),
        borderColor = if (isSelected) CyanAccent.copy(alpha = 0.5f) else Slate800
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { if (enabled) onToggle() },
                colors = CheckboxDefaults.colors(
                    checkedColor = CyanAccent,
                    uncheckedColor = Slate400,
                    checkmarkColor = Slate900
                ),
                modifier = Modifier.testTag("workload_checkbox_${workload.key}")
            )

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = workload.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = TextPrimary
                    )
                    StatusPill(
                        text = workload.tag,
                        color = if (isSelected) CyanAccent else Slate400
                    )
                }
                Text(
                    text = workload.subtitle,
                    fontSize = 12.sp,
                    color = CyanAccent.copy(alpha = 0.85f),
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(top = 2.dp)
                )
                Text(
                    text = workload.description,
                    fontSize = 11.sp,
                    color = TextSecondary,
                    lineHeight = 15.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
fun CoreToggleButton(
    coreId: Int,
    clusterName: String,
    isSelected: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit
) {
    val borderColor = if (isSelected) JadeGreen else Slate700
    val bgColor = if (isSelected) JadeGreen.copy(alpha = 0.15f) else Slate850

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(enabled = enabled) { onToggle() }
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag("core_toggle_$coreId")
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "CPU $coreId",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) JadeGreen else TextMuted
            )
            Text(
                text = clusterName,
                fontSize = 9.sp,
                color = if (isSelected) TextPrimary else TextMuted
            )
        }
    }
}

@Composable
fun LiveExecutionCard(
    state: BenchmarkServiceState,
    onCancel: () -> Unit
) {
    PrecisionCard(
        modifier = Modifier.fillMaxWidth(),
        borderColor = when (state) {
            is BenchmarkServiceState.Running -> CyanAccent
            is BenchmarkServiceState.Completed -> JadeGreen
            is BenchmarkServiceState.Failed -> RedAlert
            else -> Slate800
        }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(
                                when (state) {
                                    is BenchmarkServiceState.Running -> AmberWarning
                                    is BenchmarkServiceState.Completed -> JadeGreen
                                    is BenchmarkServiceState.Failed -> RedAlert
                                    else -> Slate400
                                }
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when (state) {
                            is BenchmarkServiceState.Running -> "EXECUTION IN PROGRESS"
                            is BenchmarkServiceState.Completed -> "SUITE COMPLETE"
                            is BenchmarkServiceState.Failed -> "SUITE FAILED"
                            else -> "HARNESS READY"
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        letterSpacing = 1.sp
                    )
                }

                if (state is BenchmarkServiceState.Running) {
                    StatusPill(
                        text = "Iter ${state.currentIteration}/${state.totalIterations}",
                        color = AmberWarning
                    )
                }
            }

            when (state) {
                is BenchmarkServiceState.Running -> {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = state.statusMessage,
                        fontSize = 13.sp,
                        color = CyanAccent,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = CyanAccent,
                        trackColor = Slate800
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Affinity: ${state.affinityMode} • Real-time sysfs polling active (50ms)",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
                is BenchmarkServiceState.Completed -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Last: ${state.lastResult.workload} in ${"%.2f".format(state.lastResult.durationMs)} ms",
                        fontSize = 13.sp,
                        color = JadeGreen,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Deterministic Checksum: ${state.lastResult.determinismChecksum} (Match: ${state.lastResult.checksumMatched})",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TextSecondary
                    )
                }
                is BenchmarkServiceState.Failed -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Error: ${state.reason}",
                        fontSize = 12.sp,
                        color = RedAlert
                    )
                }
                is BenchmarkServiceState.Idle -> {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Configured for zero JIT interference. Results are written directly to local SQLite database with full time-series telemetry.",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}
