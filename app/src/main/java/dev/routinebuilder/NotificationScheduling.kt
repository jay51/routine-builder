package dev.routinebuilder

import android.app.AlarmManager
import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

internal data class ReminderRequest(val taskId: String, val date: LocalDate, val time: String)

internal object RoutineNotifications {
    const val EXTRA_OPEN_TASK_ID = "open_task_id"
    private const val CHANNEL_ID = "routine_activity_alerts_v3"
    private const val PREVIOUS_CHANNEL_ID = "routine_activity_alerts_v2"
    private const val LEGACY_CHANNEL_ID = "routine_activity_reminders"
    private const val CHANNEL_NAME = "Routine reminders"
    private const val PREFS_NAME = "routine_reminder_schedules"
    private const val PREF_SCHEDULED_TASK_IDS = "scheduled_task_ids"
    private const val ACTION_REMIND = "dev.routinebuilder.action.REMIND"
    private const val EXTRA_TASK_ID = "task_id"
    private const val EXTRA_TIME = "scheduled_time"
    private const val EXTRA_DATE = "scheduled_date"

    fun reconcile(context: Context, tasks: List<RoutineTaskEntity>) {
        createChannel(context)
        val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val oldIds = preferences.getStringSet(PREF_SCHEDULED_TASK_IDS, emptySet()).orEmpty().toSet()
        val currentIds = tasks.map { it.id }.toSet()
        (oldIds - currentIds).forEach { taskId ->
            cancel(context, taskId)
            dismissTask(context, taskId)
        }
        tasks.forEach { scheduleNext(context, it) }
        preferences.edit().putStringSet(PREF_SCHEDULED_TASK_IDS, currentIds).apply()
    }

    fun scheduleNext(context: Context, task: RoutineTaskEntity) {
        cancel(context, task.id)
        val days = task.daysOfWeek.split(',').mapNotNull(String::toIntOrNull).toSet()
        val time = runCatching { LocalTime.parse(task.timeOfDay) }.getOrNull() ?: return
        if (days.isEmpty()) return

        val now = ZonedDateTime.now()
        val next = (0L..7L)
            .map { now.toLocalDate().plusDays(it) }
            .filter { it.dayOfWeek.value in days }
            .map { it.atTime(time).atZone(now.zone) }
            .firstOrNull { it.isAfter(now) } ?: return

        val intent = reminderIntent(context, task.id).apply {
            putExtra(EXTRA_TASK_ID, task.id)
            putExtra(EXTRA_TIME, task.timeOfDay)
            putExtra(EXTRA_DATE, next.toLocalDate().toString())
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            task.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        // Inexact alarms need no special access; Android may defer delivery during battery saving.
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.toInstant().toEpochMilli(), pendingIntent)
        val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val ids = preferences.getStringSet(PREF_SCHEDULED_TASK_IDS, emptySet()).orEmpty().toMutableSet()
        ids += task.id
        preferences.edit().putStringSet(PREF_SCHEDULED_TASK_IDS, ids).apply()
    }

