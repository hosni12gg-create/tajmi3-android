package com.tajmi3.wear

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import java.util.UUID

/** Screen for adding an amount: quick amounts first, or a number pad. */
class AmountActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val d = Store.load(this)
        val v = AmountView(this, { send(it) }, { finish() })
        v.cur = d?.cur ?: "₪"
        v.quick = (d?.quick?.takeIf { it.isNotEmpty() } ?: listOf(100.0, 50.0, 20.0, 10.0))
        setContentView(v)
    }

    private fun send(amount: Double) {
        // Delivered through the Data Layer, so it is kept and sent even if the phone is out of reach right now.
        val r = PutDataMapRequest.create("/add/" + UUID.randomUUID())
        r.dataMap.putDouble("amount", amount)
        r.dataMap.putLong("t", System.currentTimeMillis())
        Wearable.getDataClient(this).putDataItem(r.asPutDataRequest().setUrgent())

        Store.addLocal(this, amount)
        sendBroadcast(Intent(Store.ACTION).setPackage(packageName))
        ComplicationDataSourceUpdateRequester.create(
            this, ComponentName(this, ProgressComplicationService::class.java)
        ).requestUpdateAll()
    }
}
