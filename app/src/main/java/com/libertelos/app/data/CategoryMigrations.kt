package com.libertelos.app.data

object CategoryMigrations {
    /**
     * 一级/二级分类批量改名，并将「理财亏损」从居家挪到其他。
     * 只改 categories 表名称与归属，流水仍引用原 categoryId，历史自动跟随。
     */
    suspend fun migrateCategoryLabels(
        categoryDao: CategoryDao,
        transactionDao: TransactionDao
    ) {
        mergeOrRenameParent(categoryDao, transactionDao, "食品酒水", "餐饮")
        mergeOrRenameParent(categoryDao, transactionDao, "购物消费", "购物")
        mergeOrRenameParent(categoryDao, transactionDao, "居家生活", "居家")
        mergeOrRenameParent(categoryDao, transactionDao, "行车交通", "交通")
        mergeOrRenameParent(categoryDao, transactionDao, "子女费用", "子女")
        mergeOrRenameParent(categoryDao, transactionDao, "休闲娱乐", "娱乐")
        mergeOrRenameParent(categoryDao, transactionDao, "人情费用", "人情")
        mergeOrRenameParent(categoryDao, transactionDao, "礼金", "人情")
        mergeOrRenameParent(categoryDao, transactionDao, "医疗费用", "医疗")
        mergeOrRenameParent(categoryDao, transactionDao, "其他杂项", "其他")
        mergeOrRenameParent(categoryDao, transactionDao, "损失", "其他")

        mergeOrRenameLeaf(categoryDao, transactionDao, TxnType.EXPENSE, "请客吃饭", "请客")
        mergeOrRenameLeaf(categoryDao, transactionDao, TxnType.EXPENSE, "意外丢失", "丢失")
        stripMedicalFeeSuffix(categoryDao, transactionDao)
        ensureInvestmentAndMoveLoss(categoryDao)
        migrateIncomeCategories(categoryDao, transactionDao)
    }

    /**
     * 收入：确保「投资」「报销退款」；
     * 利息收入/理财收入 → 投资；财务报销/平台退款 → 报销退款。
     */
    private suspend fun migrateIncomeCategories(
        categoryDao: CategoryDao,
        transactionDao: TransactionDao
    ) {
        val invest = ensureIncomeParent(
            categoryDao,
            name = "投资",
            iconKey = "invest",
            colorArgb = 0xFF2563EB
        )
        val refund = ensureIncomeParent(
            categoryDao,
            name = "报销退款",
            iconKey = "other_in",
            colorArgb = 0xFF0EA5A4
        )
        mergeOrRenameLeaf(categoryDao, transactionDao, TxnType.INCOME, "利息", "利息收入")
        mergeOrRenameLeaf(categoryDao, transactionDao, TxnType.INCOME, "理财收益", "理财收入")
        mergeOrRenameLeaf(categoryDao, transactionDao, TxnType.INCOME, "报销", "财务报销")
        mergeOrRenameLeaf(categoryDao, transactionDao, TxnType.INCOME, "退款", "平台退款")

        moveLeafToParent(categoryDao, TxnType.INCOME, "利息收入", invest)
        moveLeafToParent(categoryDao, TxnType.INCOME, "理财收入", invest)
        moveLeafToParent(categoryDao, TxnType.INCOME, "财务报销", refund)
        moveLeafToParent(categoryDao, TxnType.INCOME, "平台退款", refund)
    }

    private suspend fun ensureIncomeParent(
        categoryDao: CategoryDao,
        name: String,
        iconKey: String,
        colorArgb: Long
    ): Category {
        categoryDao.listParents(TxnType.INCOME).firstOrNull { it.name == name }?.let { return it }
        val sort = categoryDao.maxParentSortOrder(TxnType.INCOME) + 1
        val id = categoryDao.insert(
            Category(
                name = name,
                type = TxnType.INCOME,
                parentId = null,
                sortOrder = sort,
                iconKey = iconKey,
                colorArgb = colorArgb
            )
        )
        return categoryDao.getById(id)!!
    }

