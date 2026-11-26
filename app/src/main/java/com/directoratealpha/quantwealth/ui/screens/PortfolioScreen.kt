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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.directoratealpha.quantwealth.engine.QuantEngine
import com.directoratealpha.quantwealth.model.*
import com.directoratealpha.quantwealth.ui.components.UnlockFeatureDialog
import com.directoratealpha.quantwealth.ui.theme.AppColors
import com.directoratealpha.quantwealth.ui.theme.Formatters
import com.directoratealpha.quantwealth.util.ShareCardGenerator
import java.util.Locale
import kotlin.math.max
import kotlin.math.pow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortfolioScreen(
    portfolio: PortfolioState,
    marketAssets: Map<String, MarketAsset>,
    pendingQuickTradeAsset: MarketAsset?,
    onClearPendingQuickTrade: () -> Unit,
    onOpenPosition: (MarketAsset, TradeSide, Double, Int) -> Unit,
    onClosePosition: (OpenPosition, Double) -> Unit,
    onUpdateChallenge: (Double, Int) -> Unit,
    onRequestHighLeverageAd: (onUnlocked: () -> Unit) -> Unit,
    onOpenProDialog: () -> Unit
) {
    val context = LocalContext.current

    var showTradeSheet by remember { mutableStateOf(false) }
    var selectedTradeAsset by remember { mutableStateOf<MarketAsset?>(null) }
    var showEditChallengeDialog by remember { mutableStateOf(false) }

    LaunchedEffect(pendingQuickTradeAsset) {
        if (pendingQuickTradeAsset != null) {
            selectedTradeAsset = pendingQuickTradeAsset
            showTradeSheet = true
            onClearPendingQuickTrade()
        }
    }

    // Compute live equity
    val allocatedMargin = portfolio.positions.sumOf { it.marginAllocated }
    val totalUnrealizedPnl = portfolio.positions.sumOf { pos ->
        val livePrice = marketAssets[pos.symbol]?.price ?: pos.entryPrice
        QuantEngine.computeUnrealizedPnl(pos, livePrice)
    }
    val totalEquity = (portfolio.cashBalance + allocatedMargin + totalUnrealizedPnl).coerceAtLeast(0.0)
    val totalPnlUsd = totalEquity - PortfolioState.INITIAL_CASH
    val totalPnlPct = (totalPnlUsd / PortfolioState.INITIAL_CASH) * 100.0

    // Challenge Math
    val challenge = portfolio.challenge
    val targetEquity = challenge.startEquityUsd + challenge.targetProfitUsd
    val profitTowardGoal = totalEquity - challenge.startEquityUsd
    val progressFraction = if (challenge.targetProfitUsd > 0.0) {
        (profitTowardGoal / challenge.targetProfitUsd).toFloat().coerceIn(0f, 1f)
    } else 0f

    val now = System.currentTimeMillis()
    val daysLeft = max(0L, (challenge.deadlineMillis - now) / 86_400_000L)
    val totalDays = max(1L, (challenge.deadlineMillis - challenge.createdAtMillis) / 86_400_000L)
    val elapsedFraction = (1f - (daysLeft.toFloat() / totalDays.toFloat())).coerceIn(0f, 1f)

    val statusLabel = when {
        totalEquity >= targetEquity -> "GOAL REACHED"
        daysLeft == 0L -> "EXPIRED"
        progressFraction >= elapsedFraction * 0.85f -> "ON TRACK"
        else -> "BEHIND PACE"
    }
    val requiredDailyPct = if (totalEquity >= targetEquity || daysLeft <= 0L || totalEquity <= 0.0) {
        0.0
    } else {
        ((targetEquity / totalEquity).pow(1.0 / daysLeft.toDouble()) - 1.0) * 100.0
    }

    val winCount = portfolio.closedTrades.count { it.realizedPnl > 0.0 }
    val winRatePct = if (portfolio.closedTrades.isNotEmpty()) {
        (winCount.toDouble() / portfolio.closedTrades.size.toDouble()) * 100.0
    } else 0.0

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. $100K Account Hero Card (Permanent — No Reset)
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.linearGradient(listOf(Color(0xFF132645), Color(0xFF0C162B)))
                    )
                    .border(1.2.dp, AppColors.AccentCyan.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "VIRTUAL EQUITY ($100K ACCOUNT)",
                            color = AppColors.AccentCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.9.sp
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = AppColors.CardElevated
                        ) {
                            Text(
                                text = "NO RESET",
                                color = AppColors.AccentGold,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(
                            text = Formatters.formatUsd(totalEquity),
                            color = AppColors.TextPrimary,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            softWrap = false
                        )
                        Text(
                            text = "${Formatters.formatUsd(totalPnlUsd, showSign = true)} (${Formatters.formatPct(totalPnlPct)})",
                            color = if (totalPnlUsd >= 0) AppColors.AccentEmerald else AppColors.AccentRed,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MiniStat("Available Cash", Formatters.formatUsd(portfolio.cashBalance))
                        MiniStat("In Margin", Formatters.formatUsd(allocatedMargin))
                        MiniStat(
                            "Win Rate",
                            if (portfolio.closedTrades.isEmpty()) "—" else String.format(Locale.US, "%.0f%%", winRatePct)
                        )
                    }

                    Button(
                        onClick = {
                            selectedTradeAsset = marketAssets[portfolio.watchlistSymbols.firstOrNull() ?: "BTC-USD"]
                                ?: marketAssets.values.firstOrNull()
                            showTradeSheet = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 46.dp),
                        shape = RoundedCornerShape(13.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AppColors.AccentEmerald,
                            contentColor = AppColors.BgDeep
                        )
                    ) {
                        Icon(Icons.Rounded.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "New Trade (1x–20x Leverage)",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }

        // 2. Profit Target & Deadline Challenge Card + IG Story Share
        item {
            val statusColor = when (statusLabel) {
                "GOAL REACHED", "ON TRACK" -> AppColors.AccentEmerald
                "BEHIND PACE" -> AppColors.AccentGold
                else -> AppColors.AccentRed
            }

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = AppColors.CardDark,
                border = BorderStroke(1.dp, AppColors.AccentGold.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
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
                                imageVector = Icons.Rounded.Flag,
                                contentDescription = null,
                                tint = AppColors.AccentGold,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Profit Target Challenge",
                                color = AppColors.TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = statusColor.copy(alpha = 0.18f)
                        ) {
                            Text(
                                text = statusLabel,
                                color = statusColor,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Goal: ${Formatters.formatUsd(targetEquity)} (+${Formatters.formatCompactUsd(challenge.targetProfitUsd)})",
                            color = AppColors.TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${daysLeft}d left",
                            color = AppColors.AccentCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    LinearProgressIndicator(
                        progress = { progressFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = statusColor,
                        trackColor = AppColors.CardBorder
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (requiredDailyPct > 0.0) {
                                String.format(Locale.US, "Need +%.2f%%/day to hit goal", requiredDailyPct)
                            } else {
                                String.format(Locale.US, "%.1f%% completed", progressFraction * 100f)
                            },
                            color = AppColors.TextSecondary,
                            fontSize = 11.5.sp
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { showEditChallengeDialog = true },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, AppColors.CardBorder),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(14.dp), tint = AppColors.TextPrimary)
                                Spacer(Modifier.width(4.dp))
                                Text("Goal", fontSize = 11.5.sp, color = AppColors.TextPrimary, maxLines = 1, softWrap = false)
                            }

                            Button(
                                onClick = {
                                    ShareCardGenerator.shareChallengeCard(
                                        context = context,
                                        totalEquity = totalEquity,
                                        totalPnlUsd = totalPnlUsd,
                                        totalPnlPct = totalPnlPct,
                                        targetEquityUsd = targetEquity,
                                        progressFraction = progressFraction,
                                        daysLeft = daysLeft,
                                        statusLabel = statusLabel
                                    )
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AppColors.AccentCyan,
                                    contentColor = AppColors.BgDeep
                                ),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("IG Story", fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, softWrap = false)
                            }
                        }
                    }
                }
            }
        }

        // 3. Open Positions Header
        item {
            Text(
                text = "Open Positions (${portfolio.positions.size})",
                color = AppColors.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        if (portfolio.positions.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = AppColors.CardDark,
                    border = BorderStroke(1.dp, AppColors.CardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No active positions yet. Tap 'New Trade' above or pick any asset from the Market tab to start trading with $100,000 virtual cash.",
                        color = AppColors.TextSecondary,
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
        } else {
            items(portfolio.positions, key = { it.id }) { pos ->
                val currentPrice = marketAssets[pos.symbol]?.price ?: pos.entryPrice
                val pnl = QuantEngine.computeUnrealizedPnl(pos, currentPrice)
                val roePct = if (pos.marginAllocated > 0.0) (pnl / pos.marginAllocated) * 100.0 else 0.0
                OpenPositionCard(
                    position = pos,
                    currentPrice = currentPrice,
                    unrealizedPnl = pnl,
                    roePct = roePct,
                    onClose = { onClosePosition(pos, currentPrice) }
                )
            }
        }

        // 4. Closed / Liquidated History
        if (portfolio.closedTrades.isNotEmpty()) {
            item {
                Text(
                    text = "Recent Trade History (${portfolio.closedTrades.size})",
                    color = AppColors.TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            items(portfolio.closedTrades.take(15), key = { it.id }) { trade ->
                ClosedTradeRow(trade)
            }
        }
    }

    // Trade Order Modal Bottom Sheet
    if (showTradeSheet) {
        val asset = selectedTradeAsset ?: marketAssets.values.firstOrNull()
        if (asset != null) {
            ModalBottomSheet(
                onDismissRequest = { showTradeSheet = false },
                containerColor = AppColors.CardDark,
                contentColor = AppColors.TextPrimary
            ) {
                PlaceTradeSheet(
                    initialAsset = asset,
                    availableAssets = portfolio.watchlistSymbols.mapNotNull { marketAssets[it] },
                    availableCash = portfolio.cashBalance,
                    isPro = portfolio.isPro,
                    onRequestHighLeverageAd = onRequestHighLeverageAd,
                    onOpenPro = {
                        showTradeSheet = false
                        onOpenProDialog()
                    },
                    onConfirmTrade = { chosenAsset, side, margin, lev ->
                        showTradeSheet = false
                        onOpenPosition(chosenAsset, side, margin, lev)
                    }
                )
            }
        }
    }

    // Edit Challenge Dialog
    if (showEditChallengeDialog) {
        EditChallengeDialog(
            currentTargetProfit = challenge.targetProfitUsd,
            onDismiss = { showEditChallengeDialog = false },
            onSave = { targetProfit, days ->
                showEditChallengeDialog = false
                onUpdateChallenge(targetProfit, days)
            }
        )
    }
}

@Composable
private fun MiniStat(label: String, value: String) {
    Column {
        Text(text = label, color = AppColors.TextSecondary, fontSize = 11.sp)
        Text(
            text = value,
            color = AppColors.TextPrimary,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun OpenPositionCard(
    position: OpenPosition,
    currentPrice: Double,
    unrealizedPnl: Double,
    roePct: Double,
    onClose: () -> Unit
) {
    val isProfit = unrealizedPnl >= 0.0
    val sideColor = if (position.side == TradeSide.LONG) AppColors.AccentEmerald else AppColors.AccentRed

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = AppColors.CardDark,
        border = BorderStroke(1.dp, if (isProfit) AppColors.AccentEmerald.copy(alpha = 0.35f) else AppColors.AccentRed.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
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
                    Text(
                        text = position.displaySymbol,
                        color = AppColors.TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = sideColor.copy(alpha = 0.18f)
                    ) {
                        Text(
                            text = "${position.side.name} ${position.leverage}x",
                            color = sideColor,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = Formatters.formatUsd(unrealizedPnl, showSign = true),
                        color = if (isProfit) AppColors.AccentEmerald else AppColors.AccentRed,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "ROE ${Formatters.formatPct(roePct)}",
                        color = if (isProfit) AppColors.AccentEmerald else AppColors.AccentRed,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MiniStat("Margin", Formatters.formatUsd(position.marginAllocated))
                MiniStat("Entry", Formatters.formatPrice(position.entryPrice))
                MiniStat("Live", Formatters.formatPrice(currentPrice))
                MiniStat(
                    "Liq. Price",
                    if (position.liquidationPrice > 0.0) Formatters.formatPrice(position.liquidationPrice) else "None"
                )
            }

            OutlinedButton(
                onClick = onClose,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, AppColors.CardBorder),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                Text(
                    text = "Close Position at Market",
                    color = AppColors.TextPrimary,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
private fun ClosedTradeRow(trade: ClosedTrade) {
    val isProfit = trade.realizedPnl >= 0.0
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = AppColors.CardDark,
        border = BorderStroke(1.dp, AppColors.CardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "${trade.displaySymbol} ${trade.side.name} ${trade.leverage}x",
                        color = AppColors.TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (trade.wasLiquidated) {
                        Text(
                            text = "LIQUIDATED",
                            color = AppColors.AccentRed,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
                Text(
                    text = "${Formatters.formatPrice(trade.entryPrice)} → ${Formatters.formatPrice(trade.exitPrice)}",
                    color = AppColors.TextSecondary,
                    fontSize = 11.5.sp
                )
            }
            Text(
                text = Formatters.formatUsd(trade.realizedPnl, showSign = true),
                color = if (isProfit) AppColors.AccentEmerald else AppColors.AccentRed,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

@Composable
private fun PlaceTradeSheet(
    initialAsset: MarketAsset,
    availableAssets: List<MarketAsset>,
    availableCash: Double,
    isPro: Boolean,
    onRequestHighLeverageAd: (onUnlocked: () -> Unit) -> Unit,
    onOpenPro: () -> Unit,
    onConfirmTrade: (MarketAsset, TradeSide, Double, Int) -> Unit
) {
    var selectedAsset by remember { mutableStateOf(initialAsset) }
    var side by remember { mutableStateOf(TradeSide.LONG) }
    var marginText by remember { mutableStateOf("5000") }
    var leverage by remember { mutableStateOf(1) }
    var unlockedHighLevForTrade by remember { mutableStateOf(false) }
    var pendingLockedLeverage by remember { mutableStateOf<Int?>(null) }

    val marginVal = marginText.toDoubleOrNull()?.coerceIn(0.0, availableCash) ?: 0.0
    val positionSize = marginVal * leverage.toDouble()
    val liqPrice = QuantEngine.computeLiquidationPrice(selectedAsset.price, leverage, side)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 6.dp)
            .padding(bottom = 26.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Open Virtual Position",
            color = AppColors.TextPrimary,
            fontSize = 19.sp,
            fontWeight = FontWeight.ExtraBold
        )

        // Asset Picker Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            availableAssets.forEach { asset ->
                val active = asset.symbol == selectedAsset.symbol
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (active) AppColors.AccentCyan.copy(alpha = 0.22f) else AppColors.CardElevated,
                    border = BorderStroke(1.dp, if (active) AppColors.AccentCyan else AppColors.CardBorder),
                    modifier = Modifier.clickable { selectedAsset = asset }
                ) {
                    Text(
                        text = "${asset.displaySymbol} ${Formatters.formatPrice(asset.price)}",
                        color = if (active) AppColors.AccentCyan else AppColors.TextSecondary,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                    )
                }
            }
        }

        // LONG vs SHORT Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val isLong = side == TradeSide.LONG
            Button(
                onClick = { side = TradeSide.LONG },
                modifier = Modifier.weight(1f).height(42.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isLong) AppColors.AccentEmerald else AppColors.CardElevated,
                    contentColor = if (isLong) AppColors.BgDeep else AppColors.TextSecondary
                )
            ) {
                Text("LONG (Buy)", fontWeight = FontWeight.ExtraBold, maxLines = 1, softWrap = false)
            }
            Button(
                onClick = { side = TradeSide.SHORT },
                modifier = Modifier.weight(1f).height(42.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (!isLong) AppColors.AccentRed else AppColors.CardElevated,
                    contentColor = if (!isLong) Color.White else AppColors.TextSecondary
                )
            ) {
                Text("SHORT (Sell)", fontWeight = FontWeight.ExtraBold, maxLines = 1, softWrap = false)
            }
        }

        // Margin Input + Quick Presets
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Margin to Allocate ($)", color = AppColors.TextSecondary, fontSize = 12.sp)
                Text("Cash: ${Formatters.formatUsd(availableCash)}", color = AppColors.AccentCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            OutlinedTextField(
                value = marginText,
                onValueChange = { s -> marginText = s.filter { it.isDigit() || it == '.' } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AppColors.AccentCyan,
                    unfocusedBorderColor = AppColors.CardBorder,
                    focusedTextColor = AppColors.TextPrimary,
                    unfocusedTextColor = AppColors.TextPrimary
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(1000, 5000, 10000, 25000).forEach { amt ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = AppColors.CardElevated,
                        modifier = Modifier.clickable {
                            marginText = amt.coerceAtMost(availableCash.toInt()).toString()
                        }
                    ) {
                        Text(
                            text = Formatters.formatCompactUsd(amt.toDouble()),
                            color = AppColors.TextPrimary,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        // Leverage Picker: 1x-3x FREE | 5x, 10x, 20x PRO or 1 Video Ad
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Leverage (1x–3x Free • 5x–20x PRO / 1 Ad)",
                color = AppColors.TextSecondary,
                fontSize = 12.sp
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(1, 2, 3, 5, 10, 20).forEach { lev ->
                    val isLocked = lev > 3 && !isPro && !unlockedHighLevForTrade
                    val active = leverage == lev
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = when {
                            active -> AppColors.AccentGold
                            isLocked -> AppColors.BgDeep
                            else -> AppColors.CardElevated
                        },
                        border = BorderStroke(
                            1.dp,
                            when {
                                active -> AppColors.AccentGold
                                isLocked -> AppColors.AccentGold.copy(alpha = 0.45f)
                                else -> AppColors.CardBorder
                            }
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                if (isLocked) {
                                    pendingLockedLeverage = lev
                                } else {
                                    leverage = lev
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isLocked) {
                                Icon(
                                    imageVector = Icons.Rounded.Lock,
                                    contentDescription = null,
                                    tint = AppColors.AccentGold,
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(Modifier.width(2.dp))
                            }
                            Text(
                                text = "${lev}x",
                                color = if (active) AppColors.BgDeep else if (isLocked) AppColors.AccentGold else AppColors.TextPrimary,
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

        // Order Summary & Liquidation Price Warning
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = AppColors.BgDeep,
            border = BorderStroke(1.dp, AppColors.CardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total Position Exposure", color = AppColors.TextSecondary, fontSize = 12.sp)
                    Text(Formatters.formatUsd(positionSize), color = AppColors.TextPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Entry Market Price", color = AppColors.TextSecondary, fontSize = 12.sp)
                    Text(Formatters.formatPrice(selectedAsset.price), color = AppColors.TextPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Isolated Liquidation Price", color = AppColors.AccentGold, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = if (liqPrice > 0.0) Formatters.formatPrice(liqPrice) else "None (1x Spot)",
                        color = if (liqPrice > 0.0) AppColors.AccentRed else AppColors.AccentEmerald,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }

        Button(
            onClick = {
                if (marginVal >= 10.0 && marginVal <= availableCash) {
                    onConfirmTrade(selectedAsset, side, marginVal, leverage)
                }
            },
            enabled = marginVal >= 10.0 && marginVal <= availableCash,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (side == TradeSide.LONG) AppColors.AccentEmerald else AppColors.AccentRed,
                contentColor = if (side == TradeSide.LONG) AppColors.BgDeep else Color.White
            )
        ) {
            Text(
                text = "Confirm ${side.name} ${selectedAsset.displaySymbol} (${leverage}x)",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 14.sp,
                maxLines = 1,
                softWrap = false
            )
        }
    }

    if (pendingLockedLeverage != null) {
        val targetLev = pendingLockedLeverage!!
        UnlockFeatureDialog(
            badge = "HIGH LEVERAGE (${targetLev}x)",
            title = "Unlock ${targetLev}x Leverage",
            description = "Leverage above 3x (5x, 10x, 20x) uses isolated margin with automatic liquidation. Watch 1 short video ad to unlock ${targetLev}x for this trade, or go PRO for unlimited high leverage.",
            watchAdLabel = "Watch 1 Video (Unlock ${targetLev}x)",
            onDismiss = { pendingLockedLeverage = null },
            onWatchAd = {
                pendingLockedLeverage = null
                onRequestHighLeverageAd {
                    unlockedHighLevForTrade = true
                    leverage = targetLev
                }
            },
            onOpenPro = {
                pendingLockedLeverage = null
                onOpenPro()
            }
        )
    }
}

@Composable
private fun EditChallengeDialog(
    currentTargetProfit: Double,
    onDismiss: () -> Unit,
    onSave: (Double, Int) -> Unit
) {
    var profitInput by remember { mutableStateOf(currentTargetProfit.toInt().toString()) }
    var selectedDays by remember { mutableStateOf(30) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppColors.CardDark,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                text = "Set Profit Challenge",
                color = AppColors.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Set your target profit above your current equity and choose a deadline.",
                    color = AppColors.TextSecondary,
                    fontSize = 12.5.sp
                )
                OutlinedTextField(
                    value = profitInput,
                    onValueChange = { s -> profitInput = s.filter { it.isDigit() } },
                    label = { Text("Target Profit ($)", color = AppColors.TextSecondary) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AppColors.AccentCyan,
                        unfocusedBorderColor = AppColors.CardBorder,
                        focusedTextColor = AppColors.TextPrimary,
                        unfocusedTextColor = AppColors.TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Deadline Horizon:", color = AppColors.TextSecondary, fontSize = 12.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(7, 30, 60, 90, 180).forEach { d ->
                        val active = selectedDays == d
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (active) AppColors.AccentCyan else AppColors.CardElevated,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedDays = d }
                        ) {
                            Text(
                                text = "${d}d",
                                color = if (active) AppColors.BgDeep else AppColors.TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(vertical = 8.dp).wrapContentWidth(Alignment.CenterHorizontally)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val profit = profitInput.toDoubleOrNull()?.coerceAtLeast(500.0) ?: 10000.0
                    onSave(profit, selectedDays)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.AccentCyan,
                    contentColor = AppColors.BgDeep
                )
            ) {
                Text("Start Challenge", fontWeight = FontWeight.ExtraBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = AppColors.TextSecondary)
            }
        }
    )
}
