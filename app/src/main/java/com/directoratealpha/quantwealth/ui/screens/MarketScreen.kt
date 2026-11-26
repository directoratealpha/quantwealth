package com.directoratealpha.quantwealth.ui.screens

import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.directoratealpha.quantwealth.data.MarketRepository
import com.directoratealpha.quantwealth.model.*
import com.directoratealpha.quantwealth.ui.components.AssetDetailChartSheet
import com.directoratealpha.quantwealth.ui.components.MiniSparkline
import com.directoratealpha.quantwealth.ui.components.UnlockFeatureDialog
import com.directoratealpha.quantwealth.ui.theme.AppColors
import com.directoratealpha.quantwealth.ui.theme.Formatters
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketScreen(
    portfolio: PortfolioState,
    marketAssets: Map<String, MarketAsset>,
    isRefreshing: Boolean,
    onManualRefresh: () -> Unit,
    onCacheFetchedAsset: (MarketAsset) -> Unit,
    onAddWatchlistSymbol: (String) -> Unit,
    onRemoveWatchlistSymbol: (String) -> Unit,
    onUnlockExtraSlot: () -> Unit,
    onOpenProDialog: () -> Unit,
    onQuickTradeAsset: (MarketAsset, TradeSide) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<TickerSearchResult>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var isLoadingSelectedAsset by remember { mutableStateOf(false) }

    var selectedCategory by remember { mutableStateOf(AssetCategory.ALL) }
    var showSlotUnlockDialog by remember { mutableStateOf(false) }
    var inspectedAsset by remember { mutableStateOf<MarketAsset?>(null) }

    // Live debounced Yahoo Finance global search as the user types
    LaunchedEffect(searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) {
            searchResults = emptyList()
            isSearching = false
            return@LaunchedEffect
        }
        searchResults = MarketRepository.searchCatalogLocally(q)
        isSearching = true
        delay(280L)
        searchResults = MarketRepository.searchTickersLive(q)
        isSearching = false
    }

    fun openChartForSearchItem(item: TickerSearchResult) {
        val cached = marketAssets[item.symbol]
        if (cached != null) {
            inspectedAsset = cached
            return
        }
        scope.launch {
            isLoadingSelectedAsset = true
            val fetched = MarketRepository.fetchSingleLiveAsset(
                rawSymbol = item.symbol,
                hintName = item.name
            )
            isLoadingSelectedAsset = false
            if (fetched != null) {
                onCacheFetchedAsset(fetched)
                inspectedAsset = fetched
            } else {
                Toast.makeText(
                    context,
                    "No live market data found for ${item.symbol}. Try selecting a suggested ticker.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    val orderedWatchlist = remember(portfolio.watchlistSymbols, marketAssets, selectedCategory) {
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
        contentPadding = PaddingValues(top = 10.dp, bottom = 22.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Prominent Global Search Bar (Search ANY Stock, ETF, Crypto, Gold worldwide)
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        text = "Search any stock, crypto, gold (e.g. PLTR, AAPL, BTC)...",
                        color = AppColors.TextMuted,
                        fontSize = 12.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = "Search",
                        tint = AppColors.AccentCyan
                    )
                },
                trailingIcon = {
                    if (isSearching || isLoadingSelectedAsset) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = AppColors.AccentCyan
                        )
                    } else if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Clear",
                                tint = AppColors.TextSecondary
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = AppColors.CardDark,
                    unfocusedContainerColor = AppColors.CardDark,
                    focusedBorderColor = AppColors.AccentCyan,
                    unfocusedBorderColor = AppColors.CardBorder,
                    focusedTextColor = AppColors.TextPrimary,
                    unfocusedTextColor = AppColors.TextPrimary
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }

        // 2. If User is Searching: Show Live Global Search Results (Tap to open full chart immediately!)
        if (searchQuery.trim().isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Global Market Results (${searchResults.size})",
                        color = AppColors.AccentCyan,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Tap any result to open live chart",
                        color = AppColors.TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }

            if (searchResults.isEmpty() && !isSearching) {
                item {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = AppColors.CardDark,
                        border = BorderStroke(1.dp, AppColors.CardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "No matching ticker found for "$searchQuery". Try searching by company name (e.g. Palantir, Apple, Gold) or standard symbol (PLTR, AAPL, BTC-USD).",
                            color = AppColors.TextSecondary,
                            fontSize = 12.5.sp,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            } else {
                items(searchResults, key = { it.symbol }) { item ->
                    val inWatchlist = portfolio.watchlistSymbols.contains(item.symbol)
                    val knownAsset = marketAssets[item.symbol]
                    SearchResultRow(
                        item = item,
                        knownAsset = knownAsset,
                        isInWatchlist = inWatchlist,
                        onOpenChart = { openChartForSearchItem(item) },
                        onToggleWatchlist = {
                            if (inWatchlist) {
                                if (portfolio.watchlistSymbols.size > 1) {
                                    onRemoveWatchlistSymbol(item.symbol)
                                }
                            } else if (portfolio.isPro || usedSlots < maxSlots) {
                                onAddWatchlistSymbol(item.symbol)
                                Toast.makeText(context, "Added ${item.displaySymbol} to Watchlist", Toast.LENGTH_SHORT).show()
                            } else {
                                showSlotUnlockDialog = true
                            }
                        }
                    )
                }
            }
        } else {
            // 3. Normal Watchlist Header + Add Stock Button + Refresh
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = "My Watchlist",
                            color = AppColors.TextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = if (portfolio.isPro) {
                                "$usedSlots pinned • Unlimited PRO slots • Tap any for chart"
                            } else {
                                "Slots: $usedSlots / $maxSlots (+1 per video ad) • Tap for chart"
                            },
                            color = AppColors.TextSecondary,
                            fontSize = 11.sp,
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

                        if (!portfolio.isPro && usedSlots >= maxSlots) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = AppColors.AccentGold,
                                modifier = Modifier.clickable { showSlotUnlockDialog = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Rounded.Add, contentDescription = null, tint = AppColors.BgDeep, modifier = Modifier.size(15.dp))
                                    Text("+1 Slot", color = AppColors.BgDeep, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, softWrap = false)
                                }
                            }
                        }
                    }
                }
            }

            // 4. Category Filter Chips
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

            // 5. Watchlist Cards
            items(orderedWatchlist, key = { it.symbol }) { asset ->
                MarketAssetCard(
                    asset = asset,
                    onClick = { inspectedAsset = asset }
                )
            }

            item {
                Text(
                    text = "Live market quotes & virtual trading simulation for educational purposes only — not financial advice.",
                    color = AppColors.TextMuted,
                    fontSize = 10.5.sp,
                    lineHeight = 14.sp,
                    modifier = Modifier.padding(top = 6.dp, bottom = 4.dp)
                )
            }
        }
    }

    // Watchlist Slot Limit Dialog (+1 Slot per Rewarded Video Ad)
    if (showSlotUnlockDialog) {
        UnlockFeatureDialog(
            badge = "WATCHLIST LIMIT ($usedSlots / $maxSlots)",
            title = "Unlock +1 Watchlist Slot",
            description = "Searching and viewing live charts for any stock is always free. To pin more than $maxSlots favorites to your Watchlist, watch 1 short video ad for +1 slot or upgrade to PRO.",
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

    // Full Interactive Chart & Trade Bottom Sheet
    val currentInspected = inspectedAsset?.let { marketAssets[it.symbol] ?: it }
    if (currentInspected != null) {
        val isInWatchlist = portfolio.watchlistSymbols.contains(currentInspected.symbol)
        ModalBottomSheet(
            onDismissRequest = { inspectedAsset = null },
            containerColor = AppColors.CardDark,
            contentColor = AppColors.TextPrimary
        ) {
            AssetDetailChartSheet(
                asset = currentInspected,
                isInWatchlist = isInWatchlist,
                canRemoveFromWatchlist = portfolio.watchlistSymbols.size > 1,
                onToggleWatchlist = {
                    if (isInWatchlist) {
                        if (portfolio.watchlistSymbols.size > 1) {
                            onRemoveWatchlistSymbol(currentInspected.symbol)
                            Toast.makeText(context, "Removed ${currentInspected.displaySymbol} from Watchlist", Toast.LENGTH_SHORT).show()
                        }
                    } else if (portfolio.isPro || usedSlots < maxSlots) {
                        onAddWatchlistSymbol(currentInspected.symbol)
                        Toast.makeText(context, "Added ${currentInspected.displaySymbol} to Watchlist", Toast.LENGTH_SHORT).show()
                    } else {
                        inspectedAsset = null
                        showSlotUnlockDialog = true
                    }
                },
                onTradeAsset = { chosenAsset, side ->
                    inspectedAsset = null
                    onQuickTradeAsset(chosenAsset, side)
                }
            )
        }
    }
}

@Composable
private fun SearchResultRow(
    item: TickerSearchResult,
    knownAsset: MarketAsset?,
    isInWatchlist: Boolean,
    onOpenChart: () -> Unit,
    onToggleWatchlist: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = AppColors.CardDark,
        border = BorderStroke(1.dp, AppColors.CardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenChart() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 13.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = item.symbol,
                        color = AppColors.TextPrimary,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Surface(
                        shape = RoundedCornerShape(5.dp),
                        color = AppColors.CardElevated
                    ) {
                        Text(
                            text = item.exchange,
                            color = AppColors.AccentCyan,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = item.name,
                    color = AppColors.TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (knownAsset != null) {
                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Text(
                        text = Formatters.formatPrice(knownAsset.price),
                        color = AppColors.TextPrimary,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = Formatters.formatPct(knownAsset.changePercent),
                        color = if (knownAsset.changePercent >= 0) AppColors.AccentEmerald else AppColors.AccentRed,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            IconButton(
                onClick = onToggleWatchlist,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    imageVector = if (isInWatchlist) Icons.Rounded.Star else Icons.Rounded.AddCircleOutline,
                    contentDescription = "Add Stock",
                    tint = if (isInWatchlist) AppColors.AccentGold else AppColors.AccentCyan,
                    modifier = Modifier.size(21.dp)
                )
            }
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

            MiniSparkline(
                points = asset.sparkline,
                isPositive = isUp,
                modifier = Modifier
                    .width(56.dp)
                    .height(28.dp)
            )

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

fun signalColorFor(label: SignalLabel): Color = when (label) {
    SignalLabel.STRONG_BUY -> AppColors.AccentEmerald
    SignalLabel.BUY -> AppColors.AccentCyan
    SignalLabel.NEUTRAL -> AppColors.TextSecondary
    SignalLabel.SELL -> AppColors.AccentGold
    SignalLabel.STRONG_SELL -> AppColors.AccentRed
}
