package com.ankan.netfreeze

import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.app.usage.StorageStatsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.os.storage.StorageManager
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File
import java.util.Calendar

class AppMgmtScreen(ctx: Context, nav: Nav, private val startTab: Int) : Screen(ctx, nav) {
    private val page = Page(ctx, nav, "App management")
    override val view: View get() = page.root
    private val main = Handler(Looper.getMainLooper())
    private val tabViews = ArrayList<TextView>()
    private val host = LinearLayout(ctx)
    private var tab = startTab
    private var week = false
    private var token = 0

    init {
        // quick actions
        val qa = LinearLayout(ctx)
        qa.orientation = LinearLayout.HORIZONTAL
        qa.addView(quick(Ic.DOWNLOAD, "App updates", "Check") { openStore() }, UI.lp(0, UI.WRAP, 1f))
        qa.addView(quick(Ic.SPARKLE, "Clean up junk files", "Clean up") { nav.push(CleanupScreen(ctx, nav, false)) }, UI.lp(0, UI.WRAP, 1f))
        qa.addView(quick(Ic.TRASH, "Uninstall apps", "Uninstall") { nav.push(UninstallScreen(ctx, nav)) }, UI.lp(0, UI.WRAP, 1f))
        qa.addView(quick(Ic.BOX, "APKs", "View") { nav.push(FileListScreen(ctx, nav, 1)) }, UI.lp(0, UI.WRAP, 1f))
        page.add(qa, UI.lp(UI.MATCH, UI.WRAP, 0f, 0, UI.dp(ctx, 6), 0, 0))

        // segmented tabs
        val seg = LinearLayout(ctx)
        seg.orientation = LinearLayout.HORIZONTAL
        seg.gravity = Gravity.CENTER_VERTICAL
        seg.background = UI.rounded(ctx, UI.CARD, 30f, UI.STROKE, 1f)
        seg.setPadding(UI.dp(ctx, 5), UI.dp(ctx, 5), UI.dp(ctx, 5), UI.dp(ctx, 5))
        val names = arrayOf("Screen time", "Data usage", "Battery")
        for (i in names.indices) {
            val t = UI.text(ctx, names[i], 15f, UI.MUTED, UI.medium())
            t.gravity = Gravity.CENTER
            t.setPadding(UI.dp(ctx, 4), UI.dp(ctx, 12), UI.dp(ctx, 4), UI.dp(ctx, 12))
            t.isClickable = true
            t.setOnClickListener { selectTab(i) }
            tabViews.add(t)
            seg.addView(t, UI.lp(0, UI.WRAP, 1f))
        }
        page.add(seg, UI.lp(UI.MATCH, UI.WRAP, 0f, 0, UI.dp(ctx, 24), 0, 0))
        host.orientation = LinearLayout.VERTICAL
        page.add(host, UI.lp(UI.MATCH, UI.WRAP, 0f, 0, UI.dp(ctx, 16), 0, 0))
    }

    private fun quick(icon: Int, label: String, sub: String, onClick: () -> Unit): View {
        val c = LinearLayout(ctx)
        c.orientation = LinearLayout.VERTICAL
        c.gravity = Gravity.CENTER_HORIZONTAL
        c.isClickable = true
        c.setOnClickListener { onClick() }
        val circle = FrameLayout(ctx)
        circle.background = UI.rounded(ctx, UI.CARD, 40f, UI.STROKE, 1f)
        circle.addView(IconView(ctx, icon), FrameLayout.LayoutParams(UI.dp(ctx, 26), UI.dp(ctx, 26), Gravity.CENTER))
        c.addView(circle, UI.lp(UI.dp(ctx, 58), UI.dp(ctx, 58)))
        val l = UI.text(ctx, label, 13.5f, UI.TEXT, UI.medium())
        l.gravity = Gravity.CENTER
        c.addView(l, UI.lp(UI.MATCH, UI.WRAP, 0f, UI.dp(ctx, 2), UI.dp(ctx, 10), UI.dp(ctx, 2), 0))
        val s = UI.text(ctx, sub, 12.5f, UI.MUTED)
        s.gravity = Gravity.CENTER
        c.addView(s, UI.lp(UI.WRAP, UI.WRAP, 0f, 0, UI.dp(ctx, 4), 0, 0))
        return c
    }

