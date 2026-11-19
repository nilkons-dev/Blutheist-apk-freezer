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
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

private fun gateCard(ctx: Context, title: String, text: String, button: String, onClick: () -> Unit): View {
    val c = UI.card(ctx)
    c.setPadding(UI.dp(ctx, 20), UI.dp(ctx, 20), UI.dp(ctx, 20), UI.dp(ctx, 20))
    c.addView(UI.text(ctx, title, 18f, UI.TEXT, UI.medium()))
    c.addView(UI.text(ctx, text, 14f, UI.MUTED), UI.lp(UI.MATCH, UI.WRAP, 0f, 0, UI.dp(ctx, 8), 0, UI.dp(ctx, 16)))
    val b = UI.pill(ctx, button, true)
    b.setOnClickListener { onClick() }
    c.addView(b, UI.lp(UI.MATCH, UI.WRAP))
    return c
}

// ------------------------------------------------------------------------------ duplicate files

class DuplicatesScreen(ctx: Context, nav: Nav) : Screen(ctx, nav) {
    private class Dup(val e: FileEntry, val orig: String)

    private val page = Page(ctx, nav, "Duplicate files", scroll = false)
    override val view: View get() = page.root
    private val main = Handler(Looper.getMainLooper())
    private val infoTv = UI.text(ctx, "", 15f, UI.MUTED)
    private val bar = ThinProgress(ctx)
    private val list = Lists.cardList(ctx)
    private val frame = FrameLayout(ctx)
    private val gate = FrameLayout(ctx)
    private val delBtn = UI.pill(ctx, "Delete", true)
    private val dups = ArrayList<Dup>()
    private val adapter: ItemAdapter
    private var started = false

    init {
        adapter = ItemAdapter(ctx, { dups.size }) { row, pos ->
            val d = dups[pos]
            row.showIcon(Ic.COPY)
            row.title.text = d.e.file.name
            row.sub.text = "Same as " + d.orig
            row.rtext.visibility = View.VISIBLE
            row.rtext.text = Sys.fmtBytes(d.e.size)
            row.check.visibility = View.VISIBLE
            row.check.checked = d.e.selected
        }
        list.adapter = adapter
        list.setOnItemClickListener { _, _, pos, _ ->
            if (pos in dups.indices) {
                dups[pos].e.selected = !dups[pos].e.selected
                adapter.notifyDataSetChanged()
                updateButton()
            }
        }
        page.content.addView(infoTv, UI.lp(UI.MATCH, UI.WRAP, 0f, UI.dp(ctx, 26), UI.dp(ctx, 4), UI.dp(ctx, 26), UI.dp(ctx, 8)))
        page.content.addView(bar, UI.lp(UI.MATCH, UI.dp(ctx, 5), 0f, UI.dp(ctx, 26), 0, UI.dp(ctx, 26), UI.dp(ctx, 12)))
        frame.addView(list, FrameLayout.LayoutParams(UI.MATCH, UI.MATCH))
        frame.addView(gate, FrameLayout.LayoutParams(UI.MATCH, UI.WRAP, Gravity.TOP))
        page.content.addView(frame, UI.lp(UI.MATCH, 0, 1f, UI.dp(ctx, 20), 0, UI.dp(ctx, 20), UI.dp(ctx, 110)))
        delBtn.setOnClickListener { confirm() }
    }

    override fun onShow() {
        gate.removeAllViews()
        if (!Sys.hasStorageAccess(ctx)) {
            infoTv.text = "File access is needed to compare your files."
            bar.visibility = View.GONE
            gate.addView(gateCard(ctx, "File access needed", "Allow access so Blutheist can find identical files. Nothing leaves your phone.", "Allow access") { nav.openAllFiles() })
            return
        }
        if (!started) start()
    }

    private fun quickHash(f: File): String? {
        return try {
            val md = MessageDigest.getInstance("MD5")
            RandomAccessFile(f, "r").use { raf ->
                val buf = ByteArray(65536)
                val len = raf.length()
                val n = raf.read(buf)
                if (n > 0) md.update(buf, 0, n)
                if (len > 262144L) {
                    raf.seek(len / 2)
                    val m = raf.read(buf)
                    if (m > 0) md.update(buf, 0, m)
                }
                if (len > 131072L) {
                    raf.seek(len - 65536L)
                    val e = raf.read(buf)
                    if (e > 0) md.update(buf, 0, e)
                }
            }
            md.digest().joinToString("") { String.format("%02x", it) }
        } catch (e: Exception) {
            null
        }
    }

