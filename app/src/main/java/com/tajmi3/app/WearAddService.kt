package com.tajmi3.app

import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService

/** Receives "add amount" requests from the watch app. */
class WearAddService : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) {
        var got = false
        for (e in events) {
            val path = e.dataItem.uri.path ?: continue
            if (e.type == DataEvent.TYPE_CHANGED && path.startsWith("/add/")) {
                val amount = DataMapItem.fromDataItem(e.dataItem).dataMap.getDouble("amount")
                if (amount > 0) {
                    PendingAdds.add(this, amount)
                    got = true
                }
                Wearable.getDataClient(this).deleteDataItems(e.dataItem.uri)
            }
        }
        if (got) MainActivity.live?.kick()
    }
}
