package com.tajmi3.app

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Shows the savings progress as an ongoing notification.
 * On Android 16+ it asks to be promoted (Live Update), which is what
 * One UI 8 shows in the Now Bar.
 */
object LiveNotifier {

    private const val CHANNEL = "tajmi3_live"
    private const val NOTIF_ID = 1
    private val ACCENT = Color.parseColor("#F5A300")

    private var saved = 0.0
    private var target = 0.0
    private var cur = "₪"
    private var name = "tajmi3"
    private var hasData = false

    private fun df(pattern: String) =
        DecimalFormat(pattern, DecimalFormatSymbols(Locale.US))

    private fun fmt(n: Double) = df("#,##0.##").format(n)

    fun update(ctx: Context, saved: Double, target: Double, cur: String, name: String) {
        this.saved = saved
        this.target = target
        this.cur = cur
        this.name = name
        hasData = true
        post(ctx)
    }

    fun showLast(ctx: Context) {
        if (hasData) post(ctx)
    }

    private fun shortText(remaining: Double): String {
        if (remaining <= 0) return "🎉"
        val full = fmt(remaining) + cur
        if (full.length <= 7) return full
        return df("0.#").format(remaining / 1000.0) + "K" + cur
    }

    private fun post(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val nm = ctx.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(CHANNEL, "تقدّم التجميع", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "يعرض المبلغ المتبقي وشريط التقدم"
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
        }
        nm.createNotificationChannel(channel)

        val remaining = (target - saved).coerceAtLeast(0.0)
        val pct = if (target > 0) min(100, (saved / target * 100).roundToInt()) else 0
        val done = target > 0 && remaining <= 0.0

        val tap = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val title = if (done) "وصلت للهدف، مبروك! 🎉" else "المتبقي: ${fmt(remaining)} $cur"
        val text = "$name · جمعت ${fmt(saved)} من ${fmt(target)} $cur ($pct%)"

        val b = Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(tap)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_PROGRESS)

        if (Build.VERSION.SDK_INT >= 36) {
            val style = Notification.ProgressStyle()
                .setProgressSegments(listOf(Notification.ProgressStyle.Segment(100).setColor(ACCENT)))
                .setProgress(pct)
            b.setStyle(style)
            b.setShortCriticalText(shortText(remaining))
            b.setRequestPromotedOngoing(true)
        } else {
            b.setProgress(100, pct, false)
        }

        nm.notify(NOTIF_ID, b.build())
    }
}
