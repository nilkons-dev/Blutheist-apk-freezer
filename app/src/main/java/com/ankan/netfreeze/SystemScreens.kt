package com.ankan.netfreeze

import android.app.AlertDialog
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File
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
    private val ring = RingView(ctx)
    private val captionTv = UI.text(ctx, "", 18f, UI.TEXT, UI.medium())
    private val stepsCard = UI.card(ctx)
    private val steps = ArrayList<StepRow>()
    private val infoHost = LinearLayout(ctx)
    private val listHost = LinearLayout(ctx)
    private val suggestHost = LinearLayout(ctx)
    private val boostBtn = UI.pill(ctx, "Boost now", true)
    private val cands = ArrayList<Cand>()
    private var loading = false
    private var boosting = false
    private var done = false

    init {
        val rs = minOf(UI.dp(ctx, 200), ctx.resources.displayMetrics.widthPixels - UI.dp(ctx, 170))
        page.add(ring, UI.lp(rs, rs, 0f, 0, UI.dp(ctx, 6), 0, 0).also { it.gravity = Gravity.CENTER_HORIZONTAL })
        captionTv.gravity = Gravity.CENTER
        page.add(captionTv, UI.lp(UI.MATCH, UI.WRAP, 0f, UI.dp(ctx, 10), UI.dp(ctx, 26), UI.dp(ctx, 10), 0))

        val defs = arrayOf(
            Triple(Ic.BOLT, "Memory", "Stop apps running in the background"),
            Triple(Ic.SPARKLE, "App cache", "Clear the cache of every app"),
            Triple(Ic.HOURGLASS, "Unused files", "Find files you may want to delete"),
            Triple(Ic.GAUGE, "Fresh start", "Release memory like after a restart")
        )
        for (i in defs.indices) {
            val s = StepRow(ctx, defs[i].first, defs[i].second, defs[i].third)
            steps.add(s)
            stepsCard.addView(s)
            if (i < defs.size - 1) stepsCard.addView(UI.divider(ctx), UI.lp(UI.MATCH, 1, 0f, UI.dp(ctx, 76), 0, UI.dp(ctx, 16), 0))
        }
        stepsCard.visibility = View.GONE
        page.add(stepsCard, UI.lp(UI.MATCH, UI.WRAP, 0f, 0, UI.dp(ctx, 28), 0, 0))
        infoHost.orientation = LinearLayout.VERTICAL
        listHost.orientation = LinearLayout.VERTICAL
        suggestHost.orientation = LinearLayout.VERTICAL
        page.add(infoHost, UI.lp(UI.MATCH, UI.WRAP, 0f, 0, UI.dp(ctx, 28), 0, 0))
        page.add(suggestHost, UI.lp(UI.MATCH, UI.WRAP, 0f, 0, UI.dp(ctx, 4), 0, 0))
        page.add(listHost, UI.lp(UI.MATCH, UI.WRAP, 0f, 0, UI.dp(ctx, 4), 0, 0))
        boostBtn.setOnClickListener {
            UI.tap(it)
            boost()
        }
        page.setBottom(boostBtn)
    }

    override fun onShow() {
        if (!done && !boosting) updateMem()
        updateInfo()
        buildSuggestions()
        if (!loading && !boosting && !done) load()
    }

    private fun updateMem() {
        val m = Sys.mem(ctx)
        val pct = (100 - (m.avail * 100 / m.total.coerceAtLeast(1L))).toInt()
        ring.valueText = pct.toString()
        ring.unitText = "% RAM used"
        ring.animateTo(pct / 100f, 900)
        UI.swapText(captionTv, Sys.fmtBytes(m.avail) + " free of " + Sys.fmtBytes(m.total))
    }

    private fun infoRow(icon: Int, title: String, value: String): View = UI.row(ctx, icon, title, value, false, null).also {
        it.value.setTextColor(UI.TEXT)
    }.root

    private fun updateInfo() {
        infoHost.removeAllViews()
        infoHost.addView(UI.sectionLabel(ctx, "Phone status"), UI.lp(UI.MATCH, UI.WRAP, 0f, 0, 0, 0, UI.dp(ctx, 10)))
        val m = Sys.mem(ctx)
        val st = Sys.storage()
        val c = UI.card(ctx)
        val last = Prefs.getLong(ctx, "last_optimize", 0L)
        val rows = listOf(
            infoRow(Ic.BOLT, "Memory in use", Sys.fmtBytes(m.total - m.avail) + " of " + Sys.fmtBytes(m.total)),
            infoRow(Ic.PIE, "Storage in use", Sys.fmtBytes(st.total - st.free) + " of " + Sys.fmtBytes(st.total)),
            infoRow(Ic.SPARKLE, "Cache of all apps", "About " + Sys.fmtBytes(Cleaner.estimateSystemCache(ctx))),
            infoRow(Ic.GRID, "Apps that can be stopped", cands.size.toString()),
            infoRow(Ic.CLOCK, "Last boost", if (last == 0L) "Never" else Sys.ago(last))
        )
        for (i in rows.indices) {
            c.addView(rows[i])
            if (i < rows.size - 1) c.addView(UI.divider(ctx), UI.lp(UI.MATCH, 1, 0f, UI.dp(ctx, 16), 0, UI.dp(ctx, 16), 0))
        }
        infoHost.addView(c)
    }

    private fun buildSuggestions() {
        suggestHost.removeAllViews()
        suggestHost.addView(UI.sectionLabel(ctx, "Suggested to delete"), UI.lp(UI.MATCH, UI.WRAP, 0f, 0, UI.dp(ctx, 8), 0, UI.dp(ctx, 10)))
        val c = UI.card(ctx)
        val r = Cleaner.last
        if (r == null) {
            c.addView(descRow(ctx, Ic.SEARCH, "Find unused files", "Scan your storage for old, large and junk files.") { nav.push(CleanupScreen(ctx, nav, false)) })
        } else {
            val items = listOf(
                Triple(Ic.HOURGLASS, "Old large files", Pair(7, r.old.sumOf { it.size })),
                Triple(Ic.BOX, "Obsolete APK files", Pair(1, r.apkBytes)),
                Triple(Ic.FILE, "Large files", Pair(0, r.large.sumOf { it.size }))
            )
            for (i in items.indices) {
                val t = items[i]
                val kind = t.third.first
                c.addView(descRow(ctx, t.first, t.second, Sys.fmtBytes(t.third.second) + " · tap to review and delete") { nav.push(FileListScreen(ctx, nav, kind)) })
                c.addView(UI.divider(ctx), UI.lp(UI.MATCH, 1, 0f, UI.dp(ctx, 76), 0, UI.dp(ctx, 16), 0))
            }
            c.addView(descRow(ctx, Ic.TRASH, "Junk and cache files", Sys.fmtBytes(r.junkBytes) + " · clean them in Storage cleanup") { nav.push(CleanupScreen(ctx, nav, false)) })
        }
        c.addView(UI.divider(ctx), UI.lp(UI.MATCH, 1, 0f, UI.dp(ctx, 76), 0, UI.dp(ctx, 16), 0))
        c.addView(descRow(ctx, Ic.HOURGLASS, "Unused apps", "Apps you have not opened for 30 days") { Tools.open(ctx, nav, Tools.UNUSED) })
        suggestHost.addView(c)
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
                updateInfo()
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
            listHost.addView(c, UI.lp(UI.MATCH, UI.WRAP, 0f, 0, UI.dp(ctx, 24), 0, UI.dp(ctx, 4)))
        }
        val head = LinearLayout(ctx)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        head.addView(UI.text(ctx, "Running apps", 15f, UI.MUTED), UI.lp(0, UI.WRAP, 1f, UI.dp(ctx, 6), 0, 0, 0))
        val cnt = UI.text(ctx, "", 14f, UI.TEXT)
        head.addView(cnt, UI.lp(UI.WRAP, UI.WRAP, 0f, 0, 0, UI.dp(ctx, 10), 0))
        val all = CheckView(ctx)
        head.addView(all, UI.lp(UI.WRAP, UI.WRAP))
        listHost.addView(head, UI.lp(UI.MATCH, UI.WRAP, 0f, 0, UI.dp(ctx, 24), 0, UI.dp(ctx, 10)))

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
        if (done) {
            nav.pop()
            return
        }
        if (boosting) return
        boosting = true
        boostBtn.text = "Boosting…"
        boostBtn.alpha = 0.6f
        stepsCard.visibility = View.VISIBLE
        for (s in steps) s.status.setState(StatusView.IDLE)
        listHost.visibility = View.GONE
        ring.setSpinning(true)
        val keep = cands.filter { !it.selected }.map { it.pkg }.toSet()
        val app = ctx.applicationContext
        Thread {
            val ramBefore = Sys.mem(app).avail
            val storBefore = Sys.storage().free
            var stopped = 0
            var cacheFreed = 0L

            main.post { steps[0].state(StatusView.RUNNING, "Stopping background apps…"); ring.animateTo(0.1f, 400) }
            stopped = Sys.killBackground(app, null, keep)
            try { Thread.sleep(900) } catch (e: InterruptedException) { }
            main.post { steps[0].state(StatusView.DONE, "$stopped apps asked to stop"); ring.animateTo(0.35f, 500) }

            main.post { steps[1].state(StatusView.RUNNING, "Clearing cache of every app…") }
            cacheFreed += Cleaner.clearSystemCaches(app)
            cacheFreed += Cleaner.clearOwnCache(app)
            if (Sys.hasStorageAccess(app)) cacheFreed += Cleaner.clearLegacyAppCaches(Cleaner.root())
            val cf = cacheFreed
            main.post {
                steps[1].state(StatusView.DONE, if (cf > 0L) Sys.fmtBytes(cf) + " of cache cleared" else "Cache was already clear")
                ring.animateTo(0.6f, 500)
            }

            main.post { steps[2].state(StatusView.RUNNING, "Looking for unused files…") }
            val r = Cleaner.last
            val sugBytes = if (r == null) 0L else r.old.sumOf { it.size } + r.apkBytes
            try { Thread.sleep(500) } catch (e: InterruptedException) { }
            main.post {
                steps[2].state(
                    StatusView.DONE,
                    if (r == null) "Run Storage cleanup to find them" else Sys.fmtBytes(sugBytes) + " you can review below"
                )
                ring.animateTo(0.8f, 400)
            }

            main.post { steps[3].state(StatusView.RUNNING, "Releasing memory…") }
            System.gc()
            try { Thread.sleep(1100) } catch (e: InterruptedException) { }
            val ramAfter = Sys.mem(app).avail
            val ramFreed = (ramAfter - ramBefore).coerceAtLeast(0L)
            val storFreed = (Sys.storage().free - storBefore).coerceAtLeast(0L)
            main.post {
                steps[3].state(StatusView.DONE, if (ramFreed > 0L) Sys.fmtBytes(ramFreed) + " memory released" else "Memory is already fresh")
                boosting = false
                done = true
                ring.setSpinning(false)
                ring.valueText = ""
                ring.unitText = ""
                ring.check = true
                ring.animateTo(1f, 600)
                UI.swapText(captionTv, "Boost complete")
                boostBtn.text = "Done"
                boostBtn.alpha = 1f
                Prefs.putLong(app, "last_optimize", System.currentTimeMillis())
                Prefs.addRecord(app, "System boost", "Freed " + Sys.fmtBytes(ramFreed) + " memory, " + Sys.fmtBytes(storFreed) + " storage")
                updateInfo()
                buildSuggestions()
            }
        }.start()
    }
}

