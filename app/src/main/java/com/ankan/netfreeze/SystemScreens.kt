package com.ankan.netfreeze

import android.app.AlertDialog
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.app.usage.StorageStatsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.os.storage.StorageManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File
import java.util.Calendar
import java.util.concurrent.atomic.AtomicBoolean

fun bindAppIcon(ctx: Context, row: ItemRow, pkg: String) {
    row.boundKey = pkg
    val c = IconLoader.get(pkg)
    if (c != null) {
        row.showBitmap(c)
    } else {
        row.showIcon(Ic.GRID)
        IconLoader.load(ctx, pkg, UI.dp(ctx, 46)) { bmp ->
            if (row.boundKey == pkg) row.showBitmap(bmp)
        }
    }
}

fun appLabel(ctx: Context, pkg: String): String {
    return try {
        val pm = ctx.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) {
        pkg
    }
}

fun openAppSettings(nav: Nav, pkg: String) {
    nav.launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")))
}

// ------------------------------------------------------------------------------------------ boost

class BoostScreen(ctx: Context, nav: Nav) : Screen(ctx, nav) {
    private class Cand(val pkg: String, val label: String, val lastUsed: Long) {
        var selected = true
    }

    private val page = Page(ctx, nav, "System boost")
    override val view: View get() = page.root
    private val main = Handler(Looper.getMainLooper())
    private val shield = ShieldView(ctx)
    private val bigTv = UI.text(ctx, "", 52f, UI.TEXT, UI.bold())
    private val unitTv = UI.text(ctx, "", 14f, UI.MUTED)
    private val captionTv = UI.text(ctx, "", 20f, UI.TEXT, UI.medium())
    private val listHost = LinearLayout(ctx)
    private val boostBtn = UI.pill(ctx, "Boost now", true)
    private val cands = ArrayList<Cand>()
    private var loading = false
    private var boosting = false
    private var done = false

    init {
        val sf = FrameLayout(ctx)
        sf.addView(shield, FrameLayout.LayoutParams(UI.MATCH, UI.MATCH))
        val cc = LinearLayout(ctx)
        cc.orientation = LinearLayout.VERTICAL
        cc.gravity = Gravity.CENTER
        cc.addView(bigTv)
        cc.addView(unitTv)
        sf.addView(cc, FrameLayout.LayoutParams(UI.MATCH, UI.MATCH))
        shield.mode = ShieldView.NONE
        page.add(sf, UI.lp(UI.dp(ctx, 190), UI.dp(ctx, 235), 0f, 0, UI.dp(ctx, 8), 0, 0).also { it.gravity = Gravity.CENTER_HORIZONTAL })
        captionTv.gravity = Gravity.CENTER
        page.add(captionTv, UI.lp(UI.MATCH, UI.WRAP, 0f, 0, UI.dp(ctx, 26), 0, 0))
        listHost.orientation = LinearLayout.VERTICAL
        page.add(listHost, UI.lp(UI.MATCH, UI.WRAP, 0f, 0, UI.dp(ctx, 34), 0, 0))
        boostBtn.setOnClickListener { boost() }
        page.setBottom(boostBtn)
    }

    override fun onShow() {
        if (!done) updateMem()
        if (!loading && !boosting && !done) load()
    }

    private fun updateMem() {
        val m = Sys.mem(ctx)
        val pct = (100 - (m.avail * 100 / m.total.coerceAtLeast(1L))).toInt()
        bigTv.text = pct.toString()
        unitTv.text = "% RAM in use"
        shield.progress = pct / 100f
        captionTv.text = Sys.fmtBytes(m.avail) + " free of " + Sys.fmtBytes(m.total)
    }

