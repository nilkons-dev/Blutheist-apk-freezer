package com.ankan.netfreeze

import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.util.concurrent.atomic.AtomicBoolean

class CleanupScreen(ctx: Context, nav: Nav, private val isTab: Boolean) : Screen(ctx, nav) {
    private val page = Page(ctx, nav, "Clean up", scroll = true, back = !isTab)
    override val view: View get() = page.root
    private val main = Handler(Looper.getMainLooper())

    private val ring = RingView(ctx)
    private val titleTv = UI.text(ctx, "", 26f, UI.TEXT, UI.bold())
    private val subTv = UI.text(ctx, "", 15f, UI.MUTED)
    private val progressBar = ThinProgress(ctx)
    private val cleanBtn = UI.pill(ctx, "Clean", true)
    private val permCard = UI.card(ctx)
    private val storageVal = UI.text(ctx, "", 13.5f, UI.MUTED)
    private val bar = SegBar(ctx)
    private val legend = LinearLayout(ctx)
    private val breakdownHost = LinearLayout(ctx)
    private val listHost = LinearLayout(ctx)
    private var scanning = false
    private var cleaning = false

    init {
        listHost.orientation = LinearLayout.VERTICAL
        breakdownHost.orientation = LinearLayout.VERTICAL
        legend.orientation = LinearLayout.VERTICAL

        val rs = minOf(UI.dp(ctx, 200), ctx.resources.displayMetrics.widthPixels - UI.dp(ctx, 170))
        page.add(ring, UI.lp(rs, rs, 0f, 0, UI.dp(ctx, 6), 0, 0).also { it.gravity = Gravity.CENTER_HORIZONTAL })
        titleTv.gravity = Gravity.CENTER
        page.add(titleTv, UI.lp(UI.MATCH, UI.WRAP, 0f, 0, UI.dp(ctx, 24), 0, 0))
        subTv.gravity = Gravity.CENTER
        page.add(subTv, UI.lp(UI.MATCH, UI.WRAP, 0f, UI.dp(ctx, 10), UI.dp(ctx, 8), UI.dp(ctx, 10), 0))
        progressBar.visibility = View.GONE
        page.add(progressBar, UI.lp(UI.MATCH, UI.dp(ctx, 6), 0f, UI.dp(ctx, 30), UI.dp(ctx, 16), UI.dp(ctx, 30), 0))

        cleanBtn.setOnClickListener {
            UI.tap(it)
            clean()
        }
        val bw = minOf(UI.dp(ctx, 300), ctx.resources.displayMetrics.widthPixels - UI.dp(ctx, 80))
        page.add(cleanBtn, UI.lp(bw, UI.WRAP, 0f, 0, UI.dp(ctx, 22), 0, 0).also { it.gravity = Gravity.CENTER_HORIZONTAL })

        permCard.setPadding(UI.dp(ctx, 20), UI.dp(ctx, 20), UI.dp(ctx, 20), UI.dp(ctx, 20))
        permCard.addView(UI.text(ctx, "File access needed", 18f, UI.TEXT, UI.medium()))
        permCard.addView(
            UI.text(ctx, "To find junk, 0 KB files, big files and old APKs everywhere on your phone, Blutheist needs permission to see your files. Nothing leaves your device.", 14f, UI.MUTED),
            UI.lp(UI.MATCH, UI.WRAP, 0f, 0, UI.dp(ctx, 8), 0, UI.dp(ctx, 16))
        )
        val allow = UI.pill(ctx, "Allow access", false)
        allow.setOnClickListener { nav.openAllFiles() }
        permCard.addView(allow, UI.lp(UI.MATCH, UI.WRAP))
        permCard.visibility = View.GONE
        page.add(permCard, UI.lp(UI.MATCH, UI.WRAP, 0f, 0, UI.dp(ctx, 22), 0, 0))

        page.add(breakdownHost, UI.lp(UI.MATCH, UI.WRAP, 0f, 0, UI.dp(ctx, 4), 0, 0))

        // storage card
        val sc = UI.card(ctx)
        sc.setPadding(UI.dp(ctx, 20), UI.dp(ctx, 18), UI.dp(ctx, 20), UI.dp(ctx, 18))
        val sr = LinearLayout(ctx)
        sr.orientation = LinearLayout.HORIZONTAL
        sr.gravity = Gravity.CENTER_VERTICAL
        sr.addView(UI.text(ctx, "Storage space", 18f, UI.TEXT, UI.medium()), UI.lp(0, UI.WRAP, 1f))
        sr.addView(storageVal, UI.lp(UI.WRAP, UI.WRAP))
        sc.addView(sr)
        sc.addView(bar, UI.lp(UI.MATCH, UI.dp(ctx, 12), 0f, 0, UI.dp(ctx, 16), 0, 0))
        sc.addView(legend, UI.lp(UI.MATCH, UI.WRAP, 0f, 0, UI.dp(ctx, 14), 0, 0))
        page.add(sc, UI.lp(UI.MATCH, UI.WRAP, 0f, 0, UI.dp(ctx, 22), 0, 0))
        page.add(listHost)
        updateStorage(null)
    }