// ---------------------------------------------------------------------------------------- security

class SecurityScreen(ctx: Context, nav: Nav, private val isTab: Boolean) : Screen(ctx, nav) {
    private val page = Page(ctx, nav, "Viruses & risks", scroll = true, back = !isTab)
    override val view: View get() = page.root
    private val main = Handler(Looper.getMainLooper())

    private val scanBox = LinearLayout(ctx)
    private val scanShield = ShieldView(ctx)
    private val scanLabel = UI.text(ctx, "", 17f, UI.TEXT, UI.medium())
    private val scanTime = UI.text(ctx, "", 14f, UI.MUTED)
    private val stageSteps = ArrayList<StepRow>()
    private val doneHost = LinearLayout(ctx)
    private val btn = UI.pill(ctx, "Scan", true)
    private var scanning = false
    private var cancel = AtomicBoolean(false)
    private var startedAt = 0L
    private var curStage = -1
    private var report: ScanReport? = Scanner.last

    private val ticker = object : Runnable {
        override fun run() {
            if (scanning) {
                val s = (System.currentTimeMillis() - startedAt) / 1000L
                scanTime.text = String.format(java.util.Locale.US, "Elapsed %d:%02d", s / 60, s % 60)
                main.postDelayed(this, 1000L)
            }
        }
    }

