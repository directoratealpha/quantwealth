package com.directoratealpha.quantwealth.data

import com.directoratealpha.quantwealth.engine.QuantEngine
import com.directoratealpha.quantwealth.model.*
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
        val exchange: String,
        val category: AssetCategory,
        val fallbackPrice: Double,
        val fallbackDailyPct: Double,
        val annualDrift: Double,
        val annualVol: Double,
        val dividendYieldPct: Double,
        val binanceSymbol: String? = null
    )

    val catalog: List<CatalogSeed> = listOf(
        CatalogSeed("SPY", "SPY", "SPDR S&P 500 ETF Trust", "NYSE Arca", AssetCategory.STOCKS_ETF, 598.40, 0.54, 0.115, 0.16, 1.3),
        CatalogSeed("QQQ", "QQQ", "Invesco QQQ Trust (Nasdaq-100)", "NASDAQ", AssetCategory.STOCKS_ETF, 514.20, 0.82, 0.165, 0.22, 0.6),
        CatalogSeed("NVDA", "NVDA", "NVIDIA Corporation", "NASDAQ", AssetCategory.STOCKS_ETF, 144.80, 1.95, 0.36, 0.48, 0.03),
        CatalogSeed("PLTR", "PLTR", "Palantir Technologies Inc.", "NASDAQ", AssetCategory.STOCKS_ETF, 64.90, 2.85, 0.34, 0.55, 0.0),
        CatalogSeed("AAPL", "AAPL", "Apple Inc.", "NASDAQ", AssetCategory.STOCKS_ETF, 234.50, 0.41, 0.19, 0.24, 0.44),
        CatalogSeed("GOOGL", "GOOGL", "Alphabet Inc. (Google)", "NASDAQ", AssetCategory.STOCKS_ETF, 182.60, 0.68, 0.18, 0.27, 0.45),
        CatalogSeed("TSLA", "TSLA", "Tesla, Inc.", "NASDAQ", AssetCategory.STOCKS_ETF, 342.10, -1.12, 0.28, 0.58, 0.0),
        CatalogSeed("MSFT", "MSFT", "Microsoft Corporation", "NASDAQ", AssetCategory.STOCKS_ETF, 428.90, 0.62, 0.20, 0.23, 0.72),
        CatalogSeed("AMZN", "AMZN", "Amazon.com, Inc.", "NASDAQ", AssetCategory.STOCKS_ETF, 208.40, 0.91, 0.22, 0.30, 0.0),
        CatalogSeed("META", "META", "Meta Platforms, Inc.", "NASDAQ", AssetCategory.STOCKS_ETF, 578.30, 1.15, 0.25, 0.34, 0.35),
        CatalogSeed("AMD", "AMD", "Advanced Micro Devices, Inc.", "NASDAQ", AssetCategory.STOCKS_ETF, 141.20, 1.32, 0.28, 0.46, 0.0),
        CatalogSeed("NFLX", "NFLX", "Netflix, Inc.", "NASDAQ", AssetCategory.STOCKS_ETF, 882.00, 1.40, 0.24, 0.36, 0.0),
        CatalogSeed("COIN", "COIN", "Coinbase Global, Inc.", "NASDAQ", AssetCategory.STOCKS_ETF, 304.50, 3.40, 0.35, 0.68, 0.0),
        CatalogSeed("SCHD", "SCHD", "Schwab US Dividend Equity ETF", "NYSE Arca", AssetCategory.STOCKS_ETF, 84.30, 0.25, 0.105, 0.14, 3.5),
        CatalogSeed("BTC-USD", "BTC", "Bitcoin / USD", "Crypto 24/7", AssetCategory.CRYPTO, 96450.0, 2.34, 0.42, 0.62, 0.0, "BTCUSDT"),
        CatalogSeed("ETH-USD", "ETH", "Ethereum / USD", "Crypto 24/7", AssetCategory.CRYPTO, 3480.0, 1.78, 0.35, 0.68, 0.0, "ETHUSDT"),
        CatalogSeed("SOL-USD", "SOL", "Solana / USD", "Crypto 24/7", AssetCategory.CRYPTO, 236.40, 3.12, 0.45, 0.78, 0.0, "SOLUSDT"),
        CatalogSeed("XRP-USD", "XRP", "Ripple / USD", "Crypto 24/7", AssetCategory.CRYPTO, 1.48, 1.45, 0.22, 0.72, 0.0, "XRPUSDT"),
        CatalogSeed("DOGE-USD", "DOGE", "Dogecoin / USD", "Crypto 24/7", AssetCategory.CRYPTO, 0.41, 2.60, 0.28, 0.85, 0.0, "DOGEUSDT"),
        CatalogSeed("BNB-USD", "BNB", "BNB / USD", "Crypto 24/7", AssetCategory.CRYPTO, 654.20, 0.94, 0.30, 0.56, 0.0, "BNBUSDT"),
        CatalogSeed("GC=F", "GOLD", "Gold Futures (XAU/USD)", "COMEX", AssetCategory.GOLD_MACRO, 2665.50, 0.46, 0.088, 0.14, 0.0),
        CatalogSeed("GLD", "GLD", "SPDR Gold Shares ETF", "NYSE Arca", AssetCategory.GOLD_MACRO, 246.10, 0.44, 0.085, 0.14, 0.0),
        CatalogSeed("SI=F", "SILVER", "Silver Futures (XAG/USD)", "COMEX", AssetCategory.GOLD_MACRO, 31.15, 0.62, 0.075, 0.25, 0.0),
        CatalogSeed("CL=F", "OIL", "Crude Oil WTI Futures", "NYMEX", AssetCategory.GOLD_MACRO, 69.85, -0.58, 0.04, 0.34, 0.0),
        CatalogSeed("EURUSD=X", "EUR/USD", "Euro / US Dollar Forex", "FX", AssetCategory.GOLD_MACRO, 1.0580, 0.12, 0.005, 0.08, 0.0)
    )

    private val seedMap = catalog.associateBy { it.symbol.uppercase() }

    private val dailyHistoryCache = HashMap<String, Pair<Long, List<Double>>>()
    private val monthlyHistoryCache = HashMap<String, Pair<Long, List<Double>>>()
    private val timeframeChartCache = HashMap<String, Pair<Long, List<ChartPoint>>>()

    private const val DAILY_HISTORY_TTL_MS = 15L * 60L * 1000L
    private const val MONTHLY_HISTORY_TTL_MS = 30L * 60L * 1000L
    private const val CHART_TTL_MS = 60L * 1000L

    fun buildInstantBaseline(symbols: List<String>): Map<String, MarketAsset> {
        return symbols.mapNotNull { sym -> createFallbackAssetOrNull(sym)?.let { sym to it } }.toMap()
    }

    fun inferCategory(symbol: String, quoteType: String? = null): AssetCategory {
        val up = symbol.uppercase()
        seedMap[up]?.let { return it.category }
        val qt = quoteType?.uppercase().orEmpty()
        return when {
            qt == "CRYPTOCURRENCY" || up.endsWith("-USD") || up.endsWith("USDT") -> AssetCategory.CRYPTO
            qt == "FUTURE" || qt == "CURRENCY" || qt == "INDEX" || up.endsWith("=F") || up.endsWith("=X") || up == "GLD" || up == "SLV" || up.startsWith("^") -> AssetCategory.GOLD_MACRO
            else -> AssetCategory.STOCKS_ETF
        }
    }

    /**
     * Instant local search results while live Yahoo Search API fetches global tickers.
     */
    fun searchCatalogLocally(query: String): List<TickerSearchResult> {
        val q = query.trim().uppercase()
        if (q.isEmpty()) {
            return catalog.take(12).map {
                TickerSearchResult(it.symbol, it.displaySymbol, it.name, it.exchange, it.category)
            }
        }
        return catalog.filter { seed ->
            seed.symbol.uppercase().contains(q) ||
                seed.displaySymbol.uppercase().contains(q) ||
                seed.name.uppercase().contains(q)
        }.map {
            TickerSearchResult(it.symbol, it.displaySymbol, it.name, it.exchange, it.category)
        }
    }

    /**
     * Searches ANY stock, ETF, crypto, commodity, or index worldwide via Yahoo Finance Search API.
     */
    suspend fun searchTickersLive(query: String): List<TickerSearchResult> = withContext(Dispatchers.IO) {
        val clean = query.trim()
        if (clean.isEmpty()) return@withContext searchCatalogLocally("")

        val localMatches = searchCatalogLocally(clean)
        val remoteMatches = ArrayList<TickerSearchResult>()

        try {
            val encoded = URLEncoder.encode(clean, "UTF-8")
            val urlStr = "https://query1.finance.yahoo.com/v1/finance/search?q=$encoded&quotesCount=12&newsCount=0&enableFuzzyQuery=true"
            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5500
                readTimeout = 5500
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                setRequestProperty("Accept", "application/json")
            }
            if (conn.responseCode == 200) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                val quotes = JSONObject(body).optJSONArray("quotes")
                if (quotes != null) {
                    for (i in 0 until quotes.length()) {
                        val item = quotes.getJSONObject(i)
                        val sym = item.optString("symbol", "").trim().uppercase()
                        if (sym.isBlank()) continue
                        val quoteType = item.optString("quoteType", "")
                        if (quoteType == "OPTION" || quoteType == "MUTUALFUND") continue

                        val name = item.optString("shortname", "")
                            .ifBlank { item.optString("longname", sym) }
                        val exchange = item.optString("exchDisp", quoteType.ifBlank { "Market" })
                        val cat = inferCategory(sym, quoteType)
                        remoteMatches.add(
                            TickerSearchResult(
                                symbol = sym,
                                displaySymbol = sym.replace("-USD", ""),
                                name = name,
                                exchange = exchange,
                                category = cat
                            )
                        )
                    }
                }
            } else {
                conn.disconnect()
            }
        } catch (_: Exception) {
            // Fall back smoothly to local catalog matches
        }

        val combined = LinkedHashMap<String, TickerSearchResult>()
        localMatches.forEach { combined[it.symbol] = it }
        remoteMatches.forEach { if (!combined.containsKey(it.symbol)) combined[it.symbol] = it }
        combined.values.toList()
    }

    fun createFallbackAssetOrNull(symbol: String): MarketAsset? {
        val up = symbol.uppercase()
        val seed = seedMap[up] ?: return null
        val closes = generateSyntheticSeries(
            endPrice = seed.fallbackPrice,
            count = 210,
            driftPerStep = seed.annualDrift / 252.0,
            volPerStep = seed.annualVol / 16.0,
            seedHash = up.hashCode()
        )
        val signal = QuantEngine.calculateTechnicalSignal(closes)
        val prevClose = seed.fallbackPrice / (1.0 + seed.fallbackDailyPct / 100.0)
        return MarketAsset(
            symbol = up,
            displaySymbol = seed.displaySymbol,
            name = seed.name,
            category = seed.category,
            price = seed.fallbackPrice,
            previousClose = prevClose,
            changePercent = seed.fallbackDailyPct,
            sparkline = closes.takeLast(24),
            dailyCloses = closes,
            signal = signal,
            dividendYieldPct = seed.dividendYieldPct,
            isLive = false
        )
    }

    suspend fun fetchLiveWatchlist(
        symbols: List<String>,
        existingAssets: Map<String, MarketAsset>
    ): Map<String, MarketAsset> = coroutineScope {
        val deferreds = symbols.map { sym ->
            async(Dispatchers.IO) {
                fetchSingleLiveAsset(sym, existingAssets[sym])?.let { sym to it }
            }
        }
        deferreds.awaitAll().filterNotNull().toMap()
    }

    /**
     * Fetches real market quote + 1Y daily closes for technical indicators.
     * Returns null if the ticker does not exist on Yahoo Finance / Binance (never fabricates $100).
     */
    suspend fun fetchSingleLiveAsset(
        rawSymbol: String,
        existing: MarketAsset? = null,
        hintName: String? = null
    ): MarketAsset? = withContext(Dispatchers.IO) {
        val symbol = rawSymbol.trim().uppercase()
        if (symbol.isBlank()) return@withContext null

        val seed = seedMap[symbol]
        val now = System.currentTimeMillis()

        var dailyCloses = dailyHistoryCache[symbol]?.takeIf { now - it.first < DAILY_HISTORY_TTL_MS }?.second
        var livePrice: Double? = null
        var prevClose: Double? = null
        var resolvedName: String? = seed?.name ?: hintName ?: existing?.name

        if (dailyCloses == null) {
            val chart1y = fetchYahooChart(symbol, "1y", "1d")
            if (chart1y != null) {
                if (chart1y.points.size >= 15) {
                    dailyCloses = chart1y.points.map { it.price }
                    dailyHistoryCache[symbol] = now to dailyCloses
                }
                livePrice = chart1y.regularMarketPrice
                prevClose = chart1y.previousClose
                if (!chart1y.longName.isNullOrBlank() && seed == null) {
                    resolvedName = chart1y.longName
                }
            }
        } else {
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

        if (livePrice == null && seed?.binanceSymbol != null) {
            val binanceTicker = fetchBinance24h(seed.binanceSymbol)
            if (binanceTicker != null) {
                livePrice = binanceTicker.first
                prevClose = binanceTicker.second
            }
        }

        val fallback = existing ?: createFallbackAssetOrNull(symbol)
        val finalPrice = livePrice ?: fallback?.price ?: return@withContext null
        val finalPrev = prevClose?.takeIf { it > 0.0 }
            ?: fallback?.previousClose?.takeIf { it > 0.0 }
            ?: finalPrice
        val changePct = if (finalPrev > 0.0) {
            ((finalPrice - finalPrev) / finalPrev) * 100.0
        } else {
            fallback?.changePercent ?: 0.0
        }

        val finalDailyCloses = when {
            !dailyCloses.isNullOrEmpty() -> dailyCloses.dropLast(1) + finalPrice
            fallback != null -> fallback.dailyCloses.dropLast(1) + finalPrice
            else -> List(30) { finalPrice }
        }

        val signal = QuantEngine.calculateTechnicalSignal(finalDailyCloses)
        val spark = finalDailyCloses.takeLast(24)

        MarketAsset(
            symbol = symbol,
            displaySymbol = seed?.displaySymbol ?: symbol.replace("-USD", ""),
            name = resolvedName ?: symbol,
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
     * Fetches real intraday or multi-year chart points for the interactive Asset Detail Chart.
     */
    suspend fun fetchAssetChartPoints(
        symbol: String,
        timeframe: ChartTimeframe,
        fallbackCloses: List<Double>
    ): List<ChartPoint> = withContext(Dispatchers.IO) {
        val up = symbol.trim().uppercase()
        val key = "${up}_${timeframe.name}"
        val now = System.currentTimeMillis()
        val cached = timeframeChartCache[key]?.takeIf { now - it.first < CHART_TTL_MS }?.second
        if (cached != null && cached.size >= 5) {
            return@withContext cached
        }

        val payload = fetchYahooChart(up, timeframe.yahooRange, timeframe.yahooInterval)
        if (payload != null && payload.points.size >= 5) {
            timeframeChartCache[key] = now to payload.points
            return@withContext payload.points
        }

        // Fallback to local daily closes if offline
        val slice = when (timeframe) {
            ChartTimeframe.D1 -> fallbackCloses.takeLast(12)
            ChartTimeframe.W1 -> fallbackCloses.takeLast(7)
            ChartTimeframe.M1 -> fallbackCloses.takeLast(22)
            ChartTimeframe.M3 -> fallbackCloses.takeLast(65)
            ChartTimeframe.Y1, ChartTimeframe.Y5 -> fallbackCloses
        }
        slice.mapIndexed { idx, p ->
            ChartPoint(timestampSec = (now / 1000L) - (slice.size - idx) * 86400L, price = p)
        }
    }

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
        if (chart != null && chart.points.size >= 12) {
            val closes = chart.points.map { it.price }
            monthlyHistoryCache[up] = now to closes
            return@withContext closes to true
        }

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
        val points: List<ChartPoint>
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

            val timestamps = first.optJSONArray("timestamp")
            val pointsList = ArrayList<ChartPoint>()
            val quoteArr = first.optJSONObject("indicators")?.optJSONArray("quote")
            if (quoteArr != null && quoteArr.length() > 0) {
                val closeJson = quoteArr.getJSONObject(0).optJSONArray("close")
                if (closeJson != null) {
                    for (i in 0 until closeJson.length()) {
                        if (!closeJson.isNull(i)) {
                            val v = closeJson.optDouble(i, Double.NaN)
                            if (!v.isNaN() && v > 0.0) {
                                val ts = if (timestamps != null && i < timestamps.length()) timestamps.optLong(i, 0L) else 0L
                                pointsList.add(ChartPoint(timestampSec = ts, price = v))
                            }
                        }
                    }
                }
            }

            YahooChartPayload(
                regularMarketPrice = regPrice ?: pointsList.lastOrNull()?.price,
                previousClose = prevClose ?: if (pointsList.size >= 2) pointsList[pointsList.size - 2].price else regPrice,
                longName = longName,
                points = pointsList
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
