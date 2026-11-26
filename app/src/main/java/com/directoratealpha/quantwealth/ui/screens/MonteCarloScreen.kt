package com.directoratealpha.quantwealth.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.directoratealpha.quantwealth.engine.QuantEngine
import com.directoratealpha.quantwealth.model.MonteCarloResult
import com.directoratealpha.quantwealth.ui.components.MonteCarloFanChart
import com.directoratealpha.quantwealth.ui.components.UnlockFeatureDialog
import com.directoratealpha.quantwealth.ui.theme.AppColors
import com.directoratealpha.quantwealth.ui.theme.Formatters
import com.directoratealpha.quantwealth.util.ShareCardGenerator
import java.util.Locale
import kotlin.math.roundToInt

private data class McPreset(val label: String, val muPct: Float, val volPct: Float)

@Composable
fun MonteCarloScreen(
    isPro: Boolean,
    onRequestFullMonteCarloAd: (onUnlocked: () -> Unit) -> Unit,
    onOpenProDialog: () -> Unit
) {
    val context = LocalContext.current
    val presets = remember {
        listOf(
            McPreset("S&P 500 (SPY)", 10.5f, 16f),
            McPreset("Nasdaq (QQQ)", 14.0f, 22f),
            McPreset("60/40 Balanced", 8.2f, 11f),
            McPreset("Gold (XAU)", 7.8f, 14f),
            McPreset("High Growth / Crypto", 22.0f, 45f)
        )
    }

    var selectedPresetLabel by remember { mutableStateOf(presets.first().label) }
    var initialCapital by remember { mutableStateOf(20000f) }
    var monthlyDca by remember { mutableStateOf(750f) }
    var years by remember { mutableStateOf(15) }
    var annualReturnPct by remember { mutableStateOf(10.5f) }
    var volatilityPct by remember { mutableStateOf(16f) }
    var inflationAdjusted by remember { mutableStateOf(true) }
    var fireTargetUsd by remember { mutableStateOf(500000f) }

    var fullRunUnlockedOnce by remember { mutableStateOf(false) }
    var showUnlockDialog by remember { mutableStateOf(false) }

    var result by remember {
        mutableStateOf(
            QuantEngine.runMonteCarlo(
                assetLabel = selectedPresetLabel,
                simulationsCount = if (isPro) 1000 else 100,
                years = years,
                initialCapital = initialCapital.toDouble(),
                monthlyDca = monthlyDca.toDouble(),
                annualReturnPct = annualReturnPct.toDouble(),
                volatilityPct = volatilityPct.toDouble(),
                inflationAdjusted = inflationAdjusted,
                fireTargetUsd = fireTargetUsd.toDouble()
            )
        )
    }

    fun runSim(simCount: Int) {
        result = QuantEngine.runMonteCarlo(
            assetLabel = selectedPresetLabel,
            simulationsCount = simCount,
            years = years,
            initialCapital = initialCapital.toDouble(),
            monthlyDca = monthlyDca.toDouble(),
            annualReturnPct = annualReturnPct.toDouble(),
            volatilityPct = volatilityPct.toDouble(),
            inflationAdjusted = inflationAdjusted,
            fireTargetUsd = fireTargetUsd.toDouble()
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column {
                Text(
                    text = "Monte Carlo & FIRE Lab",
                    color = AppColors.TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "Stochastic wealth projection (P10 / P50 / P90) with real inflation & 4% FIRE rule",
                    color = AppColors.TextSecondary,
                    fontSize = 11.5.sp
                )
            }
        }

        // Preset Profiles
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                presets.forEach { preset ->
                    val active = selectedPresetLabel == preset.label
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (active) AppColors.AccentPurple.copy(alpha = 0.24f) else AppColors.CardDark,
                        border = BorderStroke(1.dp, if (active) AppColors.AccentPurple else AppColors.CardBorder),
                        modifier = Modifier.clickable {
                            selectedPresetLabel = preset.label
                            annualReturnPct = preset.muPct
                            volatilityPct = preset.volPct
                            runSim(if (isPro || fullRunUnlockedOnce) 1000 else 100)
                        }
                    ) {
                        Text(
                            text = preset.label,
                            color = if (active) AppColors.AccentPurple else AppColors.TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp)
                        )
                    }
                }
            }
        }

        // Chart & Outcomes Card
        item {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = AppColors.CardDark,
                border = BorderStroke(1.dp, AppColors.AccentPurple.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "${result.simulationsCount} RUNS • ${result.years}Y ${if (result.inflationAdjusted) "(REAL $)" else "(NOMINAL)"}",
                                color = AppColors.AccentPurple,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "Median: ${Formatters.formatCompactUsd(result.medianFinalValue)}",
                                color = AppColors.TextPrimary,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }

                        Button(
                            onClick = { ShareCardGenerator.shareMonteCarloCard(context, result) },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AppColors.AccentCyan,
                                contentColor = AppColors.BgDeep
                            ),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Share", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, maxLines = 1, softWrap = false)
                        }
                    }

                    MonteCarloFanChart(
                        p10Curve = result.p10Curve,
                        p50Curve = result.p50Curve,
                        p90Curve = result.p90Curve
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutcomeBox("P10 Crash", Formatters.formatCompactUsd(result.p10FinalValue), AppColors.AccentGold, Modifier.weight(1f))
                        OutcomeBox("P50 Median", Formatters.formatCompactUsd(result.medianFinalValue), AppColors.AccentCyan, Modifier.weight(1f))
                        OutcomeBox("P90 Bull", Formatters.formatCompactUsd(result.p90FinalValue), AppColors.AccentEmerald, Modifier.weight(1f))
                    }

                    // FIRE 4% Rule Banner
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = AppColors.BgDeep,
                        border = BorderStroke(1.dp, AppColors.CardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("4% FIRE Monthly Income (Median)", color = AppColors.TextSecondary, fontSize = 11.sp)
                                Text(
                                    text = "${Formatters.formatUsd(result.medianMonthlyFireIncome)} / mo",
                                    color = AppColors.AccentEmerald,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Chance ≥ ${Formatters.formatCompactUsd(result.fireTargetUsd)}", color = AppColors.TextSecondary, fontSize = 11.sp)
                                Text(
                                    text = String.format(Locale.US, "%.1f%%", result.probabilityHitFirePct),
                                    color = AppColors.AccentCyan,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Parameter Controls
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = AppColors.CardDark,
                border = BorderStroke(1.dp, AppColors.CardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SliderRow(
                        label = "Initial Capital",
                        valueLabel = Formatters.formatUsd(initialCapital.toDouble()),
                        value = initialCapital,
                        range = 1000f..200000f,
                        onChange = { initialCapital = (it / 1000f).roundToInt() * 1000f }
                    )
                    SliderRow(
                        label = "Monthly DCA",
                        valueLabel = "${Formatters.formatUsd(monthlyDca.toDouble())}/mo",
                        value = monthlyDca,
                        range = 0f..5000f,
                        onChange = { monthlyDca = (it / 50f).roundToInt() * 50f }
                    )
                    SliderRow(
                        label = "Horizon",
                        valueLabel = "$years Years",
                        value = years.toFloat(),
                        range = 5f..30f,
                        onChange = { years = it.roundToInt() }
                    )
                    SliderRow(
                        label = "Expected Return (μ) & Volatility (σ)",
                        valueLabel = String.format(Locale.US, "μ %.1f%% • σ %.0f%%", annualReturnPct, volatilityPct),
                        value = annualReturnPct,
                        range = 4f..30f,
                        onChange = { annualReturnPct = (it * 2f).roundToInt() / 2f }
                    )
                    SliderRow(
                        label = "Target FIRE Wealth",
                        valueLabel = Formatters.formatCompactUsd(fireTargetUsd.toDouble()),
                        value = fireTargetUsd,
                        range = 100000f..3000000f,
                        onChange = { fireTargetUsd = (it / 50000f).roundToInt() * 50000f }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Adjust for 3% Annual Inflation", color = AppColors.TextSecondary, fontSize = 12.5.sp)
                        Switch(
                            checked = inflationAdjusted,
                            onCheckedChange = { inflationAdjusted = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = AppColors.BgDeep, checkedTrackColor = AppColors.AccentCyan)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { runSim(100) },
                            modifier = Modifier.weight(0.9f).defaultMinSize(minHeight = 46.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, AppColors.CardBorder),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Text("Preview (100)", color = AppColors.TextPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                        }

                        Button(
                            onClick = {
                                if (isPro || fullRunUnlockedOnce) {
                                    runSim(1000)
                                } else {
                                    showUnlockDialog = true
                                }
                            },
                            modifier = Modifier.weight(1.3f).defaultMinSize(minHeight = 46.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AppColors.AccentPurple,
                                contentColor = AppColors.TextPrimary
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp)
                        ) {
                            if (!isPro && !fullRunUnlockedOnce) {
                                Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(5.dp))
                            }
                            Text("Run 1,000 Scenarios", fontWeight = FontWeight.ExtraBold, fontSize = 12.5.sp, maxLines = 1, softWrap = false)
                        }
                    }
                }
            }
        }
    }

    if (showUnlockDialog) {
        UnlockFeatureDialog(
            badge = "INSTITUTIONAL MONTE CARLO",
            title = "Unlock 1,000-Run Simulation",
            description = "Run 1,000 full Geometric Brownian Motion paths on-device to accurately model tail-risk crashes (P10) and FIRE probability. Watch 1 video ad or go PRO.",
            watchAdLabel = "Watch 1 Video (1,000 Runs)",
            onDismiss = { showUnlockDialog = false },
            onWatchAd = {
                showUnlockDialog = false
                onRequestFullMonteCarloAd {
                    fullRunUnlockedOnce = true
                    runSim(1000)
                }
            },
            onOpenPro = {
                showUnlockDialog = false
                onOpenProDialog()
            }
        )
    }
}

@Composable
private fun OutcomeBox(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = AppColors.BgDeep,
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(9.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, color = AppColors.TextSecondary, fontSize = 10.5.sp, maxLines = 1, softWrap = false)
            Spacer(Modifier.height(2.dp))
            Text(value, color = color, fontSize = 14.5.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, softWrap = false)
        }
    }
}

@Composable
private fun SliderRow(
    label: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = AppColors.TextSecondary, fontSize = 12.sp)
            Text(valueLabel, color = AppColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(thumbColor = AppColors.AccentPurple, activeTrackColor = AppColors.AccentPurple)
        )
    }
}