    init {
        scanBox.orientation = LinearLayout.VERTICAL
        scanBox.gravity = Gravity.CENTER_HORIZONTAL
        scanShield.mode = ShieldView.NONE
        scanShield.progress = 0f
        scanBox.addView(scanShield, UI.lp(UI.dp(ctx, 180), UI.dp(ctx, 224), 0f, 0, UI.dp(ctx, 4), 0, 0))
        scanLabel.gravity = Gravity.CENTER
        scanLabel.isSingleLine = true
        scanLabel.ellipsize = android.text.TextUtils.TruncateAt.END
        scanBox.addView(scanLabel, UI.lp(UI.MATCH, UI.WRAP, 0f, UI.dp(ctx, 10), UI.dp(ctx, 22), UI.dp(ctx, 10), 0))
        scanTime.gravity = Gravity.CENTER
        scanBox.addView(scanTime, UI.lp(UI.MATCH, UI.WRAP, 0f, 0, UI.dp(ctx, 6), 0, 0))
        val defs = arrayOf(
            Triple(Ic.PHONE, "Device security", "Screen lock, root, encryption, debugging"),
            Triple(Ic.GRID, "Installed apps", "Permissions, signatures, hidden and imitation apps"),
            Triple(Ic.DROP, "App behaviour", "Background data use and unusual activity"),
            Triple(Ic.FOLDER, "Files", "Every readable file: disguised, executable and test malware")
        )
        val card = UI.card(ctx)
        for (i in defs.indices) {
            val s = StepRow(ctx, defs[i].first, defs[i].second, defs[i].third)
            stageSteps.add(s)
            card.addView(s)
            if (i < defs.size - 1) card.addView(UI.divider(ctx), UI.lp(UI.MATCH, 1, 0f, UI.dp(ctx, 76), 0, UI.dp(ctx, 16), 0))
        }
        scanBox.addView(card, UI.lp(UI.MATCH, UI.WRAP, 0f, 0, UI.dp(ctx, 26), 0, 0))
        scanBox.visibility = View.GONE
        page.add(scanBox)
        doneHost.orientation = LinearLayout.VERTICAL
        page.add(doneHost)
        btn.setOnClickListener {
            UI.tap(it)
            if (scanning) {
                cancel.set(true)
                btn.text = "Stopping…"
            } else {
                startScan()
            }
        }
        page.setBottom(btn)
        buildDone()
    }