    private suspend fun moveLeafToParent(
        categoryDao: CategoryDao,
        type: TxnType,
        leafName: String,
        parent: Category
    ) {
        val leaf = categoryDao.findLeafByName(type, leafName) ?: return
        if (leaf.parentId == parent.id) {
            if (leaf.iconKey != parent.iconKey || leaf.colorArgb != parent.colorArgb) {
                categoryDao.update(
                    leaf.copy(iconKey = parent.iconKey, colorArgb = parent.colorArgb)
                )
            }
            return
        }
        val sort = categoryDao.maxChildSortOrder(parent.id) + 1
        categoryDao.update(
            leaf.copy(
                parentId = parent.id,
                sortOrder = sort,
                iconKey = parent.iconKey,
                colorArgb = parent.colorArgb
            )
        )
    }

    /**
     * 公交地铁 → 地铁（改名保留 id），并补「公交」；
     * 删除「加油」；公交/地铁默认账户设为交通一卡通。
     */
    suspend fun migrateTransitCategories(
        categoryDao: CategoryDao,
        accountDao: AccountDao,
        transactionDao: TransactionDao
    ) {
        val transitParent = findTransitParent(categoryDao) ?: return
        val cardAccount = accountDao.findByNameContains("交通一卡通")
        val cardId = cardAccount?.id

        var metro = categoryDao.findLeafByName(TxnType.EXPENSE, "地铁")
        val busMetro = categoryDao.findLeafByName(TxnType.EXPENSE, "公交地铁")
        if (busMetro != null) {
            if (metro == null) {
                categoryDao.update(
                    busMetro.copy(
                        name = "地铁",
                        defaultAccountId = cardId ?: busMetro.defaultAccountId
                    )
                )
                metro = categoryDao.getById(busMetro.id)
            } else {
                transactionDao.reassignCategory(busMetro.id, metro.id)
                categoryDao.deleteById(busMetro.id)
            }
        }
        if (metro == null) {
            val sort = categoryDao.maxChildSortOrder(transitParent.id) + 1
            val id = categoryDao.insert(
                Category(
                    name = "地铁",
                    type = TxnType.EXPENSE,
                    parentId = transitParent.id,
                    sortOrder = sort,
                    iconKey = transitParent.iconKey,
                    colorArgb = transitParent.colorArgb,
                    defaultAccountId = cardId
                )
            )
            metro = categoryDao.getById(id)
        } else if (cardId != null && metro.defaultAccountId != cardId) {
            categoryDao.update(metro.copy(defaultAccountId = cardId))
        }

        var bus = categoryDao.findLeafByName(TxnType.EXPENSE, "公交")
        if (bus == null) {
            val sort = categoryDao.maxChildSortOrder(transitParent.id) + 1
            categoryDao.insert(
                Category(
                    name = "公交",
                    type = TxnType.EXPENSE,
                    parentId = transitParent.id,
                    sortOrder = sort,
                    iconKey = transitParent.iconKey,
                    colorArgb = transitParent.colorArgb,
                    defaultAccountId = cardId
                )
            )
        } else if (cardId != null && bus.defaultAccountId != cardId) {
            categoryDao.update(bus.copy(defaultAccountId = cardId))
        }

        categoryDao.findLeafByName(TxnType.EXPENSE, "加油")?.let { gas ->
            transactionDao.clearCategory(gas.id)
            categoryDao.deleteById(gas.id)
        }
    }

    private suspend fun mergeOrRenameParent(
        categoryDao: CategoryDao,
        transactionDao: TransactionDao,
        from: String,
        to: String
    ) {
        if (from == to) return
        val parents = categoryDao.listParents(TxnType.EXPENSE)
        val source = parents.firstOrNull { it.name == from } ?: return
        val target = parents.firstOrNull { it.name == to }
        if (target == null) {
            categoryDao.update(source.copy(name = to))
            return
        }
        if (target.id == source.id) return

        categoryDao.getChildren(source.id).forEach { child ->
            val dup = categoryDao.getChildren(target.id)
                .firstOrNull { it.name == child.name && it.id != child.id }
            if (dup != null) {
                transactionDao.reassignCategory(child.id, dup.id)
                categoryDao.deleteById(child.id)
            } else {
                categoryDao.update(
                    child.copy(
                        parentId = target.id,
                        iconKey = target.iconKey.ifBlank { child.iconKey },
                        colorArgb = target.colorArgb
                    )
                )
            }
        }
        categoryDao.deleteById(source.id)
    }

