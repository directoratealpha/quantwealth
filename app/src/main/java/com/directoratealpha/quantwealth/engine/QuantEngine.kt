package com.directoratealpha.quantwealth.engine

import com.directoratealpha.quantwealth.model.*
import kotlin.math.*
import kotlin.random.Random

object QuantEngine {

    fun calculateTechnicalSignal(closes: List<Double>): TechnicalSignal {
        if (closes.size < 15) {
            val last = closes.lastOrNull() ?: 100.0
            return TechnicalSignal(
                score = 50,
                label = SignalLabel.NEUTRAL,
                rsi14 = 50.0,
                sma50 = last,
                sma200 = last,
                macdLine = 0.0,
                macdSignal = 0.0,
                macdHistogram = 0.0,
                bollingerUpper = last * 1.05,
                bollingerMiddle = last,
                bollingerLower = last * 0.95,
                summary = "Insufficient history; neutral baseline."
            )
        }

        val currentPrice = closes.last()
        val rsi14 = computeRsi(closes, 14)
        val sma50 = computeSma(closes, min(50, closes.size))
        val sma200 = computeSma(closes, min(200, closes.size))

        val ema12Series = computeEmaSeries(closes, 12)
        val ema26Series = computeEmaSeries(closes, 26)
        val macdSeries = ema12Series.zip(ema26Series) { a, b -> a - b }
        val signalSeries = computeEmaSeries(macdSeries, 9)

        val macdLine = macdSeries.lastOrNull() ?: 0.0
        val macdSignal = signalSeries.lastOrNull() ?: 0.0
        val macdHistogram = macdLine - macdSignal

        val bbPeriod = min(20, closes.size)
        val bbSlice = closes.takeLast(bbPeriod)
        val bbMid = bbSlice.average()
        val variance = bbSlice.map { (it - bbMid).pow(2) }.average()
        val stdDev = sqrt(variance)
        val bbUpper = bbMid + 2.0 * stdDev
        val bbLower = (bbMid - 2.0 * stdDev).coerceAtLeast(0.0001)

        var score = 50.0

        score += when {
            rsi14 < 28.0 -> 22.0
            rsi14 < 38.0 -> 14.0
            rsi14 < 50.0 -> 5.0
            rsi14 <= 62.0 -> 4.0
            rsi14 <= 72.0 -> -8.0
            else -> -20.0
        }

        if (sma50 > sma200) score += 8.0 else score -= 7.0
        if (currentPrice > sma50) score += 6.0 else score -= 5.0

        score += when {
            macdHistogram > 0 && macdLine > macdSignal -> 10.0
            macdHistogram > 0 -> 5.0
            macdHistogram < 0 && macdLine < macdSignal -> -9.0
            else -> -4.0
        }

        val bandWidth = (bbUpper - bbLower).coerceAtLeast(0.0001)
        val pctB = (currentPrice - bbLower) / bandWidth
        score += when {
            pctB < 0.15 -> 10.0
            pctB < 0.35 -> 5.0
            pctB > 0.90 -> -10.0
            pctB > 0.75 -> -5.0
            else -> 0.0
        }

        val clampedScore = score.roundToInt().coerceIn(5, 96)
        val label = when {
            clampedScore >= 72 -> SignalLabel.STRONG_BUY
            clampedScore >= 58 -> SignalLabel.BUY
            clampedScore >= 42 -> SignalLabel.NEUTRAL
            clampedScore >= 28 -> SignalLabel.SELL
            else -> SignalLabel.STRONG_SELL
        }

        val summary = when (label) {
            SignalLabel.STRONG_BUY -> "Strong technical alignment: favorable RSI/trend structure with upside momentum."
            SignalLabel.BUY -> "Bullish bias: trend and momentum indicators lean positive."
            SignalLabel.NEUTRAL -> "Balanced range: waiting for clearer directional breakout."
            SignalLabel.SELL -> "Caution: elevated stretch or softening short-term momentum."
            SignalLabel.STRONG_SELL -> "Overextended or bearish trend: high risk of pullback."
        }

        return TechnicalSignal(
            score = clampedScore,
            label = label,
            rsi14 = rsi14,
            sma50 = sma50,
            sma200 = sma200,
            macdLine = macdLine,
            macdSignal = macdSignal,
            macdHistogram = macdHistogram,
            bollingerUpper = bbUpper,
            bollingerMiddle = bbMid,
            bollingerLower = bbLower,
            summary = summary
        )
    }

    private fun computeSma(values: List<Double>, period: Int): Double {
        if (values.isEmpty()) return 0.0
        val count = min(period, values.size)
        return values.takeLast(count).average()
    }

    private fun computeEmaSeries(values: List<Double>, period: Int): List<Double> {
        if (values.isEmpty()) return emptyList()
        val k = 2.0 / (period + 1.0)
        val result = ArrayList<Double>(values.size)
        var ema = values.first()
        result.add(ema)
        for (i in 1 until values.size) {
            ema = values[i] * k + ema * (1.0 - k)
            result.add(ema)
        }
        return result
    }

