package com.libertelos.app.data

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import java.io.File

/**
 * 一次性：按给定对照表合并收入分类。
 * 目标分类已存在时，流水改挂过去并删除原分类。
 * 漏记款只改挂到「其他收入」下，不并入「其他收入 / 其他收入」。
 * 分类发生变化的流水，在备注中记下原分类。
 */
object IncomeCategoryMerge {
    private const val TAG = "IncomeCategoryMerge"
    private const val PREFS = "liber_telos_prefs"
    private const val DONE = "income_category_merge_v1"

    private data class Spec(
        val fromParent: String?,
        val fromName: String,
        val toParent: String?,
        val toName: String,
        /** 只改上级分类，保留原名，不并入同名的其他二级分类 */
        val moveOnly: Boolean = false
    )

    private val specs = listOf(
        Spec(null, "漏记款", "其他收入", "漏记款", moveOnly = true),
        Spec("基金", "基金", null, "理财收入"),
        Spec(null, "福利补贴", null, "奖金收入"),
        Spec(null, "应收款", "其他收入", "其他收入"),
        Spec(null, "退款返款", null, "平台退款"),
        Spec(null, "租金", "其他收入", "其他收入"),
        Spec(null, "报销款", null, "财务报销"),
        Spec(null, "公积金", null, "公积金收入"),
        Spec(null, "工资薪水", null, "工资收入"),
        Spec(null, "利息", null, "利息收入"),
        Spec(null, "零花", "其他收入", "其他收入"),
        Spec(null, "奖金", null, "奖金收入"),
        Spec(null, "赔付款", null, "平台退款"),
        Spec(null, "兼职外快", null, "兼职收入")
    )

