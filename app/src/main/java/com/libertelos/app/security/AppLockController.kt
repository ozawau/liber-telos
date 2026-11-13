package com.libertelos.app.security

/**
 * 进程内解锁状态。MainActivity 在回后台时 lock；
 * QuickAddActivity 不参与。
 */
object AppLockController {
    @Volatile
    var unlocked: Boolean = false
        private set

    fun unlock() {
        unlocked = true
    }

    fun lock() {
        unlocked = false
    }

    fun needsUnlock(prefs: SecurityPrefs): Boolean = prefs.anyEnabled && !unlocked
}
