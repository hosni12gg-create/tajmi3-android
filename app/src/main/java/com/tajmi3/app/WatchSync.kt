package com.tajmi3.app

import android.content.Context
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable

/** Sends the current progress to the paired watch app (same package id). */
object WatchSync {
    fun send(ctx: Context, saved: Double, target: Double, cur: String, name: String) {
        try {
            val req = PutDataMapRequest.create("/tajmi3")
            req.dataMap.putDouble("saved", saved)
            req.dataMap.putDouble("target", target)
            req.dataMap.putString("cur", cur)
            req.dataMap.putString("name", name)
            req.dataMap.putLong("t", System.currentTimeMillis())
            Wearable.getDataClient(ctx).putDataItem(req.asPutDataRequest().setUrgent())
        } catch (_: Throwable) {
        }
    }
}
