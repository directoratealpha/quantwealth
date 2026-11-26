package com.directoratealpha.quantwealth.data

import com.directoratealpha.quantwealth.engine.QuantEngine
import com.directoratealpha.quantwealth.model.AssetCategory
import com.directoratealpha.quantwealth.model.MarketAsset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.cos
import kotlin.math.sin

object MarketRepository {

    data class CatalogSeed(
        val symbol: String,
        val displaySymbol: String,
        val name: String,
        val category: AssetCategory,
        val fallbackPrice: Double,
        val fallbackDailyPct: Double,
        val annualDrift: Double,
        val annualVol: Double,
        val dividendYieldPct: Double,
        val binanceSymbol: String? = null
    )

    val catalog: List<CatalogSeed> = listOf(
        CatalogSeed("SPY", "SPY", "SPDR S&P 500 ETF", AssetCategory.STOCKS_ETF, 598.40, 0.54, 0.115, 0.16, 1.3),
        CatalogSeed("QQQ", "QQQ", "Invesco Nasdaq-100 ETF", AssetCategory.STOCKS_ETF, 514.20, 0.82, 0.165, 0.22, 0.6),
        CatalogSeed("NVDA", "NVDA", "NVIDIA Corp.", AssetCategory.STOCKS_ETF, 144.80, 1.95, 0.36, 0.48, 0.03),
        CatalogSeed("GOOGL", "GOOGL", "Alphabet Inc.", AssetCategory.STOCKS_ETF, 182.60, 0.68, 0.18, 0.27, 0.45),
        CatalogSeed("TSLA", "TSLA", "Tesla Inc.", AssetCategory.STOCKS_ETF, 342.10, -1.12, 0.28, 0.58, 0.0),
        CatalogSeed("AAPL", "AAPL", "Apple Inc.", AssetCategory.STOCKS_ETF, 234.50, 0.41, 0.19, 0.24, 0.44),
        CatalogSeed("MSFT", "MSFT", "Microsoft Corp.", AssetCategory.STOCKS_ETF, 428.90, 0.62, 0.20, 0.23, 0.72),
        CatalogSeed("SCHD", "SCHD", "Schwab US Dividend ETF", AssetCategory.STOCKS_ETF, 84.30, 0.25, 0.105, 0.14, 3.5),
        CatalogSeed("BTC-USD", "BTC", "Bitcoin / USD", AssetCategory.CRYPTO, 96450.0, 2.34, 0.42, 0.62, 0.0, "BTCUSDT"),
        CatalogSeed("ETH-USD", "ETH", "Ethereum / USD", AssetCategory.CRYPTO, 3480.0, 1.78, 0.35, 0.68, 0.0, "ETHUSDT"),
        CatalogSeed("SOL-USD", "SOL", "Solana / USD", AssetCategory.CRYPTO, 236.40, 3.12, 0.45, 0.78, 0.0, "SOLUSDT"),
        CatalogSeed("XRP-USD", "XRP", "Ripple / USD", AssetCategory.CRYPTO, 1.48, 1.45, 0.22, 0.72, 0.0, "XRPUSDT"),
        CatalogSeed("BNB-USD", "BNB", "BNB / USD", AssetCategory.CRYPTO, 654.20, 0.94, 0.30, 0.56, 0.0, "BNBUSDT"),
        CatalogSeed("GC=F", "GOLD", "Gold Futures (XAU)", AssetCategory.GOLD_MACRO, 2665.50, 0.46, 0.088, 0.14, 0.0),
        CatalogSeed("GLD", "GLD", "SPDR Gold Trust ETF", AssetCategory.GOLD_MACRO, 246.10, 0.44, 0.085, 0.14, 0.0),
        CatalogSeed("SI=F", "SILVER", "Silver Futures (XAG)", AssetCategory.GOLD_MACRO, 31.15, 0.62, 0.075, 0.25, 0.0),
        CatalogSeed("CL=F", "OIL", "Crude Oil WTI", AssetCategory.GOLD_MACRO, 69.85, -0.58, 0.04, 0.34, 0.0),
        CatalogSeed("EURUSD=X", "EUR/USD", "Euro / US Dollar", AssetCategory.GOLD_MACRO, 1.0580, 0.12, 0.005, 0.08, 0.0)
    )

