package com.ankan.netfreeze

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Handler
import android.os.Looper

data class AppItem(
    val pkg: String,
    val label: String,
    val hasLauncher: Boolean,
    val isSystem: Boolean
)

/** Loads the list of installed apps once, off the main thread. Call from the main thread only. */
object AppRepo {
    @Volatile
    var items: List<AppItem> = emptyList()

    @Volatile
    var loaded: Boolean = false

    private var loading = false
    private val waiters = ArrayList<() -> Unit>()
    private val main = Handler(Looper.getMainLooper())

    fun load(ctx: Context, force: Boolean = false, done: (() -> Unit)? = null) {
        if (loaded && !force) {
            done?.invoke()
            return
        }
        if (done != null) waiters.add(done)
        if (loading) return
        loading = true
        val app = ctx.applicationContext
        Thread {
            val result: List<AppItem> = try {
                scan(app)
            } catch (e: Exception) {
                emptyList()
            }
            main.post {
                items = result
                loaded = true
                loading = false
                val copy = ArrayList(waiters)
                waiters.clear()
                for (w in copy) w()
            }
        }.start()
    }

    private fun scan(ctx: Context): List<AppItem> {
        val pm = ctx.packageManager
        val launcherPkgs = HashSet<String>()
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        for (ri in pm.queryIntentActivities(query, 0)) {
            launcherPkgs.add(ri.activityInfo.packageName)
        }
        val out = ArrayList<AppItem>()
        for (ai in pm.getInstalledApplications(0)) {
            if (ai.packageName == ctx.packageName) continue
            val isSys = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                (ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
            val label = try {
                ai.loadLabel(pm).toString()
            } catch (e: Exception) {
                ai.packageName
            }
            out.add(AppItem(ai.packageName, label, launcherPkgs.contains(ai.packageName), isSys))
        }
        out.sortWith(compareBy<AppItem>({ it.label.lowercase() }, { it.pkg }))
        return out
    }

    /** Number of visible (launcher) apps that will be frozen. */
    fun frozenCount(exempt: Set<String>): Int {
        var n = 0
        for (a in items) {
            if (a.hasLauncher && a.pkg !in exempt) n++
        }
        return n
    }
}
