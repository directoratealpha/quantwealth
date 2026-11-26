package com.directoratealpha.quantwealth

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.directoratealpha.quantwealth.ads.BannerAdBar
import com.directoratealpha.quantwealth.ads.QuantAdManager
import com.directoratealpha.quantwealth.billing.BillingManager
import com.directoratealpha.quantwealth.data.MarketRepository
import com.directoratealpha.quantwealth.data.PortfolioStorage
import com.directoratealpha.quantwealth.engine.QuantEngine
import com.directoratealpha.quantwealth.model.*
import com.directoratealpha.quantwealth.ui.components.ProUpgradeDialog
import com.directoratealpha.quantwealth.ui.screens.BacktestScreen
import com.directoratealpha.quantwealth.ui.screens.MarketScreen
import com.directoratealpha.quantwealth.ui.screens.MonteCarloScreen
import com.directoratealpha.quantwealth.ui.screens.PortfolioScreen
import com.directoratealpha.quantwealth.ui.theme.AppColors
import com.directoratealpha.quantwealth.ui.theme.QuantWealthTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

class MainActivity : ComponentActivity() {

    private lateinit var storage: PortfolioStorage
    private lateinit var adManager: QuantAdManager
    private lateinit var billingManager: BillingManager

    private val proStatusState = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        storage = PortfolioStorage(this)
        val initialPortfolio = storage.load()
        proStatusState.value = initialPortfolio.isPro

        adManager = QuantAdManager(this).apply { initialize() }
        billingManager = BillingManager(this) { isPro ->
            runOnUiThread {
                proStatusState.value = isPro
            }
        }.apply { startConnection() }

