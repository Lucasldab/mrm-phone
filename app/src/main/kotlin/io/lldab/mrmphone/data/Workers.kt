package io.lldab.mrmphone.data

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import io.lldab.mrmphone.MrmApp
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Sends queued read marks once there is a network. */
class ReadQueueWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val repo = (applicationContext as MrmApp).container.repository
        val r = repo.flushQueue()
        return if (r.retry) Result.retry() else Result.success()
    }
}

/** Downloads the pages of the next unread chapters into the page cache. */
class PrefetchWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val repo = (applicationContext as MrmApp).container.repository
        val seriesId = inputData.getLong(KEY_SERIES, -1)
        val ids = inputData.getLongArray(KEY_CHAPTERS)?.toList().orEmpty()
        if (seriesId < 0 || ids.isEmpty()) return Result.success()
        return try {
            for (id in ids) repo.downloadChapter(seriesId, id, protect = ids.toSet())
            Result.success()
        } catch (e: ApiException) {
            if (e.permanent) Result.failure() else Result.retry()
        } catch (e: IOException) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val KEY_SERIES = "series"
        const val KEY_CHAPTERS = "chapters"
    }
}

class WorkManagerScheduler(private val context: Context) : WorkScheduler {
    private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    override fun scheduleFlush() {
        val req = OneTimeWorkRequestBuilder<ReadQueueWorker>()
            .setConstraints(online)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("flush-read-queue", ExistingWorkPolicy.KEEP, req)
    }

    override fun schedulePrefetch(seriesId: Long, chapterIds: List<Long>) {
        val req = OneTimeWorkRequestBuilder<PrefetchWorker>()
            .setConstraints(online)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .setInputData(
                workDataOf(
                    PrefetchWorker.KEY_SERIES to seriesId,
                    PrefetchWorker.KEY_CHAPTERS to chapterIds.toLongArray(),
                ),
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("prefetch", ExistingWorkPolicy.REPLACE, req)
    }
}
