package com.pikmin.stephook

import android.app.AndroidAppHelper
import android.net.Uri
import android.os.SystemClock
import de.robv.android.xposed.XposedBridge

/** The pace :app publishes for the step module. */
data class Pace(val playing: Boolean, val stepsPerMin: Float)

/**
 * Reads the live pace from :app's exported, query-only `PaceProvider` (S5 / T5.2):
 *   `content://com.pikmin.walksim.pace/current` -> cursor { playing:INT(0/1), stepsPerMin:REAL }
 *
 * Queried DIRECTLY on the step-driver thread (our own thread — never Pikmin's sensor callback path),
 * cached to <= 2 Hz. All failures (null Application / null cursor / any exception) degrade to
 * `NOT_PLAYING` and never throw into the driver. (An earlier worker-thread + 100 ms `future.get` +
 * `cancel(true)` design hung permanently when the first cold binder query outran the timeout — the
 * cancelled worker stayed blocked in the binder call and every later poll queued behind it.)
 */
class PaceClient(private val nowMs: () -> Long = { SystemClock.elapsedRealtime() }) {

    @Volatile private var cached = NOT_PLAYING
    // 0L (NOT Long.MIN_VALUE): `now - Long.MIN_VALUE` overflows to a huge negative, which is always
    // < POLL_INTERVAL_MS, so poll() would return cached forever and never query. This was the S5 step regression.
    @Volatile private var lastPollMs = 0L

    /** Cached to <= 2 Hz; safe to call every driver tick. [NOT_PLAYING] whenever the walk isn't playing. */
    fun poll(): Pace {
        val now = nowMs()
        if (now - lastPollMs < POLL_INTERVAL_MS) return cached
        lastPollMs = now
        cached = try {
            queryProvider()
        } catch (t: Throwable) {
            XposedBridge.log("PaceClient: query threw: $t")
            NOT_PLAYING
        }
        return cached
    }

    private fun queryProvider(): Pace {
        val app = AndroidAppHelper.currentApplication()
        if (app == null) {
            XposedBridge.log("PaceClient: currentApplication() null")
            return NOT_PLAYING
        }
        val cursor = app.contentResolver.query(CURRENT_URI, null, null, null, null)
        if (cursor == null) {
            XposedBridge.log("PaceClient: query returned null (provider unreachable)")
            return NOT_PLAYING
        }
        cursor.use { c ->
            if (!c.moveToFirst()) {
                XposedBridge.log("PaceClient: empty cursor")
                return NOT_PLAYING
            }
            val playing = c.getInt(c.getColumnIndexOrThrow(COL_PLAYING)) == 1
            val spm = if (playing) c.getFloat(c.getColumnIndexOrThrow(COL_STEPS_PER_MIN)) else 0f
            return Pace(playing, spm)
        }
    }

    private companion object {
        val NOT_PLAYING = Pace(playing = false, stepsPerMin = 0f)
        const val POLL_INTERVAL_MS = 500L // <= 2 Hz
        val CURRENT_URI: Uri = Uri.parse("content://com.pikmin.walksim.pace/current")
        const val COL_PLAYING = "playing"
        const val COL_STEPS_PER_MIN = "stepsPerMin"
    }
}