    private suspend fun mergeOrRenameLeaf(
        categoryDao: CategoryDao,
        transactionDao: TransactionDao,
        type: TxnType,
        from: String,
        to: String
    ) {
        if (from == to) return
        val source = categoryDao.findLeafByName(type, from) ?: return
        val existing = categoryDao.findLeafByName(type, to)
        if (existing == null) {
            categoryDao.update(source.copy(name = to))
            return
        }
        if (existing.id == source.id) return
        transactionDao.reassignCategory(source.id, existing.id)
        categoryDao.deleteById(source.id)
    }

    private suspend fun stripMedicalFeeSuffix(
        categoryDao: CategoryDao,
        transactionDao: TransactionDao
    ) {
        val medical = categoryDao.listParents(TxnType.EXPENSE)
            .firstOrNull { it.name == "医疗" || it.name == "医疗费用" }
            ?: return
        categoryDao.getChildren(medical.id).forEach { child ->
            if (!child.name.endsWith("费用") || child.name.length <= 2) return@forEach
            val trimmed = child.name.removeSuffix("费用")
            if (trimmed.isBlank() || trimmed == child.name) return@forEach
            val dup = categoryDao.getChildren(medical.id)
                .firstOrNull { it.name == trimmed && it.id != child.id }
            if (dup != null) {
                transactionDao.reassignCategory(child.id, dup.id)
                categoryDao.deleteById(child.id)
            } else {
                categoryDao.update(child.copy(name = trimmed))
            }
        }
    }

    /** 确保有支出一级「投资」，并把「理财亏损」挂到其下（保留叶子 id，历史流水跟随） */
    private suspend fun ensureInvestmentAndMoveLoss(categoryDao: CategoryDao) {
        val parents = categoryDao.listParents(TxnType.EXPENSE)
        var invest = parents.firstOrNull { it.name == "投资" }
        if (invest == null) {
            val sort = categoryDao.maxParentSortOrder(TxnType.EXPENSE) + 1
            val id = categoryDao.insert(
                Category(
                    name = "投资",
                    type = TxnType.EXPENSE,
                    parentId = null,
                    sortOrder = sort,
                    iconKey = "invest",
                    colorArgb = 0xFF2563EB
                )
            )
            invest = categoryDao.getById(id)
        }
        val investParent = invest ?: return

        val loss = categoryDao.findLeafByName(TxnType.EXPENSE, "理财亏损")
        if (loss == null) {
            val childSort = categoryDao.maxChildSortOrder(investParent.id) + 1
            categoryDao.insert(
                Category(
                    name = "理财亏损",
                    type = TxnType.EXPENSE,
                    parentId = investParent.id,
                    sortOrder = childSort,
                    iconKey = investParent.iconKey,
                    colorArgb = investParent.colorArgb
                )
            )
            return
        }
        if (loss.parentId == investParent.id) {
            if (loss.iconKey != investParent.iconKey || loss.colorArgb != investParent.colorArgb) {
                categoryDao.update(
                    loss.copy(
                        iconKey = investParent.iconKey,
                        colorArgb = investParent.colorArgb
                    )
                )
            }
            return
        }
        val childSort = categoryDao.maxChildSortOrder(investParent.id) + 1
        categoryDao.update(
            loss.copy(
                parentId = investParent.id,
                sortOrder = childSort,
                iconKey = investParent.iconKey,
                colorArgb = investParent.colorArgb
            )
        )
    }

    private suspend fun findTransitParent(categoryDao: CategoryDao): Category? {
        val all = categoryDao.listParents(TxnType.EXPENSE)
        return all.firstOrNull { it.name == "行车交通" || it.name == "交通" }
    }
}
