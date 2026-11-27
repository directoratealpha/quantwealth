package com.directoratealpha.quantwealth.model

enum class AssetCategory(val label: String) {
    ALL("All"),
    STOCKS_ETF("Stocks & ETFs"),
    CRYPTO("Crypto 24/7"),
    GOLD_MACRO("Gold & Macro")
}

enum class ChartTimeframe(
    val label: String,
    val yahooRange: String,
    val yahooInterval: String
) {
    D1("1D", "1d", "5m"),
    W1("1W", "5d", "30m"),
    M1("1M", "1mo", "1d"),
    M3("3M", "3mo", "1d"),
    Y1("1Y", "1y", "1d"),
    Y5("5Y", "5y", "1wk")
}

data class ChartPoint(
    val timestampSec: Long,
    val price: Double
)

data class TickerSearchResult(
    val symbol: String,
    val displaySymbol: String,
    val name: String,
    val exchange: String,
    val category: AssetCategory
)

enum class SignalLabel(val label: String) {
    STRONG_BUY("STRONG BUY"),
    BUY("BUY"),
    NEUTRAL("NEUTRAL"),
    SELL("SELL"),
    STRONG_SELL("STRONG SELL")
}

data class TechnicalSignal(
    val score: Int,
    val label: SignalLabel,
    val rsi14: Double,
    val sma50: Double,
    val sma200: Double,
    val macdLine: Double,
    val macdSignal: Double,
    val macdHistogram: Double,
    val bollingerUpper: Double,
    val bollingerMiddle: Double,
    val bollingerLower: Double,
    val summary: String
)

data class MarketAsset(
    val symbol: String,
    val displaySymbol: String,
    val name: String,
    val category: AssetCategory,
    val price: Double,
    val previousClose: Double,
    val changePercent: Double,
    val sparkline: List<Double>,
    val dailyCloses: List<Double>,
    val signal: TechnicalSignal,
    val dividendYieldPct: Double = 0.0,
    val isLive: Boolean = false,
    val lastUpdatedMillis: Long = System.currentTimeMillis()
)

enum class TradeSide {
    LONG,
    SHORT
}

data class OpenPosition(
    val id: String,
    val symbol: String,
    val displaySymbol: String,
    val name: String,
    val side: TradeSide,
    val marginAllocated: Double,
    val leverage: Int,
    val entryPrice: Double,
    val liquidationPrice: Double,
    val openedAtMillis: Long
)

data class ClosedTrade(
    val id: String,
    val symbol: String,
    val displaySymbol: String,
    val side: TradeSide,
    val leverage: Int,
    val marginAllocated: Double,
    val entryPrice: Double,
    val exitPrice: Double,
    val realizedPnl: Double,
    val closedAtMillis: Long,
    val wasLiquidated: Boolean
)

data class ProfitChallenge(
    val targetProfitUsd: Double = 15000.0,
    val startEquityUsd: Double = 100000.0,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val deadlineMillis: Long = System.currentTimeMillis() + 30L * 86_400_000L
)

data class PortfolioState(
    val cashBalance: Double = 100000.0,
    val positions: List<OpenPosition> = emptyList(),
    val closedTrades: List<ClosedTrade> = emptyList(),
    val watchlistSymbols: List<String> = DEFAULT_WATCHLIST,
    val maxWatchlistSlots: Int = 10,
    val challenge: ProfitChallenge = ProfitChallenge(),
    val isPro: Boolean = false
) {
    companion object {
        const val INITIAL_CASH = 100000.0
        // 5 starter assets so user immediately has 5 free open watchlist slots ready!
        val DEFAULT_WATCHLIST = listOf(
            "SPY", "NVDA", "PLTR", "BTC-USD", "GC=F"
        )
    }
}

data class BacktestAssetWeight(
    val symbol: String,
    val weightPct: Int
)

data class BacktestResult(
    val symbolsLabel: String,
    val years: Int,
    val initialCapital: Double,
    val monthlyDca: Double,
    val dripEnabled: Boolean,
    val finalValue: Double,
    val totalInvested: Double,
    val totalReturnPct: Double,
    val cagrPct: Double,
    val maxDrawdownPct: Double,
    val sharpeRatio: Double,
    val portfolioCurve: List<Double>,
    val investedCurve: List<Double>,
    val usedLiveData: Boolean
)

data class MonteCarloResult(
    val assetLabel: String,
    val simulationsCount: Int,
    val years: Int,
    val initialCapital: Double,
    val monthlyDca: Double,
    val annualReturnPct: Double,
    val volatilityPct: Double,
    val inflationAdjusted: Boolean,
    val fireTargetUsd: Double,
    val p10Curve: List<Double>,
    val p50Curve: List<Double>,
    val p90Curve: List<Double>,
    val medianFinalValue: Double,
    val p10FinalValue: Double,
    val p90FinalValue: Double,
    val p5FinalValue: Double,
    val p95FinalValue: Double,
    val totalContributed: Double,
    val medianProfitUsd: Double,
    val probabilityOfLossPct: Double,
    val probProfitBelowFirePct: Double,
    val estimatedFireYear: Int?,
    val medianMonthlyFireIncome: Double,
    val probabilityHitFirePct: Double
)
