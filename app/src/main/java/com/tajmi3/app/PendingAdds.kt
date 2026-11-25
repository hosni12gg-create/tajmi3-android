package com.tajmi3.app

import android.content.Context
import org.json.JSONArray

/** Amounts sent from the watch, waiting until the app's page applies them. */
object PendingAdds {
    private const val P = "tajmi3_pending"

    @Synchronized
    fun add(ctx: Context, amount: Double) {
        val p = ctx.getSharedPreferences(P, Context.MODE_PRIVATE)
        val a = JSONArray(p.getString("list", "[]"))
        a.put(amount)
        p.edit().putString("list", a.toString()).apply()
    }

    /** Returns the queue as a JSON array string and empties it. */
    @Synchronized
    fun take(ctx: Context): String {
        val p = ctx.getSharedPreferences(P, Context.MODE_PRIVATE)
        val s = p.getString("list", "[]") ?: "[]"
        p.edit().putString("list", "[]").apply()
        return s
    }
}
