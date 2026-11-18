package com.ankan.netfreeze

import android.app.AlertDialog
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

class CleanupScreen(ctx: Context, nav: Nav) : Screen(ctx, nav) {
    private val page = Page(ctx, nav, "Clean up")
    override val view: View get() = page.root
    private val main = Handler(Looper.getMainLooper())

    private val shield = ShieldView(ctx)
    private val titleTv = UI.text(ctx, "", 28f, UI.TEXT, UI.bold())
    private val subTv = UI.text(ctx, "", 15f, UI.MUTED)
    private val sizeTv = UI.text(ctx, "", 26f, UI.TEXT, UI.bold())
    private val unitTv = UI.text(ctx, "", 13f, UI.MUTED)
    private val cleanBtn = UI.pill(ctx, "Clean", true)
    private val permCard = UI.card(ctx)
    private val storageVal = UI.text(ctx, "", 13.5f, UI.MUTED)
    private val bar = SegBar(ctx)
    private val listHost = LinearLayout(ctx)
    private var scanning = false
    private var cleaning = false

    init {
        listHost.orientation = LinearLayout.VERTICAL

        // header: title on the left, shield with size on the right
        val head = LinearLayout(ctx)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        val left = LinearLayout(ctx)
        left.orientation = LinearLayout.VERTICAL
        left.addView(titleTv)
        left.addView(subTv, UI.lp(UI.WRAP, UI.WRAP, 0f, 0, UI.dp(ctx, 6), 0, 0))
        head.addView(left, UI.lp(0, UI.WRAP, 1f))
        val sf = FrameLayout(ctx)
        shield.mode = ShieldView.NONE
        sf.addView(shield, FrameLayout.LayoutParams(UI.MATCH, UI.MATCH))
        val cc = LinearLayout(ctx)
        cc.orientation = LinearLayout.VERTICAL
        cc.gravity = Gravity.CENTER
        cc.addView(sizeTv)
        cc.addView(unitTv)
        sf.addView(cc, FrameLayout.LayoutParams(UI.MATCH, UI.MATCH))
        head.addView(sf, UI.lp(UI.dp(ctx, 100), UI.dp(ctx, 126)))
        page.add(head, UI.lp(UI.MATCH, UI.WRAP, 0f, UI.dp(ctx, 6), UI.dp(ctx, 6), UI.dp(ctx, 6), 0))

        cleanBtn.setOnClickListener { clean() }
        val bw = minOf(UI.dp(ctx, 280), ctx.resources.displayMetrics.widthPixels - UI.dp(ctx, 70))
        page.add(cleanBtn, UI.lp(bw, UI.WRAP, 0f, 0, UI.dp(ctx, 22), 0, 0).also { it.gravity = Gravity.CENTER_HORIZONTAL })

        // permission card
        permCard.setPadding(UI.dp(ctx, 20), UI.dp(ctx, 20), UI.dp(ctx, 20), UI.dp(ctx, 20))
        permCard.addView(UI.text(ctx, "File access needed", 18f, UI.TEXT, UI.medium()))
        permCard.addView(
            UI.text(ctx, "To find junk, big files and old APKs, Blutheist needs permission to see files on your phone. Nothing leaves your device.", 14f, UI.MUTED),
            UI.lp(UI.MATCH, UI.WRAP, 0f, 0, UI.dp(ctx, 8), 0, UI.dp(ctx, 16))
        )
        val allow = UI.pill(ctx, "Allow access", true)
        allow.setOnClickListener { nav.openAllFiles() }
        permCard.addView(allow, UI.lp(UI.MATCH, UI.WRAP))
        permCard.visibility = View.GONE
        page.add(permCard, UI.lp(UI.MATCH, UI.WRAP, 0f, 0, UI.dp(ctx, 22), 0, 0))

        // storage card
        val sc = UI.card(ctx)
        sc.setPadding(UI.dp(ctx, 20), UI.dp(ctx, 18), UI.dp(ctx, 20), UI.dp(ctx, 20))
        val sr = LinearLayout(ctx)
        sr.orientation = LinearLayout.HORIZONTAL
        sr.gravity = Gravity.CENTER_VERTICAL
        sr.addView(UI.text(ctx, "Storage space", 18f, UI.TEXT, UI.medium()), UI.lp(0, UI.WRAP, 1f))
        sr.addView(storageVal, UI.lp(UI.WRAP, UI.WRAP))
        sc.addView(sr)
        sc.addView(bar, UI.lp(UI.MATCH, UI.dp(ctx, 12), 0f, 0, UI.dp(ctx, 16), 0, 0))
        page.add(sc, UI.lp(UI.MATCH, UI.WRAP, 0f, 0, UI.dp(ctx, 22), 0, 0))
        page.add(listHost)
        updateStorage(null)
    }

