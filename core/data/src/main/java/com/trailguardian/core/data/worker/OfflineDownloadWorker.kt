package com.trailguardian.core.data.worker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.trailguardian.core.data.db.TrailGuardianDatabase
import com.trailguardian.core.data.repository.OfflineMapRepository
import com.trailguardian.core.data.tile.TileCache
import com.trailguardian.core.data.tile.TileDownloader
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

class OfflineDownloadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    private val notificationId = NOTIFICATION_BASE + (id.hashCode() and 0xFF)

    override suspend fun doWork(): Result {
        val regionId = inputData.getString(KEY_REGION_ID) ?: return Result.success()
        val context = applicationContext
        val database = TrailGuardianDatabase.getInstance(context)
        val cache = TileCache(context)
        val client = OkHttpClient.Builder()
            .callTimeout(18, TimeUnit.SECONDS)
            .build()
        val repository = OfflineMapRepository(context, database, cache)
        val region = repository.getRegion(regionId) ?: return Result.success()

        createNotificationChannel(context)
        setForeground(createForegroundInfo(region.name, 0, region.tileCount))

        val urls = repository.regionUrls(region)
        val result = try {
            TileDownloader(cache, client).download(urls) { progress ->
                repository.updateProgress(
                    id = regionId,
                    progress = progress.progress,
                    completed = progress.completed,
                    failed = progress.failed,
                    bytes = progress.bytes,
                )
                runCatching {
                    setProgress(
                        workDataOf(
                            KEY_PROGRESS to progress.progress,
                            KEY_COMPLETED to progress.completed,
                            KEY_TOTAL to urls.size,
                        ),
                    )
                }
                if (progress.completed % 50 == 0 || progress.completed == urls.size) {
                    runCatching {
                        setForeground(
                            createForegroundInfo(region.name, progress.progress, urls.size),
                        )
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            repository.markError(regionId)
            return Result.success()
        }

        repository.complete(
            id = regionId,
            resultTotal = result.total,
            failed = result.failed,
            bytes = result.bytes,
        )
        return Result.success()
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                "离线地图下载",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "离线地图瓦片下载进度"
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun createForegroundInfo(name: String, progress: Int, total: Int): ForegroundInfo {
        val notification = buildNotification(name, progress, total)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    private fun buildNotification(name: String, progress: Int, total: Int): Notification {
        val launchIntent = applicationContext.packageManager
            .getLaunchIntentForPackage(applicationContext.packageName)
        val pendingIntent = launchIntent?.let {
            PendingIntent.getActivity(
                applicationContext,
                0,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
        val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("正在下载离线地图")
            .setContentText(if (total > 0) "$name · $progress% ($progress/$total)" else name)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress.coerceIn(0, 100), false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        if (pendingIntent != null) builder.setContentIntent(pendingIntent)
        return builder.build()
    }

    companion object {
        private const val KEY_REGION_ID = "regionId"
        private const val KEY_PROGRESS = "progress"
        private const val KEY_COMPLETED = "completed"
        private const val KEY_TOTAL = "total"
        private const val CHANNEL_ID = "trail_offline_downloads"
        private const val NOTIFICATION_BASE = 2100

        fun enqueue(context: Context, regionId: String) {
            val request = androidx.work.OneTimeWorkRequestBuilder<OfflineDownloadWorker>()
                .setInputData(Data.Builder().putString(KEY_REGION_ID, regionId).build())
                .addTag(regionId)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "offline-$regionId",
                androidx.work.ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }
}
