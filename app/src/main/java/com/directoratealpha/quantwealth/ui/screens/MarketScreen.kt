package com.directoratealpha.quantwealth.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.directoratealpha.quantwealth.data.MarketRepository
import com.directoratealpha.quantwealth.model.*
import com.directoratealpha.quantwealth.ui.components.MiniSparkline
import com.directoratealpha.quantwealth.ui.components.UnlockFeatureDialog
import com.directoratealpha.quantwealth.ui.theme.AppColors
import com.directoratealpha.quantwealth.ui.theme.Formatters
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketScreen(
    portfolio: PortfolioState,
    marketAssets: Map<String, MarketAsset>,
    isRefreshing: Boolean,
    onManualRefresh: () -> Unit,
    onAddTicker: (String) -> Unit,
    onRemoveTicker: (String) -> Unit,
    onUnlockExtraSlot: () -> Unit,
    onUnlockFullSignal: (String) -> Unit,
    onOpenProDialog: () -> Unit,
    onQuickTradeAsset: (MarketAsset) -> Unit
) {
    var selectedCategory by remember { mutableStateOf(AssetCategory.ALL) }
    var showAddTickerDialog by remember { mutableStateOf(false) }
    var showSlotUnlockDialog by remember { mutableStateOf(false) }
    var inspectedSymbol by remember { mutableStateOf<String?>(null) }

    val orderedAssets = remember(portfolio.watchlistSymbols, marketAssets, selectedCategory) {
        portfolio.watchlistSymbols.mapNotNull { sym -> marketAssets[sym] }.filter { asset ->
            selectedCategory == AssetCategory.ALL || asset.category == selectedCategory
        }
    }

    val usedSlots = portfolio.watchlistSymbols.size
    val maxSlots = portfolio.maxWatchlistSlots

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Watchlist Header + Slot Counter + Add Button
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = "Live Market & Quant Signals",
                        color = AppColors.TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (portfolio.isPro) {
                            "$usedSlots tracked • Unlimited PRO slots"
                        } else {
                            "Watchlist slots: $usedSlots / $maxSlots (+1 per video ad)"
                        },
                        color = AppColors.TextSecondary,
                        fontSize = 11.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(
                        onClick = onManualRefresh,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(AppColors.CardDark)
                            .border(1.dp, AppColors.CardBorder, RoundedCornerShape(10.dp))
                    ) {
                        if (isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = AppColors.AccentCyan
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "Refresh",
                                tint = AppColors.AccentCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = AppColors.AccentCyan,
                        modifier = Modifier.clickable {
                            if (portfolio.isPro || usedSlots < maxSlots) {
                                showAddTickerDialog = true
                            } else {
                                showSlotUnlockDialog = true
                            }
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = "Add Ticker",
                                tint = AppColors.BgDeep,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Ticker",
                                color = AppColors.BgDeep,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }

        // Category Filter Row
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssetCategory.values().forEach { cat ->
                    val selected = selectedCategory == cat
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (selected) AppColors.AccentCyan.copy(alpha = 0.2f) else AppColors.CardDark,
                        border = BorderStroke(
                            1.dp,
                            if (selected) AppColors.AccentCyan else AppColors.CardBorder
                        ),
                        modifier = Modifier.clickable { selectedCategory = cat }
                    ) {
                        Text(
                            text = cat.label,
                            color = if (selected) AppColors.AccentCyan else AppColors.TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                        )
                    }
                }
            }
        }

        // Asset Rows
        items(orderedAssets, key = { it.symbol }) { asset ->
            MarketAssetCard(
                asset = asset,
                onClick = { inspectedSymbol = asset.symbol }
            )
        }

        // Educational Compliance Footer
        item {
            Text(
                text = "Quantitative technical indicators & virtual simulation for educational purposes only — not financial advice.",
                color = AppColors.TextMuted,
                fontSize = 10.5.sp,
                lineHeight = 14.sp,
                modifier = Modifier.padding(top = 6.dp, bottom = 4.dp)
            )
        }
    }

    // Slot Limit Reached Modal (1 Rewarded Video = +1 Slot!)
    if (showSlotUnlockDialog) {
        UnlockFeatureDialog(
            badge = "WATCHLIST LIMIT ($usedSlots / $maxSlots)",
            title = "Add +1 Watchlist Slot",
            description = "You are tracking $usedSlots assets. Watch 1 short video ad to permanently unlock +1 extra watchlist slot, or upgrade to PRO for unlimited tickers.",
            watchAdLabel = "Watch 1 Video (+1 Slot)",
            onDismiss = { showSlotUnlockDialog = false },
            onWatchAd = {
                showSlotUnlockDialog = false
                onUnlockExtraSlot()
            },
            onOpenPro = {
                showSlotUnlockDialog = false
                onOpenProDialog()
            }
        )
    }

    // Add Custom Ticker Modal
    if (showAddTickerDialog) {
        AddTickerDialog(
            existingSymbols = portfolio.watchlistSymbols.toSet(),
            onDismiss = { showAddTickerDialog = false },
            onConfirmTicker = { sym ->
                showAddTickerDialog = false
                onAddTicker(sym)
            }
        )
    }

    // Asset Quant Signal & Quick Trade Modal Bottom Sheet
    val inspectedAsset = inspectedSymbol?.let { marketAssets[it] }
    if (inspectedAsset != null) {
        val isFullSignalUnlocked = portfolio.isPro || portfolio.unlockedFullSignalSymbols.contains(inspectedAsset.symbol)
        ModalBottomSheet(
            onDismissRequest = { inspectedSymbol = null },
            containerColor = AppColors.CardDark,
            contentColor = AppColors.TextPrimary
        ) {
            AssetQuantDetailSheet(
                asset = inspectedAsset,
                canRemove = portfolio.watchlistSymbols.size > 1,
                isFullSignalUnlocked = isFullSignalUnlocked,
                onUnlockFullSignal = { onUnlockFullSignal(inspectedAsset.symbol) },
                onOpenPro = {
                    inspectedSymbol = null
                    onOpenProDialog()
                },
                onTradeClick = {
                    inspectedSymbol = null
                    onQuickTradeAsset(inspectedAsset)
                },
                onRemoveClick = {
                    inspectedSymbol = null
                    onRemoveTicker(inspectedAsset.symbol)
                }
            )
        }
    }
}