    override fun onShow() {
        if (!scanning) {
            report = Scanner.last
            buildDone()
        }
    }

    private fun full(): Boolean = Prefs.getInt(ctx, "scan_depth", 1) == 1

    private fun startScan() {
        if (scanning) return
        scanning = true
        cancel = AtomicBoolean(false)
        startedAt = System.currentTimeMillis()
        curStage = -1
        scanBox.visibility = View.VISIBLE
        doneHost.visibility = View.GONE
        scanShield.progress = 0f
        scanShield.centerText = "0"
        scanShield.centerUnit = "%"
        scanLabel.text = "Starting…"
        for (s in stageSteps) {
            s.status.setState(StatusView.IDLE)
        }
        stageSteps[0].subTv.text = "Screen lock, root, encryption, debugging"
        stageSteps[1].subTv.text = "Permissions, signatures, hidden and imitation apps"
        stageSteps[2].subTv.text = "Background data use and unusual activity"
        stageSteps[3].subTv.text = if (full() && Sys.hasStorageAccess(ctx)) "Every readable file: disguised, executable and test malware" else "Skipped (quick scan or no file access)"
        btn.text = "Cancel"
        btn.background = UI.pillBg(ctx, false)
        btn.setTextColor(UI.TEXT)
        main.post(ticker)
        val app = ctx.applicationContext
        val my = cancel
        val isFull = full()
        Thread {
            val rep = Scanner.run(app, isFull, my) { stage, stageLabel, detail, f ->
                main.post {
                    scanShield.progress = f
                    scanShield.centerText = (f * 100f).toInt().toString()
                    scanLabel.text = "$stageLabel · $detail"
                    if (stage != curStage) {
                        for (k in 0 until stage) stageSteps[k].status.setState(StatusView.DONE)
                        if (stage in stageSteps.indices) stageSteps[stage].status.setState(StatusView.RUNNING)
                        curStage = stage
                    }
                    if (stage in stageSteps.indices) stageSteps[stage].subTv.text = detail
                }
            }
            main.post {
                scanning = false
                Scanner.last = rep
                report = rep
                if (!rep.cancelled) {
                    Prefs.putInt(app, "risk_count", rep.risky())
                    Prefs.putLong(app, "last_scan", System.currentTimeMillis())
                    Prefs.addRecord(
                        app, if (isFull) "Full scan" else "Quick scan",
                        rep.apps.toString() + " apps, " + rep.files + " files checked. " +
                            (if (rep.risky() == 0) "No risks found." else rep.risky().toString() + " need review.")
                    )
                }
                btn.text = "Scan Again"
                btn.background = UI.pillBg(ctx, true)
                btn.setTextColor(UI.ON_ACCENT)
                scanBox.visibility = View.GONE
                doneHost.visibility = View.VISIBLE
                buildDone()
                UI.stagger(page.root)
            }
        }.start()
    }

