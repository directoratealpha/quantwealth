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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.directoratealpha.quantwealth.data.MarketRepository
import com.directoratealpha.quantwealth.engine.QuantEngine
import com.directoratealpha.quantwealth.model.BacktestAssetWeight
import com.directoratealpha.quantwealth.model.BacktestResult
import com.directoratealpha.quantwealth.ui.components.BacktestEquityChart
import com.directoratealpha.quantwealth.ui.components.UnlockFeatureDialog
import com.directoratealpha.quantwealth.ui.theme.AppColors
import com.directoratealpha.quantwealth.ui.theme.Formatters
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun BacktestScreen(
    isPro: Boolean,
    onRequestMultiAssetAd: (onUnlocked: () -> Unit) -> Unit,
    onOpenProDialog: () -> Unit
) {
    val presetSymbols = remember { listOf("SPY", "QQQ", "NVDA", "PLTR", "GOOGL", "SCHD", "GLD", "BTC-USD") }

    var selectedWeights by remember { mutableStateOf(listOf(BacktestAssetWeight("SPY", 100))) }
    var multiAssetUnlocked by remember { mutableStateOf(false) }
    var showMultiAssetDialog by remember { mutableStateOf(false) }

    var initialCapital by remember { mutableStateOf(10000f) }
    var monthlyDca by remember { mutableStateOf(500f) }
    var years by remember { mutableStateOf(10) }
    var dripEnabled by remember { mutableStateOf(true) }

    var cachedSeriesMap by remember { mutableStateOf<Map<String, List<Double>>>(emptyMap()) }
    var allLiveData by remember { mutableStateOf(true) }
    var isFetchingHistory by remember { mutableStateOf(false) }

    // Fetch historical prices whenever selected tickers or horizon changes
    LaunchedEffect(selectedWeights, years) {
        isFetchingHistory = true
        val historyMap = HashMap<String, List<Double>>()
        var liveFlag = true
        for (item in selectedWeights) {
            val (series, live) = MarketRepository.fetchMonthlyHistory(item.symbol, years)
            historyMap[item.symbol] = series
            if (!live) liveFlag = false
        }
        cachedSeriesMap = historyMap
        allLiveData = liveFlag
        isFetchingHistory = false
    }

    // Recompute backtest instantaneously whenever sliders, DRIP, or fetched history updates
    val result: BacktestResult? = remember(
        selectedWeights,
        cachedSeriesMap,
        initialCapital,
        monthlyDca,
        years,
        dripEnabled,
        allLiveData
    ) {
        if (cachedSeriesMap.isEmpty()) return@remember null
        val divMap = selectedWeights.associate { item ->
            val seed = MarketRepository.catalog.firstOrNull { it.symbol == item.symbol }
            item.symbol to (seed?.dividendYieldPct ?: 0.0)
        }
        QuantEngine.runBacktest(
            weights = selectedWeights,
            monthlySeriesMap = cachedSeriesMap,
            dividendYieldsMap = divMap,
            initialCapital = initialCapital.toDouble(),
            monthlyDca = monthlyDca.toDouble(),
            years = years,
            dripEnabled = dripEnabled,
            usedLiveData = allLiveData
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Real Historical ETF Backtester",
                        color = AppColors.TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "Drag sliders to simulate DCA + DRIP on real historical prices",
                        color = AppColors.TextSecondary,
                        fontSize = 11.5.sp
                    )
                }
                if (isFetchingHistory) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = AppColors.AccentCyan
                    )
                }
            }
        }

        // Results Chart & Quant Metrics (Placed right at top so you see live slider changes!)
        val res = result
        if (res != null) {
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = AppColors.CardDark,
                    border = BorderStroke(1.dp, AppColors.AccentEmerald.copy(alpha = 0.4f)),
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
                                    text = "${res.symbolsLabel} (${res.years}Y)",
                                    color = AppColors.AccentCyan,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = Formatters.formatUsd(res.finalValue),
                                    color = AppColors.TextPrimary,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (res.usedLiveData) AppColors.AccentEmerald.copy(alpha = 0.16f) else AppColors.AccentGold.copy(alpha = 0.16f)
                            ) {
                                Text(
                                    text = if (res.usedLiveData) "LIVE HISTORY" else "MODEL DATA",
                                    color = if (res.usedLiveData) AppColors.AccentEmerald else AppColors.AccentGold,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                                )
                            }
                        }

                        BacktestEquityChart(
                            portfolioCurve = res.portfolioCurve,
                            investedCurve = res.investedCurve,
                            height = 175.dp
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Green: Portfolio Value", color = AppColors.AccentEmerald, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Text("Dashed: Invested (${Formatters.formatCompactUsd(res.totalInvested)})", color = AppColors.AccentCyan, fontSize = 11.sp)
                        }

                        HorizontalDivider(color = AppColors.CardBorder)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            QuantMetricBox(
                                label = "CAGR / yr",
                                value = Formatters.formatPct(res.cagrPct),
                                valueColor = if (res.cagrPct >= 0) AppColors.AccentEmerald else AppColors.AccentRed,
                                modifier = Modifier.weight(1f)
                            )
                            QuantMetricBox(
                                label = "Max Drawdown",
                                value = String.format(Locale.US, "-%.1f%%", res.maxDrawdownPct),
                                valueColor = AppColors.AccentRed,
                                modifier = Modifier.weight(1f)
                            )
                            QuantMetricBox(
                                label = "Sharpe Ratio",
                                value = String.format(Locale.US, "%.2f", res.sharpeRatio),
                                valueColor = if (res.sharpeRatio >= 1.0) AppColors.AccentCyan else AppColors.AccentGold,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // Asset Basket & Sliders
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = AppColors.CardDark,
                border = BorderStroke(1.dp, AppColors.CardBorder),
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
                        Text(
                            text = "Portfolio Assets (${selectedWeights.size}/5)",
                            color = AppColors.TextPrimary,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (!isPro && !multiAssetUnlocked) {
                            Text(
                                text = "1 free • 2–5 via Ad/PRO",
                                color = AppColors.AccentGold,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        presetSymbols.forEach { sym ->
                            val isSelected = selectedWeights.any { it.symbol == sym }
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) AppColors.AccentCyan.copy(alpha = 0.22f) else AppColors.CardElevated,
                                border = BorderStroke(1.dp, if (isSelected) AppColors.AccentCyan else AppColors.CardBorder),
                                modifier = Modifier.clickable {
                                    if (isSelected) {
                                        if (selectedWeights.size > 1) {
                                            val updated = selectedWeights.filter { it.symbol != sym }
                                            val equalW = 100 / updated.size
                                            selectedWeights = updated.map { it.copy(weightPct = equalW) }
                                        }
                                    } else {
                                        if (isPro || multiAssetUnlocked) {
                                            if (selectedWeights.size < 5) {
                                                val updated = selectedWeights + BacktestAssetWeight(sym, 20)
                                                val equalW = 100 / updated.size
                                                selectedWeights = updated.map { it.copy(weightPct = equalW) }
                                            }
                                        } else {
                                            selectedWeights = listOf(BacktestAssetWeight(sym, 100))
                                        }
                                    }
                                }
                            ) {
                                Text(
                                    text = sym.replace("-USD", ""),
                                    color = if (isSelected) AppColors.AccentCyan else AppColors.TextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }

                    if (!isPro && !multiAssetUnlocked) {
                        OutlinedButton(
                            onClick = { showMultiAssetDialog = true },
                            modifier = Modifier.fillMaxWidth().height(38.dp),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, AppColors.AccentGold.copy(alpha = 0.6f)),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            Icon(Icons.Rounded.Lock, contentDescription = null, tint = AppColors.AccentGold, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Unlock Multi-Asset Basket (Up to 5 ETFs)",
                                color = AppColors.AccentGold,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    Column {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Initial Investment", color = AppColors.TextSecondary, fontSize = 12.sp)
                            Text(Formatters.formatUsd(initialCapital.toDouble()), color = AppColors.TextPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = initialCapital,
                            onValueChange = { initialCapital = (it / 1000f).roundToInt() * 1000f },
                            valueRange = 1000f..100000f,
                            colors = SliderDefaults.colors(thumbColor = AppColors.AccentCyan, activeTrackColor = AppColors.AccentCyan)
                        )
                    }

                    Column {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Monthly Contribution (DCA)", color = AppColors.TextSecondary, fontSize = 12.sp)
                            Text("${Formatters.formatUsd(monthlyDca.toDouble())}/mo", color = AppColors.AccentEmerald, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = monthlyDca,
                            onValueChange = { monthlyDca = (it / 50f).roundToInt() * 50f },
                            valueRange = 0f..5000f,
                            colors = SliderDefaults.colors(thumbColor = AppColors.AccentEmerald, activeTrackColor = AppColors.AccentEmerald)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(3, 5, 10, 15).forEach { y ->
                                val active = years == y
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (active) AppColors.AccentCyan else AppColors.CardElevated,
                                    modifier = Modifier.clickable { years = y }
                                ) {
                                    Text(
                                        text = "${y}Y",
                                        color = if (active) AppColors.BgDeep else AppColors.TextSecondary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("DRIP", color = AppColors.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(6.dp))
                            Switch(
                                checked = dripEnabled,
                                onCheckedChange = { dripEnabled = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = AppColors.BgDeep, checkedTrackColor = AppColors.AccentEmerald)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showMultiAssetDialog) {
        UnlockFeatureDialog(
            badge = "MULTI-ASSET PORTFOLIO",
            title = "Combine Up to 5 ETFs / Stocks",
            description = "Build blended portfolios (e.g., SPY + QQQ + GLD + BTC) and compute combined Sharpe Ratio & Max Drawdown. Watch 1 short video ad to unlock for this session, or upgrade to PRO.",
            watchAdLabel = "Watch 1 Video to Unlock",
            onDismiss = { showMultiAssetDialog = false },
            onWatchAd = {
                showMultiAssetDialog = false
                onRequestMultiAssetAd { multiAssetUnlocked = true }
            },
            onOpenPro = {
                showMultiAssetDialog = false
                onOpenProDialog()
            }
        )
    }
}

@Composable
private fun QuantMetricBox(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = AppColors.BgDeep,
        border = BorderStroke(1.dp, AppColors.CardBorder),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = label, color = AppColors.TextSecondary, fontSize = 10.5.sp, maxLines = 1, softWrap = false)
            Spacer(Modifier.height(2.dp))
            Text(text = value, color = valueColor, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, softWrap = false)
        }
    }
}
