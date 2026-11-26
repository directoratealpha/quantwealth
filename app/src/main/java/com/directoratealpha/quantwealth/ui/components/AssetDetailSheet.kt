package com.directoratealpha.quantwealth.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.directoratealpha.quantwealth.data.MarketRepository
import com.directoratealpha.quantwealth.model.*
import com.directoratealpha.quantwealth.ui.screens.signalColorFor
import com.directoratealpha.quantwealth.ui.theme.AppColors
import com.directoratealpha.quantwealth.ui.theme.Formatters
import java.util.Locale

@Composable
fun AssetDetailChartSheet(
    asset: MarketAsset,
    isInWatchlist: Boolean,
    canRemoveFromWatchlist: Boolean,
    onToggleWatchlist: () -> Unit,
    onTradeAsset: (MarketAsset, TradeSide) -> Unit
) {
    var selectedTimeframe by remember { mutableStateOf(ChartTimeframe.M1) }
    var chartPoints by remember(asset.symbol) {
        mutableStateOf(
            asset.dailyCloses.takeLast(22).mapIndexed { idx, p ->
                ChartPoint(timestampSec = (System.currentTimeMillis() / 1000L) - (22 - idx) * 86400L, price = p)
            }
        )
    }
    var isLoadingChart by remember { mutableStateOf(false) }
    var scrubbedPoint by remember { mutableStateOf<ChartPoint?>(null) }

    LaunchedEffect(asset.symbol, selectedTimeframe) {
        isLoadingChart = true
        chartPoints = MarketRepository.fetchAssetChartPoints(
            symbol = asset.symbol,
            timeframe = selectedTimeframe,
            fallbackCloses = asset.dailyCloses
        )
        isLoadingChart = false
    }

    val firstPrice = chartPoints.firstOrNull()?.price ?: asset.previousClose
    val activePrice = scrubbedPoint?.price ?: asset.price
    val periodDeltaUsd = activePrice - firstPrice
    val periodDeltaPct = if (firstPrice > 0.0) (periodDeltaUsd / firstPrice) * 100.0 else asset.changePercent
    val isUp = periodDeltaPct >= 0.0

    val periodHigh = chartPoints.maxOfOrNull { it.price } ?: asset.price
    val periodLow = chartPoints.minOfOrNull { it.price } ?: asset.price
    val sig = asset.signal
    val sigColor = signalColorFor(sig.label)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 4.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header: Name + Live Badge + Watchlist Star
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
                        text = asset.displaySymbol,
                        color = AppColors.AccentCyan,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    if (asset.symbol != asset.displaySymbol) {
                        Text(
                            text = "(${asset.symbol})",
                            color = AppColors.TextMuted,
                            fontSize = 11.5.sp
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (asset.isLive) AppColors.AccentEmerald.copy(alpha = 0.16f) else AppColors.CardElevated
                    ) {
                        Text(
                            text = if (asset.isLive) "LIVE QUOTE" else "CACHED",
                            color = if (asset.isLive) AppColors.AccentEmerald else AppColors.TextSecondary,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = asset.name,
                    color = AppColors.TextPrimary,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (!isInWatchlist || canRemoveFromWatchlist) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isInWatchlist) AppColors.AccentGold.copy(alpha = 0.18f) else AppColors.CardElevated,
                    border = BorderStroke(1.dp, if (isInWatchlist) AppColors.AccentGold else AppColors.CardBorder),
                    modifier = Modifier.clickable { onToggleWatchlist() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (isInWatchlist) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                            contentDescription = "Watchlist",
                            tint = if (isInWatchlist) AppColors.AccentGold else AppColors.TextPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (isInWatchlist) "Saved" else "Watchlist",
                            color = if (isInWatchlist) AppColors.AccentGold else AppColors.TextPrimary,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }

        // Big Price & Scrub Timestamp
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Column {
                Text(
                    text = Formatters.formatPrice(activePrice),
                    color = AppColors.TextPrimary,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "${Formatters.formatUsd(periodDeltaUsd, showSign = true)} (${Formatters.formatPct(periodDeltaPct)})",
                        color = if (isUp) AppColors.AccentEmerald else AppColors.AccentRed,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    val tsLabel = scrubbedPoint?.let {
                        Formatters.formatTimestamp(
                            it.timestampSec,
                            includeTime = selectedTimeframe == ChartTimeframe.D1 || selectedTimeframe == ChartTimeframe.W1
                        )
                    } ?: selectedTimeframe.label
                    Text(
                        text = "• $tsLabel",
                        color = AppColors.TextSecondary,
                        fontSize = 11.5.sp
                    )
                }
            }

            if (isLoadingChart) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = AppColors.AccentCyan
                )
            }
        }

        // Interactive Touch-Scrubbing Price Chart
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = AppColors.BgDeep,
            border = BorderStroke(1.dp, AppColors.CardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InteractiveAssetPriceChart(
                    points = chartPoints,
                    isPositive = isUp,
                    onScrubPointChanged = { scrubbedPoint = it },
                    height = 200.dp
                )

                // Timeframe Selector Row (1D | 1W | 1M | 3M | 1Y | 5Y)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ChartTimeframe.values().forEach { tf ->
                        val active = selectedTimeframe == tf
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (active) AppColors.AccentCyan else AppColors.CardDark,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedTimeframe = tf }
                        ) {
                            Text(
                                text = tf.label,
                                color = if (active) AppColors.BgDeep else AppColors.TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier
                                    .padding(vertical = 7.dp)
                                    .wrapContentWidth(Alignment.CenterHorizontally)
                            )
                        }
                    }
                }
            }
        }

        // Direct Buy / Long & Sell / Short Trade Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = { onTradeAsset(asset, TradeSide.LONG) },
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 48.dp),
                shape = RoundedCornerShape(13.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.AccentEmerald,
                    contentColor = AppColors.BgDeep
                )
            ) {
                Icon(Icons.Rounded.TrendingUp, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Buy / Long", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, maxLines = 1, softWrap = false)
            }

            Button(
                onClick = { onTradeAsset(asset, TradeSide.SHORT) },
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 48.dp),
                shape = RoundedCornerShape(13.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.AccentRed,
                    contentColor = AppColors.TextPrimary
                )
            ) {
                Icon(Icons.Rounded.TrendingDown, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Sell / Short", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, maxLines = 1, softWrap = false)
            }
        }

        // Complete Technical Signals & Range Card (100% Free — No Ad Wall!)
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = AppColors.CardElevated,
            border = BorderStroke(1.dp, sigColor.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(13.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "QUANT SIGNAL: ${sig.label.label}",
                        color = sigColor,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "Score ${sig.score}/100",
                        color = AppColors.TextPrimary,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = sig.summary,
                    color = AppColors.TextSecondary,
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp
                )
                HorizontalDivider(color = AppColors.CardBorder, modifier = Modifier.padding(vertical = 2.dp))

                StatRow("${selectedTimeframe.label} High / Low", "${Formatters.formatPrice(periodHigh)} / ${Formatters.formatPrice(periodLow)}")
                StatRow("RSI (14)", String.format(Locale.US, "%.1f", sig.rsi14))
                StatRow("SMA 50 / SMA 200", "${Formatters.formatPrice(sig.sma50)} / ${Formatters.formatPrice(sig.sma200)}")
                StatRow("MACD (12, 26, 9)", String.format(Locale.US, "%.2f (Hist %+.2f)", sig.macdLine, sig.macdHistogram))
                StatRow("Bollinger (20, 2σ)", "${Formatters.formatPrice(sig.bollingerLower)} – ${Formatters.formatPrice(sig.bollingerUpper)}")
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = AppColors.TextSecondary, fontSize = 12.sp)
        Text(value, color = AppColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}
