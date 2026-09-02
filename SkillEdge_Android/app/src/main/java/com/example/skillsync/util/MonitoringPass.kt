package com.example.skillsync.util

import android.content.Context
import android.util.Log
import com.example.skillsync.data.api.RetrofitClient
import com.example.skillsync.data.cache.LocalCache
import com.example.skillsync.data.sync.SyncCoordinator
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * One delivery-monitoring pass: refresh the manager snapshots, diff against the
 * shared [NotificationStateStore] seen-set, and fire a system notification for
 * anything new.
 *
 * Both the foreground [MonitoringService] (real-time while the app is closed)
 * and the [SkillSyncNotificationWorker] (WorkManager backstop) call this, so an
 * event is reported exactly once regardless of which path notices it first.
 *
 * After the notification-detection pass it also checks whether a proactive
 * digest is due (morning brief 07:00-10:00 local, once a day; weekly wrap
 * Friday 16:00-19:00 local, once a week) and posts one summary notification —
 * guarded through [DigestStateStore] so it never spams.
 */
object MonitoringPass {

    sealed class Result {
        object Ok : Result()
        object NotLoggedIn : Result()
        object Retry : Result()
    }

    suspend fun run(context: Context): Result {
        return try {
            SyncCoordinator.initialize(context)
            NotificationStateStore.init(context)

            val email = com.example.skillsync.data.SessionManager.getEmail()
            if (email.isNullOrBlank()) return Result.NotLoggedIn

            if (!SyncCoordinator.sync(context)) return Result.Retry
            val data = LocalCache.loadMap("dashboard_$email") ?: return Result.Retry

            if (NotificationStateStore.isFirstRun(email)) {
                seedSeenState(email, data)
                maybePostDigests(context, email)
                return Result.Ok
            }

            val events = NotificationEngine.detect(
                data,
                NotificationStateStore.getSeen(email, NotificationEngine.BUCKET_ALLOCATION),
                NotificationStateStore.getSeen(email, NotificationEngine.BUCKET_FEEDBACK),
                NotificationStateStore.getSeen(email, NotificationEngine.BUCKET_DEMAND),
                NotificationStateStore.getSeen(email, NotificationEngine.BUCKET_DELIVERY),
            )
            if (events.isNotEmpty()) {
                NotificationEngine.toNotifications(events).forEach { event ->
                    LocalNotificationService.showNotification(context, event)
                }
                events.groupBy { it.bucket }.forEach { (bucket, group) ->
                    NotificationStateStore.addSeen(email, bucket, group.map { it.id }.toSet())
                }
            }

            maybePostDigests(context, email)
            ViberAutomationEngine.processPass(context, email, data)
            Result.Ok
        } catch (e: Exception) {
            Log.e("MonitoringPass", "monitoring pass failed", e)
            Result.Retry
        }
    }

    private fun seedSeenState(email: String, data: Map<String, Any>) {
        NotificationEngine.detect(data, emptySet(), emptySet(), emptySet(), emptySet())
            .groupBy { it.bucket }
            .forEach { (bucket, group) ->
                NotificationStateStore.addSeen(email, bucket, group.map { it.id }.toSet())
            }
        NotificationStateStore.markInitialized(email)
    }

    /**
     * Post the morning brief and/or the Friday wrap when the local clock is in
     * the window and this period's digest has not already been sent.
     */
    private suspend fun maybePostDigests(context: Context, email: String) {
        try {
            DigestStateStore.init(context)
            val now = Calendar.getInstance()
            val hour = now.get(Calendar.HOUR_OF_DAY)
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(now.time)
            val weekKey = "${now.get(Calendar.YEAR)}-W${now.get(Calendar.WEEK_OF_YEAR)}"

            if (hour in 7..9 && DigestStateStore.lastMorningDate(email) != today) {
                val d = runCatching { RetrofitClient.instance.getDigest(email, "morning") }.getOrNull()
                if (d != null && d["loading"] != true) {
                    val count = (d["items"] as? List<*>)?.size ?: 0
                    val headline = d["headline"]?.toString().orEmpty()
                    LocalNotificationService.showNotification(
                        context,
                        NotifyEvent(
                            id = "digest_morning_$today", bucket = "digest",
                            title = "Your day: $count item${if (count == 1) "" else "s"} need you",
                            message = headline.ifBlank { "Open This Week for today's priorities." },
                            targetType = "priorities", targetId = email,
                        ),
                    )
                    DigestStateStore.setMorningDate(email, today)
                }
            }

            if (now.get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY && hour in 16..18 &&
                DigestStateStore.lastWeeklyWeek(email) != weekKey
            ) {
                val d = runCatching { RetrofitClient.instance.getDigest(email, "weekly") }.getOrNull()
                if (d != null && d["loading"] != true) {
                    val msg = d["message"]?.toString().orEmpty()
                    LocalNotificationService.showNotification(
                        context,
                        NotifyEvent(
                            id = "digest_weekly_$weekKey", bucket = "digest",
                            title = "Your week in review",
                            message = msg.ifBlank { "Open This Week for the team summary." }.take(400),
                            targetType = "priorities", targetId = email,
                        ),
                    )
                    DigestStateStore.setWeeklyWeek(email, weekKey)
                }
            }
        } catch (e: Exception) {
            Log.w("MonitoringPass", "digest post failed", e)
        }
    }
}