    private fun start() {
        started = true
        bar.visibility = View.VISIBLE
        infoTv.text = "Scanning storage…"
        Thread {
            var r = Cleaner.last
            if (r == null) {
                r = Cleaner.scan(Cleaner.root(), AtomicBoolean(false)) { n, _ ->
                    main.post { infoTv.text = "Scanning storage… $n files" }
                }
                Cleaner.last = r
            }
            val files = r.mid
            val bySize = files.groupBy { it.size }.filter { it.value.size >= 2 }
            val total = bySize.values.sumOf { it.size }.coerceAtLeast(1)
            var done = 0
            val out = ArrayList<Dup>()
            for ((_, group) in bySize) {
                val byHash = HashMap<String, ArrayList<FileEntry>>()
                for (e in group) {
                    val h = quickHash(e.file)
                    done++
                    if (h != null) byHash.getOrPut(h) { ArrayList() }.add(e)
                    if (done % 20 == 0) {
                        val p = done.toFloat() / total
                        main.post {
                            infoTv.text = "Comparing files… $done of $total"
                            bar.animateTo(p)
                        }
                    }
                }
                for ((_, same) in byHash) {
                    if (same.size < 2) continue
                    same.sortBy { it.modified }
                    val orig = same[0].file.name
                    for (k in 1 until same.size) {
                        same[k].selected = true
                        out.add(Dup(same[k], orig))
                    }
                }
            }
            out.sortByDescending { it.e.size }
            main.post {
                dups.clear()
                dups.addAll(out)
                adapter.notifyDataSetChanged()
                bar.visibility = View.GONE
                val wasted = out.sumOf { it.e.size }
                infoTv.text = if (out.isEmpty()) "No duplicate files found." else out.size.toString() + " duplicate files · " + Sys.fmtBytes(wasted) + " can be freed"
                updateButton()
            }
        }.start()
    }

    private fun updateButton() {
        val sel = dups.filter { it.e.selected }
        if (sel.isNotEmpty()) {
            delBtn.text = "Delete (" + sel.size + ") · " + Sys.fmtBytes(sel.sumOf { it.e.size })
            page.setBottom(delBtn)
        } else {
            page.setBottom(null)
        }
    }

    private fun confirm() {
        val sel = dups.filter { it.e.selected }
        if (sel.isEmpty()) return
        val go = {
            Thread {
                val freed = Cleaner.delete(sel.map { it.e })
                main.post {
                    dups.removeAll { !it.e.file.exists() }
                    Cleaner.last?.mid?.removeAll { !it.file.exists() }
                    adapter.notifyDataSetChanged()
                    updateButton()
                    infoTv.text = Sys.fmtBytes(freed) + " freed · " + dups.size + " duplicates left"
                    Prefs.addRecord(ctx.applicationContext, "Duplicate files", Sys.fmtBytes(freed) + " cleared")
                }
            }.start()
        }
        if (!Prefs.getBool(ctx, "confirm_delete", true)) {
            go()
            return
        }
        AlertDialog.Builder(ctx)
            .setTitle("Delete duplicates?")
            .setMessage(sel.size.toString() + " file(s), " + Sys.fmtBytes(sel.sumOf { it.e.size }) + ". The oldest copy of each file is kept.")
            .setPositiveButton("Delete") { _, _ -> go() }
            .setNegativeButton("Cancel", null)
            .show()
    }
}

// ----------------------------------------------------------------------------- storage analyzer

class StorageScreen(ctx: Context, nav: Nav, private val dir: File) : Screen(ctx, nav) {
    private class Node(val file: File, var size: Long)

    private val isRoot = dir.path == Cleaner.root().path
    private val page = Page(ctx, nav, if (isRoot) "Storage analyzer" else dir.name, scroll = false)
    override val view: View get() = page.root
    private val main = Handler(Looper.getMainLooper())
    private val totalTv = UI.text(ctx, "Calculating…", 15f, UI.MUTED)
    private val list = Lists.cardList(ctx)
    private val frame = FrameLayout(ctx)
    private val gate = FrameLayout(ctx)
    private val nodes = ArrayList<Node>()
    private val adapter: ItemAdapter
    private var total = 1L
    private var loaded = false

