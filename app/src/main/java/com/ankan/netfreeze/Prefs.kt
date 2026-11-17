package com.ankan.netfreeze

import android.content.Context

/** Stores which apps keep their internet while the freeze is on. */
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
}