    private fun ignored(): Set<String> = Prefs.getSet(ctx, "ignored_threats")

    private fun buildDone() {
        doneHost.removeAllViews()
        val rep = report
        val ign = ignored()
        val threats = rep?.threats?.filter { (it.pkg ?: it.path ?: "") !in ign } ?: emptyList()
        val risky = threats.count { it.severity >= 2 }
        val failed = rep?.failedChecks() ?: 0

        // header
        val head = LinearLayout(ctx)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        val left = LinearLayout(ctx)
        left.orientation = LinearLayout.VERTICAL
        val title: String
        val sub: String
        if (rep == null) {
            title = "Not scanned yet"
            sub = "Run a scan to check your phone, apps and files."
        } else if (rep.cancelled) {
            title = "Scan stopped"
            sub = "Run it again to finish checking."
        } else if (risky == 0 && failed == 0) {
            title = "Secure"
            sub = "Blutheist Shield found no risks."
        } else {
            title = if (risky > 0) "Review needed" else "Mostly secure"
            sub = risky.toString() + " risky item(s), " + failed + " setting(s) to improve."
        }
        left.addView(UI.text(ctx, title, 32f, UI.TEXT, UI.bold()))
        left.addView(UI.text(ctx, sub, 15f, UI.MUTED), UI.lp(UI.WRAP, UI.WRAP, 0f, 0, UI.dp(ctx, 8), 0, 0))
        head.addView(left, UI.lp(0, UI.WRAP, 1f, 0, 0, UI.dp(ctx, 12), 0))
        head.addView(ShieldView(ctx), UI.lp(UI.dp(ctx, 84), UI.dp(ctx, 106)))
        doneHost.addView(head, UI.lp(UI.MATCH, UI.WRAP, 0f, UI.dp(ctx, 6), UI.dp(ctx, 8), UI.dp(ctx, 6), UI.dp(ctx, 6)))

        if (rep == null) {
            doneHost.addView(UI.sectionLabel(ctx, "What the scan checks"), UI.lp(UI.MATCH, UI.WRAP, 0f, 0, UI.dp(ctx, 24), 0, UI.dp(ctx, 10)))
            val c = UI.card(ctx)
            val items = listOf(
                Triple(Ic.PHONE, "Device security", "Screen lock, root, encryption, USB debugging, patch level, admin and accessibility apps."),
                Triple(Ic.GRID, "Installed apps", "Every app: permissions, hidden apps, fake lookalikes, test certificates, spy-app names."),
                Triple(Ic.DROP, "App behaviour", "Apps that use lots of data in the background without being opened."),
                Triple(Ic.FOLDER, "Files", "Every readable file: disguised programs, executables, mismatched content and test malware.")
            )
            for (i in items.indices) {
                val t = items[i]
                val r = ItemRow(ctx)
                r.showIcon(t.first)
                r.title.text = t.second
                r.sub.text = t.third
                r.sub.isSingleLine = false
                r.sub.maxLines = 3
                c.addView(r)
                if (i < items.size - 1) c.addView(UI.divider(ctx), UI.lp(UI.MATCH, 1, 0f, UI.dp(ctx, 76), 0, UI.dp(ctx, 16), 0))
            }
            doneHost.addView(c)
            doneHost.addView(
                UI.text(ctx, "A full scan reads every file, so it can take several minutes on a big phone. You can leave this screen open and keep using other apps.", 13f, UI.MUTED),
                UI.lp(UI.MATCH, UI.WRAP, 0f, UI.dp(ctx, 6), UI.dp(ctx, 14), UI.dp(ctx, 6), 0)
            )
            btn.text = "Start scan"
            addRecords()
            addHonesty()
            return
        }
        btn.text = "Scan Again"

        // stats
        doneHost.addView(UI.sectionLabel(ctx, "Scan summary"), UI.lp(UI.MATCH, UI.WRAP, 0f, 0, UI.dp(ctx, 18), 0, UI.dp(ctx, 10)))
        val stats = UI.card(ctx)
        val rows = listOf(
            Triple(Ic.GRID, "Apps analysed", rep.apps.toString()),
            Triple(Ic.FILE, "Files scanned", if (rep.filesSkipped) "Skipped" else String.format(java.util.Locale.US, "%,d", rep.files)),
            Triple(Ic.PIE, "Data scanned", if (rep.filesSkipped) "—" else Sys.fmtBytes(rep.bytes)),
            Triple(Ic.CLOCK, "Time taken", Sys.fmtDuration(rep.durationMs).let { if (rep.durationMs < 60000L) (rep.durationMs / 1000L).toString() + " s" else it }),
            Triple(Ic.SHIELD, "Last scan", Sys.ago(Prefs.getLong(ctx, "last_scan", 0L)))
        )
        for (i in rows.indices) {
            stats.addView(UI.row(ctx, rows[i].first, rows[i].second, rows[i].third, false, null).also { it.value.setTextColor(UI.TEXT) }.root)
            if (i < rows.size - 1) stats.addView(UI.divider(ctx), UI.lp(UI.MATCH, 1, 0f, UI.dp(ctx, 16), 0, UI.dp(ctx, 16), 0))
        }
        doneHost.addView(stats)

        if (rep.filesSkipped || rep.behaviorSkipped) {
            val n = UI.card(ctx)
            n.setPadding(UI.dp(ctx, 20), UI.dp(ctx, 18), UI.dp(ctx, 20), UI.dp(ctx, 18))
            n.addView(UI.text(ctx, "Scan was not complete", 17f, UI.TEXT, UI.medium()))
            val parts = ArrayList<String>()
            if (rep.filesSkipped) parts.add("File scan needs All files access (or the scan depth is set to Quick).")
            if (rep.behaviorSkipped) parts.add("Behaviour analysis needs Usage access.")
            n.addView(UI.text(ctx, parts.joinToString(" "), 13.5f, UI.MUTED), UI.lp(UI.MATCH, UI.WRAP, 0f, 0, UI.dp(ctx, 6), 0, UI.dp(ctx, 12)))
            val row = LinearLayout(ctx)
            row.orientation = LinearLayout.HORIZONTAL
            if (rep.filesSkipped) {
                val b = UI.smallPill(ctx, "Allow file access")
                b.setOnClickListener { nav.openAllFiles() }
                row.addView(b, UI.lp(UI.WRAP, UI.WRAP, 0f, 0, 0, UI.dp(ctx, 8), 0))
            }
            if (rep.behaviorSkipped) {
                val b = UI.smallPill(ctx, "Allow usage access")
                b.setOnClickListener { nav.openUsage() }
                row.addView(b, UI.lp(UI.WRAP, UI.WRAP))
            }
            n.addView(row)
            doneHost.addView(n, UI.lp(UI.MATCH, UI.WRAP, 0f, 0, UI.dp(ctx, 14), 0, 0))
        }

        // threats
        doneHost.addView(
            UI.sectionLabel(ctx, if (threats.isEmpty()) "Threats & risks" else "Threats & risks (" + threats.size + ")"),
            UI.lp(UI.MATCH, UI.WRAP, 0f, 0, UI.dp(ctx, 24), 0, UI.dp(ctx, 10))
        )
        val tc = UI.card(ctx)
        if (threats.isEmpty()) {
            val r = ItemRow(ctx)
            r.showIcon(Ic.CHECK)
            r.title.text = "No threats found"
            r.sub.text = "Nothing suspicious in the apps and files that were checked."
            r.sub.isSingleLine = false
            tc.addView(r)
        } else {
            val shown = threats.take(60)
            for (i in shown.indices) {
                val t = shown[i]
                val row = ItemRow(ctx)
                if (t.pkg != null) bindAppIcon(ctx, row, t.pkg) else row.showIcon(if (t.severity >= 3) Ic.WARN else Ic.FILE)
                row.title.text = t.title
                row.sub.text = t.detail
                row.sub.isSingleLine = false
                row.sub.maxLines = 4
                row.rtext.visibility = View.VISIBLE
                row.rtext.text = when (t.severity) {
                    3 -> "HIGH"
                    2 -> "MEDIUM"
                    else -> "LOW"
                }
                row.rtext.setTextColor(if (t.severity >= 3) UI.TEXT else UI.MUTED)
                row.rtext.typeface = UI.bold()
                row.isClickable = true
                row.setOnClickListener { threatActions(t) }
                tc.addView(row)
                if (i < shown.size - 1) tc.addView(UI.divider(ctx), UI.lp(UI.MATCH, 1, 0f, UI.dp(ctx, 16), 0, UI.dp(ctx, 16), 0))
            }
        }
        doneHost.addView(tc)

        // device checks
        doneHost.addView(UI.sectionLabel(ctx, "Device security"), UI.lp(UI.MATCH, UI.WRAP, 0f, 0, UI.dp(ctx, 24), 0, UI.dp(ctx, 10)))
        val cc = UI.card(ctx)
        for (i in rep.checks.indices) {
            val ck = rep.checks[i]
            val row = ItemRow(ctx)
            row.showIcon(if (ck.ok) Ic.CHECK else Ic.WARN)
            row.title.text = ck.title
            row.sub.text = ck.detail
            row.sub.isSingleLine = false
            row.sub.maxLines = 3
            cc.addView(row)
            if (i < rep.checks.size - 1) cc.addView(UI.divider(ctx), UI.lp(UI.MATCH, 1, 0f, UI.dp(ctx, 16), 0, UI.dp(ctx, 16), 0))
        }
        doneHost.addView(cc)
        addRecords()
        addHonesty()
    }