    private val seedMap = catalog.associateBy { it.symbol.uppercase() }

    // In-memory cache for daily close histories (avoids re-downloading 1y candles every 12 seconds)
    private val dailyHistoryCache = HashMap<String, Pair<Long, List<Double>>>()
    private val monthlyHistoryCache = HashMap<String, Pair<Long, List<Double>>>()

    private const val DAILY_HISTORY_TTL_MS = 15L * 60L * 1000L // 15 min
    private const val MONTHLY_HISTORY_TTL_MS = 30L * 60L * 1000L // 30 min

    fun buildInstantBaseline(symbols: List<String>): Map<String, MarketAsset> {
        return symbols.associateWith { sym -> createFallbackAsset(sym) }
    }

    fun inferCategory(symbol: String): AssetCategory {
        val up = symbol.uppercase()
        seedMap[up]?.let { return it.category }
        return when {
            up.endsWith("-USD") || up.endsWith("USDT") -> AssetCategory.CRYPTO
            up.endsWith("=F") || up.endsWith("=X") || up == "GLD" || up == "SLV" || up.startsWith("^") -> AssetCategory.GOLD_MACRO
            else -> AssetCategory.STOCKS_ETF
        }
    }

    fun createFallbackAsset(symbol: String): MarketAsset {
        val up = symbol.uppercase()
        val seed = seedMap[up]
        val basePrice = seed?.fallbackPrice ?: 100.0
        val dailyPct = seed?.fallbackDailyPct ?: 0.35
        val closes = generateSyntheticSeries(
            endPrice = basePrice,
            count = 210,
            driftPerStep = (seed?.annualDrift ?: 0.10) / 252.0,
            volPerStep = (seed?.annualVol ?: 0.22) / 16.0,
            seedHash = up.hashCode()
        )
        val signal = QuantEngine.calculateTechnicalSignal(closes)
        val prevClose = basePrice / (1.0 + dailyPct / 100.0)
        return MarketAsset(
            symbol = up,
            displaySymbol = seed?.displaySymbol ?: up.replace("-USD", ""),
            name = seed?.name ?: "$up Market Asset",
            category = inferCategory(up),
            price = basePrice,
            previousClose = prevClose,
            changePercent = dailyPct,
            sparkline = closes.takeLast(24),
            dailyCloses = closes,
            signal = signal,
            dividendYieldPct = seed?.dividendYieldPct ?: 0.0,
            isLive = false
        )
    }

    suspend fun fetchLiveWatchlist(
        symbols: List<String>,
        existingAssets: Map<String, MarketAsset>
    ): Map<String, MarketAsset> = coroutineScope {
        val deferreds = symbols.map { sym ->
            async(Dispatchers.IO) {
                sym to fetchSingleLiveAsset(sym, existingAssets[sym])
            }
        }
        deferreds.awaitAll().toMap()
    }

