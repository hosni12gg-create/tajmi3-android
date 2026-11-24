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

    private const val CHANNEL = "tajmi3_live2"
    private const val NOTIF_ID = 1
    private val ACCENT = Color.parseColor("#FFC247")
    private val CHIP_BG = Color.parseColor("#B86E00")

    private var saved = 0.0
    private var target = 0.0
    private var cur = "₪"
    private var name = "tajmi3"
    private var hasData = false
    private var enabled = true

    fun setEnabled(ctx: Context, on: Boolean) {
        if (enabled == on) return
        enabled = on
        if (on) { if (hasData) post(ctx) }
        else ctx.getSystemService(NotificationManager::class.java).cancel(NOTIF_ID)
    }

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
        if (!enabled) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val nm = ctx.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(CHANNEL, "تقدّم التجميع", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "يعرض المبلغ المتبقي وشريط التقدم"
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
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

        val title = if (done) "وصلت للهدف، مبروك! 🎉" else "$pct% · المتبقي ${fmt(remaining)} $cur"
        val text = "$name · جمعت ${fmt(saved)} من ${fmt(target)} $cur"

        val b = Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(tap)
            .setOngoing(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_PROGRESS)

        if (Build.VERSION.SDK_INT >= 36) {
            val style = Notification.ProgressStyle()
                .setProgressSegments(listOf(Notification.ProgressStyle.Segment(100).setColor(ACCENT)))
                .setProgress(pct)
            b.setStyle(style)
            b.setShortCriticalText("$pct%")
            requestPromoted(b)
        } else {
            b.setProgress(100, pct, false)
        }

        samsungNowBar(ctx, b, title, text, pct, remaining)

        nm.notify(NOTIF_ID, b.build())
        if (Build.VERSION.SDK_INT >= 36) checkPromotion(ctx, nm)
    }

    /**
     * Samsung's own Now Bar (One UI 7+) reads these extras. Other phones ignore them.
     */
    private fun samsungNowBar(
        ctx: Context, b: Notification.Builder,
        title: String, text: String, pct: Int, remaining: Double
    ) {
        val x = android.os.Bundle().apply {
            putInt("android.ongoingActivityNoti.style", 1)
            putString("android.ongoingActivityNoti.primaryInfo", title)
            putString("android.ongoingActivityNoti.secondaryInfo", text)
            putString("android.ongoingActivityNoti.nowbarPrimaryInfo", title)
            putString("android.ongoingActivityNoti.nowbarSecondaryInfo", text)
            putString("android.ongoingActivityNoti.chipExpandedText", "$pct%")
            putInt("android.ongoingActivityNoti.chipBgColor", CHIP_BG)
            putParcelable(
                "android.ongoingActivityNoti.chipIcon",
                android.graphics.drawable.Icon.createWithResource(ctx, R.drawable.ic_stat)
            )
            putInt("android.ongoingActivityNoti.progress", pct)
            putInt("android.ongoingActivityNoti.progressMax", 100)
        }
        b.addExtras(x)
    }

    /** setRequestPromotedOngoing is API 36.1, so call it by reflection and fall back to the extra. */
    private fun requestPromoted(b: Notification.Builder) {
        try {
            Notification.Builder::class.java
                .getMethod("setRequestPromotedOngoing", java.lang.Boolean.TYPE)
                .invoke(b, true)
        } catch (_: Throwable) {
            b.addExtras(android.os.Bundle().apply { putBoolean("android.requestPromotedOngoing", true) })
        }
    }

    /**
     * If the user turned off "Live notifications" for this app, the Now Bar will never show it.
     * Tell them once and open the right settings page.
     */
    private fun checkPromotion(ctx: Context, nm: NotificationManager) {
        val allowed = try {
            NotificationManager::class.java.getMethod("canPostPromotedNotifications").invoke(nm) as Boolean
        } catch (_: Throwable) { true }
        if (allowed) return

        val prefs = ctx.getSharedPreferences("tajmi3_live", Context.MODE_PRIVATE)
        if (prefs.getBoolean("asked", false)) return
        prefs.edit().putBoolean("asked", true).apply()

        android.os.Handler(android.os.Looper.getMainLooper()).post {
            android.widget.Toast.makeText(ctx, "فعّل «الإشعارات الحية» لتجميع عشان يظهر بالـ Now Bar", android.widget.Toast.LENGTH_LONG).show()
        }
        val pkg = ctx.packageName
        val intents = listOf(
            Intent("android.settings.APP_NOTIFICATION_PROMOTION_SETTINGS"),
            Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        )
        for (i in intents) {
            try {
                ctx.startActivity(i.putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                break
            } catch (_: Throwable) { }
        }
    }
}