    private fun openStore() {
        val i = ctx.packageManager.getLaunchIntentForPackage("com.android.vending")
        if (i != null) nav.launch(i) else nav.toast("Play Store not found")
    }

    override fun onShow() {
        selectTab(tab)
    }

    private fun selectTab(i: Int) {
        tab = i
        for (k in tabViews.indices) {
            val on = k == i
            tabViews[k].setTextColor(if (on) UI.TEXT else UI.MUTED)
            tabViews[k].background = if (on) UI.rounded(ctx, UI.CARD2, 26f) else null
        }
        host.removeAllViews()
        token++
        when (i) {
            0 -> buildUsage(true)
            1 -> buildUsage(false)
            else -> buildBattery()
        }
    }

    private fun needAccess(text: String) {
        val c = UI.card(ctx)
        c.setPadding(UI.dp(ctx, 20), UI.dp(ctx, 20), UI.dp(ctx, 20), UI.dp(ctx, 20))
        c.addView(UI.text(ctx, "Usage access needed", 18f, UI.TEXT, UI.medium()))
        c.addView(UI.text(ctx, text, 14f, UI.MUTED), UI.lp(UI.MATCH, UI.WRAP, 0f, 0, UI.dp(ctx, 8), 0, UI.dp(ctx, 16)))
        val b = UI.pill(ctx, "Allow usage access", true)
        b.setOnClickListener { nav.openUsage() }
        c.addView(b, UI.lp(UI.MATCH, UI.WRAP))
        host.addView(c)
    }

    private fun periodToggle(title: TextView, onChange: () -> Unit): View {
        val box = LinearLayout(ctx)
        box.orientation = LinearLayout.HORIZONTAL
        box.background = UI.rounded(ctx, UI.CARD, 22f, UI.STROKE, 1f)
        box.setPadding(UI.dp(ctx, 3), UI.dp(ctx, 3), UI.dp(ctx, 3), UI.dp(ctx, 3))
        val a = UI.text(ctx, "DAY", 13f, UI.TEXT, UI.bold())
        val b = UI.text(ctx, "WEEK", 13f, UI.MUTED, UI.bold())
        for (t in listOf(a, b)) {
            t.gravity = Gravity.CENTER
            t.setPadding(UI.dp(ctx, 14), UI.dp(ctx, 7), UI.dp(ctx, 14), UI.dp(ctx, 7))
        }
        fun paint() {
            a.setTextColor(if (!week) UI.TEXT else UI.MUTED)
            b.setTextColor(if (week) UI.TEXT else UI.MUTED)
            a.background = if (!week) UI.rounded(ctx, UI.CARD2, 18f) else null
            b.background = if (week) UI.rounded(ctx, UI.CARD2, 18f) else null
        }
        a.setOnClickListener { week = false; paint(); onChange() }
        b.setOnClickListener { week = true; paint(); onChange() }
        paint()
        box.addView(a)
        box.addView(b)
        return box
    }

    private fun startOfRange(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        if (week) cal.add(Calendar.DAY_OF_YEAR, -6)
        return cal.timeInMillis
    }

