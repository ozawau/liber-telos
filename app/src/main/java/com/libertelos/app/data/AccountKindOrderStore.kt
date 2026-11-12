package com.libertelos.app.data

import android.content.Context

object AccountKindOrderStore {
    private const val PREFS = "liber_telos_prefs"
    private const val KEY_KIND_ORDER = "account_kind_order"

    fun load(context: Context): List<AccountKind> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_KIND_ORDER, null)
            ?: return AccountKind.entries.toList()
        val parsed = raw.split(',')
            .mapNotNull { name -> runCatching { AccountKind.valueOf(name) }.getOrNull() }
        val missing = AccountKind.entries.filterNot { it in parsed }
        return parsed + missing
    }

    fun save(context: Context, order: List<AccountKind>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_KIND_ORDER, order.joinToString(",") { it.name })
            .apply()
    }
}
