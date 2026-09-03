package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.BenchmarkViewModel
import com.example.ui.components.PrecisionCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.JadeGreen
import com.example.ui.theme.RedAlert
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun OptimizationsScreen(
    viewModel: BenchmarkViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val powerManager = remember { context.getSystemService(Context.POWER_SERVICE) as? PowerManager }
    val isIgnoringBatteryOptimizations = powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))
            // Battery Optimization Status
            PrecisionCard(
                modifier = Modifier.fillMaxWidth(),
                borderColor = if (isIgnoringBatteryOptimizations) JadeGreen else AmberWarning
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "BATTERY OPTIMIZATION (DOZE)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyanAccent,
                            letterSpacing = 1.sp
                        )
                        StatusPill(
                            text = if (isIgnoringBatteryOptimizations) "UNRESTRICTED" else "OPTIMIZED (RESTRICTED)",
                            color = if (isIgnoringBatteryOptimizations) JadeGreen else AmberWarning
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Android Doze mode and manufacturer battery savers may forcibly lower CPU frequencies or kill native background threads during long benchmark runs.",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            try {
                                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                context.startActivity(intent)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Slate800,
                            contentColor = CyanAccent
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("request_battery_opt_button")
                    ) {
                        Icon(Icons.Default.BatteryAlert, contentDescription = "Battery", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isIgnoringBatteryOptimizations) "Re-check Battery Exemption" else "Request Unrestricted Exemption",
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        item {
            // Samsung Game Booster / GOS & Adaptive Performance
            PrecisionCard(
                modifier = Modifier.fillMaxWidth(),
                borderColor = Slate700
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = "Samsung Info", tint = AmberWarning, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "SAMSUNG GAME BOOSTER & GOS",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = AmberWarning,
                            letterSpacing = 1.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "On Samsung Galaxy devices (Exynos & Snapdragon), Game Optimizing Service (GOS) and Game Booster monitor app package categories and dynamically adjust thermal governor limits:\n\n" +
                                "• Alternate Game Performance Management: In Game Booster Settings > Labs, toggling this on/off produces measurable differences in sustained CPU thermal thresholds.\n" +
                                "• Recommendation: Record benchmarks in both states as distinct experimental datasets to quantify vendor throttling interference.\n" +
                                "• Thermal Guardian (Good Lock): Adjusting the CPU threshold slider directly shifts the trip points visible in the sysfs thermal zones.",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        lineHeight = 17.sp
                    )
                }
            }
        }

        item {
            // Protocol for Strict Determinism
            PrecisionCard(
                modifier = Modifier.fillMaxWidth(),
                borderColor = Slate700
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, contentDescription = "Protocol", tint = CyanAccent, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "REPRODUCIBLE TEST PROTOCOL",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyanAccent,
                            letterSpacing = 1.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "1. Cold-Start Temperature: Ensure initial SoC temperature is within 22°C - 26°C before starting consecutive measurement runs.\n\n" +
                                "2. Ambient Thermal Dissipation: Place device horizontally on a non-conductive surface (wood/plastic). Avoid handheld operation, which acts as an inconsistent heat conductor.\n\n" +
                                "3. Radio Isolation: Turn on Airplane Mode to eliminate cellular radio wakeups and network thread preemption.\n\n" +
                                "4. Fixed Clocks / Single Core Affinity: For maximum reproducibility, select 'Prime Core Only'. This isolates inter-core cache-coherency noise and scheduling jitter.",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        lineHeight = 17.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
