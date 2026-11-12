package com.libertelos.app.ui.components

import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

fun formatYuan(cents: Long, withSign: Boolean = false): String {
    val yuan = cents / 100.0
    val nf = NumberFormat.getCurrencyInstance(Locale.CHINA).apply {
        currency = Currency.getInstance("CNY")
        maximumFractionDigits = 2
        minimumFractionDigits = 2
    }
    val raw = nf.format(kotlin.math.abs(yuan))
    return when {
        !withSign -> raw
        cents > 0 -> "+$raw"
        cents < 0 -> "-$raw"
        else -> raw
    }
}

fun formatYuanPlain(cents: Long): String {
    val yuan = cents / 100.0
    return String.format(Locale.CHINA, "¥%.2f", yuan)
}
