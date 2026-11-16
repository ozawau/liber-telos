package com.libertelos.app.data

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import java.io.File

/**
 * 一次性：按给定对照表合并支出二级分类。
 * 目标已存在时，流水改挂到目标分类，并删除原分类。
 */
object ExpenseCategoryMerge {
    private const val TAG = "ExpenseCategoryMerge"
    private const val PREFS = "liber_telos_prefs"
    private const val DONE = "expense_category_merge_v1"

    private data class Spec(
        val fromParent: String?,
        val fromName: String,
        val toParent: String?,
        val toName: String
    )

    private val specs = listOf(
        Spec(null, "日常用品", null, "家居百货"),
        Spec(null, "化妆护肤", null, "洗护用品"),
        Spec(null, "礼物", null, "礼品纪念品"),
        Spec(null, "购物其他", null, "家居百货"),
        Spec(null, "运动户外", null, "保健用品"),
        Spec(null, "餐具", null, "厨卫清洁"),
        Spec(null, "外出美食", null, "早午晚餐"),
        Spec(null, "晚餐", null, "早午晚餐"),
        Spec(null, "午餐", null, "早午晚餐"),
        Spec(null, "早餐", null, "早午晚餐"),
        Spec(null, "饮料水果", null, "零食饮料"),
        Spec(null, "夜宵", null, "早午晚餐"),
        Spec(null, "买菜做饭", null, "买菜原料"),
        Spec(null, "漏记款", null, "烂账损失"),
        Spec("居家", "丢失", "其他", "丢失"),
        Spec(null, "手机电话", null, "手机话费"),
        Spec(null, "住宿房租", null, "房租"),
        Spec(null, "材料建材", null, "房屋修缮"),
        Spec(null, "办公用品", "购物", "文具玩具"),
        Spec(null, "电脑宽带", null, "网费"),
        Spec(null, "生活其他", null, "税费手续费"),
        Spec(null, "美发美容", null, "理发"),
        Spec(null, "自行车", null, "共享单车"),
        Spec("子女", "其他", "其他", "其他支出"),
        Spec(null, "K歌", null, "卡拉OK"),
        Spec(null, "娱乐其他", "其他", "其他支出"),
        Spec(null, "婚丧嫁娶", null, "礼金红包"),
        Spec("医教", "医疗药品", "医疗", "药品"),
        Spec(null, "打字复印", "购物", "文化用品"),
        Spec(null, "挂号门诊", "医疗", "门诊"),
        Spec(null, "住院费", "医疗", "住院"),
        Spec(null, "养生保健", "医疗", "药品")
    )

    suspend fun run(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(DONE, false)) return
        val db = AppDatabase.get(context)
        try {
            val report = db.withTransaction { merge(db) }
            prefs.edit().putBoolean(DONE, true).commit()
            File(context.filesDir, "import/expense_category_merge_report.txt").writeText(report)
            Log.i(TAG, report)
        } catch (t: Throwable) {
            Log.e(TAG, "expense category merge failed", t)
            File(context.filesDir, "import/expense_category_merge_error.txt").writeText(
                t.stackTraceToString()
            )
        }
    }

    private suspend fun merge(db: AppDatabase): String {
        val categoryDao = db.categoryDao()
        val transactionDao = db.transactionDao()
        val categories = categoryDao.listAll().toMutableList()
        val lines = StringBuilder()
        specs.forEach { spec ->
            val source = findLeaf(categories, spec.fromParent, spec.fromName)
            val target = findLeaf(categories, spec.toParent, spec.toName)
            if (source.id == target.id) return@forEach
            val count = transactionDao.countByCategory(source.id)
            transactionDao.reassignCategory(source.id, target.id)
            if (source.isProtected) {
                error("不能删除受保护分类 ${label(categories, source)}")
            }
            categoryDao.deleteById(source.id)
            categories.removeAll { it.id == source.id }
            lines.append(label(categories, source, spec))
                .append(" -> ")
                .append(label(categories, target))
                .append('\t')
                .append(count)
                .append('\n')
        }
        val removedParents = StringBuilder()
        categoryDao.listParents(TxnType.EXPENSE).forEach { parent ->
            if (parent.isProtected) return@forEach
            val children = categoryDao.getChildren(parent.id)
            if (children.isNotEmpty()) return@forEach
            if (transactionDao.countByCategory(parent.id) > 0) return@forEach
            categoryDao.deleteById(parent.id)
            removedParents.append(parent.name).append('\n')
        }
        val fresh = categoryDao.listAll().filter { it.type == TxnType.EXPENSE }
        val parents = fresh.filter { it.parentId == null }.sortedBy { it.sortOrder }
        return buildString {
            append("支出分类已合并\n")
            append(lines)
            if (removedParents.isNotEmpty()) {
                append("删除已空的一级分类\n")
                append(removedParents)
            }
            append("合并后的支出分类\n")
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

    private fun findLeaf(
        categories: List<Category>,
        parentName: String?,
        name: String
    ): Category {
        val matches = categories.filter { category ->
            category.type == TxnType.EXPENSE &&
                category.parentId != null &&
                category.name == name &&
                (parentName == null || parentNameOf(categories, category) == parentName)
        }
        if (matches.size == 1) return matches.first()
        val where = if (parentName == null) name else "$parentName·$name"
        error(
            if (matches.isEmpty()) "找不到支出分类 $where"
            else "支出分类 $where 不唯一（${matches.size}）"
        )
    }

    private fun parentNameOf(categories: List<Category>, category: Category): String =
        categories.firstOrNull { it.id == category.parentId }?.name ?: "?"

    private fun label(categories: List<Category>, category: Category, spec: Spec? = null): String {
        val parent = spec?.fromParent ?: parentNameOf(categories, category)
        val name = spec?.fromName ?: category.name
        return "$parent·$name"
    }
}