    override fun onShow() {
        updateStorage(Cleaner.last)
        if (!Sys.hasStorageAccess(ctx)) {
            showState(0)
            return
        }
        val r = Cleaner.last
        if (r != null && !scanning && !cleaning) {
            render(r)
        } else if (r == null && !scanning && !cleaning) {
            startScan()
        }
    }

    /** 0 = needs permission, 1 = scanning, 2 = junk found, 3 = cleaning, 4 = clean */
    private fun showState(s: Int) {
        permCard.visibility = if (s == 0) View.VISIBLE else View.GONE
        cleanBtn.visibility = if (s == 2 || s == 0) View.VISIBLE else View.GONE
        progressBar.visibility = if (s == 1 || s == 3) View.VISIBLE else View.GONE
        ring.setSpinning(s == 1 || s == 3)
        when (s) {
            0 -> {
                UI.swapText(titleTv, "Clear app caches")
                UI.swapText(subTv, "Allow file access to also find junk and 0 KB files everywhere")
                cleanBtn.text = "Clear app caches"
                ring.check = false
                ring.iconKind = Ic.SPARKLE
            }
            1 -> {
                UI.swapText(titleTv, "Scanning storage…")
                ring.check = false
                ring.iconKind = -1
                ring.valueText = ""
                ring.unitText = ""
            }
            2 -> {
                cleanBtn.text = "Clean"
                ring.check = false
                ring.iconKind = -1
            }
            3 -> {
                UI.swapText(titleTv, "Cleaning…")
            }
            else -> {
                ring.iconKind = -1
                ring.check = true
                ring.animateTo(1f, 600)
            }
        }
    }

    private fun startScan() {
        scanning = true
        showState(1)
        subTv.text = "Looking for junk, cache and 0 KB files"
        ring.progress = 0f
        val st = Sys.storage()
        val used = (st.total - st.free).coerceAtLeast(1L)
        Thread {
            val r = Cleaner.scan(Cleaner.root(), AtomicBoolean(false)) { n, bytes ->
                main.post {
                    subTv.text = String.format(java.util.Locale.US, "%,d files · %s checked", n, Sys.fmtBytes(bytes))
                    progressBar.value = (bytes.toFloat() / used).coerceAtMost(0.98f)
                }
            }
            main.post {
                Cleaner.last = r
                scanning = false
                render(r)
            }
        }.start()
    }

    private fun render(r: ScanResult) {
        if (r.junkBytes > 0L || r.emptyDirs.isNotEmpty()) {
            showState(2)
            UI.swapText(titleTv, "Junk files detected")
            subTv.text = r.junk.size.toString() + " files · " + r.emptyDirs.size + " empty folders"
            val s = Sys.fmtBytes(r.junkBytes)
            ring.valueText = s.substringBefore(' ')
            ring.unitText = s.substringAfter(' ', "")
            ring.animateTo(1f, 800)
        } else {
            showState(4)
            UI.swapText(titleTv, "Your phone is clean")
            subTv.text = "No junk, cache or 0 KB files found"
        }
        updateStorage(r)
        buildBreakdown(r)
        buildLists(r)
    }

