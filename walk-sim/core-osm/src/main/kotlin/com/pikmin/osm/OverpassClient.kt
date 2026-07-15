package com.pikmin.osm

import com.pikmin.model.LatLng
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.zip.GZIPInputStream
import kotlin.math.cos

/**
 * Fetches raw Overpass `out geom` JSON for a bbox of side ≈ 2R around a centre point (T3.2).
 *
 * java.net.HttpURLConnection only (no OkHttp); gzip; connect 10 s / read 20 s; one retry with backoff;
 * a clear [IOException] on failure. The bbox is coverage only — the circular radius is enforced later by
 * the walker in `:core-sim`, so no clipping is done here.
 */
object OverpassClient {

    private const val ENDPOINT = "https://overpass-api.de/api/interpreter"
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 20_000
    private const val RETRY_BACKOFF_MS = 1_500L
    private const val METERS_PER_DEGREE = 111_320.0

    /** Overpass QL selecting every `highway` way inside the bbox of side ≈ 2·[radiusM] around [center]. */
    fun query(center: LatLng, radiusM: Int): String {
        val dLat = radiusM / METERS_PER_DEGREE
        val dLng = radiusM / (METERS_PER_DEGREE * cos(Math.toRadians(center.lat)))
        val south = center.lat - dLat
        val west = center.lng - dLng
        val north = center.lat + dLat
        val east = center.lng + dLng
        return "[out:json][timeout:25];way[\"highway\"]($south,$west,$north,$east);out geom;"
    }

    /** Blocking fetch of the raw JSON body. One retry with backoff, then a clear [IOException]. */
    fun fetch(center: LatLng, radiusM: Int): String {
        val ql = query(center, radiusM)
        var lastError: Exception? = null
        repeat(2) { attempt ->
            try {
                return request(ql)
            } catch (e: Exception) {
                lastError = e
                if (attempt == 0) Thread.sleep(RETRY_BACKOFF_MS)
            }
        }
        throw IOException("Overpass fetch failed after retry: ${lastError?.message}", lastError)
    }

    private fun request(ql: String): String {
        val conn = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            doOutput = true
            setRequestProperty("Accept-Encoding", "gzip")
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        }
        try {
            conn.outputStream.use { it.write(("data=" + URLEncoder.encode(ql, "UTF-8")).toByteArray()) }
            if (conn.responseCode != HttpURLConnection.HTTP_OK) throw IOException("Overpass HTTP ${conn.responseCode}")
            val stream = if (conn.contentEncoding.equals("gzip", ignoreCase = true)) {
                GZIPInputStream(conn.inputStream)
            } else {
                conn.inputStream
            }
            return stream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
