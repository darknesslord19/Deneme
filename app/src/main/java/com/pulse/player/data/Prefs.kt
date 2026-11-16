package com.pulse.player.data

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    fun get(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences("pulse", Context.MODE_PRIVATE)

    fun favorites(ctx: Context): Set<Long> =
        get(ctx).getStringSet("favs", emptySet())!!.mapNotNull { it.toLongOrNull() }.toSet()

    fun saveFavorites(ctx: Context, set: Set<Long>) {
        get(ctx).edit().putStringSet("favs", set.map { it.toString() }.toSet()).apply()
    }
}