    private fun load() {
        loading = true
        val app = ctx.applicationContext
        Thread {
            val pm = app.packageManager
            val protectedPk = Sys.protectedPackages(app)
            val usage = Sys.hasUsageAccess(app)
            val recent = HashMap<String, Long>()
            if (usage) {
                try {
                    val usm = app.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
                    val now = System.currentTimeMillis()
                    val list = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 24L * 3600L * 1000L, now)
                    if (list != null) {
                        for (s in list) {
                            if (s.lastTimeUsed > 0L) {
                                val old = recent[s.packageName] ?: 0L
                                if (s.lastTimeUsed > old) recent[s.packageName] = s.lastTimeUsed
                            }
                        }
                    }
                } catch (e: Exception) {
                    // ignore
                }
            }
            val out = ArrayList<Cand>()
            for (ai in pm.getInstalledApplications(0)) {
                val pkg = ai.packageName
                if (pkg in protectedPk) continue
                if (pm.getLaunchIntentForPackage(pkg) == null) continue
                if (usage && !recent.containsKey(pkg)) continue
                val label = try {
                    ai.loadLabel(pm).toString()
                } catch (e: Exception) {
                    pkg
                }
                out.add(Cand(pkg, label, recent[pkg] ?: 0L))
            }
            if (usage) out.sortByDescending { it.lastUsed } else out.sortBy { it.label.lowercase() }
            main.post {
                loading = false
                cands.clear()
                cands.addAll(out)
                buildList(usage)
            }
        }.start()
    }

    private fun buildList(usage: Boolean) {
        listHost.removeAllViews()
        if (!usage) {
            val c = UI.card(ctx)
            c.setPadding(UI.dp(ctx, 18), UI.dp(ctx, 16), UI.dp(ctx, 18), UI.dp(ctx, 16))
            c.addView(UI.text(ctx, "Smarter list", 16f, UI.TEXT, UI.medium()))
            c.addView(
                UI.text(ctx, "Allow usage access to list only the apps you used recently. Without it, all installed apps are listed.", 13.5f, UI.MUTED),
                UI.lp(UI.MATCH, UI.WRAP, 0f, 0, UI.dp(ctx, 6), 0, UI.dp(ctx, 12))
            )
            val b = UI.smallPill(ctx, "Allow usage access")
            b.setOnClickListener { nav.openUsage() }
            c.addView(b, UI.lp(UI.WRAP, UI.WRAP))
            listHost.addView(c, UI.lp(UI.MATCH, UI.WRAP, 0f, 0, 0, 0, UI.dp(ctx, 18)))
        }
        val head = LinearLayout(ctx)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        head.addView(UI.text(ctx, "Running apps", 15f, UI.MUTED), UI.lp(0, UI.WRAP, 1f, UI.dp(ctx, 6), 0, 0, 0))
        val cnt = UI.text(ctx, "", 14f, UI.TEXT)
        head.addView(cnt, UI.lp(UI.WRAP, UI.WRAP, 0f, 0, 0, UI.dp(ctx, 10), 0))
        val all = CheckView(ctx)
        head.addView(all, UI.lp(UI.WRAP, UI.WRAP))
        listHost.addView(head, UI.lp(UI.MATCH, UI.WRAP, 0f, 0, 0, 0, UI.dp(ctx, 10)))

        val card = UI.card(ctx)
        val rows = ArrayList<ItemRow>()
        fun refreshHead() {
            val n = cands.count { it.selected }
            cnt.text = "$n selected"
            all.checked = n == cands.size && n > 0
            for (i in rows.indices) rows[i].check.checked = cands[i].selected
        }
        for (i in cands.indices) {
            val c = cands[i]
            val row = ItemRow(ctx)
            bindAppIcon(ctx, row, c.pkg)
            row.title.text = c.label
            row.sub.text = if (c.lastUsed > 0L) "Used " + Sys.ago(c.lastUsed) else "Installed app"
            row.check.visibility = View.VISIBLE
            row.isClickable = true
            row.setOnClickListener {
                c.selected = !c.selected
                refreshHead()
            }
            rows.add(row)
            card.addView(row)
            if (i < cands.size - 1) card.addView(UI.divider(ctx), UI.lp(UI.MATCH, 1, 0f, UI.dp(ctx, 16), 0, UI.dp(ctx, 16), 0))
        }
        all.isClickable = true
        all.setOnClickListener {
            val target = !(cands.isNotEmpty() && cands.all { it.selected })
            for (c in cands) c.selected = target
            refreshHead()
        }
        if (cands.isEmpty()) {
            card.addView(UI.text(ctx, "No recently used apps", 15f, UI.MUTED).also {
                it.gravity = Gravity.CENTER
                it.setPadding(0, UI.dp(ctx, 24), 0, UI.dp(ctx, 24))
            })
        }
        listHost.addView(card)
        refreshHead()
    }

    private fun boost() {
        if (boosting || done) {
            if (done) nav.pop()
            return
        }
        val sel = cands.filter { it.selected }.map { it.pkg }
        boosting = true
        boostBtn.text = "Boosting…"
        boostBtn.alpha = 0.6f
        shield.setSpinning(true)
        val app = ctx.applicationContext
        Thread {
            val before = Sys.mem(app).avail
            Sys.killBackground(app, sel)
            try {
                Thread.sleep(1400)
            } catch (e: InterruptedException) {
                // ignore
            }
            val after = Sys.mem(app).avail
            main.post {
                boosting = false
                done = true
                shield.setSpinning(false)
                shield.mode = ShieldView.CHECK
                shield.progress = -1f
                bigTv.text = ""
                unitTv.text = ""
                val freed = (after - before).coerceAtLeast(0L)
                captionTv.text = if (freed > 0L) Sys.fmtBytes(freed) + " freed" else "Memory is already optimized"
                listHost.removeAllViews()
                boostBtn.text = "Done"
                boostBtn.alpha = 1f
                Prefs.putLong(app, "last_optimize", System.currentTimeMillis())
                Prefs.addRecord(app, "System boost", "Stopped " + sel.size + " background apps")
            }
        }.start()
    }
}