    private fun computeRsi(values: List<Double>, period: Int): Double {
        if (values.size <= period) return 50.0
        val start = values.size - period - 1
        val initialStart = max(1, start - period)

        var initGains = 0.0
        var initLosses = 0.0
        var count = 0
        for (i in initialStart until min(initialStart + period, values.size)) {
            val diff = values[i] - values[i - 1]
            if (diff >= 0) initGains += diff else initLosses += abs(diff)
            count++
        }
        var avgGain = if (count > 0) initGains / count else 0.0
        var avgLoss = if (count > 0) initLosses / count else 0.0

        for (i in min(initialStart + period, values.size) until values.size) {
            val diff = values[i] - values[i - 1]
            val gain = if (diff > 0) diff else 0.0
            val loss = if (diff < 0) -diff else 0.0
            avgGain = (avgGain * (period - 1) + gain) / period
            avgLoss = (avgLoss * (period - 1) + loss) / period
        }

        if (avgLoss == 0.0) return 100.0
        val rs = avgGain / avgLoss
        return 100.0 - (100.0 / (1.0 + rs))
    }

    fun computeLiquidationPrice(entryPrice: Double, leverage: Int, side: TradeSide): Double {
        if (leverage <= 1 && side == TradeSide.LONG) return 0.0
        val maintenanceFactor = 0.95 / leverage.toDouble()
        return when (side) {
            TradeSide.LONG -> (entryPrice * (1.0 - maintenanceFactor)).coerceAtLeast(0.0)
            TradeSide.SHORT -> entryPrice * (1.0 + maintenanceFactor)
        }
    }

    fun computeUnrealizedPnl(position: OpenPosition, currentPrice: Double): Double {
        if (position.entryPrice <= 0.0) return 0.0
        val priceMovePct = (currentPrice - position.entryPrice) / position.entryPrice
        val directionalPct = if (position.side == TradeSide.LONG) priceMovePct else -priceMovePct
        val rawPnl = position.marginAllocated * position.leverage.toDouble() * directionalPct
        return rawPnl.coerceAtLeast(-position.marginAllocated)
    }

    fun isPositionLiquidated(position: OpenPosition, currentPrice: Double): Boolean {
        if (position.leverage <= 1 && position.side == TradeSide.LONG) return false
        return when (position.side) {
            TradeSide.LONG -> currentPrice <= position.liquidationPrice
            TradeSide.SHORT -> currentPrice >= position.liquidationPrice
        }
    }

    fun runBacktest(
        weights: List<BacktestAssetWeight>,
        monthlySeriesMap: Map<String, List<Double>>,
        dividendYieldsMap: Map<String, Double>,
        initialCapital: Double,
        monthlyDca: Double,
        years: Int,
        dripEnabled: Boolean,
        usedLiveData: Boolean
    ): BacktestResult {
        val totalWeight = weights.sumOf { it.weightPct }.coerceAtLeast(1)
        val normWeights = weights.map { it.symbol to (it.weightPct.toDouble() / totalWeight.toDouble()) }

        val targetMonths = years * 12
        val availableMonths = normWeights.minOfOrNull { (sym, _) ->
            (monthlySeriesMap[sym]?.size ?: 2) - 1
        }?.coerceAtLeast(1) ?: 1
        val months = min(targetMonths, availableMonths)

        val symbolMonthlyReturns = normWeights.associate { (sym, _) ->
            val series = monthlySeriesMap[sym].orEmpty()
            val slice = if (series.size >= months + 1) series.takeLast(months + 1) else series
            val returns = ArrayList<Double>(months)
            val monthlyDiv = if (dripEnabled) (dividendYieldsMap[sym] ?: 0.0) / 100.0 / 12.0 else 0.0
            for (i in 1 until slice.size) {
                val prev = slice[i - 1].coerceAtLeast(0.0001)
                val curr = slice[i]
                returns.add(((curr - prev) / prev) + monthlyDiv)
            }
            sym to returns
        }

        val portfolioCurve = ArrayList<Double>(months + 1)
        val investedCurve = ArrayList<Double>(months + 1)
        val periodicReturns = ArrayList<Double>(months)

        var currentValue = initialCapital
        var totalInvested = initialCapital
        var peakValue = initialCapital
        var maxDrawdown = 0.0

        portfolioCurve.add(currentValue)
        investedCurve.add(totalInvested)

        for (m in 0 until months) {
            var weightedReturn = 0.0
            for ((sym, w) in normWeights) {
                val rList = symbolMonthlyReturns[sym].orEmpty()
                val r = if (m < rList.size) rList[m] else 0.006
                weightedReturn += w * r
            }
            periodicReturns.add(weightedReturn)

            currentValue = (currentValue * (1.0 + weightedReturn)) + monthlyDca
            totalInvested += monthlyDca

            if (currentValue > peakValue) peakValue = currentValue
            if (peakValue > 0.0) {
                val dd = (peakValue - currentValue) / peakValue
                if (dd > maxDrawdown) maxDrawdown = dd
            }

            portfolioCurve.add(currentValue)
            investedCurve.add(totalInvested)
        }

        val totalReturnPct = if (totalInvested > 0.0) ((currentValue - totalInvested) / totalInvested) * 100.0 else 0.0
        val compoundedFactor = periodicReturns.fold(1.0) { acc, r -> acc * (1.0 + r).coerceAtLeast(0.05) }
        val actualYears = (months.toDouble() / 12.0).coerceAtLeast(0.25)
        val cagrPct = (compoundedFactor.pow(1.0 / actualYears) - 1.0) * 100.0

        val rfMonthly = 0.04 / 12.0
        val excessReturns = periodicReturns.map { it - rfMonthly }
        val meanExcess = if (excessReturns.isNotEmpty()) excessReturns.average() else 0.0
        val stdMonthly = if (excessReturns.size > 1) {
            val mVal = excessReturns.average()
            sqrt(excessReturns.map { (it - mVal).pow(2) }.average())
        } else 0.01
        val sharpe = if (stdMonthly > 0.0001) (meanExcess / stdMonthly) * sqrt(12.0) else 0.0

        val label = normWeights.joinToString(" + ") { (sym, w) ->
            "${sym.replace("-USD", "")} ${(w * 100).roundToInt()}%"
        }

        return BacktestResult(
            symbolsLabel = label,
            years = max(1, (months / 12.0).roundToInt()),
            initialCapital = initialCapital,
            monthlyDca = monthlyDca,
            dripEnabled = dripEnabled,
            finalValue = currentValue,
            totalInvested = totalInvested,
            totalReturnPct = totalReturnPct,
            cagrPct = cagrPct,
            maxDrawdownPct = maxDrawdown * 100.0,
            sharpeRatio = sharpe,
            portfolioCurve = portfolioCurve,
            investedCurve = investedCurve,
            usedLiveData = usedLiveData
        )
    }

