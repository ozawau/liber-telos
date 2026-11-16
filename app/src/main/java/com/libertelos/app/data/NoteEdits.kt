package com.libertelos.app.data

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import java.io.File

/**
 * 一次性：按原文改三条备注。只动备注，不改金额、账户和时间。
 */
object NoteEdits {
    private const val TAG = "NoteEdits"
    private const val PREFS = "liber_telos_prefs"
    private const val DONE = "note_edits_v1"

    private val edits = listOf(
        "送孔丽姜糖茶" to "",
        "请柴改兰吃呷哺" to "",
        "寄月饼给父母，寄安仔给何丽英" to "寄月饼给父母"
    )

    suspend fun run(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(DONE, false)) return
        val db = AppDatabase.get(context)
        try {
            val report = db.withTransaction { apply(db) }
            prefs.edit().putBoolean(DONE, true).commit()
            File(context.filesDir, "import/note_edits_report.txt").writeText(report)
            Log.i(TAG, report)
        } catch (t: Throwable) {
            Log.e(TAG, "note edits failed", t)
            File(context.filesDir, "import/note_edits_error.txt").writeText(t.stackTraceToString())
        }
    }

    private suspend fun apply(db: AppDatabase): String {
        val transactionDao = db.transactionDao()
        val txns = transactionDao.listAll()
        val lines = StringBuilder()
        edits.forEach { (from, to) ->
            val matches = txns.filter { it.note == from }
            if (matches.size != 1) {
                error("备注「$from」匹配到 ${matches.size} 条，未修改")
            }
            val txn = matches.first()
            transactionDao.update(txn.copy(note = to))
            lines.append(txn.id)
                .append('\t')
                .append(if (from.isEmpty()) "（空）" else from)
                .append(" -> ")
                .append(if (to.isEmpty()) "（空）" else to)
                .append('\n')
        }
        return "备注已修改\n$lines"
    }
}
