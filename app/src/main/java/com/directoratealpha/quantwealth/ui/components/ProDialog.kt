package com.directoratealpha.quantwealth.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.directoratealpha.quantwealth.ui.theme.AppColors

@Composable
fun ProUpgradeDialog(
    monthlyPrice: String,
    lifetimePrice: String,
    onDismiss: () -> Unit,
    onBuyMonthly: () -> Unit,
    onBuyLifetime: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppColors.CardDark,
        shape = RoundedCornerShape(22.dp),
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.WorkspacePremium,
                        contentDescription = null,
                        tint = AppColors.AccentGold,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "QUANTWEALTH PRO",
                        color = AppColors.AccentGold,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.2.sp
                    )
                }
                Text(
                    text = "Unlock Full Quant Suite",
                    color = AppColors.TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val perks = listOf(
                    "Unlimited live watchlist slots (Stocks, Crypto, Gold)",
                    "Free 5x, 10x & 20x leverage in $100k simulator",
                    "Multi-asset ETF Backtester (up to 5 weighted assets)",
                    "Unlimited 1,000-Run Monte Carlo + Zero Ads forever"
                )
                perks.forEach { perk ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = AppColors.AccentEmerald,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = perk,
                            color = AppColors.TextSecondary,
                            fontSize = 12.5.sp,
                            lineHeight = 17.sp
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))

                Button(
                    onClick = onBuyLifetime,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 50.dp),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppColors.AccentGold,
                        contentColor = AppColors.BgDeep
                    )
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "LIFETIME PRO • $lifetimePrice ONCE",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                        Text(
                            text = "Best Value • Linked to your Google Account",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                OutlinedButton(
                    onClick = onBuyMonthly,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 46.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.2.dp, AppColors.AccentCyan),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = "Monthly Subscription • $monthlyPrice",
                        color = AppColors.AccentCyan,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Maybe Later", color = AppColors.TextSecondary)
            }
        }
    )
}

@Composable
fun UnlockFeatureDialog(
    badge: String,
    title: String,
    description: String,
    watchAdLabel: String,
    onDismiss: () -> Unit,
    onWatchAd: () -> Unit,
    onOpenPro: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppColors.CardDark,
        shape = RoundedCornerShape(20.dp),
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = badge,
                    color = AppColors.AccentGold,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = title,
                    color = AppColors.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = description,
                    color = AppColors.TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
                Button(
                    onClick = onWatchAd,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppColors.AccentCyan,
                        contentColor = AppColors.BgDeep
                    )
                ) {
                    Icon(Icons.Rounded.PlayCircle, contentDescription = null, modifier = Modifier.size(19.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = watchAdLabel,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                }
                Button(
                    onClick = onOpenPro,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppColors.AccentGold,
                        contentColor = AppColors.BgDeep
                    )
                ) {
                    Icon(Icons.Rounded.WorkspacePremium, contentDescription = null, modifier = Modifier.size(19.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Go PRO (Unlimited + No Ads)",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = AppColors.TextSecondary)
            }
        }
    )
}
