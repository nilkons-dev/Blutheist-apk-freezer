package com.ankan.netfreeze

import android.content.Context

/** Small settings store. */
object Prefs {
    private const val NAME = "netfreeze_cfg"
    private const val KEY_EXEMPT = "allowed_packages"

    private fun sp(ctx: Context) = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    /** Apps in this set are NOT frozen (they keep normal internet). */
    fun exempt(ctx: Context): Set<String> {
        return sp(ctx).getStringSet(KEY_EXEMPT, emptySet())?.toSet() ?: emptySet()
    }

    fun saveExempt(ctx: Context, packages: Set<String>) {
        sp(ctx).edit().putStringSet(KEY_EXEMPT, HashSet(packages)).apply()
    }

    fun getLong(ctx: Context, key: String, def: Long = 0L): Long = sp(ctx).getLong(key, def)
    fun putLong(ctx: Context, key: String, v: Long) {
        sp(ctx).edit().putLong(key, v).apply()
    }

    fun getInt(ctx: Context, key: String, def: Int = 0): Int = sp(ctx).getInt(key, def)
    fun putInt(ctx: Context, key: String, v: Int) {
        sp(ctx).edit().putInt(key, v).apply()
    }

    fun getBool(ctx: Context, key: String, def: Boolean): Boolean = sp(ctx).getBoolean(key, def)
    fun putBool(ctx: Context, key: String, v: Boolean) {
        sp(ctx).edit().putBoolean(key, v).apply()
    }

    /** Scan history, newest first. Each entry: "time|title|message". */
    fun records(ctx: Context): List<String> {
        val s = sp(ctx).getString("records", "") ?: ""
        return if (s.isEmpty()) emptyList() else s.split("\n")
    }

    fun addRecord(ctx: Context, title: String, message: String) {
        val line = System.currentTimeMillis().toString() + "|" + title + "|" + message.replace("\n", " ").replace("|", "/")
        val list = ArrayList<String>()
        list.add(line)
        list.addAll(records(ctx))
        val trimmed = list.take(20)
        sp(ctx).edit().putString("records", trimmed.joinToString("\n")).apply()
    }
}
