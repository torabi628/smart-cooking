package ir.aminim.smartcooking.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import ir.aminim.smartcooking.MainActivity
import ir.aminim.smartcooking.R
import ir.aminim.smartcooking.data.Storage
import ir.aminim.smartcooking.settings.AppSettings
import java.util.Calendar
import java.util.concurrent.TimeUnit

/** یادآور روزانه مواد رو به انقضای «یخچال من» */
object Reminders {
    private const val CHANNEL = "pantry"
    private const val WORK = "daily_pantry"

    fun schedule(c: Context) {
        val s = AppSettings(c)
        val wm = WorkManager.getInstance(c)
        if (!s.reminders) { wm.cancelUniqueWork(WORK); return }
        val now = Calendar.getInstance()
        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, s.reminderHour); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
            if (before(now)) add(Calendar.DAY_OF_MONTH, 1)
        }
        val req = PeriodicWorkRequestBuilder<PantryWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(next.timeInMillis - now.timeInMillis, TimeUnit.MILLISECONDS)
            .build()
        wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, req)
    }

    fun notify(c: Context, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val nm = c.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(CHANNEL, "یادآور مواد یخچال", NotificationManager.IMPORTANCE_DEFAULT))
        val pi = PendingIntent.getActivity(
            c, 0, Intent(c, MainActivity::class.java).putExtra("open", "pantry").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(title).setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pi).setAutoCancel(true).build()
        runCatching { NotificationManagerCompat.from(c).notify(7001, n) }
    }
}

class PantryWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val pantry = Storage(applicationContext).loadPantry()
        val soon = pantry.filter { it.daysLeft() in 0..1 }
        val gone = pantry.filter { it.daysLeft() < 0 }
        if (soon.isEmpty() && gone.isEmpty()) return Result.success()
        val parts = mutableListOf<String>()
        if (soon.isNotEmpty()) parts += "تا فردا مصرف شود: " + soon.joinToString("، ") { it.name }
        if (gone.isNotEmpty()) parts += "تاریخ گذشته: " + gone.joinToString("، ") { it.name }
        Reminders.notify(applicationContext, "مواد یخچال منتظر شما هستند 🍳", parts.joinToString("\n") + "\nبزنید تا غذایی با همین مواد پیشنهاد بدهیم.")
        return Result.success()
    }
}