    /** screen = true: screen time, false: data usage. */
    private fun buildUsage(screen: Boolean) {
        if (!Sys.hasUsageAccess(ctx)) {
            needAccess(
                if (screen) "To show how long you use each app, Android needs you to allow usage access for Blutheist."
                else "To show how much data each app uses, Android needs you to allow usage access for Blutheist."
            )
            return
        }
        val card = UI.card(ctx)
        card.setPadding(UI.dp(ctx, 20), UI.dp(ctx, 18), UI.dp(ctx, 20), UI.dp(ctx, 20))
        val top = LinearLayout(ctx)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        val col = LinearLayout(ctx)
        col.orientation = LinearLayout.VERTICAL
        val cap = UI.text(ctx, "", 14.5f, UI.MUTED)
        val big = UI.text(ctx, "…", 34f, UI.TEXT, UI.bold())
        val sub = UI.text(ctx, "", 13.5f, UI.MUTED)
        col.addView(cap)
        col.addView(big, UI.lp(UI.WRAP, UI.WRAP, 0f, 0, UI.dp(ctx, 8), 0, 0))
        col.addView(sub, UI.lp(UI.WRAP, UI.WRAP, 0f, 0, UI.dp(ctx, 6), 0, 0))
        top.addView(col, UI.lp(0, UI.WRAP, 1f))
        val chart = BarsView(ctx)
        val listHost = LinearLayout(ctx)
        listHost.orientation = LinearLayout.VERTICAL
        fun load() {
            cap.text = (if (screen) "Screen time " else "Data used ") + (if (week) "last 7 days" else "today")
            big.text = "…"
            sub.text = ""
            chart.setBars(emptyList())
            listHost.removeAllViews()
            if (screen) loadScreenTime(big, sub, chart, listHost) else loadData(big, sub, chart, listHost)
        }
        top.addView(periodToggle(cap) { load() })
        card.addView(top)
        card.addView(UI.text(ctx, "By app", 14f, UI.MUTED), UI.lp(UI.WRAP, UI.WRAP, 0f, 0, UI.dp(ctx, 22), 0, UI.dp(ctx, 10)))
        card.addView(chart, UI.lp(UI.MATCH, UI.dp(ctx, 190)))
        host.addView(card)
        host.addView(listHost, UI.lp(UI.MATCH, UI.WRAP, 0f, 0, UI.dp(ctx, 16), 0, 0))
        load()
    }

    private class UsageItem(val pkg: String, val label: String, val value: Long)

    private fun showResults(
        items: List<UsageItem>, chart: BarsView, listHost: LinearLayout, fmt: (Long) -> String
    ) {
        val top = items.take(6)
        val mx = (top.firstOrNull()?.value ?: 1L).coerceAtLeast(1L).toFloat()
        chart.setBars(top.map { Pair(it.value / mx, it.label.take(1).uppercase()) })
        listHost.removeAllViews()
        if (items.isEmpty()) return
        val c = UI.card(ctx)
        val shown = items.take(10)
        for (i in shown.indices) {
            val it = shown[i]
            val row = ItemRow(ctx)
            bindAppIcon(ctx, row, it.pkg)
            row.title.text = it.label
            row.sub.visibility = View.GONE
            row.rtext.visibility = View.VISIBLE
            row.rtext.setTextColor(UI.TEXT)
            row.rtext.text = fmt(it.value)
            row.isClickable = true
            val pkg = it.pkg
            row.setOnClickListener { openAppSettings(nav, pkg) }
            c.addView(row)
            if (i < shown.size - 1) c.addView(UI.divider(ctx), UI.lp(UI.MATCH, 1, 0f, UI.dp(ctx, 16), 0, UI.dp(ctx, 16), 0))
        }
        listHost.addView(c)
    }

