package com.tajmi3.wear

import android.content.Context
import com.google.android.gms.wearable.DataMap

object Store {
    const val ACTION = "com.tajmi3.wear.UPDATED"
    private const val P = "tajmi3_watch"

    data class Data(
        val saved: Double, val target: Double, val cur: String, val name: String,
        val quick: List<Double> = emptyList()
    )

    fun save(ctx: Context, m: DataMap) {
        ctx.getSharedPreferences(P, Context.MODE_PRIVATE).edit()
            .putString("saved", m.getDouble("saved").toString())
            .putString("target", m.getDouble("target").toString())
            .putString("cur", m.getString("cur") ?: "₪")
            .putString("name", m.getString("name") ?: "")
            .putString("quick", m.getString("quick") ?: "")
            .putBoolean("has", true)
            .apply()
    }

    fun load(ctx: Context): Data? {
        val p = ctx.getSharedPreferences(P, Context.MODE_PRIVATE)
        if (!p.getBoolean("has", false)) return null
        return Data(
            p.getString("saved", "0")!!.toDoubleOrNull() ?: 0.0,
            p.getString("target", "0")!!.toDoubleOrNull() ?: 0.0,
            p.getString("cur", "₪")!!,
            p.getString("name", "")!!,
            (p.getString("quick", "") ?: "").split(",").mapNotNull { it.trim().toDoubleOrNull() }.filter { it > 0 }
        )
    }

    /** Show the new total right away; the phone confirms with the real value later. */
    fun addLocal(ctx: Context, amount: Double) {
        val d = load(ctx) ?: return
        ctx.getSharedPreferences(P, Context.MODE_PRIVATE).edit()
            .putString("saved", (d.saved + amount).toString()).apply()
    }
}
