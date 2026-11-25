package com.tajmi3.wear

import android.content.ComponentName
import android.content.Intent
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService

/** Receives updates sent from the phone app. */
class DataService : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) {
        for (e in events) {
            if (e.type == DataEvent.TYPE_CHANGED && e.dataItem.uri.path == "/tajmi3") {
                Store.save(this, DataMapItem.fromDataItem(e.dataItem).dataMap)
                sendBroadcast(Intent(Store.ACTION).setPackage(packageName))
                // refresh the watch-face complication(s)
                ComplicationDataSourceUpdateRequester.create(
                    this, ComponentName(this, ProgressComplicationService::class.java)
                ).requestUpdateAll()
            }
        }
    }
}
