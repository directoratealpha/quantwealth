package com.directoratealpha.quantwealth.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

object AppColors {
    val BgDeep = Color(0xFF070C17)
    val BgSurface = Color(0xFF0B1222)
    val CardDark = Color(0xFF0F172A)
    val CardElevated = Color(0xFF16213A)
    val CardBorder = Color(0xFF1E2D4A)

    val AccentCyan = Color(0xFF38BDF8)
    val AccentEmerald = Color(0xFF10B981)
    val AccentRed = Color(0xFFEF4444)
    val AccentGold = Color(0xFFF59E0B)
    val AccentPurple = Color(0xFFA855F7)

    val TextPrimary = Color(0xFFF8FAFC)
    val TextSecondary = Color(0xFF94A3B8)
    val TextMuted = Color(0xFF64748B)
}

private val QuantDarkScheme = darkColorScheme(
    primary = AppColors.AccentCyan,
    onPrimary = AppColors.BgDeep,
    secondary = AppColors.AccentEmerald,
    onSecondary = AppColors.BgDeep,
    tertiary = AppColors.AccentGold,
    background = AppColors.BgDeep,
    onBackground = AppColors.TextPrimary,
    surface = AppColors.CardDark,
    onSurface = AppColors.TextPrimary,
    error = AppColors.AccentRed
)

@Composable
fun QuantWealthTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = QuantDarkScheme,
        content = content
    )
}

object Formatters {
    fun formatUsd(amount: Double, showSign: Boolean = false): String {
        val sign = when {
            amount < 0 -> "-"
            showSign && amount > 0 -> "+"
            else -> ""
        }
        val absVal = abs(amount)
        return when {
            absVal >= 1_000_000_000.0 -> String.format(Locale.US, "%s$%.2fB", sign, absVal / 1_000_000_000.0)
            absVal >= 10_000_000.0 -> String.format(Locale.US, "%s$%.2fM", sign, absVal / 1_000_000.0)
            else -> String.format(Locale.US, "%s$%,.2f", sign, absVal)
        }
    }

    fun formatCompactUsd(amount: Double): String {
        val sign = if (amount < 0) "-" else ""
        val absVal = abs(amount)
        return when {
            absVal >= 1_000_000.0 -> String.format(Locale.US, "%s$%.2fM", sign, absVal / 1_000_000.0)
            absVal >= 1_000.0 -> String.format(Locale.US, "%s$%.1fk", sign, absVal / 1_000.0)
            else -> String.format(Locale.US, "%s$%.0f", sign, absVal)
        }
    }

    fun formatPrice(price: Double): String {
        return when {
            price >= 1000.0 -> String.format(Locale.US, "$%,.2f", price)
            price >= 1.0 -> String.format(Locale.US, "$%.2f", price)
            price >= 0.01 -> String.format(Locale.US, "$%.4f", price)
            else -> String.format(Locale.US, "$%.6f", price)
        }
    }

    fun formatPct(pct: Double, showSign: Boolean = true): String {
        val sign = if (showSign && pct > 0) "+" else ""
        return String.format(Locale.US, "%s%.2f%%", sign, pct)
    }

    fun formatTimestamp(timestampSec: Long, includeTime: Boolean): String {
        if (timestampSec <= 0L) return ""
        val pattern = if (includeTime) "MMM d, HH:mm" else "MMM d, yyyy"
        return SimpleDateFormat(pattern, Locale.US).format(Date(timestampSec * 1000L))
    }
}