    fun runMonteCarlo(
        assetLabel: String,
        simulationsCount: Int,
        years: Int,
        initialCapital: Double,
        monthlyDca: Double,
        annualReturnPct: Double,
        volatilityPct: Double,
        inflationAdjusted: Boolean,
        fireTargetUsd: Double
    ): MonteCarloResult {
        val inflationRate = if (inflationAdjusted) 0.03 else 0.0
        val realMu = (annualReturnPct / 100.0) - inflationRate
        val sigma = (volatilityPct / 100.0).coerceAtLeast(0.01)

        val dt = 1.0 / 12.0
        val driftStep = (realMu - 0.5 * sigma * sigma) * dt
        val volStep = sigma * sqrt(dt)

        val yearlySnapshots = Array(years + 1) { DoubleArray(simulationsCount) }
        val rng = Random(System.currentTimeMillis())

        for (sim in 0 until simulationsCount) {
            var value = initialCapital
            yearlySnapshots[0][sim] = value
            for (yr in 1..years) {
                for (m in 0 until 12) {
                    val u1 = rng.nextDouble().coerceIn(1e-9, 1.0)
                    val u2 = rng.nextDouble()
                    val z = sqrt(-2.0 * ln(u1)) * cos(2.0 * PI * u2)
                    val growth = exp(driftStep + volStep * z)
                    value = (value * growth + monthlyDca).coerceAtLeast(0.0)
                }
                yearlySnapshots[yr][sim] = value
            }
        }

        val p10Curve = ArrayList<Double>(years + 1)
        val p50Curve = ArrayList<Double>(years + 1)
        val p90Curve = ArrayList<Double>(years + 1)

        for (yr in 0..years) {
            val sorted = yearlySnapshots[yr].sortedArray()
            val idx10 = ((simulationsCount - 1) * 0.10).roundToInt().coerceIn(0, simulationsCount - 1)
            val idx50 = ((simulationsCount - 1) * 0.50).roundToInt().coerceIn(0, simulationsCount - 1)
            val idx90 = ((simulationsCount - 1) * 0.90).roundToInt().coerceIn(0, simulationsCount - 1)
            p10Curve.add(sorted[idx10])
            p50Curve.add(sorted[idx50])
            p90Curve.add(sorted[idx90])
        }

        val finalYearValues = yearlySnapshots[years]
        val hitFireCount = finalYearValues.count { it >= fireTargetUsd }
        val probHitFire = (hitFireCount.toDouble() / simulationsCount.toDouble()) * 100.0
        val medianFinal = p50Curve.last()
        val monthlyFireIncome = (medianFinal * 0.04) / 12.0

        return MonteCarloResult(
            assetLabel = assetLabel,
            simulationsCount = simulationsCount,
            years = years,
            initialCapital = initialCapital,
            monthlyDca = monthlyDca,
            annualReturnPct = annualReturnPct,
            volatilityPct = volatilityPct,
            inflationAdjusted = inflationAdjusted,
            fireTargetUsd = fireTargetUsd,
            p10Curve = p10Curve,
            p50Curve = p50Curve,
            p90Curve = p90Curve,
            medianFinalValue = medianFinal,
            p10FinalValue = p10Curve.last(),
            p90FinalValue = p90Curve.last(),
            medianMonthlyFireIncome = monthlyFireIncome,
            probabilityHitFirePct = probHitFire
        )
    }
}
