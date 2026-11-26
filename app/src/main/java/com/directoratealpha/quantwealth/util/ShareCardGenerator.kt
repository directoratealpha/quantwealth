package com.directoratealpha.quantwealth.util

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.widget.Toast
import androidx.core.content.FileProvider
import com.directoratealpha.quantwealth.model.MonteCarloResult
import com.directoratealpha.quantwealth.ui.theme.Formatters
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlin.math.max

object ShareCardGenerator {

    fun shareChallengeCard(
        context: Context,
        totalEquity: Double,
        totalPnlUsd: Double,
        totalPnlPct: Double,
        targetEquityUsd: Double,
        progressFraction: Float,
        daysLeft: Long,
        statusLabel: String
    ) {
        try {
            val width = 1080
            val height = 1920
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            // Deep Quant Gradient Background
            val bgPaint = Paint().apply {
                shader = LinearGradient(
                    0f, 0f, width.toFloat(), height.toFloat(),
                    intArrayOf(Color.parseColor("#070C17"), Color.parseColor("#0F1D3A"), Color.parseColor("#070C17")),
                    null,
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

            // Subtle Grid Accent
            val gridPaint = Paint().apply {
                color = Color.parseColor("#1838BDF8")
                strokeWidth = 2f
            }
            for (x in 90 until width step 120) {
                canvas.drawLine(x.toFloat(), 0f, x.toFloat(), height.toFloat(), gridPaint)
            }
            for (y in 120 until height step 160) {
                canvas.drawLine(0f, y.toFloat(), width.toFloat(), y.toFloat(), gridPaint)
            }

            // Header Brand
            val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#38BDF8")
                textSize = 44f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                letterSpacing = 0.14f
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("QUANTWEALTH", width / 2f, 220f, brandPaint)

            val subBrandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#94A3B8")
                textSize = 30f
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("$100K PAPER TRADING CHALLENGE", width / 2f, 275f, subBrandPaint)

            // Main Card Container
            val cardRect = RectF(80f, 380f, width - 80f, 1480f)
            val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#0F172A")
            }
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#38BDF8")
                style = Paint.Style.STROKE
                strokeWidth = 4f
            }
            canvas.drawRoundRect(cardRect, 48f, 48f, cardPaint)
            canvas.drawRoundRect(cardRect, 48f, 48f, borderPaint)

            // Status Badge
            val isPositive = totalPnlUsd >= 0
            val statusColor = if (statusLabel == "GOAL REACHED" || statusLabel == "ON TRACK") {
                Color.parseColor("#10B981")
            } else {
                Color.parseColor("#F59E0B")
            }
            val pillRect = RectF(310f, 450f, width - 310f, 530f)
            val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = statusColor }
            canvas.drawRoundRect(pillRect, 40f, 40f, pillPaint)

            val pillTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#070C17")
                textSize = 34f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(statusLabel, width / 2f, 503f, pillTextPaint)

            // Equity Label & Value
            val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#94A3B8")
                textSize = 34f
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("CURRENT VIRTUAL EQUITY", width / 2f, 650f, labelPaint)

            val equityPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 112f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(Formatters.formatUsd(totalEquity), width / 2f, 785f, equityPaint)

            val pnlPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (isPositive) Color.parseColor("#10B981") else Color.parseColor("#EF4444")
                textSize = 54f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(
                "${Formatters.formatUsd(totalPnlUsd, showSign = true)}  (${Formatters.formatPct(totalPnlPct)})",
                width / 2f,
                875f,
                pnlPaint
            )

            // Progress Bar
            canvas.drawText(
                "TARGET: ${Formatters.formatUsd(targetEquityUsd)}  •  ${daysLeft}D LEFT",
                width / 2f,
                1040f,
                labelPaint
            )
            val trackRect = RectF(150f, 1085f, width - 150f, 1135f)
            val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1E2D4A") }
            canvas.drawRoundRect(trackRect, 25f, 25f, trackPaint)

            val fillWidth = (width - 300f) * progressFraction.coerceIn(0.03f, 1f)
            val fillRect = RectF(150f, 1085f, 150f + fillWidth, 1135f)
            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#38BDF8") }
            canvas.drawRoundRect(fillRect, 25f, 25f, fillPaint)

            val pctLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#38BDF8")
                textSize = 48f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(
                String.format(Locale.US, "%.1f%% TO GOAL", progressFraction * 100f),
                width / 2f,
                1230f,
                pctLabelPaint
            )

            val ctaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#F59E0B")
                textSize = 36f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("Can you beat my virtual portfolio?", width / 2f, 1380f, ctaPaint)

            // Footer Disclaimer
            val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#64748B")
                textSize = 26f
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("QuantWealth: Trading Simulator • Virtual Funds Only", width / 2f, 1740f, footerPaint)

            saveAndShareBitmap(context, bitmap, "quantwealth_challenge.png", "My $100K Trading Challenge on QuantWealth!")
        } catch (e: Exception) {
            Toast.makeText(context, "Could not generate share card.", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareMonteCarloCard(context: Context, result: MonteCarloResult) {
        try {
            val width = 1080
            val height = 1920
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            val bgPaint = Paint().apply {
                shader = LinearGradient(
                    0f, 0f, width.toFloat(), height.toFloat(),
                    intArrayOf(Color.parseColor("#070C17"), Color.parseColor("#141236"), Color.parseColor("#070C17")),
                    null,
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

            val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#38BDF8")
                textSize = 44f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                letterSpacing = 0.14f
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("QUANTWEALTH LAB", width / 2f, 200f, brandPaint)

            val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#94A3B8")
                textSize = 32f
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(
                "${result.simulationsCount} MONTE CARLO SIMULATIONS • ${result.years} YEARS",
                width / 2f,
                260f,
                subPaint
            )

            // Card
            val cardRect = RectF(76f, 340f, width - 76f, 1560f)
            val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#0F172A") }
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#A855F7")
                style = Paint.Style.STROKE
                strokeWidth = 4f
            }
            canvas.drawRoundRect(cardRect, 48f, 48f, cardPaint)
            canvas.drawRoundRect(cardRect, 48f, 48f, borderPaint)

            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 44f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("${result.assetLabel} Projection", width / 2f, 435f, titlePaint)

            // Draw Mini Fan Chart inside (140..940, 500..940)
            val chartLeft = 140f
            val chartRight = 940f
            val chartTop = 510f
            val chartBottom = 940f
            val maxY = max(1.0, result.p90Curve.maxOrNull() ?: 1.0)

            fun drawSeries(values: List<Double>, hex: String, widthPx: Float) {
                if (values.size < 2) return
                val path = Path()
                values.forEachIndexed { idx, v ->
                    val x = chartLeft + (idx.toFloat() / (values.size - 1).toFloat()) * (chartRight - chartLeft)
                    val y = chartBottom - ((v / maxY).toFloat() * (chartBottom - chartTop))
                    if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor(hex)
                    style = Paint.Style.STROKE
                    strokeWidth = widthPx
                    strokeCap = Paint.Cap.ROUND
                }
                canvas.drawPath(path, p)
            }

            drawSeries(result.p90Curve, "#10B981", 8f)
            drawSeries(result.p50Curve, "#38BDF8", 11f)
            drawSeries(result.p10Curve, "#F59E0B", 8f)

            // Outcomes text
            val metricLabel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#94A3B8")
                textSize = 30f
                textAlign = Paint.Align.CENTER
            }
            val metricValue = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#38BDF8")
                textSize = 92f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("MEDIAN WEALTH (P50)", width / 2f, 1055f, metricLabel)
            canvas.drawText(Formatters.formatCompactUsd(result.medianFinalValue), width / 2f, 1160f, metricValue)

            val subRowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 34f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            subRowPaint.color = Color.parseColor("#F59E0B")
            canvas.drawText("Pessimistic P10: ${Formatters.formatCompactUsd(result.p10FinalValue)}", width / 2f, 1265f, subRowPaint)

            subRowPaint.color = Color.parseColor("#10B981")
            canvas.drawText("Optimistic P90: ${Formatters.formatCompactUsd(result.p90FinalValue)}", width / 2f, 1330f, subRowPaint)

            subRowPaint.color = Color.WHITE
            canvas.drawText(
                "4% FIRE Income: ${Formatters.formatUsd(result.medianMonthlyFireIncome)}/mo",
                width / 2f,
                1455f,
                subRowPaint
            )

            val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#64748B")
                textSize = 26f
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("QuantWealth: Trading Simulator • Educational Simulation Only", width / 2f, 1740f, footerPaint)

            saveAndShareBitmap(context, bitmap, "quantwealth_montecarlo.png", "My Monte Carlo Wealth Simulation on QuantWealth!")
        } catch (e: Exception) {
            Toast.makeText(context, "Could not generate share card.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveAndShareBitmap(context: Context, bitmap: Bitmap, filename: String, shareText: String) {
        val dir = File(context.cacheDir, "shared_cards")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, filename)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 95, out)
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, shareText)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share to Instagram Story / Social"))
    }
}
