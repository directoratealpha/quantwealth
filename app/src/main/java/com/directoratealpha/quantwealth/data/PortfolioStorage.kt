package com.directoratealpha.quantwealth.data

import android.content.Context
import com.directoratealpha.quantwealth.model.*
import org.json.JSONArray
import org.json.JSONObject

class PortfolioStorage(context: Context) {

    private val prefs = context.getSharedPreferences("quantwealth_state_v1", Context.MODE_PRIVATE)

    fun load(): PortfolioState {
        val raw = prefs.getString("state_json", null) ?: return PortfolioState()
        return try {
            val obj = JSONObject(raw)
            val cash = obj.optDouble("cashBalance", PortfolioState.INITIAL_CASH)
            val maxSlots = obj.optInt("maxWatchlistSlots", 10).coerceAtLeast(10)
            val isPro = obj.optBoolean("isPro", false)

            val watchlist = ArrayList<String>()
            val wlArr = obj.optJSONArray("watchlistSymbols")
            if (wlArr != null) {
                for (i in 0 until wlArr.length()) {
                    val s = wlArr.optString(i)
                    if (s.isNotBlank()) watchlist.add(s)
                }
            }
            if (watchlist.isEmpty()) watchlist.addAll(PortfolioState.DEFAULT_WATCHLIST)

            val positions = ArrayList<OpenPosition>()
            val posArr = obj.optJSONArray("positions")
            if (posArr != null) {
                for (i in 0 until posArr.length()) {
                    val p = posArr.getJSONObject(i)
                    positions.add(
                        OpenPosition(
                            id = p.getString("id"),
                            symbol = p.getString("symbol"),
                            displaySymbol = p.optString("displaySymbol", p.getString("symbol")),
                            name = p.optString("name", p.getString("symbol")),
                            side = runCatching { TradeSide.valueOf(p.getString("side")) }.getOrDefault(TradeSide.LONG),
                            marginAllocated = p.getDouble("marginAllocated"),
                            leverage = p.optInt("leverage", 1),
                            entryPrice = p.getDouble("entryPrice"),
                            liquidationPrice = p.optDouble("liquidationPrice", 0.0),
                            openedAtMillis = p.optLong("openedAtMillis", System.currentTimeMillis())
                        )
                    )
                }
            }

            val closedTrades = ArrayList<ClosedTrade>()
            val closedArr = obj.optJSONArray("closedTrades")
            if (closedArr != null) {
                for (i in 0 until closedArr.length()) {
                    val c = closedArr.getJSONObject(i)
                    closedTrades.add(
                        ClosedTrade(
                            id = c.getString("id"),
                            symbol = c.getString("symbol"),
                            displaySymbol = c.optString("displaySymbol", c.getString("symbol")),
                            side = runCatching { TradeSide.valueOf(c.getString("side")) }.getOrDefault(TradeSide.LONG),
                            leverage = c.optInt("leverage", 1),
                            marginAllocated = c.getDouble("marginAllocated"),
                            entryPrice = c.getDouble("entryPrice"),
                            exitPrice = c.getDouble("exitPrice"),
                            realizedPnl = c.getDouble("realizedPnl"),
                            closedAtMillis = c.optLong("closedAtMillis", System.currentTimeMillis()),
                            wasLiquidated = c.optBoolean("wasLiquidated", false)
                        )
                    )
                }
            }

            val chObj = obj.optJSONObject("challenge")
            val challenge = if (chObj != null) {
                ProfitChallenge(
                    targetProfitUsd = chObj.optDouble("targetProfitUsd", 15000.0),
                    startEquityUsd = chObj.optDouble("startEquityUsd", PortfolioState.INITIAL_CASH),
                    createdAtMillis = chObj.optLong("createdAtMillis", System.currentTimeMillis()),
                    deadlineMillis = chObj.optLong("deadlineMillis", System.currentTimeMillis() + 30L * 86_400_000L)
                )
            } else {
                ProfitChallenge()
            }

            val unlockedSignals = HashSet<String>()
            val sigArr = obj.optJSONArray("unlockedFullSignalSymbols")
            if (sigArr != null) {
                for (i in 0 until sigArr.length()) {
                    unlockedSignals.add(sigArr.getString(i))
                }
            }

            PortfolioState(
                cashBalance = cash,
                positions = positions,
                closedTrades = closedTrades,
                watchlistSymbols = watchlist,
                maxWatchlistSlots = maxSlots,
                challenge = challenge,
                isPro = isPro,
                unlockedFullSignalSymbols = unlockedSignals
            )
        } catch (_: Exception) {
            PortfolioState()
        }
    }

    fun save(state: PortfolioState) {
        val obj = JSONObject()
        obj.put("cashBalance", state.cashBalance)
        obj.put("maxWatchlistSlots", state.maxWatchlistSlots)
        obj.put("isPro", state.isPro)

        val wlArr = JSONArray()
        state.watchlistSymbols.forEach { wlArr.put(it) }
        obj.put("watchlistSymbols", wlArr)

        val posArr = JSONArray()
        state.positions.forEach { p ->
            posArr.put(JSONObject().apply {
                put("id", p.id)
                put("symbol", p.symbol)
                put("displaySymbol", p.displaySymbol)
                put("name", p.name)
                put("side", p.side.name)
                put("marginAllocated", p.marginAllocated)
                put("leverage", p.leverage)
                put("entryPrice", p.entryPrice)
                put("liquidationPrice", p.liquidationPrice)
                put("openedAtMillis", p.openedAtMillis)
            })
        }
        obj.put("positions", posArr)

        val closedArr = JSONArray()
        state.closedTrades.take(100).forEach { c ->
            closedArr.put(JSONObject().apply {
                put("id", c.id)
                put("symbol", c.symbol)
                put("displaySymbol", c.displaySymbol)
                put("side", c.side.name)
                put("leverage", c.leverage)
                put("marginAllocated", c.marginAllocated)
                put("entryPrice", c.entryPrice)
                put("exitPrice", c.exitPrice)
                put("realizedPnl", c.realizedPnl)
                put("closedAtMillis", c.closedAtMillis)
                put("wasLiquidated", c.wasLiquidated)
            })
        }
        obj.put("closedTrades", closedArr)

        obj.put("challenge", JSONObject().apply {
            put("targetProfitUsd", state.challenge.targetProfitUsd)
            put("startEquityUsd", state.challenge.startEquityUsd)
            put("createdAtMillis", state.challenge.createdAtMillis)
            put("deadlineMillis", state.challenge.deadlineMillis)
        })

        val sigArr = JSONArray()
        state.unlockedFullSignalSymbols.forEach { sigArr.put(it) }
        obj.put("unlockedFullSignalSymbols", sigArr)

        prefs.edit().putString("state_json", obj.toString()).apply()
    }
}