        setContent {
            QuantWealthTheme {
                QuantWealthAppRoot(
                    activity = this,
                    initialPortfolio = initialPortfolio,
                    externalProStatus = proStatusState.value,
                    storage = storage,
                    adManager = adManager,
                    billingManager = billingManager
                )
            }
        }
    }

    override fun onDestroy() {
        billingManager.destroy()
        super.onDestroy()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuantWealthAppRoot(
    activity: ComponentActivity,
    initialPortfolio: PortfolioState,
    externalProStatus: Boolean,
    storage: PortfolioStorage,
    adManager: QuantAdManager,
    billingManager: BillingManager
) {
    val scope = rememberCoroutineScope()
    val tabs = remember { listOf("Market", "Portfolio", "Backtest", "Monte Carlo") }
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { tabs.size })

    var portfolio by remember { mutableStateOf(initialPortfolio) }
    var marketAssets by remember {
        mutableStateOf(MarketRepository.buildInstantBaseline(initialPortfolio.watchlistSymbols))
    }
    var isRefreshing by remember { mutableStateOf(false) }
    var showProDialog by remember { mutableStateOf(false) }
    var pendingQuickTradeAsset by remember { mutableStateOf<MarketAsset?>(null) }

    val monthlyPrice by billingManager.monthlyPrice.collectAsState()
    val lifetimePrice by billingManager.lifetimePrice.collectAsState()

    fun updatePortfolio(transform: (PortfolioState) -> PortfolioState) {
        val next = transform(portfolio)
        portfolio = next
        storage.save(next)
    }

    // Sync Google Play Billing PRO state
    LaunchedEffect(externalProStatus) {
        if (externalProStatus && !portfolio.isPro) {
            updatePortfolio { it.copy(isPro = true) }
        }
    }

    // Check automatic Isolated Margin Liquidations on every price refresh
    fun checkLiquidations(latestAssets: Map<String, MarketAsset>) {
        if (portfolio.positions.isEmpty()) return
        val surviving = ArrayList<OpenPosition>()
        val liquidatedTrades = ArrayList<ClosedTrade>()

        for (pos in portfolio.positions) {
            val livePrice = latestAssets[pos.symbol]?.price ?: pos.entryPrice
            if (QuantEngine.isPositionLiquidated(pos, livePrice)) {
                liquidatedTrades.add(
                    ClosedTrade(
                        id = UUID.randomUUID().toString(),
                        symbol = pos.symbol,
                        displaySymbol = pos.displaySymbol,
                        side = pos.side,
                        leverage = pos.leverage,
                        marginAllocated = pos.marginAllocated,
                        entryPrice = pos.entryPrice,
                        exitPrice = livePrice,
                        realizedPnl = -pos.marginAllocated,
                        closedAtMillis = System.currentTimeMillis(),
                        wasLiquidated = true
                    )
                )
            } else {
                surviving.add(pos)
            }
        }

        if (liquidatedTrades.isNotEmpty()) {
            updatePortfolio {
                it.copy(
                    positions = surviving,
                    closedTrades = liquidatedTrades + it.closedTrades
                )
            }
            val syms = liquidatedTrades.joinToString(", ") { it.displaySymbol }
            Toast.makeText(activity, "Position liquidated (Isolated Margin): $syms", Toast.LENGTH_LONG).show()
        }
    }

    suspend fun refreshMarketQuotes() {
        if (isRefreshing) return
        isRefreshing = true
        val symbolsToFetch = (portfolio.watchlistSymbols + portfolio.positions.map { it.symbol }).distinct()
        val fetched = MarketRepository.fetchLiveWatchlist(symbolsToFetch, marketAssets)
        marketAssets = marketAssets + fetched
        checkLiquidations(marketAssets)
        isRefreshing = false
    }

    // Live polling loop every 12 seconds while app is open
    LaunchedEffect(portfolio.watchlistSymbols) {
        while (true) {
            refreshMarketQuotes()
            delay(12_000L)
        }
    }

    Scaffold(
        containerColor = AppColors.BgDeep,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AppColors.BgDeep)
                    .statusBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Brand + Live Indicator + PRO Pill
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "QUANTWEALTH",
                            color = AppColors.TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.8.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = AppColors.AccentEmerald.copy(alpha = 0.16f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(AppColors.AccentEmerald)
                                )
                                Text(
                                    text = "LIVE",
                                    color = AppColors.AccentEmerald,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }

                    if (!portfolio.isPro) {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = AppColors.AccentGold,
                            modifier = Modifier.clickable { showProDialog = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.WorkspacePremium,
                                    contentDescription = "PRO",
                                    tint = AppColors.BgDeep,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "PRO",
                                    color = AppColors.BgDeep,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = AppColors.AccentEmerald.copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, AppColors.AccentEmerald)
                        ) {
                            Text(
                                text = "PRO ACTIVE",
                                color = AppColors.AccentEmerald,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                // 4-Tab Pill Bar synced with HorizontalPager Swipe
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(AppColors.CardDark)
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    tabs.forEachIndexed { index, title ->
                        val selected = pagerState.currentPage == index
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(9.dp))
                                .background(if (selected) AppColors.AccentCyan else AppColors.CardDark)
                                .clickable {
                                    scope.launch { pagerState.animateScrollToPage(index) }
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = title,
                                color = if (selected) AppColors.BgDeep else AppColors.TextSecondary,
                                fontSize = 11.5.sp,
                                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            Column(modifier = Modifier.navigationBarsPadding()) {
                BannerAdBar(isPro = portfolio.isPro)
            }
        }
    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) { page ->
            when (page) {
                0 -> MarketScreen(
                    portfolio = portfolio,
                    marketAssets = marketAssets,
                    isRefreshing = isRefreshing,
                    onManualRefresh = { scope.launch { refreshMarketQuotes() } },
                    onAddTicker = { rawSym ->
                        val sym = rawSym.trim().uppercase()
                        if (portfolio.watchlistSymbols.contains(sym)) {
                            Toast.makeText(activity, "$sym is already in your watchlist.", Toast.LENGTH_SHORT).show()
                        } else if (!portfolio.isPro && portfolio.watchlistSymbols.size >= portfolio.maxWatchlistSlots) {
                            Toast.makeText(activity, "Watchlist slot limit reached.", Toast.LENGTH_SHORT).show()
                        } else {
                            updatePortfolio { it.copy(watchlistSymbols = it.watchlistSymbols + sym) }
                            marketAssets = marketAssets + (sym to MarketRepository.createFallbackAsset(sym))
                            scope.launch {
                                val live = MarketRepository.fetchSingleLiveAsset(sym, marketAssets[sym])
                                marketAssets = marketAssets + (sym to live)
                            }
                        }
                    },
                    onRemoveTicker = { sym ->
                        if (portfolio.watchlistSymbols.size > 1) {
                            updatePortfolio { it.copy(watchlistSymbols = it.watchlistSymbols.filterNot { s -> s == sym }) }
                        }
                    },
                    onUnlockExtraSlot = {
                        adManager.showRewardedAd(
                            activity = activity,
                            isPro = portfolio.isPro,
                            onRewardEarned = {
                                updatePortfolio { it.copy(maxWatchlistSlots = it.maxWatchlistSlots + 1) }
                                Toast.makeText(activity, "+1 Watchlist Slot unlocked!", Toast.LENGTH_SHORT).show()
                            }
                        )
                    },
                    onUnlockFullSignal = { sym ->
                        adManager.showRewardedAd(
                            activity = activity,
                            isPro = portfolio.isPro,
                            onRewardEarned = {
                                updatePortfolio { it.copy(unlockedFullSignalSymbols = it.unlockedFullSignalSymbols + sym) }
                            }
                        )
                    },
                    onOpenProDialog = { showProDialog = true },
                    onQuickTradeAsset = { asset ->
                        pendingQuickTradeAsset = asset
                        scope.launch { pagerState.animateScrollToPage(1) }
                    }
                )

                1 -> PortfolioScreen(
                    portfolio = portfolio,
                    marketAssets = marketAssets,
                    pendingQuickTradeAsset = pendingQuickTradeAsset,
                    onClearPendingQuickTrade = { pendingQuickTradeAsset = null },
                    onOpenPosition = { asset, side, margin, lev ->
                        if (margin <= portfolio.cashBalance && margin >= 10.0) {
                            val liq = QuantEngine.computeLiquidationPrice(asset.price, lev, side)
                            val newPos = OpenPosition(
                                id = UUID.randomUUID().toString(),
                                symbol = asset.symbol,
                                displaySymbol = asset.displaySymbol,
                                name = asset.name,
                                side = side,
                                marginAllocated = margin,
                                leverage = lev,
                                entryPrice = asset.price,
                                liquidationPrice = liq,
                                openedAtMillis = System.currentTimeMillis()
                            )
                            updatePortfolio {
                                it.copy(
                                    cashBalance = (it.cashBalance - margin).coerceAtLeast(0.0),
                                    positions = listOf(newPos) + it.positions
                                )
                            }
                            Toast.makeText(
                                activity,
                                "Opened ${side.name} ${asset.displaySymbol} (${lev}x)",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    onClosePosition = { pos, exitPrice ->
                        val pnl = QuantEngine.computeUnrealizedPnl(pos, exitPrice)
                        val returnedCash = (pos.marginAllocated + pnl).coerceAtLeast(0.0)
                        val closed = ClosedTrade(
                            id = UUID.randomUUID().toString(),
                            symbol = pos.symbol,
                            displaySymbol = pos.displaySymbol,
                            side = pos.side,
                            leverage = pos.leverage,
                            marginAllocated = pos.marginAllocated,
                            entryPrice = pos.entryPrice,
                            exitPrice = exitPrice,
                            realizedPnl = pnl,
                            closedAtMillis = System.currentTimeMillis(),
                            wasLiquidated = false
                        )
                        updatePortfolio {
                            it.copy(
                                cashBalance = it.cashBalance + returnedCash,
                                positions = it.positions.filterNot { p -> p.id == pos.id },
                                closedTrades = listOf(closed) + it.closedTrades
                            )
                        }
                    },
                    onUpdateChallenge = { targetProfit, days ->
                        val allocated = portfolio.positions.sumOf { it.marginAllocated }
                        val unrealized = portfolio.positions.sumOf { pos ->
                            val live = marketAssets[pos.symbol]?.price ?: pos.entryPrice
                            QuantEngine.computeUnrealizedPnl(pos, live)
                        }
                        val currentEquity = (portfolio.cashBalance + allocated + unrealized).coerceAtLeast(100.0)
                        val now = System.currentTimeMillis()
                        updatePortfolio {
                            it.copy(
                                challenge = ProfitChallenge(
                                    targetProfitUsd = targetProfit,
                                    startEquityUsd = currentEquity,
                                    createdAtMillis = now,
                                    deadlineMillis = now + days.toLong() * 86_400_000L
                                )
                            )
                        }
                    },
                    onRequestHighLeverageAd = { onUnlocked ->
                        adManager.showRewardedAd(
                            activity = activity,
                            isPro = portfolio.isPro,
                            onRewardEarned = onUnlocked
                        )
                    },
                    onOpenProDialog = { showProDialog = true }
                )

                2 -> BacktestScreen(
                    isPro = portfolio.isPro,
                    onRequestMultiAssetAd = { onUnlocked ->
                        adManager.showRewardedAd(
                            activity = activity,
                            isPro = portfolio.isPro,
                            onRewardEarned = onUnlocked
                        )
                    },
                    onRunWithPossibleInterstitial = { onProceed ->
                        adManager.maybeShowSimulationInterstitial(activity, portfolio.isPro, onProceed)
                    },
                    onOpenProDialog = { showProDialog = true }
                )

                3 -> MonteCarloScreen(
                    isPro = portfolio.isPro,
                    onRequestFullMonteCarloAd = { onUnlocked ->
                        adManager.showRewardedAd(
                            activity = activity,
                            isPro = portfolio.isPro,
                            onRewardEarned = onUnlocked
                        )
                    },
                    onOpenProDialog = { showProDialog = true }
                )
            }
        }
    }

    if (showProDialog) {
        ProUpgradeDialog(
            monthlyPrice = monthlyPrice,
            lifetimePrice = lifetimePrice,
            onDismiss = { showProDialog = false },
            onBuyMonthly = {
                showProDialog = false
                billingManager.launchMonthlyPurchase(activity)
            },
            onBuyLifetime = {
                showProDialog = false
                billingManager.launchLifetimePurchase(activity)
            }
        )
    }
}
