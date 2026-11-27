package com.directoratealpha.quantwealth.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import kotlinx.coroutines.launch
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
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

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

    var fullRunUnlocked by remember { mutableStateOf(false) }
    var showUnlockDialog by remember { mutableStateOf(false) }
    var manualSeedBump by remember { mutableStateOf(0) }

    val isFullLabActive = isPro || fullRunUnlocked
    val activeSimCount = if (isFullLabActive) 1000 else 100

    val result: MonteCarloResult = remember(
        selectedPresetLabel,
        activeSimCount,
        years,
        initialCapital,
        monthlyDca,
        annualReturnPct,
        volatilityPct,
        inflationAdjusted,
        fireTargetUsd,
        manualSeedBump
    ) {
        QuantEngine.runMonteCarlo(
            assetLabel = selectedPresetLabel,
            simulationsCount = activeSimCount,
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
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 0. Header
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

        // 1. Asset Presets
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

        // 2. Main Fan Chart & Summary Card
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
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "${result.simulationsCount} RUNS • ${result.years}Y ${if (result.inflationAdjusted) "(REAL $)" else "(NOMINAL)"}",
                                    color = if (isFullLabActive) AppColors.AccentEmerald else AppColors.AccentPurple,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
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
                            Column(modifier = Modifier.weight(1.15f).padding(end = 6.dp)) {
                                Text(
                                    text = "4% FIRE Income / mo",
                                    color = AppColors.TextSecondary,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    softWrap = false
                                )
                                Text(
                                    text = "${Formatters.formatUsd(result.medianMonthlyFireIncome)}/mo",
                                    color = AppColors.AccentEmerald,
                                    fontSize = 15.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                            Column(
                                horizontalAlignment = Alignment.End,
                                modifier = Modifier.weight(0.85f)
                            ) {
                                Text(
                                    text = "Hit ${Formatters.formatCompactUsd(result.fireTargetUsd)}",
                                    color = AppColors.TextSecondary,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    softWrap = false
                                )
                                Text(
                                    text = String.format(Locale.US, "%.1f%%", result.probabilityHitFirePct),
                                    color = AppColors.AccentCyan,
                                    fontSize = 15.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Deep Risk & Stress Test Card (Visibly Unlocks on 1,000-Run Mode!)
        item {
            if (isFullLabActive) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = AppColors.CardDark,
                    border = BorderStroke(1.dp, AppColors.AccentEmerald.copy(alpha = 0.5f)),
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
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Verified,
                                    contentDescription = null,
                                    tint = AppColors.AccentEmerald,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Deep Risk & Stress Test",
                                    color = AppColors.TextPrimary,
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = AppColors.AccentEmerald.copy(alpha = 0.16f)
                            ) {
                                Text(
                                    text = "1,000 RUNS",
                                    color = AppColors.AccentEmerald,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                )
                            }
                        }

                        // Extreme Tail Scenarios (P5 vs P95) + Loss Probability
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutcomeBox("P5 Extreme", Formatters.formatCompactUsd(result.p5FinalValue), AppColors.AccentRed, Modifier.weight(1f))
                            OutcomeBox("Loss Risk", String.format(Locale.US, "%.1f%%", result.probabilityOfLossPct), if (result.probabilityOfLossPct > 15.0) AppColors.AccentRed else AppColors.AccentGold, Modifier.weight(1f))
                            OutcomeBox("P95 Extreme", Formatters.formatCompactUsd(result.p95FinalValue), AppColors.AccentEmerald, Modifier.weight(1f))
                        }

                        // Invested vs Profit + Estimated FIRE Year
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = AppColors.BgDeep,
                            border = BorderStroke(1.dp, AppColors.CardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                RiskKeyVal("Total Cash Contributed", Formatters.formatUsd(result.totalContributed), AppColors.TextPrimary)
                                RiskKeyVal(
                                    "Median Compounding Profit",
                                    Formatters.formatUsd(result.medianProfitUsd, showSign = true),
                                    if (result.medianProfitUsd >= 0.0) AppColors.AccentEmerald else AppColors.AccentRed
                                )
                                RiskKeyVal(
                                    "Estimated FIRE Year (Median)",
                                    result.estimatedFireYear?.let { "Year $it of ${result.years}" } ?: "Beyond ${result.years}Y horizon",
                                    if (result.estimatedFireYear != null) AppColors.AccentCyan else AppColors.AccentGold
                                )
                            }
                        }

                        // Visual Outcome Distribution Bars
                        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Text(
                                text = "1,000-Run Outcome Probability Breakdown",
                                color = AppColors.TextSecondary,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            DistributionBar(
                                label = "Finish Below Invested (Loss)",
                                pct = result.probabilityOfLossPct,
                                color = AppColors.AccentRed
                            )
                            DistributionBar(
                                label = "Profitable (Below FIRE Goal)",
                                pct = result.probProfitBelowFirePct,
                                color = AppColors.AccentGold
                            )
                            DistributionBar(
                                label = "Reach FIRE Goal (≥ ${Formatters.formatCompactUsd(result.fireTargetUsd)})",
                                pct = result.probabilityHitFirePct,
                                color = AppColors.AccentEmerald
                            )
                        }
                    }
                }
            } else {
                // Locked Teaser Card right below the chart so unlocking has an immediate visible payoff
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = AppColors.CardDark,
                    border = BorderStroke(1.dp, AppColors.AccentGold.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showUnlockDialog = true }
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Lock,
                                    contentDescription = null,
                                    tint = AppColors.AccentGold,
                                    modifier = Modifier.size(17.dp)
                                )
                                Text(
                                    text = "Deep Risk & Stress Test (1,000 Runs)",
                                    color = AppColors.AccentGold,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                        Text(
                            text = "Unlock P5/P95 extreme crash tails, Probability of Loss, Estimated FIRE Year, and 1,000-run Outcome Distribution Bars.",
                            color = AppColors.TextSecondary,
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                        Button(
                            onClick = { showUnlockDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 44.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AppColors.AccentPurple,
                                contentColor = AppColors.TextPrimary
                            )
                        ) {
                            Icon(Icons.Rounded.PlayCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Unlock 1,000 Runs + Risk Lab (1 Video)",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.5.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }

        // 4. Simulation Parameters & Separate Return + Volatility Sliders
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
                        label = "Expected Annual Return (μ)",
                        valueLabel = String.format(Locale.US, "%.1f%% / yr", annualReturnPct),
                        value = annualReturnPct,
                        range = 3f..30f,
                        onChange = { annualReturnPct = (it * 2f).roundToInt() / 2f }
                    )
                    SliderRow(
                        label = "Annual Volatility / Risk (σ)",
                        valueLabel = String.format(Locale.US, "%.0f%%", volatilityPct),
                        value = volatilityPct,
                        range = 5f..65f,
                        onChange = { volatilityPct = it.roundToInt().toFloat() }
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

                    // Single clean action button with smooth scroll back to chart
                    Button(
                        onClick = {
                            if (isFullLabActive) {
                                manualSeedBump++
                                scope.launch { listState.animateScrollToItem(2) }
                                Toast.makeText(context, "Re-ran 1,000 fresh scenarios", Toast.LENGTH_SHORT).show()
                            } else {
                                manualSeedBump++
                                scope.launch { listState.animateScrollToItem(2) }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 46.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isFullLabActive) AppColors.AccentEmerald else AppColors.AccentCyan,
                            contentColor = AppColors.BgDeep
                        )
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (isFullLabActive) "Re-Run 1,000 Scenarios & View Risk" else "Re-Roll 100 Scenarios",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }

    if (showUnlockDialog) {
        UnlockFeatureDialog(
            badge = "INSTITUTIONAL MONTE CARLO",
            title = "Unlock 1,000 Runs + Risk Lab",
            description = "Run 1,000 full Geometric Brownian Motion paths on-device and unlock P5/P95 crash tails, Loss Probability, Estimated FIRE Year, and Outcome Distribution Bars.",
            watchAdLabel = "Watch 1 Video (Unlock All)",
            onDismiss = { showUnlockDialog = false },
            onWatchAd = {
                showUnlockDialog = false
                onRequestFullMonteCarloAd {
                    fullRunUnlocked = true
                    manualSeedBump++
                    Toast.makeText(context, "1,000 Scenarios + Deep Risk Breakdown Unlocked!", Toast.LENGTH_SHORT).show()
                    scope.launch { listState.animateScrollToItem(2) }
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
    color: Color,
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
private fun RiskKeyVal(label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = AppColors.TextSecondary, fontSize = 12.sp)
        Text(value, color = valueColor, fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun DistributionBar(label: String, pct: Double, color: Color) {
    val clamped = (pct / 100.0).toFloat().coerceIn(0f, 1f)
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = AppColors.TextSecondary, fontSize = 11.5.sp)
            Text(
                text = String.format(Locale.US, "%.1f%%", pct),
                color = color,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
        LinearProgressIndicator(
            progress = { clamped },
            modifier = Modifier
                .fillMaxWidth()
                .height(7.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = color,
            trackColor = AppColors.CardBorder
        )
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