    private fun clean() {
        if (cleaning) return
        val app = ctx.applicationContext
        val r = Cleaner.last
        cleaning = true
        cleanBtn.visibility = View.GONE
        showState(3)
        ring.progress = 0f
        progressBar.value = 0f
        Thread {
            var freed = 0L
            // 1. caches of every app (done by Android itself)
            main.post { subTv.text = "Clearing app caches…" }
            freed += Cleaner.clearSystemCaches(app)
            freed += Cleaner.clearOwnCache(app)
            if (Sys.hasStorageAccess(app)) freed += Cleaner.clearLegacyAppCaches(Cleaner.root())
            val f1 = freed
            main.post {
                subTv.text = Sys.fmtBytes(f1) + " freed so far"
                progressBar.animateTo(0.2f)
            }
            // 2. junk files, a few at a time; 3. empty folders
            if (r != null) {
                val base = freed
                freed += Cleaner.deleteJunk(r) { done, total, f ->
                    val p = 0.2f + 0.7f * done / total
                    main.post {
                        subTv.text = Sys.fmtBytes(base + f) + " freed · " + done + " of " + total + " files"
                        progressBar.animateTo(p, 300)
                        ring.progress = p
                    }
                }
            }
            val total = freed
            main.post {
                cleaning = false
                showState(4)
                UI.swapText(titleTv, "Cleanup complete")
                subTv.text = Sys.fmtBytes(total) + " cleared"
                progressBar.visibility = View.GONE
                Prefs.addRecord(app, "Storage cleanup", Sys.fmtBytes(total) + " cleared")
                if (r != null) {
                    updateStorage(r)
                    buildBreakdown(r)
                    buildLists(r)
                } else {
                    updateStorage(null)
                }
            }
        }.start()
    }

    private fun updateStorage(r: ScanResult?) {
        val st = Sys.storage()
        val used = st.total - st.free
        storageVal.text = Sys.fmtBytes(used) + " of " + Sys.fmtBytes(st.total) + " used"
        val total = st.total.toFloat().coerceAtLeast(1f)
        legend.removeAllViews()
        if (r == null) {
            bar.setParts(listOf(Pair(used / total, UI.ACCENT)))
            legend.visibility = View.GONE
            return
        }
        legend.visibility = View.VISIBLE
        val rest = r.audioBytes + r.docBytes + r.archiveBytes + r.apkBytes
        val media = r.imageBytes + r.videoBytes + rest
        val other = (used - media).coerceAtLeast(0L)
        val c2 = Color.parseColor("#A0A0A6")
        val c3 = Color.parseColor("#6C6C72")
        val c4 = Color.parseColor("#45454A")
        bar.setParts(listOf(Pair(other / total, UI.ACCENT), Pair(r.imageBytes / total, c2), Pair(r.videoBytes / total, c3), Pair(rest / total, c4)))
        val items = arrayOf(
            Triple(UI.ACCENT, "Apps & system", other), Triple(c2, "Photos", r.imageBytes),
            Triple(c3, "Videos", r.videoBytes), Triple(c4, "Audio, documents, archives, APKs", rest),
            Triple(UI.CARD2, "Free", st.free)
        )
        for (it in items) {
            val row = LinearLayout(ctx)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            val dot = View(ctx)
            dot.background = UI.rounded(ctx, it.first, 5f, UI.STROKE, 1f)
            row.addView(dot, UI.lp(UI.dp(ctx, 10), UI.dp(ctx, 10)))
            row.addView(UI.text(ctx, it.second, 14f, Color.parseColor("#C9C9CF")), UI.lp(0, UI.WRAP, 1f, UI.dp(ctx, 10), 0, 0, 0))
            row.addView(UI.text(ctx, Sys.fmtBytes(it.third), 14f, UI.MUTED), UI.lp(UI.WRAP, UI.WRAP))
            legend.addView(row, UI.lp(UI.MATCH, UI.WRAP, 0f, 0, UI.dp(ctx, 8), 0, 0))
        }
    }

    private fun buildBreakdown(r: ScanResult) {
        breakdownHost.removeAllViews()
        breakdownHost.addView(UI.sectionLabel(ctx, "What was found"), UI.lp(UI.MATCH, UI.WRAP, 0f, 0, 0, 0, UI.dp(ctx, 10)))
        val c = UI.card(ctx)
        val icons = intArrayOf(Ic.FILE, Ic.BOX, Ic.IMAGE, Ic.TRASH)
        for (i in 0 until 4) {
            val row = ItemRow(ctx)
            row.showIcon(icons[i])
            row.title.text = Cleaner.CAT_NAMES[i]
            row.sub.text = r.catCount[i].toString() + " files"
            row.rtext.visibility = View.VISIBLE
            row.rtext.text = Sys.fmtBytes(r.catBytes[i])
            c.addView(row)
            c.addView(UI.divider(ctx), UI.lp(UI.MATCH, 1, 0f, UI.dp(ctx, 16), 0, UI.dp(ctx, 16), 0))
        }
        val e = ItemRow(ctx)
        e.showIcon(Ic.FOLDER)
        e.title.text = "Empty folders"
        e.sub.text = r.emptyDirs.size.toString() + " folders"
        e.rtext.visibility = View.VISIBLE
        e.rtext.text = "0 B"
        c.addView(e)
        breakdownHost.addView(c, UI.lp(UI.MATCH, UI.WRAP, 0f, 0, 0, 0, UI.dp(ctx, 4)))
        val last = Prefs.records(ctx).firstOrNull { it.contains("|Storage cleanup|") }
        if (last != null) {
            val p = last.split("|")
            val t = UI.text(ctx, "Last cleanup: " + p[2] + " · " + Sys.ago(p[0].toLongOrNull() ?: 0L), 13f, UI.MUTED)
            t.gravity = Gravity.CENTER
            breakdownHost.addView(t, UI.lp(UI.MATCH, UI.WRAP, 0f, UI.dp(ctx, 8), UI.dp(ctx, 10), UI.dp(ctx, 8), UI.dp(ctx, 4)))
        }
    }