@Composable
private fun MarketAssetCard(
    asset: MarketAsset,
    onClick: () -> Unit
) {
    val isUp = asset.changePercent >= 0.0
    val signalColor = signalColorFor(asset.signal.label)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = AppColors.CardDark,
        border = BorderStroke(1.dp, AppColors.CardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 13.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Symbol, Name & Signal Badge
            Column(
                modifier = Modifier
                    .weight(1.15f)
                    .padding(end = 6.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = asset.displaySymbol,
                        color = AppColors.TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        softWrap = false
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = signalColor.copy(alpha = 0.16f)
                    ) {
                        Text(
                            text = "${asset.signal.label.label} ${asset.signal.score}",
                            color = signalColor,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = asset.name,
                    color = AppColors.TextSecondary,
                    fontSize = 11.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Middle: Sparkline
            MiniSparkline(
                points = asset.sparkline,
                isPositive = isUp,
                modifier = Modifier
                    .width(56.dp)
                    .height(28.dp)
            )

            // Right: Live Price + Daily Change
            Column(
                modifier = Modifier
                    .weight(0.85f)
                    .padding(start = 8.dp),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = Formatters.formatPrice(asset.price),
                    color = AppColors.TextPrimary,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    softWrap = false
                )
                Text(
                    text = Formatters.formatPct(asset.changePercent),
                    color = if (isUp) AppColors.AccentEmerald else AppColors.AccentRed,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
private fun AssetQuantDetailSheet(
    asset: MarketAsset,
    canRemove: Boolean,
    isFullSignalUnlocked: Boolean,
    onUnlockFullSignal: () -> Unit,
    onOpenPro: () -> Unit,
    onTradeClick: () -> Unit,
    onRemoveClick: () -> Unit
) {
    val sig = asset.signal
    val sigColor = signalColorFor(sig.label)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 6.dp)
            .padding(bottom = 26.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${asset.displaySymbol} • ${asset.symbol}",
                    color = AppColors.AccentCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = asset.name,
                    color = AppColors.TextPrimary,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = Formatters.formatPrice(asset.price),
                    color = AppColors.TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = Formatters.formatPct(asset.changePercent),
                    color = if (asset.changePercent >= 0) AppColors.AccentEmerald else AppColors.AccentRed,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Quant Score Hero
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = AppColors.CardElevated,
            border = BorderStroke(1.dp, sigColor.copy(alpha = 0.45f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
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
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "Score ${sig.score}/100",
                        color = AppColors.TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = sig.summary,
                    color = AppColors.TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
                HorizontalDivider(color = AppColors.CardBorder, modifier = Modifier.padding(vertical = 2.dp))

                // RSI (14) — Always Free
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("RSI (14) Momentum", color = AppColors.TextSecondary, fontSize = 12.5.sp)
                    val rsiTag = when {
                        sig.rsi14 < 30 -> "Oversold"
                        sig.rsi14 > 70 -> "Overbought"
                        else -> "Healthy"
                    }
                    Text(
                        text = String.format(Locale.US, "%.1f (%s)", sig.rsi14, rsiTag),
                        color = AppColors.TextPrimary,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // MACD + Bollinger + SMA 50/200 — PRO or 1 Rewarded Ad
                if (isFullSignalUnlocked) {
                    val trendText = if (sig.sma50 >= sig.sma200) "Golden Cross (Bullish)" else "Death Cross (Bearish)"
                    IndicatorRow("SMA 50 / SMA 200", "${Formatters.formatPrice(sig.sma50)} / ${Formatters.formatPrice(sig.sma200)}")
                    IndicatorRow("Trend Structure", trendText)
                    IndicatorRow(
                        "MACD (12, 26, 9)",
                        String.format(Locale.US, "Line %.2f • Hist %+.2f", sig.macdLine, sig.macdHistogram)
                    )
                    IndicatorRow(
                        "Bollinger (20, 2σ)",
                        "${Formatters.formatPrice(sig.bollingerLower)} – ${Formatters.formatPrice(sig.bollingerUpper)}"
                    )
                } else {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = AppColors.BgDeep.copy(alpha = 0.85f),
                        border = BorderStroke(1.dp, AppColors.AccentGold.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Lock,
                                    contentDescription = null,
                                    tint = AppColors.AccentGold,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "MACD, Bollinger Bands & SMA 50/200",
                                    color = AppColors.TextPrimary,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = onUnlockFullSignal,
                                    modifier = Modifier.weight(1f).defaultMinSize(minHeight = 40.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = AppColors.AccentCyan,
                                        contentColor = AppColors.BgDeep
                                    )
                                ) {
                                    Text("Watch 1 Ad", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, maxLines = 1, softWrap = false)
                                }
                                Button(
                                    onClick = onOpenPro,
                                    modifier = Modifier.weight(1f).defaultMinSize(minHeight = 40.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = AppColors.AccentGold,
                                        contentColor = AppColors.BgDeep
                                    )
                                ) {
                                    Text("Go PRO", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, maxLines = 1, softWrap = false)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onTradeClick,
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.AccentEmerald,
                    contentColor = AppColors.BgDeep
                )
            ) {
                Icon(Icons.Rounded.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Trade in $100K Sim",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    softWrap = false
                )
            }

            if (canRemove) {
                OutlinedButton(
                    onClick = onRemoveClick,
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, AppColors.AccentRed.copy(alpha = 0.6f))
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = "Remove",
                        tint = AppColors.AccentRed,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun IndicatorRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = AppColors.TextSecondary, fontSize = 12.sp)
        Text(value, color = AppColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun AddTickerDialog(
    existingSymbols: Set<String>,
    onDismiss: () -> Unit,
    onConfirmTicker: (String) -> Unit
) {
    var input by remember { mutableStateOf("") }
    val suggestions = remember(existingSymbols) {
        MarketRepository.catalog.filter { !existingSymbols.contains(it.symbol) }.take(8)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppColors.CardDark,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                text = "Add Live Ticker",
                color = AppColors.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Enter any Yahoo Finance ticker (e.g., AAPL, MSFT, AMZN, META, BTC-USD, GLD, SI=F, EURUSD=X).",
                    color = AppColors.TextSecondary,
                    fontSize = 12.sp
                )
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it.uppercase().replace(" ", "") },
                    placeholder = { Text("e.g. AAPL or DOGE-USD", color = AppColors.TextMuted) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AppColors.AccentCyan,
                        unfocusedBorderColor = AppColors.CardBorder,
                        focusedTextColor = AppColors.TextPrimary,
                        unfocusedTextColor = AppColors.TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                if (suggestions.isNotEmpty()) {
                    Text(
                        text = "Quick Pick:",
                        color = AppColors.TextSecondary,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        suggestions.forEach { seed ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = AppColors.CardElevated,
                                border = BorderStroke(1.dp, AppColors.CardBorder),
                                modifier = Modifier.clickable { onConfirmTicker(seed.symbol) }
                            ) {
                                Text(
                                    text = "+ ${seed.displaySymbol}",
                                    color = AppColors.AccentCyan,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val clean = input.trim().uppercase()
                    if (clean.isNotBlank()) onConfirmTicker(clean)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.AccentCyan,
                    contentColor = AppColors.BgDeep
                )
            ) {
                Text("Add", fontWeight = FontWeight.ExtraBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = AppColors.TextSecondary)
            }
        }
    )
}

fun signalColorFor(label: SignalLabel): Color = when (label) {
    SignalLabel.STRONG_BUY -> AppColors.AccentEmerald
    SignalLabel.BUY -> AppColors.AccentCyan
    SignalLabel.NEUTRAL -> AppColors.TextSecondary
    SignalLabel.SELL -> AppColors.AccentGold
    SignalLabel.STRONG_SELL -> AppColors.AccentRed
}
