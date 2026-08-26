package com.miolauncher.app.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * 每日精品推荐（WorkManager 每日定时）。
 * 零成本轮询：一次 /api/discover/daily 返回几百字节，通知文案用信息性标题。
 * 免打扰时段 22:00~08:00 不推送；总开关关闭不推送。
 */
class DailyRecommendWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        if (!DiscoverStore.allowDailyPush(ctx)) return Result.success()
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if (hour >= 22 || hour < 8) return Result.success()  // 免打扰时段
        val dev = DiscoverStore.deviceId(ctx)
        val recs = ServerDiscoveryApi.fetchDaily(ctx, dev, DiscoverStore.topTags(ctx))
        if (recs.isEmpty()) return Result.success()
        showNotification(ctx, recs)
        return Result.success()
    }

    private fun showNotification(context: Context, recs: List<DiscoverServer>) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "每日推荐", NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
        val top = recs.first()
        val title = buildString {
            append("📋 今日热门：")
            append(top.name)
            top.rating?.let { append(" 好评率 $it%") }
        }
        val others = recs.drop(1).take(2).joinToString(" · ") { it.name }
        val text = if (others.isNotEmpty()) "$others 等 ${recs.size} 个好服等你" else "点击查看详情，一键进服"

        // 点击 → 打开发现页该服详情
        DiscoverNav.pendingServerId = top.id
        val intent = Intent(context, com.miolauncher.app.MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notif = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pi)
            .build()
        nm.notify(NOTIF_ID, notif)
    }

    companion object {
        private const val CHANNEL = "daily_recommend"
        private const val NOTIF_ID = 2001
        const val WORK_NAME = "daily_recommend"

        /** 注册/更新每日定时任务（间隔 24h，首次延迟到下一个推送时刻） */
        fun schedule(context: Context) {
            val delayMs = delayToNextPush(DiscoverStore.pushHour(context))
            val request = PeriodicWorkRequestBuilder<DailyRecommendWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }

        /** 距下一个推送时刻的毫秒数（当前时刻之后最近的 pushHour:00） */
        fun delayToNextPush(pushHour: Int): Long {
            val now = Calendar.getInstance()
            val target = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, pushHour)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (!target.after(now)) target.add(Calendar.DAY_OF_YEAR, 1)
            return target.timeInMillis - now.timeInMillis
        }
    }
}