    private fun buildLists(r: ScanResult) {
        listHost.removeAllViews()
        listHost.addView(UI.sectionLabel(ctx, "App cleanup"), UI.lp(UI.MATCH, UI.WRAP, 0f, 0, UI.dp(ctx, 24), 0, UI.dp(ctx, 10)))
        val a = UI.card(ctx)
        a.addView(descRow(ctx, Ic.CHAT, "WhatsApp cleaner", Sys.fmtBytes(r.whatsappBytes) + " of media on this phone") { nav.push(AppMediaScreen(ctx, nav, 0)) })
        a.addView(UI.divider(ctx), UI.lp(UI.MATCH, 1, 0f, UI.dp(ctx, 76), 0, UI.dp(ctx, 16), 0))
        a.addView(descRow(ctx, Ic.SEND, "Telegram cleaner", Sys.fmtBytes(r.telegramBytes) + " of media on this phone") { nav.push(AppMediaScreen(ctx, nav, 1)) })
        listHost.addView(a)

        listHost.addView(UI.sectionLabel(ctx, "Recommended cleanup"), UI.lp(UI.MATCH, UI.WRAP, 0f, 0, UI.dp(ctx, 24), 0, UI.dp(ctx, 10)))
        val b = UI.card(ctx)
        val rows = arrayOf(
            Triple(Ic.HOURGLASS, "Old large files", Pair(7, r.old.sumOf { it.size })),
            Triple(Ic.FILE, "Large files", Pair(0, r.large.sumOf { it.size })),
            Triple(Ic.VIDEO, "Videos", Pair(2, r.videoBytes)),
            Triple(Ic.IMAGE, "Photos", Pair(3, r.imageBytes)),
            Triple(Ic.BOX, "APK files", Pair(1, r.apkBytes)),
            Triple(Ic.MUSIC, "Audio", Pair(4, r.audioBytes)),
            Triple(Ic.FILE, "Documents", Pair(5, r.docBytes)),
            Triple(Ic.ARCHIVE, "Archives", Pair(6, r.archiveBytes))
        )
        for (i in rows.indices) {
            val t = rows[i]
            val row = ItemRow(ctx)
            row.showIcon(t.first)
            row.title.text = t.second
            row.sub.text = "Tap to review and delete"
            row.rtext.visibility = View.VISIBLE
            row.rtext.text = Sys.fmtBytes(t.third.second)
            row.showChevron()
            row.isClickable = true
            val kind = t.third.first
            row.setOnClickListener {
                UI.tap(it)
                nav.push(FileListScreen(ctx, nav, kind))
            }
            UI.press(row, 0.985f)
            b.addView(row)
            if (i < rows.size - 1) b.addView(UI.divider(ctx), UI.lp(UI.MATCH, 1, 0f, UI.dp(ctx, 16), 0, UI.dp(ctx, 16), 0))
        }
        listHost.addView(b)

        listHost.addView(UI.sectionLabel(ctx, "How cleanup works"), UI.lp(UI.MATCH, UI.WRAP, 0f, 0, UI.dp(ctx, 24), 0, UI.dp(ctx, 10)))
        val info = UI.card(ctx)
        info.setPadding(UI.dp(ctx, 20), UI.dp(ctx, 18), UI.dp(ctx, 20), UI.dp(ctx, 18))
        info.addView(UI.text(ctx, "Blutheist reads every folder it is allowed to see and finds cache files, temp and log files, thumbnails, 0 KB files and empty folders. Cache of other apps is cleared by Android itself. Photos, videos and documents are never deleted unless you pick them.", 14f, UI.MUTED))
        listHost.addView(info)
    }
}