    suspend fun run(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(DONE, false)) return
        val db = AppDatabase.get(context)
        try {
            val report = db.withTransaction { merge(db) }
            prefs.edit().putBoolean(DONE, true).commit()
            File(context.filesDir, "import/income_category_merge_report.txt").writeText(report)
            Log.i(TAG, report)
        } catch (t: Throwable) {
            Log.e(TAG, "income category merge failed", t)
            File(context.filesDir, "import/income_category_merge_error.txt").writeText(
                t.stackTraceToString()
            )
        }
    }

    private suspend fun merge(db: AppDatabase): String {
        val categoryDao = db.categoryDao()
        val transactionDao = db.transactionDao()
        var categories = categoryDao.listAll().toMutableList()
        val lines = StringBuilder()

        specs.forEach { spec ->
            val source = findSource(categories, spec.fromParent, spec.fromName)
            val mark = originMark(categories, source)
            if (spec.moveOnly) {
                val parent = findParent(categories, spec.toParent ?: error("移动缺少上级"))
                val alreadyThere = source.parentId == parent.id && source.name == spec.toName
                if (!alreadyThere) {
                    val oldParentId = source.parentId
                    if (oldParentId != null && oldParentId != parent.id) {
                        moveTransactions(transactionDao, oldParentId, source.id, mark)
                    }
                    val noted = annotate(transactionDao, source.id, mark)
                    val sort = categoryDao.maxChildSortOrder(parent.id) + 1
                    categoryDao.update(
                        source.copy(
                            name = spec.toName,
                            parentId = parent.id,
                            sortOrder = sort,
                            iconKey = parent.iconKey,
                            colorArgb = parent.colorArgb
                        )
                    )
                    lines.append(mark.removePrefix("原分类："))
                        .append(" -> ")
                        .append(parent.name)
                        .append('·')
                        .append(spec.toName)
                        .append("\t移动 ")
                        .append(noted)
                        .append('\n')
                }
            } else {
                val target = findLeaf(categories, spec.toParent, spec.toName)
                if (source.id != target.id) {
                    val count = moveTransactions(transactionDao, source.id, target.id, mark)
                    if (source.isProtected) {
                        error("不能删除受保护分类 ${originMark(categories, source)}")
                    }
                    if (source.parentId == null) {
                        val children = categories.filter { it.parentId == source.id }
                        if (children.isNotEmpty()) {
                            error("${source.name} 下还有二级分类，不能删除")
                        }
                    }
                    categoryDao.deleteById(source.id)
                    lines.append(mark.removePrefix("原分类："))
                        .append(" -> ")
                        .append(leafLabel(categories, target))
                        .append('\t')
                        .append(count)
                        .append('\n')
                }
            }
            categories = categoryDao.listAll().toMutableList()
        }

        val removedParents = StringBuilder()
        categoryDao.listParents(TxnType.INCOME).forEach { parent ->
            if (parent.isProtected) return@forEach
            if (categoryDao.getChildren(parent.id).isNotEmpty()) return@forEach
            if (transactionDao.countByCategory(parent.id) > 0) return@forEach
            categoryDao.deleteById(parent.id)
            removedParents.append(parent.name).append('\n')
        }

        val fresh = categoryDao.listAll().filter { it.type == TxnType.INCOME }
        val parents = fresh.filter { it.parentId == null }.sortedBy { it.sortOrder }
        return buildString {
            append("收入分类已合并\n")
            append(lines)
            if (removedParents.isNotEmpty()) {
                append("删除已空的一级分类\n")
                append(removedParents)
            }
            append("合并后的收入分类\n")
            parents.forEach { parent ->
                append(parent.name).append('\n')
                fresh.filter { it.parentId == parent.id }
                    .sortedBy { it.sortOrder }
                    .forEach { child ->
                        append("  ").append(child.name).append('\n')
                    }
            }
        }
    }

    private suspend fun annotate(
        transactionDao: TransactionDao,
        categoryId: Long,
        mark: String
    ): Int {
        val txns = transactionDao.listByCategory(categoryId)
        txns.forEach { txn ->
            val note = withOrigin(txn.note, mark)
            if (note != txn.note) {
                transactionDao.update(txn.copy(note = note))
            }
        }
        return txns.size
    }

    private suspend fun moveTransactions(
        transactionDao: TransactionDao,
        fromId: Long,
        toId: Long,
        mark: String
    ): Int {
        val txns = transactionDao.listByCategory(fromId)
        txns.forEach { txn ->
            transactionDao.update(
                txn.copy(
                    categoryId = toId,
                    note = withOrigin(txn.note, mark)
                )
            )
        }
        return txns.size
    }

    private fun withOrigin(note: String, mark: String): String {
        if (mark in note) return note
        if (note.isBlank()) return mark
        return "$note；$mark"
    }

    private fun findSource(
        categories: List<Category>,
        parentName: String?,
        name: String
    ): Category {
        val leaves = categories.filter { category ->
            category.type == TxnType.INCOME &&
                category.parentId != null &&
                category.name == name &&
                (parentName == null || parentNameOf(categories, category) == parentName)
        }
        if (leaves.size == 1) return leaves.first()
        if (leaves.isEmpty() && parentName == null) {
            val parent = categories.firstOrNull {
                it.type == TxnType.INCOME && it.parentId == null && it.name == name
            }
            if (parent != null) return parent
        }
        val where = if (parentName == null) name else "$parentName·$name"
        error(
            if (leaves.isEmpty()) "找不到收入分类 $where"
            else "收入分类 $where 不唯一（${leaves.size}）"
        )
    }

    private fun findLeaf(
        categories: List<Category>,
        parentName: String?,
        name: String
    ): Category {
        val matches = categories.filter { category ->
            category.type == TxnType.INCOME &&
                category.parentId != null &&
                category.name == name &&
                (parentName == null || parentNameOf(categories, category) == parentName)
        }
        if (matches.size == 1) return matches.first()
        val where = if (parentName == null) name else "$parentName·$name"
        error(
            if (matches.isEmpty()) "找不到收入分类 $where"
            else "收入分类 $where 不唯一（${matches.size}）"
        )
    }

    private fun findParent(categories: List<Category>, name: String): Category =
        categories.firstOrNull {
            it.type == TxnType.INCOME && it.parentId == null && it.name == name
        } ?: error("找不到一级收入分类 $name")

    private fun parentNameOf(categories: List<Category>, category: Category): String =
        categories.firstOrNull { it.id == category.parentId }?.name ?: category.name

    private fun originMark(categories: List<Category>, category: Category): String {
        if (category.parentId == null) return "原分类：${category.name}"
        return "原分类：${parentNameOf(categories, category)}·${category.name}"
    }

    private fun leafLabel(categories: List<Category>, category: Category): String =
        "${parentNameOf(categories, category)}·${category.name}"
}