    override fun onShow() {
        if (!Sys.hasStorageAccess(ctx)) {
            showState(0)
            updateStorage(null)
            return
        }
        val r = Cleaner.last
        if (r != null) {
            render(r)
        } else if (!scanning) {
            startScan()
        }
    }

    private fun showState(s: Int) {
        permCard.visibility = if (s == 0) View.VISIBLE else View.GONE
        cleanBtn.visibility = if (s == 2) View.VISIBLE else View.GONE
        shield.setSpinning(s == 1)
        when (s) {
            0 -> {
                titleTv.text = "Allow access"
                subTv.text = "to scan your storage"
                sizeTv.text = ""
                unitTv.text = ""
                shield.mode = ShieldView.NONE
                shield.progress = 0f
            }
            1 -> {
                titleTv.text = "Scanning…"
                sizeTv.text = ""
                unitTv.text = ""
                shield.mode = ShieldView.NONE
                shield.progress = 0f
            }
            2 -> {
                shield.mode = ShieldView.NONE
                shield.progress = -1f
            }
            else -> {
                sizeTv.text = ""
                unitTv.text = ""
                shield.mode = ShieldView.CHECK
                shield.progress = -1f
            }
        }
    }

    private fun startScan() {
        scanning = true
        showState(1)
        subTv.text = "Looking for junk files"
        Thread {
            val r = Cleaner.scan(Cleaner.root(), AtomicBoolean(false)) { n ->
                main.post { subTv.text = "$n files checked" }
            }
            main.post {
                Cleaner.last = r
                scanning = false
                render(r)
            }
        }.start()
    }

    private fun render(r: ScanResult) {
        if (cleaning) return
        if (r.junkBytes > 0L || r.emptyDirs.isNotEmpty()) {
            showState(2)
            titleTv.text = "Junk files detected"
            subTv.text = r.junk.size.toString() + " files · " + r.emptyDirs.size + " empty folders"
            val s = Sys.fmtBytes(r.junkBytes)
            sizeTv.text = s.substringBefore(' ')
            unitTv.text = s.substringAfter(' ', "")
        } else {
            showState(3)
            titleTv.text = "Your phone is clean"
            subTv.text = "No junk files found"
        }
        updateStorage(r)
        buildLists(r)
    }

    private fun clean() {
        val r = Cleaner.last ?: return
        if (cleaning) return
        cleaning = true
        cleanBtn.text = "Cleaning…"
        cleanBtn.alpha = 0.6f
        shield.setSpinning(true)
        val app = ctx.applicationContext
        Thread {
            var freed = Cleaner.delete(r.junk)
            Cleaner.deleteEmptyDirs(r.emptyDirs)
            freed += Cleaner.clearOwnCache(app)
            r.junk.clear()
            r.emptyDirs.clear()
            r.junkBytes = 0L
            main.post {
                cleaning = false
                cleanBtn.text = "Clean"
                cleanBtn.alpha = 1f
                showState(3)
                titleTv.text = "Cleanup complete"
                subTv.text = Sys.fmtBytes(freed) + " cleared"
                Prefs.addRecord(app, "Storage cleanup", Sys.fmtBytes(freed) + " cleared")
                updateStorage(r)
                buildLists(r)
            }
        }.start()
    }

    private fun updateStorage(r: ScanResult?) {
        val st = Sys.storage()
        val used = st.total - st.free
        storageVal.text = Sys.fmtBytes(used) + " of " + Sys.fmtBytes(st.total) + " used"
        val total = st.total.toFloat().coerceAtLeast(1f)
        if (r == null) {
            bar.setParts(listOf(Pair(used / total, Color.WHITE)))
            return
        }
        val rest = r.audioBytes + r.docBytes + r.archiveBytes + r.apkBytes
        val media = r.imageBytes + r.videoBytes + rest
        val other = (used - media).coerceAtLeast(0L)
        bar.setParts(
            listOf(
                Pair(other / total, Color.WHITE),
                Pair(r.imageBytes / total, Color.parseColor("#A0A0A6")),
                Pair(r.videoBytes / total, Color.parseColor("#6C6C72")),
                Pair(rest / total, Color.parseColor("#45454A"))
            )
        )
    }