    init {
        adapter = ItemAdapter(ctx, { nodes.size }) { row, pos ->
            val n = nodes[pos]
            row.showIcon(if (n.file.isDirectory) Ic.FOLDER else Ic.FILE)
            row.title.text = n.file.name
            val pct = (n.size * 100 / total.coerceAtLeast(1L)).toInt()
            row.sub.text = pct.toString() + "% of this folder"
            row.rtext.visibility = View.VISIBLE
            row.rtext.text = Sys.fmtBytes(n.size)
            row.chev.visibility = if (n.file.isDirectory) View.VISIBLE else View.GONE
        }
        list.adapter = adapter
        list.setOnItemClickListener { _, _, pos, _ ->
            if (pos in nodes.indices && nodes[pos].file.isDirectory) nav.push(StorageScreen(ctx, nav, nodes[pos].file))
        }
        list.setOnItemLongClickListener { _, _, pos, _ ->
            if (pos in nodes.indices) askDelete(nodes[pos])
            true
        }
        page.content.addView(totalTv, UI.lp(UI.MATCH, UI.WRAP, 0f, UI.dp(ctx, 26), UI.dp(ctx, 4), UI.dp(ctx, 26), UI.dp(ctx, 4)))
        page.content.addView(
            UI.text(ctx, "Tap a folder to open it. Press and hold to delete.", 12.5f, UI.MUTED),
            UI.lp(UI.MATCH, UI.WRAP, 0f, UI.dp(ctx, 26), 0, UI.dp(ctx, 26), UI.dp(ctx, 12))
        )
        frame.addView(list, FrameLayout.LayoutParams(UI.MATCH, UI.MATCH))
        frame.addView(gate, FrameLayout.LayoutParams(UI.MATCH, UI.WRAP, Gravity.TOP))
        page.content.addView(frame, UI.lp(UI.MATCH, 0, 1f, UI.dp(ctx, 20), 0, UI.dp(ctx, 20), UI.dp(ctx, 20)))
    }

    override fun onShow() {
        gate.removeAllViews()
        if (!Sys.hasStorageAccess(ctx)) {
            totalTv.text = "File access is needed."
            gate.addView(gateCard(ctx, "File access needed", "Allow access so Blutheist can measure your folders.", "Allow access") { nav.openAllFiles() })
            return
        }
        load()
    }

    private fun load() {
        Thread {
            val kids: Array<File> = try { dir.listFiles() ?: emptyArray<File>() } catch (e: Exception) { emptyArray<File>() }
            val out = ArrayList<Node>()
            var sum = 0L
            for (k in kids) {
                val s = Cleaner.dirSize(k)
                sum += s
                out.add(Node(k, s))
            }
            out.sortByDescending { it.size }
            main.post {
                nodes.clear()
                nodes.addAll(out)
                total = sum
                adapter.notifyDataSetChanged()
                totalTv.text = Sys.fmtBytes(sum) + " in " + out.size + " items"
                loaded = true
            }
        }.start()
    }