    private fun loadScreenTime(big: TextView, sub: TextView, chart: BarsView, listHost: LinearLayout) {
        val my = token
        val app = ctx.applicationContext
        val begin = startOfRange()
        Thread {
            val out = ArrayList<UsageItem>()
            var total = 0L
            try {
                val usm = app.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
                val map = usm.queryAndAggregateUsageStats(begin, System.currentTimeMillis())
                val pm = app.packageManager
                val home = HashSet<String>()
                for (ri in pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)) home.add(ri.activityInfo.packageName)
                for ((pkg, st) in map) {
                    val t = st.totalTimeInForeground
                    if (t < 60000L || pkg in home || pkg == "com.android.systemui") continue
                    if (pm.getLaunchIntentForPackage(pkg) == null) continue
                    total += t
                    out.add(UsageItem(pkg, appLabel(app, pkg), t))
                }
            } catch (e: Exception) {
                // ignore
            }
            out.sortByDescending { it.value }
            main.post {
                if (my != token) return@post
                big.text = Sys.fmtDuration(total)
                sub.text = if (out.isEmpty()) "No usage recorded yet" else out.size.toString() + " apps used"
                showResults(out, chart, listHost) { Sys.fmtDuration(it) }
            }
        }.start()
    }

    private fun loadData(big: TextView, sub: TextView, chart: BarsView, listHost: LinearLayout) {
        val my = token
        val app = ctx.applicationContext
        val begin = startOfRange()
        Thread {
            val perUid = HashMap<Int, Long>()
            var wifi = 0L
            var mobile = 0L
            val end = System.currentTimeMillis()
            try {
                val nsm = app.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
                for (type in intArrayOf(ConnectivityManager.TYPE_WIFI, ConnectivityManager.TYPE_MOBILE)) {
                    try {
                        val stats = nsm.querySummary(type, null, begin, end)
                        val b = NetworkStats.Bucket()
                        while (stats.hasNextBucket()) {
                            stats.getNextBucket(b)
                            val bytes = b.rxBytes + b.txBytes
                            perUid[b.uid] = (perUid[b.uid] ?: 0L) + bytes
                            if (type == ConnectivityManager.TYPE_WIFI) wifi += bytes else mobile += bytes
                        }
                        stats.close()
                    } catch (e: Exception) {
                        // this network type is not readable on this phone
                    }
                }
            } catch (e: Exception) {
                // ignore
            }
            val pm = app.packageManager
            val byPkg = HashMap<String, Long>()
            for ((uid, bytes) in perUid) {
                if (bytes <= 0L) continue
                val pkg = pm.getPackagesForUid(uid)?.firstOrNull() ?: continue
                byPkg[pkg] = (byPkg[pkg] ?: 0L) + bytes
            }
            val out = ArrayList<UsageItem>()
            for ((pkg, bytes) in byPkg) {
                if (pm.getLaunchIntentForPackage(pkg) == null) continue
                out.add(UsageItem(pkg, appLabel(app, pkg), bytes))
            }
            out.sortByDescending { it.value }
            main.post {
                if (my != token) return@post
                big.text = Sys.fmtBytes(wifi + mobile)
                sub.text = "Wi-Fi " + Sys.fmtBytes(wifi) + " · Mobile " + Sys.fmtBytes(mobile)
                showResults(out, chart, listHost) { Sys.fmtBytes(it) }
            }
        }.start()
    }

    private fun buildBattery() {
        val b = Sys.battery(ctx)
        val card = UI.card(ctx)
        card.setPadding(UI.dp(ctx, 20), UI.dp(ctx, 20), UI.dp(ctx, 20), UI.dp(ctx, 22))
        card.addView(UI.text(ctx, "Battery level", 14.5f, UI.MUTED))
        card.addView(UI.text(ctx, b.level.toString() + "%", 44f, UI.TEXT, UI.bold()), UI.lp(UI.WRAP, UI.WRAP, 0f, 0, UI.dp(ctx, 8), 0, 0))
        card.addView(UI.text(ctx, b.statusText + " · " + b.source, 14f, UI.MUTED), UI.lp(UI.WRAP, UI.WRAP, 0f, 0, UI.dp(ctx, 6), 0, UI.dp(ctx, 16)))
        val bar = ThinProgress(ctx)
        bar.animateTo(b.level / 100f, 1000)
        card.addView(bar, UI.lp(UI.MATCH, UI.dp(ctx, 8)))
        host.addView(card)
        val c = UI.card(ctx)
        val rows = arrayOf(
            Pair("Temperature", Sys.fmtTemp(ctx, b.tempC)),
            Pair("Voltage", String.format(java.util.Locale.US, "%.2f V", b.voltageV)),
            Pair("Health", b.health),
            Pair("Technology", b.tech)
        )
        for (i in rows.indices) {
            val r = UI.row(ctx, -1, rows[i].first, rows[i].second, false, null)
            r.value.setTextColor(UI.TEXT)
            c.addView(r.root)
            if (i < rows.size - 1) c.addView(UI.divider(ctx), UI.lp(UI.MATCH, 1, 0f, UI.dp(ctx, 16), 0, UI.dp(ctx, 16), 0))
        }
        host.addView(c, UI.lp(UI.MATCH, UI.WRAP, 0f, 0, UI.dp(ctx, 16), 0, 0))
    }
}

