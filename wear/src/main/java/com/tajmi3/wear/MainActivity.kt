package com.tajmi3.wear

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable

class MainActivity : Activity() {
    private lateinit var ring: RingView

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) = refresh()
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        ring = RingView(this)
        setContentView(ring)
    }

    override fun onStart() {
        super.onStart()
        val f = IntentFilter(Store.ACTION)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, f, Context.RECEIVER_NOT_EXPORTED)
        else registerReceiver(receiver, f)
        refresh()
        // Pick up whatever the phone sent while this app was closed.
        Wearable.getDataClient(this).dataItems.addOnSuccessListener { buf ->
            try {
                for (item in buf) {
                    if (item.uri.path == "/tajmi3") {
                        Store.save(this, DataMapItem.fromDataItem(item).dataMap)
                    }
                }
            } finally {
                buf.release()
            }
            refresh()
        }
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(receiver)
    }

    private fun refresh() = ring.show(Store.load(this))
}