    private fun askDelete(n: Node) {
        AlertDialog.Builder(ctx)
            .setTitle("Delete " + n.file.name + "?")
            .setMessage(Sys.fmtBytes(n.size) + " will be permanently deleted.")
            .setPositiveButton("Delete") { _, _ ->
                Thread {
                    val ok = try { n.file.deleteRecursively() } catch (e: Exception) { false }
                    main.post {
                        nav.toast(if (ok) "Deleted" else "Could not delete")
                        if (ok) load()
                    }
                }.start()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}

// ------------------------------------------------------------------------------- quick settings

class QuickSettingsScreen(ctx: Context, nav: Nav) : Screen(ctx, nav) {
    private val page = Page(ctx, nav, "Quick settings")
    override val view: View get() = page.root

    init {
        page.add(
            UI.text(ctx, "One tap to the settings you use most.", 14.5f, UI.MUTED),
            UI.lp(UI.MATCH, UI.WRAP, 0f, UI.dp(ctx, 6), 0, UI.dp(ctx, 6), UI.dp(ctx, 14))
        )
        group("Connections", listOf(
            Triple(Ic.WIFI, "Wi-Fi", Settings.ACTION_WIFI_SETTINGS),
            Triple(Ic.BLUETOOTH, "Bluetooth", Settings.ACTION_BLUETOOTH_SETTINGS),
            Triple(Ic.DROP, "Mobile network", Settings.ACTION_DATA_ROAMING_SETTINGS),
            Triple(Ic.SEND, "Airplane mode", Settings.ACTION_AIRPLANE_MODE_SETTINGS),
            Triple(Ic.PIN, "Location", Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        ))
        group("Device", listOf(
            Triple(Ic.SUN, "Display", Settings.ACTION_DISPLAY_SETTINGS),
            Triple(Ic.VOLUME, "Sound", Settings.ACTION_SOUND_SETTINGS),
            Triple(Ic.BATTERY, "Battery saver", Settings.ACTION_BATTERY_SAVER_SETTINGS),
            Triple(Ic.PIE, "Storage", Settings.ACTION_INTERNAL_STORAGE_SETTINGS),
            Triple(Ic.CLOCK, "Date & time", Settings.ACTION_DATE_SETTINGS)
        ))
        group("System", listOf(
            Triple(Ic.GRID, "Apps", Settings.ACTION_APPLICATION_SETTINGS),
            Triple(Ic.LOCK, "Security", Settings.ACTION_SECURITY_SETTINGS),
            Triple(Ic.USER, "Accessibility", Settings.ACTION_ACCESSIBILITY_SETTINGS),
            Triple(Ic.CODE, "Developer options", Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
        ))
    }

    private fun group(title: String, items: List<Triple<Int, String, String>>) {
        page.add(UI.sectionLabel(ctx, title), UI.lp(UI.MATCH, UI.WRAP, 0f, 0, UI.dp(ctx, 18), 0, UI.dp(ctx, 10)))
        val c = UI.card(ctx)
        for (i in items.indices) {
            val t = items[i]
            val row = UI.row(ctx, t.first, t.second, "", true) { nav.launch(Intent(t.third)) }
            c.addView(row.root)
            if (i < items.size - 1) c.addView(UI.divider(ctx), UI.lp(UI.MATCH, 1, 0f, UI.dp(ctx, 16), 0, UI.dp(ctx, 16), 0))
        }
        page.add(c)
    }
}

// ------------------------------------------------------------------------------------ unused apps

class UnusedAppsScreen(ctx: Context, nav: Nav) : Screen(ctx, nav) {
    private class Entry(val pkg: String, val label: String, val last: Long)

    private val page = Page(ctx, nav, "Unused apps", scroll = false)
    override val view: View get() = page.root
    private val main = Handler(Looper.getMainLooper())
    private val infoTv = UI.text(ctx, "", 15f, UI.MUTED)
    private val list = Lists.cardList(ctx)
    private val frame = FrameLayout(ctx)
    private val gate = FrameLayout(ctx)
    private val entries = ArrayList<Entry>()
    private val adapter: ItemAdapter

    init {
        adapter = ItemAdapter(ctx, { entries.size }) { row, pos ->
            val e = entries[pos]
            bindAppIcon(ctx, row, e.pkg)
            row.title.text = e.label
            row.sub.text = if (e.last <= 0L) "Not opened in the last 30 days" else "Last opened " + Sys.ago(e.last)
            row.showChevron()
        }
        list.adapter = adapter
        list.setOnItemClickListener { _, _, pos, _ ->
            if (pos in entries.indices) nav.launch(Intent(Intent.ACTION_DELETE, Uri.parse("package:" + entries[pos].pkg)))
        }
        page.content.addView(infoTv, UI.lp(UI.MATCH, UI.WRAP, 0f, UI.dp(ctx, 26), UI.dp(ctx, 4), UI.dp(ctx, 26), UI.dp(ctx, 12)))
        frame.addView(list, FrameLayout.LayoutParams(UI.MATCH, UI.MATCH))
        frame.addView(gate, FrameLayout.LayoutParams(UI.MATCH, UI.WRAP, Gravity.TOP))
        page.content.addView(frame, UI.lp(UI.MATCH, 0, 1f, UI.dp(ctx, 20), 0, UI.dp(ctx, 20), UI.dp(ctx, 20)))
    }

    override fun onShow() {
        gate.removeAllViews()
        if (!Sys.hasUsageAccess(ctx)) {
            infoTv.text = "Usage access is needed to see when apps were last opened."
            gate.addView(gateCard(ctx, "Usage access needed", "Allow usage access so Blutheist can tell which apps you have not opened for a month.", "Allow usage access") { nav.openUsage() })
            return
        }
        infoTv.text = "Checking app usage…"
        AppRepo.load(ctx, true) { load() }
    }

    private fun load() {
        val app = ctx.applicationContext
        Thread {
            val now = System.currentTimeMillis()
            val usm = app.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val map = try { usm.queryAndAggregateUsageStats(now - 30L * 24L * 3600L * 1000L, now) } catch (e: Exception) { emptyMap<String, android.app.usage.UsageStats>() }
            val out = ArrayList<Entry>()
            for (a in AppRepo.items) {
                if (!a.hasLauncher || a.isSystem) continue
                val last = map[a.pkg]?.lastTimeUsed ?: 0L
                if (last <= 0L || now - last > 30L * 24L * 3600L * 1000L) out.add(Entry(a.pkg, a.label, last))
            }
            out.sortBy { it.label.lowercase() }
            main.post {
                entries.clear()
                entries.addAll(out)
                adapter.notifyDataSetChanged()
                infoTv.text = if (out.isEmpty()) "Every app was opened in the last 30 days." else out.size.toString() + " apps not opened in 30 days. Tap one to uninstall."
            }
        }.start()
    }
}
