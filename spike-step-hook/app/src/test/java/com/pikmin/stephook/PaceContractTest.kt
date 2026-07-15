package com.pikmin.stephook

import android.database.Cursor
import java.io.File
import java.lang.reflect.Proxy
import java.util.Properties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Pins [PaceClient]'s IPC constants to the canonical contract
 * (docs/sdlc/walk-simulator/pace-contract.properties) and proves the client is additive-tolerant:
 * an old, versionless 2-column cursor still parses. A rename/removal on either side is exactly what
 * makes these go RED — nothing else.
 */
class PaceContractTest {
    private val canonical = Properties().apply {
        File("/Users/davidhuang/Projects/pikmin-remote-control/docs/sdlc/walk-simulator/pace-contract.properties")
            .inputStream().use { load(it) }
    }

    @Test
    fun clientConstantsMatchCanonical() {
        assertEquals(
            "content://${canonical.getProperty("authority")}/${canonical.getProperty("path")}",
            PaceClient.CURRENT_URI_STR,
        )
        assertEquals(canonical.getProperty("col.playing"), PaceClient.COL_PLAYING)
        assertEquals(canonical.getProperty("col.stepsPerMin"), PaceClient.COL_STEPS_PER_MIN)
        assertEquals(canonical.getProperty("pollIntervalMsMax").toLong(), PaceClient.POLL_INTERVAL_MS)
    }

    @Test
    fun parsesVersionlessCursor() {
        // An old provider (pre-Stage-2) emits only { playing, stepsPerMin } — parseRow reads by NAME,
        // so it still parses without a schemaVersion column present.
        val c = rowCursor(listOf("playing", "stepsPerMin"), listOf(1, 90f))
        assertEquals(Pace(true, 90f), PaceClient.parseRow(c.apply { moveToFirst() }))
    }

    /**
     * A [Cursor] over a single named row, faked via a JDK dynamic proxy. Android framework classes
     * (MatrixCursor) are not mocked in plain unit tests, so we implement only the reads parseRow uses
     * (getColumnIndexOrThrow / getInt / getFloat) and default the rest — no Android body is invoked.
     */
    private fun rowCursor(columns: List<String>, row: List<Any>): Cursor =
        Proxy.newProxyInstance(
            Cursor::class.java.classLoader,
            arrayOf(Cursor::class.java),
        ) { _, method, args ->
            when (method.name) {
                "getColumnIndexOrThrow", "getColumnIndex" -> columns.indexOf(args[0] as String)
                "getInt" -> (row[args[0] as Int] as Number).toInt()
                "getFloat" -> (row[args[0] as Int] as Number).toFloat()
                "moveToFirst", "moveToNext" -> true
                else -> when (method.returnType) {
                    java.lang.Boolean.TYPE -> false
                    java.lang.Integer.TYPE, java.lang.Short.TYPE -> 0
                    java.lang.Long.TYPE -> 0L
                    java.lang.Float.TYPE -> 0f
                    java.lang.Double.TYPE -> 0.0
                    else -> null
                }
            }
        } as Cursor
}