    private fun buildLists(r: ScanResult) {
        listHost.removeAllViews()
        listHost.addView(UI.sectionLabel(ctx, "App cleanup"), UI.lp(UI.MATCH, UI.WRAP, 0f, 0, UI.dp(ctx, 24), 0, UI.dp(ctx, 10)))
        val a = UI.card(ctx)
        a.addView(UI.row(ctx, Ic.CHAT, "WhatsApp cleaner", Sys.fmtBytes(r.whatsappBytes), true) { nav.push(AppMediaScreen(ctx, nav, 0)) }.root)
        a.addView(UI.divider(ctx), UI.lp(UI.MATCH, 1, 0f, UI.dp(ctx, 16), 0, UI.dp(ctx, 16), 0))
        a.addView(UI.row(ctx, Ic.SEND, "Telegram cleaner", Sys.fmtBytes(r.telegramBytes), true) { nav.push(AppMediaScreen(ctx, nav, 1)) }.root)
        listHost.addView(a)

        listHost.addView(UI.sectionLabel(ctx, "Recommended cleanup"), UI.lp(UI.MATCH, UI.WRAP, 0f, 0, UI.dp(ctx, 24), 0, UI.dp(ctx, 10)))
        val b = UI.card(ctx)
        val largeBytes = r.large.sumOf { it.size }
        val rows = arrayOf(
            Triple(Ic.FILE, "Large files", Pair(0, largeBytes)),
            Triple(Ic.VIDEO, "Videos", Pair(2, r.videoBytes)),
            Triple(Ic.IMAGE, "Photos", Pair(3, r.imageBytes)),
            Triple(Ic.BOX, "APK files", Pair(1, r.apkBytes)),
            Triple(Ic.MUSIC, "Audio", Pair(4, r.audioBytes)),
            Triple(Ic.FILE, "Documents", Pair(5, r.docBytes)),
            Triple(Ic.ARCHIVE, "Archives", Pair(6, r.archiveBytes))
        )
        for (i in rows.indices) {
            val t = rows[i]
            b.addView(UI.row(ctx, t.first, t.second, Sys.fmtBytes(t.third.second), true) { nav.push(FileListScreen(ctx, nav, t.third.first)) }.root)
            if (i < rows.size - 1) b.addView(UI.divider(ctx), UI.lp(UI.MATCH, 1, 0f, UI.dp(ctx, 16), 0, UI.dp(ctx, 16), 0))
        }
        listHost.addView(b)
    }
}

/** Files of one category from the last scan, with check boxes and delete. */
class FileListScreen(ctx: Context, nav: Nav, private val kind: Int) : Screen(ctx, nav) {
    private val titles = arrayOf("Large files", "APK files", "Videos", "Photos", "Audio", "Documents", "Archives")
    private val icons = intArrayOf(Ic.FILE, Ic.BOX, Ic.VIDEO, Ic.IMAGE, Ic.MUSIC, Ic.FILE, Ic.ARCHIVE)
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
                else -> r.archives
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
        AlertDialog.Builder(ctx)
            .setTitle("Delete files?")
            .setMessage(sel.size.toString() + " file(s), " + Sys.fmtBytes(sel.sumOf { it.size }) + ", will be permanently deleted.")
            .setPositiveButton("Delete") { _, _ -> doDelete(sel) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun doDelete(sel: List<FileEntry>) {
        Thread {
            val freed = Cleaner.delete(sel)
            main.post {
                val r = Cleaner.last
                if (r != null) {
                    for (l in listOf(r.large, r.apks, r.videos, r.images, r.audio, r.docs, r.archives)) {
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
        AlertDialog.Builder(ctx)
            .setTitle("Delete media?")
            .setMessage(Sys.fmtBytes(sel.sumOf { it.size }) + " of files will be permanently deleted from this phone.")
            .setPositiveButton("Delete") { _, _ ->
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
            .setNegativeButton("Cancel", null)
            .show()
    }
}