    private fun addRecords() {
        val recs = Prefs.records(ctx)
        if (recs.isEmpty()) return
        doneHost.addView(UI.sectionLabel(ctx, "Scan records"), UI.lp(UI.MATCH, UI.WRAP, 0f, 0, UI.dp(ctx, 24), 0, UI.dp(ctx, 10)))
        val c = UI.card(ctx)
        val fmt = java.text.SimpleDateFormat("MMM d, h:mm a", java.util.Locale.getDefault())
        for (i in recs.indices) {
            val parts = recs[i].split("|")
            if (parts.size < 3) continue
            val col = LinearLayout(ctx)
            col.orientation = LinearLayout.VERTICAL
            col.setPadding(UI.dp(ctx, 20), UI.dp(ctx, 14), UI.dp(ctx, 20), UI.dp(ctx, 14))
            col.addView(UI.text(ctx, parts[1], 17f, UI.TEXT, UI.medium()))
            col.addView(UI.text(ctx, parts[2], 14f, android.graphics.Color.parseColor("#B5B5BB")), UI.lp(UI.WRAP, UI.WRAP, 0f, 0, UI.dp(ctx, 4), 0, 0))
            val t = try {
                fmt.format(java.util.Date(parts[0].toLong()))
            } catch (e: Exception) {
                ""
            }
            col.addView(UI.text(ctx, t, 12.5f, UI.MUTED), UI.lp(UI.WRAP, UI.WRAP, 0f, 0, UI.dp(ctx, 4), 0, 0))
            c.addView(col)
            if (i < recs.size - 1) c.addView(UI.divider(ctx))
        }
        doneHost.addView(c)
    }

