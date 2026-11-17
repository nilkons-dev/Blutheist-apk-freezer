package com.ankan.netfreeze

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.ColorDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.inputmethod.EditorInfo
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView

/** Lists every app with its icon. The switch decides whether the app is frozen. */
class AppsTab(private val ctx: Context, private val host: Host) : Tab {

    private val root = LinearLayout(ctx)
    override val view: View get() = root

    private val subtitle: TextView
    private val search: EditText
    private val listView = ListView(ctx)
    private val progress = ProgressBar(ctx)
    private val empty: TextView
    private val chips = ArrayList<TextView>()
    private val systemChip: TextView

    private val adapter = AppsAdapter()
    private var shown: List<AppItem> = emptyList()
    private var exempt = HashSet<String>()
    private var query = ""
    private var filter = 0 // 0 = all, 1 = frozen, 2 = allowed
    private var showSystem = false
    private val iconPx = UI.dp(ctx, 44)

    init {
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(UI.BG)
        val pad = UI.dp(ctx, 20)

        // title row
        val head = LinearLayout(ctx)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        val titleCol = LinearLayout(ctx)
        titleCol.orientation = LinearLayout.VERTICAL
        titleCol.addView(UI.text(ctx, "Apps", 26f, UI.TEXT, UI.medium()))
        subtitle = UI.text(ctx, " ", 13f, UI.MUTED)
        titleCol.addView(subtitle, UI.lp(UI.WRAP, UI.WRAP, 0f, 0, UI.dp(ctx, 4), 0, 0))
        head.addView(titleCol, UI.lp(0, UI.WRAP, 1f))
        head.addView(actionButton("Freeze all") { setAll(true) }, UI.lp(UI.WRAP, UI.WRAP, 0f, 0, 0, UI.dp(ctx, 8), 0))
        head.addView(actionButton("Allow all") { setAll(false) })
        root.addView(head, UI.lp(UI.MATCH, UI.WRAP, 0f, pad, UI.dp(ctx, 22), pad, 0))

        val hint = UI.text(
            ctx,
            "Switch ON = frozen (no background internet). Switch OFF = keeps normal internet. " +
                "Turn OFF the apps you still need, like your browser or messenger.",
            12f, UI.MUTED
        )
        root.addView(hint, UI.lp(UI.MATCH, UI.WRAP, 0f, pad, UI.dp(ctx, 10), pad, 0))

        // search
        search = EditText(ctx)
        search.hint = "Search apps"
        search.setHintTextColor(UI.MUTED)
        search.setTextColor(UI.TEXT)
        search.textSize = 15f
        search.isSingleLine = true
        search.imeOptions = EditorInfo.IME_ACTION_DONE
        search.background = UI.rounded(ctx, UI.CARD, 14f, UI.STROKE, 1f)
        search.setPadding(UI.dp(ctx, 16), UI.dp(ctx, 12), UI.dp(ctx, 16), UI.dp(ctx, 12))
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                query = s?.toString() ?: ""
                applyFilter()
            }
        })
        root.addView(search, UI.lp(UI.MATCH, UI.WRAP, 0f, pad, UI.dp(ctx, 14), pad, 0))

        // chips
        val chipRow = LinearLayout(ctx)
        chipRow.orientation = LinearLayout.HORIZONTAL
        val names = arrayOf("All", "Frozen", "Allowed")
        for (i in names.indices) {
            val c = chip(names[i])
            c.setOnClickListener {
                filter = i
                styleChips()
                applyFilter()
            }
            chips.add(c)
            chipRow.addView(c, UI.lp(UI.WRAP, UI.WRAP, 0f, 0, 0, UI.dp(ctx, 8), 0))
        }
        systemChip = chip("+ System")
        systemChip.setOnClickListener {
            showSystem = !showSystem
            styleChips()
            applyFilter()
        }
        chipRow.addView(systemChip)
        root.addView(chipRow, UI.lp(UI.MATCH, UI.WRAP, 0f, pad, UI.dp(ctx, 12), pad, UI.dp(ctx, 8)))
        styleChips()

        // list
        listView.divider = null
        listView.dividerHeight = 0
        listView.selector = ColorDrawable(Color.parseColor("#14FFFFFF"))
        listView.isFastScrollEnabled = true
        listView.overScrollMode = View.OVER_SCROLL_NEVER
        listView.adapter = adapter
        listView.setOnItemClickListener { _, _, position, _ ->
            if (position in shown.indices) toggle(shown[position])
        }

        empty = UI.text(ctx, "No apps found", 14f, UI.MUTED)
        empty.gravity = Gravity.CENTER
        empty.visibility = View.GONE

        val frame = FrameLayout(ctx)
        frame.addView(listView, FrameLayout.LayoutParams(UI.MATCH, UI.MATCH))
        frame.addView(empty, FrameLayout.LayoutParams(UI.MATCH, UI.WRAP, Gravity.CENTER))
        frame.addView(progress, FrameLayout.LayoutParams(UI.dp(ctx, 40), UI.dp(ctx, 40), Gravity.CENTER))
        root.addView(frame, UI.lp(UI.MATCH, 0, 1f))
    }

    private fun actionButton(label: String, onClick: () -> Unit): TextView {
        val t = UI.text(ctx, label, 12.5f, UI.ACCENT, UI.medium())
        t.setPadding(UI.dp(ctx, 12), UI.dp(ctx, 8), UI.dp(ctx, 12), UI.dp(ctx, 8))
        t.background = UI.rounded(ctx, UI.CARD, 10f, UI.STROKE, 1f)
        t.setOnClickListener { onClick() }
        return t
    }

    private fun chip(label: String): TextView {
        val t = UI.text(ctx, label, 12.5f, UI.MUTED, UI.medium())
        t.setPadding(UI.dp(ctx, 14), UI.dp(ctx, 8), UI.dp(ctx, 14), UI.dp(ctx, 8))
        return t
    }

    private fun styleChips() {
        for (i in chips.indices) {
            styleChip(chips[i], filter == i)
        }
        styleChip(systemChip, showSystem)
    }

    private fun styleChip(t: TextView, on: Boolean) {
        if (on) {
            t.setTextColor(Color.parseColor("#04121C"))
            t.background = UI.rounded(ctx, UI.ACCENT, 18f)
        } else {
            t.setTextColor(UI.MUTED)
            t.background = UI.rounded(ctx, UI.CARD, 18f, UI.STROKE, 1f)
        }
    }

    override fun onShow() {
        exempt = HashSet(Prefs.exempt(ctx))
        if (AppRepo.loaded) {
            progress.visibility = View.GONE
            applyFilter()
        } else {
            progress.visibility = View.VISIBLE
            AppRepo.load(ctx) {
                progress.visibility = View.GONE
                applyFilter()
            }
        }
    }

    private fun applyFilter() {
        val q = query.trim().lowercase()
        val list = ArrayList<AppItem>()
        var frozenN = 0
        var allowedN = 0
        for (a in AppRepo.items) {
            if (!showSystem && !a.hasLauncher) continue
            val frozen = a.pkg !in exempt
            if (frozen) frozenN++ else allowedN++
            if (filter == 1 && !frozen) continue
            if (filter == 2 && frozen) continue
            if (q.isNotEmpty() && !a.label.lowercase().contains(q) && !a.pkg.lowercase().contains(q)) continue
            list.add(a)
        }
        shown = list
        adapter.notifyDataSetChanged()
        subtitle.text = "$frozenN frozen · $allowedN allowed"
        empty.visibility = if (list.isEmpty() && AppRepo.loaded) View.VISIBLE else View.GONE
    }

    private fun toggle(item: AppItem) {
        if (item.pkg in exempt) exempt.remove(item.pkg) else exempt.add(item.pkg)
        commit()
    }

    private fun setAll(freeze: Boolean) {
        if (!AppRepo.loaded) return
        if (freeze) {
            exempt.clear()
        } else {
            for (a in AppRepo.items) exempt.add(a.pkg)
        }
        commit()
    }

    private fun commit() {
        Prefs.saveExempt(ctx, exempt)
        applyFilter()
        host.exemptChanged()
    }

    private class Holder(
        val root: View,
        val icon: ImageView,
        val name: TextView,
        val status: TextView,
        val pill: PillSwitch
    ) {
        var boundPkg: String? = null
    }

    private fun newHolder(): Holder {
        val row = LinearLayout(ctx)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.minimumHeight = UI.dp(ctx, 64)
        row.setPadding(UI.dp(ctx, 20), UI.dp(ctx, 8), UI.dp(ctx, 20), UI.dp(ctx, 8))
        row.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS

        val icon = ImageView(ctx)
        icon.scaleType = ImageView.ScaleType.FIT_XY
        icon.clipToOutline = true
        val radius = UI.dpf(ctx, 12f)
        icon.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, outline: Outline) {
                outline.setRoundRect(0, 0, v.width, v.height, radius)
            }
        }
        row.addView(icon, UI.lp(UI.dp(ctx, 44), UI.dp(ctx, 44)))

        val col = LinearLayout(ctx)
        col.orientation = LinearLayout.VERTICAL
        val name = UI.text(ctx, "", 15.5f, UI.TEXT, UI.medium())
        name.isSingleLine = true
        name.ellipsize = android.text.TextUtils.TruncateAt.END
        val status = UI.text(ctx, "", 12f, UI.MUTED)
        status.isSingleLine = true
        status.ellipsize = android.text.TextUtils.TruncateAt.END
        col.addView(name)
        col.addView(status, UI.lp(UI.WRAP, UI.WRAP, 0f, 0, UI.dp(ctx, 3), 0, 0))
        row.addView(col, UI.lp(0, UI.WRAP, 1f, UI.dp(ctx, 14), 0, UI.dp(ctx, 12), 0))

        val pill = PillSwitch(ctx)
        row.addView(pill, UI.lp(UI.WRAP, UI.WRAP))

        val h = Holder(row, icon, name, status, pill)
        row.tag = h
        return h
    }

    private inner class AppsAdapter : BaseAdapter() {
        override fun getCount(): Int = shown.size
        override fun getItem(position: Int): Any = shown[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val h: Holder
            val v: View
            if (convertView == null) {
                h = newHolder()
                v = h.root
            } else {
                v = convertView
                h = convertView.tag as Holder
            }
            val item = shown[position]
            h.boundPkg = item.pkg
            h.name.text = item.label
            val frozen = item.pkg !in exempt
            val base = if (frozen) "Frozen" else "Internet allowed"
            h.status.text = if (item.hasLauncher) base else base + " · " + item.pkg
            h.status.setTextColor(if (frozen) UI.ACCENT else UI.GOOD)
            h.pill.set(frozen, false)

            val cached = IconLoader.get(item.pkg)
            if (cached != null) {
                h.icon.setImageBitmap(cached)
            } else {
                h.icon.setImageDrawable(UI.rounded(ctx, UI.CARD_HI, 12f))
                val holder = h
                val pkg = item.pkg
                IconLoader.load(ctx, pkg, iconPx) { bmp: Bitmap ->
                    if (holder.boundPkg == pkg) holder.icon.setImageBitmap(bmp)
                }
            }
            return v
        }
    }
}