class UninstallScreen(ctx: Context, nav: Nav) : Screen(ctx, nav) {
    private class Entry(val pkg: String, val label: String, var size: Long)

    private val page = Page(ctx, nav, "Uninstall apps", scroll = false)
    override val view: View get() = page.root
    private val main = Handler(Looper.getMainLooper())
    private val list = Lists.cardList(ctx)
    private val empty = Lists.emptyText(ctx, "No user apps found.")
    private val entries = ArrayList<Entry>()
    private val adapter: ItemAdapter

    init {
        adapter = ItemAdapter(ctx, { entries.size }) { row, pos ->
            val e = entries[pos]
            bindAppIcon(ctx, row, e.pkg)
            row.title.text = e.label
            row.sub.text = e.pkg
            row.rtext.visibility = View.VISIBLE
            row.rtext.text = if (e.size > 0L) Sys.fmtBytes(e.size) else ""
        }
        list.adapter = adapter
        list.setOnItemClickListener { _, _, pos, _ ->
            if (pos in entries.indices) {
                nav.launch(Intent(Intent.ACTION_DELETE, Uri.parse("package:" + entries[pos].pkg)))
            }
        }
        val frame = FrameLayout(ctx)
        frame.addView(list, FrameLayout.LayoutParams(UI.MATCH, UI.MATCH))
        frame.addView(empty, FrameLayout.LayoutParams(UI.MATCH, UI.WRAP, Gravity.CENTER))
        page.content.addView(frame, UI.lp(UI.MATCH, 0, 1f, UI.dp(ctx, 20), UI.dp(ctx, 8), UI.dp(ctx, 20), UI.dp(ctx, 20)))
    }

    override fun onShow() {
        AppRepo.load(ctx, true) {
            val app = ctx.applicationContext
            val src = AppRepo.items.filter { it.hasLauncher && !it.isSystem }
            entries.clear()
            for (a in src) entries.add(Entry(a.pkg, a.label, 0L))
            adapter.notifyDataSetChanged()
            empty.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
            Thread {
                val usage = Sys.hasUsageAccess(app)
                for (e in entries.toList()) e.size = sizeOf(app, e.pkg, usage)
                val sorted = entries.toList().sortedByDescending { it.size }
                main.post {
                    entries.clear()
                    entries.addAll(sorted)
                    adapter.notifyDataSetChanged()
                }
            }.start()
        }
    }

    private fun sizeOf(app: Context, pkg: String, usage: Boolean): Long {
        val pm = app.packageManager
        try {
            val ai = pm.getApplicationInfo(pkg, 0)
            if (usage && Build.VERSION.SDK_INT >= 26) {
                try {
                    val ssm = app.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
                    val st = ssm.queryStatsForPackage(StorageManager.UUID_DEFAULT, pkg, UserHandle.getUserHandleForUid(ai.uid))
                    return st.appBytes + st.dataBytes + st.cacheBytes
                } catch (e: Exception) {
                    // fall back to the APK size
                }
            }
            return File(ai.sourceDir).length()
        } catch (e: Exception) {
            return 0L
        }
    }
}
