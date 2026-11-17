package com.ankan.netfreeze

import android.content.Context

object Prefs {
    private const val NAME = "netfreeze_cfg"
    private const val KEY_ALLOWED = "allowed_packages"

    fun allowed(ctx: Context): Set<String> {
        val sp = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        return sp.getStringSet(KEY_ALLOWED, emptySet())?.toSet() ?: emptySet()
    }

    fun saveAllowed(ctx: Context, packages: Set<String>) {
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_ALLOWED, HashSet(packages))
            .apply()
    }
}
