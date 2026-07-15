package com.pikmin.stephook

import android.app.AndroidAppHelper
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import de.robv.android.xposed.XposedBridge
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Production step driver (S5 / T5.3, T5.4). A single singleton daemon thread (per hooked process) drives every
 * captured step listener:
 *   - polls :app's pace via [PaceClient] (<= 2 Hz, synchronous on this thread; every failure — null app / null
 *     cursor / any throw — degrades to not-playing, never blocks the tick with a value),
 *   - converts pace -> whole pulses via [PaceScheduler] (time-based, fractional-lossless),
 *   - delivers each pulse to `STEP_DETECTOR` listeners as a 1.0 event, and updates `STEP_COUNTER` listeners
 *     with the running cumulative total — both posted to the listener's CAPTURED [Handler] (design "Handler
 *     delivery", never a raw per-listener thread),
 *   - parks (a [TICK_MS] sleep = the only idle wakeup, the presence poll) whenever the walk isn't playing.
 *
 * Feeds BOTH sensors on purpose: `STEP_DETECTOR` (in Pikmin's process) drives the game/landing-page live
 * count, while `STEP_COUNTER` (in the GMS process, read by Google Fit's LocalSensorAdapter) drives the Weekly
 * Challenge and the count-reconciliation baseline. The synthetic counter value = [counterBase] (the real
 * hardware counter, captured once so Fit's day-start delta stays sane) + [cumulativeInjected]. NOTE: Pikmin
 * and GMS are separate processes, each with its OWN [StepInjector] singleton polling the same [PaceProvider];
 * they track the same rate independently (small bounded cross-process drift), not literally the same pulses.
 * (Detector-only — the earlier design — stalled the challenge and reset the daily total.)
 *
 * Thread-leak fix (QT-1): feeds are keyed per (listener, sensor-type) with an [AtomicBoolean] + [WeakReference];
 * [detach] (driven by the `unregisterListenerImpl` hook) and GC both retire a feed, so the module holds exactly
 * ONE thread per hooked process (this driver; [PaceClient] polls synchronously on it — no worker thread) no
 * matter how often Pikmin/GMS re-registers.
 */
object StepInjector {

    private const val TICK_MS = 500L // driver cadence == presence-poll cadence (<= 2 Hz)

    // key = (identityHashCode(listener), sensor.type) — a single listener registered for BOTH types (a common
    // Android idiom) then gets one Feed per type instead of the second silently overwriting the first.
    private data class FeedKey(val listenerId: Int, val sensorType: Int)

    private class Feed(
        val ref: WeakReference<SensorEventListener>,
        val sensor: Sensor,
        val handler: Handler,
        val active: AtomicBoolean = AtomicBoolean(true),
    )

    private val feeds = ConcurrentHashMap<FeedKey, Feed>()
    private val paceClient = PaceClient()
    private val scheduler = PaceScheduler { SystemClock.elapsedRealtime() }
    private val driverStarted = AtomicBoolean(false)

    // STEP_COUNTER is cumulative-since-boot. Synthetic value = counterBase (real hardware counter, captured
    // once so Fit's day-start delta stays sane) + cumulativeInjected (running injected total), monotonic.
    private val counterBase = AtomicLong(-1L)      // -1 = not yet captured
    private val cumulativeInjected = AtomicLong(0L)
    @Volatile private var baseReader: SensorEventListener? = null

    // SensorEvent(int valueSize) is package-private — reach it once via reflection.
    private val sensorEventCtor =
        SensorEvent::class.java.getDeclaredConstructor(Integer.TYPE).apply { isAccessible = true }

    /** True for our own one-shot counter-base reader, so [StepHook] doesn't try to feed it. */
    fun isInternalReader(listener: SensorEventListener): Boolean = listener === baseReader

    /** Register a captured step listener for feeding (idempotent per (listener, sensor-type)). */
    fun attach(listener: SensorEventListener, sensor: Sensor, handler: Handler?) {
        val h = handler ?: Handler(Looper.getMainLooper()) // null handler => SensorManager delivers on main
        feeds[FeedKey(System.identityHashCode(listener), sensor.type)] = Feed(WeakReference(listener), sensor, h)
        if (sensor.type == Sensor.TYPE_STEP_COUNTER) captureCounterBaseIfNeeded()
        XposedBridge.log("StepInjector: attach type=${sensor.type} ${listener.javaClass.name} (feeds=${feeds.size})")
        startDriver()
    }

    /** Retire a listener's feed on unregister (`sensor == null` => all of the listener's feeds). */
    fun detach(listener: SensorEventListener, sensor: Sensor?) {
        val id = System.identityHashCode(listener)
        val removed = feeds.keys.filter { it.listenerId == id && (sensor == null || it.sensorType == sensor.type) }
        // deactivate BEFORE removing: a concurrent deliver() that already holds this feed then sees active=false
        // and skips it, rather than delivering one stray event into a just-unregistered listener.
        for (k in removed) { feeds[k]?.active?.set(false); feeds.remove(k) }
        if (removed.isNotEmpty()) XposedBridge.log("StepInjector: detach ${listener.javaClass.name} (feeds=${feeds.size})")
    }

    /**
     * One-shot read of the real hardware STEP_COUNTER so the synthetic cumulative starts from a sane base
     * (else Fit's `counter - dayStartBaseline` would show a wild today-count). The reader is tagged via
     * [isInternalReader] so [StepHook] skips it, and unregisters itself after the first (on-change) value.
     * Falls back to base 0 if the sensor is absent or registration fails, so the counter feed never wedges.
     */
    private fun captureCounterBaseIfNeeded() {
        if (counterBase.get() >= 0L || baseReader != null) return
        val app = AndroidAppHelper.currentApplication() ?: return
        val sm = app.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
        val counter = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: run { counterBase.compareAndSet(-1L, 0L); return }
        val reader = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                try {
                    val v = if (e.values != null && e.values.isNotEmpty()) e.values[0].toLong() else 0L
                    if (counterBase.compareAndSet(-1L, v)) XposedBridge.log("StepInjector: counter base=$v")
                } catch (t: Throwable) {
                    XposedBridge.log("StepInjector: base read error: $t"); counterBase.compareAndSet(-1L, 0L)
                }
                runCatching { sm.unregisterListener(this) } // one-shot
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        baseReader = reader
        // registerListener SIGNALS FAILURE BY RETURNING false (it does not throw) — so check the boolean too,
        // not just runCatching. On any failure: clear baseReader (else the `baseReader != null` guard above would
        // block every future retry) and fall back to base 0 so the counter feed still runs (base 0 + injected)
        // instead of wedging at -1 forever.
        val registered = runCatching { sm.registerListener(reader, counter, SensorManager.SENSOR_DELAY_FASTEST) }
            .getOrElse { XposedBridge.log("StepInjector: base register threw: $it"); false }
        if (!registered) {
            XposedBridge.log("StepInjector: base register failed — falling back to base 0")
            baseReader = null
            counterBase.compareAndSet(-1L, 0L)
        }
    }

    private fun startDriver() {
        if (!driverStarted.compareAndSet(false, true)) return
        Thread {
            while (true) {
                try {
                    val pace = paceClient.poll()
                    val pulses = scheduler.onTick(pace.playing, pace.stepsPerMin)
                    if (pulses > 0) {
                        cumulativeInjected.addAndGet(pulses.toLong()) // keep counter total in lockstep with detector
                        deliver(pulses)
                    }
                } catch (t: Throwable) {
                    XposedBridge.log("StepInjector: driver error: $t")
                }
                try {
                    Thread.sleep(TICK_MS)
                } catch (ie: InterruptedException) {
                    // keep the process-lifetime driver alive
                }
            }
        }.apply { isDaemon = true; name = "step-driver" }.start()
    }

    private fun deliver(pulses: Int) {
        val base = counterBase.get()
        val counterTotal = if (base >= 0L) base + cumulativeInjected.get() else -1L // -1 until base captured
        val iter = feeds.entries.iterator() // named (not `it`) so the inner onFailure lambdas' `it` = the Throwable
        while (iter.hasNext()) {
            val (_, feed) = iter.next()
            val listener = feed.ref.get()
            if (listener == null || !feed.active.get()) {
                iter.remove() // GC'd or retired -> prune, keeping feed/handler refs bounded
                continue
            }
            val sensor = feed.sensor
            feed.handler.post {
                if (sensor.type == Sensor.TYPE_STEP_COUNTER) {
                    // Cumulative-since-boot: one event carrying the running total (skipped until base known).
                    if (counterTotal >= 0L) runCatching { listener.onSensorChanged(newCounterEvent(sensor, counterTotal)) }
                        .onFailure { XposedBridge.log("StepInjector: counter deliver error: $it") }
                } else {
                    val now = SystemClock.elapsedRealtimeNanos()
                    for (i in 0 until pulses) {
                        // per-pulse guard: a transient failure on one pulse must not drop the rest of this tick
                        runCatching {
                            // distinct, monotonic, <= now timestamps (1 ms apart) — one discrete step each
                            listener.onSensorChanged(newDetectorEvent(sensor, now - (pulses - 1 - i) * 1_000_000L))
                        }.onFailure { XposedBridge.log("StepInjector: deliver error: $it") }
                    }
                }
            }
        }
    }

    private fun newDetectorEvent(sensor: Sensor, timestampNs: Long): SensorEvent {
        val ev = sensorEventCtor.newInstance(1) as SensorEvent
        ev.sensor = sensor
        ev.accuracy = SensorManager.SENSOR_STATUS_ACCURACY_HIGH
        ev.timestamp = timestampNs
        ev.values[0] = 1f // TYPE_STEP_DETECTOR always reports 1.0 (one step)
        return ev
    }

    private fun newCounterEvent(sensor: Sensor, total: Long): SensorEvent {
        val ev = sensorEventCtor.newInstance(1) as SensorEvent
        ev.sensor = sensor
        ev.accuracy = SensorManager.SENSOR_STATUS_ACCURACY_HIGH
        ev.timestamp = SystemClock.elapsedRealtimeNanos()
        ev.values[0] = total.toFloat() // cumulative steps since boot (real base + injected)
        return ev
    }
}
