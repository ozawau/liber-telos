package com.libertelos.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.libertelos.app.data.AccountIcons

fun accountIconVector(iconKey: String): ImageVector = when (iconKey) {
    "credit_card" -> Icons.Filled.CreditCard
    "debit_card" -> Icons.Outlined.CreditCard
    "yuebao" -> Icons.Filled.Savings
    "huabei" -> Icons.Filled.CreditCard
    "meituan" -> Icons.Filled.Storefront
    "wechat" -> Icons.Filled.AccountBalanceWallet
    "stock" -> Icons.Filled.ShowChart
    "stable_fund" -> Icons.Filled.AccountBalance
    "transit_card" -> Icons.Filled.DirectionsBus
    "cash" -> Icons.Filled.Payments
    else -> Icons.Filled.AccountBalanceWallet
}

@Composable
fun AccountIconBadge(
    iconKey: String,
    size: Dp = 42.dp,
    rounded: Boolean = true,
    modifier: Modifier = Modifier
) {
    val spec = AccountIcons.byKey(iconKey)
    val shape = if (rounded) CircleShape else RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(Color(spec.colorArgb).copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = accountIconVector(iconKey),
            contentDescription = spec.label,
            tint = Color(spec.colorArgb),
            modifier = Modifier.size(size * 0.48f)
        )
    }
}