    private fun addHonesty() {
        doneHost.addView(
            UI.text(
                ctx,
                "Android does not let any app read the private data of other apps. Blutheist Shield therefore checks each app's manifest, permissions, signature, install source and behaviour, and reads every file it is allowed to see. It has no cloud virus database, so use it as a strong safety review.",
                12.5f, UI.MUTED
            ),
            UI.lp(UI.MATCH, UI.WRAP, 0f, UI.dp(ctx, 6), UI.dp(ctx, 20), UI.dp(ctx, 6), 0)
        )
    }

    private fun threatActions(t: Threat) {
        val key = t.pkg ?: t.path ?: return
        val labels = ArrayList<String>()
        if (t.pkg != null) {
            labels.add("Open app settings")
            labels.add("Uninstall")
        } else {
            labels.add("Delete file")
        }
        labels.add("Ignore this item")
        AlertDialog.Builder(ctx)
            .setTitle(t.title)
            .setMessage(t.detail)
            .setItems(labels.toTypedArray()) { _, which ->
                val pick = labels[which]
                when (pick) {
                    "Open app settings" -> openAppSettings(nav, t.pkg ?: "")
                    "Uninstall" -> nav.launch(Intent(Intent.ACTION_DELETE, Uri.parse("package:" + t.pkg)))
                    "Delete file" -> {
                        val ok = try { File(t.path ?: "").delete() } catch (e: Exception) { false }
                        nav.toast(if (ok) "File deleted" else "Could not delete the file")
                        if (ok) {
                            report?.threats?.remove(t)
                            buildDone()
                        }
                    }
                    else -> {
                        Prefs.putSet(ctx, "ignored_threats", ignored() + key)
                        nav.toast("Ignored")
                        buildDone()
                    }
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }
}