    fun cancel(context: Context, taskId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            taskId.hashCode(),
            reminderIntent(context, taskId),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            // Android channel sounds are immutable after creation; upgrade once and retain user choices.
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return
            val legacy = manager.getNotificationChannel(PREVIOUS_CHANNEL_ID)
                ?: manager.getNotificationChannel(LEGACY_CHANNEL_ID)
            val importance = if (legacy != null && (legacy.importance < NotificationManager.IMPORTANCE_DEFAULT ||
                    (Build.VERSION.SDK_INT >= 29 && legacy.hasUserSetImportance()))) legacy.importance
                else NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = "Reminders for activities in your routines"
                lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
                val preserveSound = legacy != null && (
                    legacy.sound == null ||
                    (Build.VERSION.SDK_INT >= 30 && legacy.hasUserSetSound()) ||
                    (Build.VERSION.SDK_INT < 30 && legacy.sound != android.provider.Settings.System.DEFAULT_NOTIFICATION_URI)
                )
                if (preserveSound) {
                    setSound(legacy?.sound, legacy?.audioAttributes)
                } else {
                    setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                }
                if (legacy != null) {
                    enableVibration(legacy.shouldVibrate())
                    vibrationPattern = legacy.vibrationPattern
                }
            }
            manager.createNotificationChannel(channel)
            manager.deleteNotificationChannel(PREVIOUS_CHANNEL_ID)
            manager.deleteNotificationChannel(LEGACY_CHANNEL_ID)
        }
    }

    fun fullScreenAllowed(context: Context): Boolean = Build.VERSION.SDK_INT < 34 ||
        context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

    fun channelImportance(context: Context): Int =
        context.getSystemService(NotificationManager::class.java).getNotificationChannel(CHANNEL_ID)?.importance
            ?: NotificationManager.IMPORTANCE_HIGH

    fun soundLabel(context: Context): String {
        val channel = context.getSystemService(NotificationManager::class.java).getNotificationChannel(CHANNEL_ID)
            ?: return "Device alarm ringtone"
        return when (channel.sound) {
            null -> "Silent"
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) -> "Device alarm ringtone"
            else -> "Selected ringtone"
        }
    }

    fun request(intent: Intent): ReminderRequest? {
        val taskId = intent.getStringExtra(EXTRA_OPEN_TASK_ID) ?: return null
        val date = intent.getStringExtra(EXTRA_DATE)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
        val time = intent.getStringExtra(EXTRA_TIME) ?: return null
        return ReminderRequest(taskId, date, time)
    }

    fun openTaskIntent(context: Context, request: ReminderRequest) = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        putExtra(EXTRA_OPEN_TASK_ID, request.taskId)
        putExtra(EXTRA_DATE, request.date.toString())
        putExtra(EXTRA_TIME, request.time)
        data = Uri.parse("routinebuilder://open/${request.taskId}/${request.date}")
    }

    fun dismiss(context: Context, request: ReminderRequest) {
        NotificationManagerCompat.from(context).cancel(request.taskId, request.date.toString().hashCode())
    }

    fun dismissTask(context: Context, taskId: String) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.activeNotifications.filter { it.tag == taskId }.forEach { manager.cancel(it.tag, it.id) }
        manager.cancel(taskId.hashCode()) // Clear any reminders from the previous notification format.
    }

    fun fullScreenSettingsIntent(context: Context): Intent = if (Build.VERSION.SDK_INT >= 34) {
        Intent(android.provider.Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}"))
    } else channelSettingsIntent(context)

    fun channelSettingsIntent(context: Context): Intent = Intent(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
        putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
        putExtra(android.provider.Settings.EXTRA_CHANNEL_ID, CHANNEL_ID)
    }

    internal suspend fun handleReminder(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
        val task = RoutineDatabase.get(context).routineDao().activeRoutineTask(taskId) ?: return
        val scheduledDate = intent.getStringExtra(EXTRA_DATE)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return
        val scheduledTime = intent.getStringExtra(EXTRA_TIME) ?: return
        val today = LocalDate.now()
        val scheduledDay = scheduledDate.dayOfWeek
        val selectedDays = task.daysOfWeek.split(',').mapNotNull(String::toIntOrNull).toSet()
        val isCurrentSchedule = task.timeOfDay == scheduledTime && scheduledDay.value in selectedDays && scheduledDate == today
        val alreadyCompleted = RoutineDatabase.get(context).routineDao().hasTaskLog(taskId, today.toString())

        if (isCurrentSchedule && !alreadyCompleted && notificationsAllowed(context)) {
            val request = ReminderRequest(taskId, scheduledDate, scheduledTime)
            val openAppIntent = openTaskIntent(context, request)
            val notificationId = scheduledDate.toString().hashCode()
            val openApp = PendingIntent.getActivity(
                context,
                notificationId,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Time for ${task.title}")
                .setContentText("Your routine activity is ready.")
                .setContentIntent(openApp)
                .addAction(0, "Complete task", openApp)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            val locked = context.getSystemService(KeyguardManager::class.java).isKeyguardLocked
            val interactive = context.getSystemService(PowerManager::class.java).isInteractive
            if (fullScreenAllowed(context) && channelImportance(context) >= NotificationManager.IMPORTANCE_HIGH && (locked || !interactive)) {
                val fullScreenIntent = Intent(context, ReminderActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION
                    putExtras(openAppIntent)
                    data = Uri.parse("routinebuilder://reminder/$taskId/$scheduledDate")
                }
                val fullScreen = PendingIntent.getActivity(context, notificationId, fullScreenIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                builder.setFullScreenIntent(fullScreen, true)
            }
            try {
                NotificationManagerCompat.from(context).notify(taskId, notificationId, builder.build())
            } catch (_: SecurityException) {
                // If special access was revoked while preparing the reminder, retain the ordinary notification.
                builder.setFullScreenIntent(null, false)
                runCatching { NotificationManagerCompat.from(context).notify(taskId, notificationId, builder.build()) }
            }
        }

        scheduleNext(context, task)
    }

    private fun notificationsAllowed(context: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    private fun reminderIntent(context: Context, taskId: String) = Intent(context, TaskReminderReceiver::class.java).apply {
        action = ACTION_REMIND
        data = Uri.parse("routinebuilder://task/$taskId")
    }
}

class TaskReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                RoutineNotifications.createChannel(context)
                RoutineNotifications.handleReminder(context, intent)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

class RoutineScheduleRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val tasks = RoutineDatabase.get(context).routineDao().activeRoutineTasks()
                RoutineNotifications.reconcile(context, tasks)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
