package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import com.example.benchmark.CoreTopology
import com.example.benchmark.ThermalZone
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
import com.example.ui.theme.Slate850
import com.example.ui.theme.Slate900
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun TopologyScreen(
    viewModel: BenchmarkViewModel,
    modifier: Modifier = Modifier
) {
    val topology by viewModel.topology.collectAsState()
    val thermals by viewModel.thermals.collectAsState()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))
            // Overview summary card
            PrecisionCard(
                modifier = Modifier.fillMaxWidth(),
                borderColor = CyanAccent.copy(alpha = 0.4f)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "CPU CLUSTER TOPOLOGY",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyanAccent,
                            letterSpacing = 1.2.sp
                        )
                        Button(
                            onClick = { viewModel.refreshHardwareInfo() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Slate800,
                                contentColor = CyanAccent
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("refresh_hardware_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Refresh", fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MetricBadge(
                            label = "Total Cores",
                            value = "${topology?.totalCores ?: 8}",
                            color = TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        MetricBadge(
                            label = "Prime Cores",
                            value = "${topology?.primeCount ?: 1}",
                            color = CyanAccent,
                            modifier = Modifier.weight(1f)
                        )
                        MetricBadge(
                            label = "Mid Cores",
                            value = "${topology?.midCount ?: 0}",
                            color = AmberWarning,
                            modifier = Modifier.weight(1f)
                        )
                        MetricBadge(
                            label = "Little",
                            value = "${topology?.littleCount ?: 0}",
                            color = JadeGreen,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = "INDIVIDUAL CORE FREQUENCIES & ONLINE STATUS",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = CyanAccent,
                letterSpacing = 1.sp
            )
        }

        items(topology?.cores ?: emptyList()) { core ->
            CoreDetailCard(core = core)
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SYSFS THERMAL ZONES & SENSORS",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyanAccent,
                    letterSpacing = 1.sp
                )
                if ((thermals?.batteryTempC ?: -1f) > 0f) {
                    StatusPill(
                        text = "Battery: ${"%.1f".format(thermals?.batteryTempC)}°C",
                        color = JadeGreen
                    )
                }
            }
        }

        val zones = thermals?.zones ?: emptyList()
        if (zones.isEmpty()) {
            item {
                PrecisionCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Reading `/sys/class/thermal/thermal_zone*` sensors...",
                        modifier = Modifier.padding(16.dp),
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        } else {
            items(zones) { zone ->
                ThermalZoneCard(zone = zone)
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun CoreDetailCard(core: CoreTopology) {
    val clusterColor = when (core.clusterName) {
        "PRIME" -> CyanAccent
        "MID" -> AmberWarning
        "LITTLE" -> JadeGreen
        else -> Slate400
    }

    PrecisionCard(
        modifier = Modifier.fillMaxWidth(),
        borderColor = clusterColor.copy(alpha = 0.35f)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(clusterColor.copy(alpha = 0.15f))
                        .border(1.dp, clusterColor.copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${core.coreId}",
                        fontWeight = FontWeight.ExtraBold,
                        color = clusterColor,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 16.sp
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "CPU ${core.coreId}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = TextPrimary
                        )
                        StatusPill(
                            text = core.clusterName,
                            color = clusterColor
                        )
                        if (core.isOnline) {
                            StatusPill(text = "ONLINE", color = JadeGreen)
                        } else {
                            StatusPill(text = "OFFLINE", color = RedAlert)
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Max: ${formatFreq(core.maxFreqKhz)} • Min: ${formatFreq(core.minFreqKhz)}",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (core.curFreqKhz > 0) formatFreq(core.curFreqKhz) else formatFreq(core.maxFreqKhz),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = clusterColor
                )
                Text(
                    text = "Current Clock",
                    fontSize = 10.sp,
                    color = TextMuted
                )
            }
        }
    }
}

@Composable
fun ThermalZoneCard(zone: ThermalZone) {
    val tempColor = when {
        zone.tempC >= 70f -> RedAlert
        zone.tempC >= 55f -> AmberWarning
        else -> JadeGreen
    }

    PrecisionCard(
        modifier = Modifier.fillMaxWidth(),
        borderColor = Slate800
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Zone ${zone.id}: ${zone.type}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { (zone.tempC.coerceIn(20f, 90f) - 20f) / 70f },
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = tempColor,
                    trackColor = Slate800
                )
            }

            Text(
                text = "${"%.1f".format(zone.tempC)}°C",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = tempColor
            )
        }
    }
}

private fun formatFreq(khz: Long): String {
    return if (khz >= 1_000_000) {
        "%.2f GHz".format(khz / 1_000_000.0)
    } else if (khz > 0) {
        "${khz / 1000} MHz"
    } else {
        "N/A"
    }
}
