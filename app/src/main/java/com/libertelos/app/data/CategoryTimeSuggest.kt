package com.libertelos.app.data

import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min

/**
 * 按「一天内相近时段」统计最常用二级分类。
 * 只看 [lookbackDays] 天内的流水；以当前时刻为中心，
 * 落在 ±[windowMinutes] 环形窗口内的计入频次。
 */
object CategoryTimeSuggest {
    const val DEFAULT_WINDOW_MINUTES = 90
    const val DEFAULT_LOOKBACK_DAYS = 30

    private const val DAY_MS = 24L * 60L * 60L * 1000L

    fun mostUsedCategoryId(
        type: TxnType,
        atMillis: Long,
        transactions: List<Transaction>,
        windowMinutes: Int = DEFAULT_WINDOW_MINUTES,
        lookbackDays: Int = DEFAULT_LOOKBACK_DAYS
    ): Long? {
        if (type != TxnType.EXPENSE && type != TxnType.INCOME) return null
        val lookbackStart = atMillis - lookbackDays * DAY_MS
        val cal = Calendar.getInstance(Locale.CHINA)
        cal.timeInMillis = atMillis
        val nowMins = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)

        val counts = HashMap<Long, Int>()
        val latestAt = HashMap<Long, Long>()
        for (txn in transactions) {
            if (txn.type != type) continue
            if (txn.occurredAt < lookbackStart || txn.occurredAt > atMillis) continue
            val catId = txn.categoryId ?: continue
            cal.timeInMillis = txn.occurredAt
            val mins = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
            val delta = abs(mins - nowMins)
            val circular = min(delta, 24 * 60 - delta)
            if (circular > windowMinutes) continue
            counts[catId] = (counts[catId] ?: 0) + 1
            val prev = latestAt[catId] ?: 0L
            if (txn.occurredAt > prev) latestAt[catId] = txn.occurredAt
        }
        return counts.maxWithOrNull(
            compareBy<Map.Entry<Long, Int>> { it.value }
                .thenBy { latestAt[it.key] ?: 0L }
        )?.key
    }
}