/** Files of one category from the last scan, with check boxes and delete. */
class FileListScreen(ctx: Context, nav: Nav, private val kind: Int) : Screen(ctx, nav) {
    private val titles = arrayOf("Large files", "APK files", "Videos", "Photos", "Audio", "Documents", "Archives", "Old large files")
    private val icons = intArrayOf(Ic.FILE, Ic.BOX, Ic.VIDEO, Ic.IMAGE, Ic.MUSIC, Ic.FILE, Ic.ARCHIVE, Ic.HOURGLASS)
    private val page = Page(ctx, nav, titles[kind], scroll = false)
    override val view: View get() = page.root
    private val main = Handler(Looper.getMainLooper())
    private val items = ArrayList<FileEntry>()
    private val list = Lists.cardList(ctx)
    private val empty = Lists.emptyText(ctx, "Nothing here. Run a scan from Clean up first.")
    private val delBtn = UI.pill(ctx, "Delete", true)
    private val adapter: ItemAdapter

    init {
        adapter = ItemAdapter(ctx, { items.size }) { row, pos -> bind(row, pos) }
        list.adapter = adapter
        list.setOnItemClickListener { _, _, pos, _ ->
            if (pos in items.indices) {
                items[pos].selected = !items[pos].selected
                adapter.notifyDataSetChanged()
                updateButton()
            }
        }
        val frame = FrameLayout(ctx)
        frame.addView(list, FrameLayout.LayoutParams(UI.MATCH, UI.MATCH))
        frame.addView(empty, FrameLayout.LayoutParams(UI.MATCH, UI.WRAP, Gravity.CENTER))
        page.content.addView(frame, UI.lp(UI.MATCH, 0, 1f, UI.dp(ctx, 20), UI.dp(ctx, 8), UI.dp(ctx, 20), UI.dp(ctx, 110)))
        delBtn.setOnClickListener { confirm() }
    }

    private fun bind(row: ItemRow, pos: Int) {
        val e = items[pos]
        row.showIcon(icons[kind])
        row.title.text = e.file.name
        row.sub.text = e.file.parent ?: ""
        row.rtext.visibility = View.VISIBLE
        row.rtext.text = Sys.fmtBytes(e.size)
        row.check.visibility = View.VISIBLE
        row.check.checked = e.selected
    }

    override fun onShow() {
        val r = Cleaner.last
        items.clear()
        if (r != null) {
            val src = when (kind) {
                0 -> r.large
                1 -> r.apks
                2 -> r.videos
                3 -> r.images
                4 -> r.audio
                5 -> r.docs
                6 -> r.archives
                else -> r.old
            }
            for (e in src) items.add(e)
        }
        adapter.notifyDataSetChanged()
        empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        updateButton()
    }

    private fun updateButton() {
        var n = 0
        var bytes = 0L
        for (e in items) if (e.selected) {
            n++
            bytes += e.size
        }
        if (n > 0) {
            delBtn.text = "Delete ($n) · " + Sys.fmtBytes(bytes)
            page.setBottom(delBtn)
        } else {
            page.setBottom(null)
        }
    }

    private fun confirm() {
        val sel = items.filter { it.selected }
        if (sel.isEmpty()) return
        if (!Prefs.getBool(ctx, "confirm_delete", true)) {
            doDelete(sel)
            return
        }
        Sheet.confirm(ctx, "Delete files?", sel.size.toString() + " file(s), " + Sys.fmtBytes(sel.sumOf { it.size }) + ", will be permanently deleted.", "Delete") { doDelete(sel) }
    }

    private fun doDelete(sel: List<FileEntry>) {
        Thread {
            val freed = Cleaner.delete(sel)
            main.post {
                val r = Cleaner.last
                if (r != null) {
                    for (l in listOf(r.large, r.old, r.apks, r.videos, r.images, r.audio, r.docs, r.archives)) {
                        l.removeAll { !it.file.exists() }
                    }
                    when (kind) {
                        1 -> r.apkBytes = (r.apkBytes - freed).coerceAtLeast(0L)
                        2 -> r.videoBytes = (r.videoBytes - freed).coerceAtLeast(0L)
                        3 -> r.imageBytes = (r.imageBytes - freed).coerceAtLeast(0L)
                        4 -> r.audioBytes = (r.audioBytes - freed).coerceAtLeast(0L)
                        5 -> r.docBytes = (r.docBytes - freed).coerceAtLeast(0L)
                        6 -> r.archiveBytes = (r.archiveBytes - freed).coerceAtLeast(0L)
                        else -> { }
                    }
                }
                items.removeAll { !it.file.exists() }
                adapter.notifyDataSetChanged()
                empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
                updateButton()
                nav.toast(Sys.fmtBytes(freed) + " freed")
                Prefs.addRecord(ctx.applicationContext, titles[kind], Sys.fmtBytes(freed) + " cleared")
            }
        }.start()
    }
}