// ---------------------------------------------------------------------------------------- security

class SecurityScreen(ctx: Context, nav: Nav) : Screen(ctx, nav) {
    private class Finding(val pkg: String, val label: String, val reasons: List<String>, val points: Int)

    private val page = Page(ctx, nav, "Viruses & risks")
    override val view: View get() = page.root
    private val main = Handler(Looper.getMainLooper())

    private val scanShield = ShieldView(ctx)
    private val pctTv = UI.text(ctx, "0", 52f, UI.TEXT, UI.bold())
    private val scanBox = LinearLayout(ctx)
    private val scanLabel = UI.text(ctx, "", 18f, UI.TEXT, UI.medium())
    private val doneBox = LinearLayout(ctx)
    private val doneTitle = UI.text(ctx, "", 34f, UI.TEXT, UI.bold())
    private val doneSub = UI.text(ctx, "", 15f, UI.MUTED)
    private val resultHost = LinearLayout(ctx)
    private val btn = UI.pill(ctx, "Scan Again", true)
    private var scanning = false
    private var cancel = AtomicBoolean(false)

    init {
        // scanning layout
        scanBox.orientation = LinearLayout.VERTICAL
        scanBox.gravity = Gravity.CENTER_HORIZONTAL
        val sf = FrameLayout(ctx)
        scanShield.mode = ShieldView.NONE
        sf.addView(scanShield, FrameLayout.LayoutParams(UI.MATCH, UI.MATCH))
        val cc = LinearLayout(ctx)
        cc.orientation = LinearLayout.VERTICAL
        cc.gravity = Gravity.CENTER
        cc.addView(pctTv)
        cc.addView(UI.text(ctx, "%", 14f, UI.MUTED))
        sf.addView(cc, FrameLayout.LayoutParams(UI.MATCH, UI.MATCH))
        scanBox.addView(sf, UI.lp(UI.dp(ctx, 190), UI.dp(ctx, 235), 0f, 0, UI.dp(ctx, 8), 0, 0))
        scanLabel.gravity = Gravity.CENTER
        scanLabel.isSingleLine = true
        scanLabel.ellipsize = android.text.TextUtils.TruncateAt.END
        scanBox.addView(scanLabel, UI.lp(UI.MATCH, UI.WRAP, 0f, 0, UI.dp(ctx, 26), 0, 0))
        page.add(scanBox)

        // done layout
        doneBox.orientation = LinearLayout.HORIZONTAL
        doneBox.gravity = Gravity.CENTER_VERTICAL
        val left = LinearLayout(ctx)
        left.orientation = LinearLayout.VERTICAL
        left.addView(doneTitle)
        left.addView(doneSub, UI.lp(UI.WRAP, UI.WRAP, 0f, 0, UI.dp(ctx, 8), 0, 0))
        doneBox.addView(left, UI.lp(0, UI.WRAP, 1f, 0, 0, UI.dp(ctx, 12), 0))
        val dshield = ShieldView(ctx)
        doneBox.addView(dshield, UI.lp(UI.dp(ctx, 84), UI.dp(ctx, 106)))
        page.add(doneBox, UI.lp(UI.MATCH, UI.WRAP, 0f, UI.dp(ctx, 6), UI.dp(ctx, 8), UI.dp(ctx, 6), 0))

        resultHost.orientation = LinearLayout.VERTICAL
        page.add(resultHost, UI.lp(UI.MATCH, UI.WRAP, 0f, 0, UI.dp(ctx, 8), 0, 0))
        btn.setOnClickListener { if (scanning) cancelScan() else startScan() }
        page.setBottom(btn)
        showDone(null)
    }

    override fun onShow() {
        if (!scanning && Prefs.getLong(ctx, "last_scan", 0L) == 0L) startScan()
    }

    private fun cancelScan() {
        cancel.set(true)
    }