    suspend fun fetchSingleLiveAsset(
        rawSymbol: String,
        existing: MarketAsset? = null
    ): MarketAsset = withContext(Dispatchers.IO) {
        val symbol = rawSymbol.trim().uppercase()
        val seed = seedMap[symbol]
        val now = System.currentTimeMillis()

        // 1. Ensure we have 1-year daily closes for Technical Signals (cached 15 min)
        var dailyCloses = dailyHistoryCache[symbol]?.takeIf { now - it.first < DAILY_HISTORY_TTL_MS }?.second
        var livePrice: Double? = null
        var prevClose: Double? = null
        var resolvedName: String? = seed?.name

        if (dailyCloses == null) {
            val chart1y = fetchYahooChart(symbol, "1y", "1d")
            if (chart1y != null) {
                if (chart1y.closes.size >= 20) {
                    dailyCloses = chart1y.closes
                    dailyHistoryCache[symbol] = now to chart1y.closes
                }
                livePrice = chart1y.regularMarketPrice
                prevClose = chart1y.previousClose
                if (!chart1y.longName.isNullOrBlank() && resolvedName == null) {
                    resolvedName = chart1y.longName
                }
            }
        } else {
            // Fast intraday refresh for live price
            val binanceTicker = seed?.binanceSymbol?.let { fetchBinance24h(it) }
            if (binanceTicker != null) {
                livePrice = binanceTicker.first
                prevClose = binanceTicker.second
            } else {
                val quickYahoo = fetchYahooChart(symbol, "5d", "15m")
                if (quickYahoo != null) {
                    livePrice = quickYahoo.regularMarketPrice
                    prevClose = quickYahoo.previousClose
                }
            }
        }

        // If Yahoo didn't return crypto price on first run, try Binance directly
        if (livePrice == null && seed?.binanceSymbol != null) {
            val binanceTicker = fetchBinance24h(seed.binanceSymbol)
            if (binanceTicker != null) {
                livePrice = binanceTicker.first
                prevClose = binanceTicker.second
            }
        }

        val fallback = existing ?: createFallbackAsset(symbol)
        val finalPrice = livePrice ?: fallback.price
        val finalPrev = prevClose?.takeIf { it > 0.0 } ?: fallback.previousClose.takeIf { it > 0.0 } ?: finalPrice
        val changePct = if (finalPrev > 0.0) ((finalPrice - finalPrev) / finalPrev) * 100.0 else fallback.changePercent

        val finalDailyCloses = if (!dailyCloses.isNullOrEmpty()) {
            dailyCloses.dropLast(1) + finalPrice
        } else {
            fallback.dailyCloses.dropLast(1) + finalPrice
        }

        val signal = QuantEngine.calculateTechnicalSignal(finalDailyCloses)
        val spark = finalDailyCloses.takeLast(24)

        MarketAsset(
            symbol = symbol,
            displaySymbol = seed?.displaySymbol ?: symbol.replace("-USD", ""),
            name = resolvedName ?: fallback.name,
            category = inferCategory(symbol),
            price = finalPrice,
            previousClose = finalPrev,
            changePercent = changePct,
            sparkline = spark,
            dailyCloses = finalDailyCloses,
            signal = signal,
            dividendYieldPct = seed?.dividendYieldPct ?: 0.0,
            isLive = livePrice != null,
            lastUpdatedMillis = now
        )
    }

    /**
     * Fetches real monthly historical prices (up to 20 years) for the Portfolio Backtester.
     */
    suspend fun fetchMonthlyHistory(
        symbol: String,
        years: Int
    ): Pair<List<Double>, Boolean> = withContext(Dispatchers.IO) {
        val up = symbol.trim().uppercase()
        val now = System.currentTimeMillis()
        val cached = monthlyHistoryCache[up]?.takeIf { now - it.first < MONTHLY_HISTORY_TTL_MS }?.second
        if (cached != null && cached.size >= 12) {
            return@withContext cached to true
        }

        val range = when {
            years <= 5 -> "5y"
            years <= 10 -> "10y"
            else -> "max"
        }
        val chart = fetchYahooChart(up, range, "1mo")
        if (chart != null && chart.closes.size >= 12) {
            monthlyHistoryCache[up] = now to chart.closes
            return@withContext chart.closes to true
        }

        // Offline / rate-limited fallback based on historical statistical properties
        val seed = seedMap[up]
        val monthsNeeded = years * 12 + 1
        val synthetic = generateSyntheticSeries(
            endPrice = seed?.fallbackPrice ?: 100.0,
            count = monthsNeeded,
            driftPerStep = (seed?.annualDrift ?: 0.10) / 12.0,
            volPerStep = (seed?.annualVol ?: 0.18) / 3.46,
            seedHash = up.hashCode() + 99
        )
        synthetic to false
    }

