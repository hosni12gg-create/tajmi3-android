package com.tajmi3.wear

import android.app.PendingIntent
import android.content.Intent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Watch-face complication: product name + percentage. Tapping it opens the watch app.
 * Types: ring (RANGED_VALUE), short text, long text.
 */
class ProgressComplicationService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        build(type, Store.Data(1350.0, 3000.0, "₪", "لابتوب"))

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? =
        build(request.complicationType, Store.load(this))

    private fun build(type: ComplicationType, d: Store.Data?): ComplicationData? {
        val pct = if (d != null && d.target > 0) min(100.0, d.saved / d.target * 100) else 0.0
        val name = (d?.name?.takeIf { it.isNotBlank() }) ?: "تجميع"
        val percent = if (d == null) "--" else "${pct.roundToInt()}%"

        val tap = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val desc = PlainComplicationText.Builder("تقدّم التجميع $name $percent").build()
        val pText = PlainComplicationText.Builder(percent).build()
        val nText = PlainComplicationText.Builder(name).build()

        return when (type) {
            ComplicationType.RANGED_VALUE ->
                RangedValueComplicationData.Builder(pct.toFloat(), 0f, 100f, desc)
                    .setText(pText).setTitle(nText).setTapAction(tap).build()

            ComplicationType.SHORT_TEXT ->
                ShortTextComplicationData.Builder(pText, desc)
                    .setTitle(nText).setTapAction(tap).build()

            ComplicationType.LONG_TEXT ->
                LongTextComplicationData.Builder(nText, desc)
                    .setTitle(pText).setTapAction(tap).build()

            else -> null
        }
    }
}