    private fun startScan() {
        if (scanning) return
        scanning = true
        cancel = AtomicBoolean(false)
        val my = cancel
        scanBox.visibility = View.VISIBLE
        doneBox.visibility = View.GONE
        resultHost.removeAllViews()
        scanShield.progress = 0f
        pctTv.text = "0"
        scanLabel.text = "Starting…"
        btn.text = "Cancel"
        btn.background = UI.pill(ctx, "x", false).background
        btn.setTextColor(UI.TEXT)
        val app = ctx.applicationContext
        Thread {
            val pm = app.packageManager
            val apps = pm.getInstalledApplications(0).filter { (it.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0 && it.packageName != app.packageName }
            val found = ArrayList<Finding>()
            var n = 0
            for (ai in apps) {
                if (my.get()) break
                n++
                val label = appLabel(app, ai.packageName)
                val pct = n * 100 / apps.size.coerceAtLeast(1)
                main.post {
                    pctTv.text = pct.toString()
                    scanShield.progress = pct / 100f
                    scanLabel.text = "Scanning: $label"
                }
                val f = inspect(app, ai.packageName, label)
                if (f != null) found.add(f)
            }
            val cancelled = my.get()
            main.post {
                scanning = false
                btn.text = "Scan Again"
                btn.background = UI.pill(ctx, "x", true).background
                btn.setTextColor(Color.BLACK)
                if (cancelled) {
                    showDone(null)
                } else {
                    found.sortByDescending { it.points }
                    Prefs.putInt(app, "risk_count", found.size)
                    Prefs.putLong(app, "last_scan", System.currentTimeMillis())
                    Prefs.addRecord(app, "Manual scan", n.toString() + " apps checked. " + (if (found.isEmpty()) "No risks found." else found.size.toString() + " need review."))
                    showDone(found)
                }
            }
        }.start()
    }

    private fun inspect(app: Context, pkg: String, label: String): Finding? {
        val pm = app.packageManager
        val pi: PackageInfo = try {
            pm.getPackageInfo(pkg, PackageManager.GET_PERMISSIONS or PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS)
        } catch (e: Exception) {
            return null
        }
        val reasons = ArrayList<String>()
        var pts = 0
        val perms: Array<String> = pi.requestedPermissions ?: emptyArray<String>()
        val flags = pi.requestedPermissionsFlags
        fun requested(p: String): Boolean = perms.contains(p)
        fun granted(p: String): Boolean {
            val i = perms.indexOf(p)
            return i >= 0 && flags != null && i < flags.size && (flags[i] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
        }
        if (granted("android.permission.READ_SMS") || granted("android.permission.RECEIVE_SMS") || granted("android.permission.SEND_SMS")) {
            pts += 2
            reasons.add("Can read or send SMS")
        }
        if (granted("android.permission.READ_CALL_LOG")) {
            pts += 1
            reasons.add("Can read call log")
        }
        if (requested("android.permission.SYSTEM_ALERT_WINDOW")) {
            pts += 1
            reasons.add("Can draw over other apps")
        }
        if (requested("android.permission.REQUEST_INSTALL_PACKAGES")) {
            pts += 2
            reasons.add("Can install other apps")
        }
        val services: Array<android.content.pm.ServiceInfo> = pi.services ?: emptyArray<android.content.pm.ServiceInfo>()
        if (services.any { it.permission == "android.permission.BIND_ACCESSIBILITY_SERVICE" }) {
            pts += 3
            reasons.add("Uses accessibility service")
        }
        if (services.any { it.permission == "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE" }) {
            pts += 2
            reasons.add("Reads your notifications")
        }
        val receivers: Array<android.content.pm.ActivityInfo> = pi.receivers ?: emptyArray<android.content.pm.ActivityInfo>()
        if (receivers.any { it.permission == "android.permission.BIND_DEVICE_ADMIN" }) {
            pts += 3
            reasons.add("Device administrator")
        }
        val installer: String? = try {
            pm.getInstallerPackageName(pkg)
        } catch (e: Exception) {
            null
        }
        val stores = setOf(
            "com.android.vending", "com.sec.android.app.samsungapps", "com.amazon.venezia", "com.huawei.appmarket",
            "com.xiaomi.mipicks", "com.xiaomi.market", "com.heytap.market", "com.oppo.market", "com.vivo.appstore",
            "com.miui.packageinstaller.store"
        )
        if (installer == null || installer !in stores) {
            pts += 1
            reasons.add("Installed outside an app store")
        }
        var dangerous = 0
        for (p in perms) if (granted(p) && isDangerous(p)) dangerous++
        if (dangerous >= 8) {
            pts += 1
            reasons.add("Many sensitive permissions granted")
        }
        return if (pts >= 4) Finding(pkg, label, reasons, pts) else null
    }

    private fun isDangerous(p: String): Boolean {
        return p.endsWith("READ_CONTACTS") || p.endsWith("CAMERA") || p.endsWith("RECORD_AUDIO") ||
            p.endsWith("ACCESS_FINE_LOCATION") || p.endsWith("READ_SMS") || p.endsWith("READ_CALL_LOG") ||
            p.endsWith("READ_PHONE_STATE") || p.endsWith("READ_CALENDAR") || p.endsWith("BODY_SENSORS") ||
            p.endsWith("ACCESS_BACKGROUND_LOCATION") || p.endsWith("WRITE_CONTACTS") || p.endsWith("SEND_SMS")
    }

    private fun showDone(found: List<Finding>?) {
        scanBox.visibility = View.GONE
        doneBox.visibility = View.VISIBLE
        resultHost.removeAllViews()
        val lastScan = Prefs.getLong(ctx, "last_scan", 0L)
        if (found == null) {
            val risk = Prefs.getInt(ctx, "risk_count", 0)
            doneTitle.text = if (lastScan == 0L) "Not scanned" else if (risk == 0) "Secure" else "Review needed"
            doneSub.text = if (lastScan == 0L) "Tap Scan Again to check your apps." else "Last scan " + Sys.ago(lastScan)
        } else if (found.isEmpty()) {
            doneTitle.text = "Secure"
            doneSub.text = "No risky apps were found."
        } else {
            doneTitle.text = "Review needed"
            doneSub.text = found.size.toString() + " app(s) use powerful permissions."
        }

        if (found != null && found.isNotEmpty()) {
            resultHost.addView(UI.sectionLabel(ctx, "Apps to review"), UI.lp(UI.MATCH, UI.WRAP, 0f, 0, UI.dp(ctx, 22), 0, UI.dp(ctx, 10)))
            val c = UI.card(ctx)
            for (i in found.indices) {
                val f = found[i]
                val row = ItemRow(ctx)
                bindAppIcon(ctx, row, f.pkg)
                row.title.text = f.label
                row.sub.text = f.reasons.joinToString(" · ")
                row.sub.isSingleLine = false
                row.sub.maxLines = 3
                row.isClickable = true
                row.setOnClickListener { openAppSettings(nav, f.pkg) }
                c.addView(row)
                if (i < found.size - 1) c.addView(UI.divider(ctx), UI.lp(UI.MATCH, 1, 0f, UI.dp(ctx, 16), 0, UI.dp(ctx, 16), 0))
            }
            resultHost.addView(c)
        }

        val info = UI.text(
            ctx,
            "Blutheist Shield checks your apps for risky permissions and unknown install sources. It has no virus-signature database, so treat results as a safety review, not a guarantee.",
            12.5f, UI.MUTED
        )
        resultHost.addView(info, UI.lp(UI.MATCH, UI.WRAP, 0f, UI.dp(ctx, 6), UI.dp(ctx, 20), UI.dp(ctx, 6), 0))

        val recs = Prefs.records(ctx)
        if (recs.isNotEmpty()) {
            resultHost.addView(UI.sectionLabel(ctx, "Scan records"), UI.lp(UI.MATCH, UI.WRAP, 0f, 0, UI.dp(ctx, 24), 0, UI.dp(ctx, 10)))
            val c = UI.card(ctx)
            val fmt = java.text.SimpleDateFormat("MMM d, h:mm a", java.util.Locale.getDefault())
            for (i in recs.indices) {
                val parts = recs[i].split("|")
                if (parts.size < 3) continue
                val col = LinearLayout(ctx)
                col.orientation = LinearLayout.VERTICAL
                col.setPadding(UI.dp(ctx, 20), UI.dp(ctx, 14), UI.dp(ctx, 20), UI.dp(ctx, 14))
                col.addView(UI.text(ctx, parts[1], 17f, UI.TEXT, UI.medium()))
                col.addView(UI.text(ctx, parts[2], 14f, Color.parseColor("#B5B5BB")), UI.lp(UI.WRAP, UI.WRAP, 0f, 0, UI.dp(ctx, 4), 0, 0))
                val t = try {
                    fmt.format(java.util.Date(parts[0].toLong()))
                } catch (e: Exception) {
                    ""
                }
                col.addView(UI.text(ctx, t, 12.5f, UI.MUTED), UI.lp(UI.WRAP, UI.WRAP, 0f, 0, UI.dp(ctx, 4), 0, 0))
                c.addView(col)
                if (i < recs.size - 1) c.addView(UI.divider(ctx))
            }
            resultHost.addView(c)
        }
    }
}