/** WhatsApp / Telegram media folders. 0 = WhatsApp, 1 = Telegram. */
class AppMediaScreen(ctx: Context, nav: Nav, private val app: Int) : Screen(ctx, nav) {
    private class Dir(val dir: java.io.File, var size: Long) {
        var selected = false
    }

    private val page = Page(ctx, nav, if (app == 0) "WhatsApp cleaner" else "Telegram cleaner", scroll = false)
    override val view: View get() = page.root
    private val main = Handler(Looper.getMainLooper())
    private val dirs = ArrayList<Dir>()
    private val list = Lists.cardList(ctx)
    private val empty = Lists.emptyText(ctx, "No media folders found.")
    private val btn = UI.pill(ctx, "Clean selected", true)
    private val adapter: ItemAdapter

    init {
        adapter = ItemAdapter(ctx, { dirs.size }) { row, pos ->
            val d = dirs[pos]
            row.showIcon(if (app == 0) Ic.CHAT else Ic.SEND)
            row.title.text = d.dir.name.removePrefix(".")
            row.sub.text = "Tap to select"
            row.rtext.visibility = View.VISIBLE
            row.rtext.text = Sys.fmtBytes(d.size)
            row.check.visibility = View.VISIBLE
            row.check.checked = d.selected
        }
        list.adapter = adapter
        list.setOnItemClickListener { _, _, pos, _ ->
            if (pos in dirs.indices) {
                dirs[pos].selected = !dirs[pos].selected
                adapter.notifyDataSetChanged()
                updateButton()
            }
        }
        val note = UI.text(ctx, "Deletes media from this phone only. Your chats and cloud backups are not touched.", 13.5f, UI.MUTED)
        page.content.addView(note, UI.lp(UI.MATCH, UI.WRAP, 0f, UI.dp(ctx, 26), UI.dp(ctx, 4), UI.dp(ctx, 26), UI.dp(ctx, 12)))
        val frame = FrameLayout(ctx)
        frame.addView(list, FrameLayout.LayoutParams(UI.MATCH, UI.MATCH))
        frame.addView(empty, FrameLayout.LayoutParams(UI.MATCH, UI.WRAP, Gravity.CENTER))
        page.content.addView(frame, UI.lp(UI.MATCH, 0, 1f, UI.dp(ctx, 20), 0, UI.dp(ctx, 20), UI.dp(ctx, 110)))
        btn.setOnClickListener { confirm() }
    }

    override fun onShow() {
        Thread {
            val found = ArrayList<Dir>()
            for (d in Cleaner.mediaDirs(Cleaner.root(), app)) found.add(Dir(d, Cleaner.dirSize(d)))
            found.sortByDescending { it.size }
            main.post {
                dirs.clear()
                dirs.addAll(found)
                adapter.notifyDataSetChanged()
                empty.visibility = if (dirs.isEmpty()) View.VISIBLE else View.GONE
                updateButton()
            }
        }.start()
    }

    private fun updateButton() {
        val n = dirs.count { it.selected }
        if (n > 0) {
            btn.text = "Clean selected ($n)"
            page.setBottom(btn)
        } else {
            page.setBottom(null)
        }
    }

    private fun confirm() {
        val sel = dirs.filter { it.selected }
        if (sel.isEmpty()) return
        Sheet.confirm(ctx, "Delete media?", Sys.fmtBytes(sel.sumOf { it.size }) + " of files will be permanently deleted from this phone.", "Delete") {
                Thread {
                    var freed = 0L
                    for (d in sel) freed += Cleaner.clearDir(d.dir)
                    main.post {
                        for (d in sel) {
                            d.size = Cleaner.dirSize(d.dir)
                            d.selected = false
                        }
                        adapter.notifyDataSetChanged()
                        updateButton()
                        nav.toast(Sys.fmtBytes(freed) + " freed")
                        Prefs.addRecord(ctx.applicationContext, if (app == 0) "WhatsApp cleaner" else "Telegram cleaner", Sys.fmtBytes(freed) + " cleared")
                    }
                }.start()
        }
    }
}
