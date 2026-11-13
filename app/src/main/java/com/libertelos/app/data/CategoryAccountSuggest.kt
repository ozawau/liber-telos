package com.libertelos.app.data

/**
 * 解析记账时应自动填入的账户：
 * 1. 分类已绑定默认账户且可见 → 用默认账户
 * 2. 否则取该分类最近 [recentLimit] 笔带账户的流水中出现次数最多的账户
 *    （次数相同则取最近出现的）
 */
object CategoryAccountSuggest {
    const val RECENT_LIMIT = 15

    fun resolveAccountId(
        category: Category,
        transactions: List<Transaction>,
        visibleAccountIds: Set<Long>,
        recentLimit: Int = RECENT_LIMIT
    ): Long? {
        val def = category.defaultAccountId
        if (def != null && def in visibleAccountIds) return def

        val recent = transactions
            .asSequence()
            .filter {
                it.categoryId == category.id &&
                    it.accountId != null &&
                    it.accountId in visibleAccountIds
            }
            .sortedByDescending { it.occurredAt }
            .take(recentLimit)
            .mapNotNull { it.accountId }
            .toList()

        if (recent.isEmpty()) return null
        val counts = recent.groupingBy { it }.eachCount()
        val maxCount = counts.values.maxOrNull() ?: return null
        val top = counts.filterValues { it == maxCount }.keys
        return recent.firstOrNull { it in top }
    }
}