    private data class YahooChartPayload(
        val regularMarketPrice: Double?,
        val previousClose: Double?,
        val longName: String?,
        val closes: List<Double>
    )

    private fun fetchYahooChart(symbol: String, range: String, interval: String): YahooChartPayload? {
        return try {
            val encoded = URLEncoder.encode(symbol, "UTF-8")
            val urlStr = "https://query1.finance.yahoo.com/v8/finance/chart/$encoded?range=$range&interval=$interval"
            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 6500
                readTimeout = 6500
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                setRequestProperty("Accept", "application/json")
            }
            if (conn.responseCode != 200) {
                conn.disconnect()
                return null
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            val root = JSONObject(body)
            val resultArr = root.optJSONObject("chart")?.optJSONArray("result") ?: return null
            if (resultArr.length() == 0) return null
            val first = resultArr.getJSONObject(0)
            val meta = first.optJSONObject("meta")

            val regPrice = meta?.optDouble("regularMarketPrice", Double.NaN)?.takeIf { !it.isNaN() && it > 0 }
            val prevClose = (meta?.optDouble("chartPreviousClose", Double.NaN)?.takeIf { !it.isNaN() && it > 0 }
                ?: meta?.optDouble("previousClose", Double.NaN)?.takeIf { !it.isNaN() && it > 0 })
            val longName = meta?.optString("shortName", "")?.ifBlank { meta.optString("longName", "") }

            val closesList = ArrayList<Double>()
            val quoteArr = first.optJSONObject("indicators")?.optJSONArray("quote")
            if (quoteArr != null && quoteArr.length() > 0) {
                val closeJson = quoteArr.getJSONObject(0).optJSONArray("close")
                if (closeJson != null) {
                    for (i in 0 until closeJson.length()) {
                        if (!closeJson.isNull(i)) {
                            val v = closeJson.optDouble(i, Double.NaN)
                            if (!v.isNaN() && v > 0.0) {
                                closesList.add(v)
                            }
                        }
                    }
                }
            }

            YahooChartPayload(
                regularMarketPrice = regPrice ?: closesList.lastOrNull(),
                previousClose = prevClose ?: if (closesList.size >= 2) closesList[closesList.size - 2] else regPrice,
                longName = longName,
                closes = closesList
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun fetchBinance24h(binanceSymbol: String): Pair<Double, Double>? {
        return try {
            val urlStr = "https://api.binance.com/api/v3/ticker/24hr?symbol=$binanceSymbol"
            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
                setRequestProperty("Accept", "application/json")
            }
            if (conn.responseCode != 200) {
                conn.disconnect()
                return null
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            val obj = JSONObject(body)
            val lastPrice = obj.optString("lastPrice", "").toDoubleOrNull() ?: return null
            val openPrice = obj.optString("openPrice", "").toDoubleOrNull() ?: lastPrice
            if (lastPrice > 0.0) lastPrice to openPrice else null
        } catch (_: Exception) {
            null
        }
    }

    private fun generateSyntheticSeries(
        endPrice: Double,
        count: Int,
        driftPerStep: Double,
        volPerStep: Double,
        seedHash: Int
    ): List<Double> {
        val raw = DoubleArray(count)
        var v = 100.0
        raw[0] = v
        for (i in 1 until count) {
            val wave = sin(i * 0.27 + seedHash * 0.13) * 0.65 + cos(i * 0.61 + seedHash * 0.07) * 0.35
            val stepReturn = driftPerStep + volPerStep * wave * 0.45
            v = (v * (1.0 + stepReturn)).coerceAtLeast(1.0)
            raw[i] = v
        }
        val scale = endPrice / raw.last().coerceAtLeast(0.0001)
        return raw.map { it * scale }
    }
}
