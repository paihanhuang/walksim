package com.pikmin.walksim

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.SystemClock

/**
 * Exported, permission-free, query-only pace channel (AC-17). Pikmin's :stephook process polls
 *   content://com.pikmin.walksim.pace/current
 * and reads a single-row cursor { playing:INT(0/1), stepsPerMin:REAL, schemaVersion:INT } derived from the live fix
 * (pure math in [PaceDerivation]). Every query records [WalkBus.lastQueriedElapsedMs] so :app can infer
 * whether the step module is active (AC-23). insert/update/delete/getType are stubbed.
 */
class PaceProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        WalkBus.lastQueriedElapsedMs = SystemClock.elapsedRealtime()
        val pace = PaceDerivation.derive(WalkBus.sample.value, WalkBus.strideM)
        return MatrixCursor(arrayOf(COL_PLAYING, COL_STEPS_PER_MIN, COL_SCHEMA_VERSION)).apply {
            addRow(arrayOf<Any>(if (pace.playing) 1 else 0, pace.stepsPerMin, SCHEMA_VERSION))
        }
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun update(uri: Uri, values: ContentValues?, s: String?, a: Array<out String>?): Int = 0
    override fun delete(uri: Uri, s: String?, a: Array<out String>?): Int = 0

    companion object {
        const val AUTHORITY = "com.pikmin.walksim.pace"
        const val CURRENT_URI_STR = "content://$AUTHORITY/current"
        val CURRENT_URI: Uri = Uri.parse(CURRENT_URI_STR)
        const val COL_PLAYING = "playing"
        const val COL_STEPS_PER_MIN = "stepsPerMin"
        // Additive, trailing schema-version column (Stage 2). Old clients read playing/stepsPerMin
        // by name and ignore this; bumping it signals a shape change without breaking them.
        const val COL_SCHEMA_VERSION = "schemaVersion"
        const val SCHEMA_VERSION = 1
    }
}
