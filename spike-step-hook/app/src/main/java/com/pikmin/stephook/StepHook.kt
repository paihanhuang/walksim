package com.pikmin.stephook

import android.hardware.Sensor
import android.hardware.SensorEventListener
import android.os.Handler
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Production LSPosed entry (S5). Scoped to two processes, each fed EXACTLY the one sensor it actually
 * registers (verified via `dumpsys sensorservice`):
 *   - `com.nianticlabs.pikmin` registers `STEP_DETECTOR` (FitnessManager) -> drives the game's live /
 *     landing-page count. We feed only `STEP_DETECTOR` here.
 *   - `com.google.android.gms` registers `STEP_COUNTER` (Google Fit's LocalSensorAdapter) -> drives the
 *     Weekly Challenge + the count reconciliation baseline. We feed only `STEP_COUNTER` here.
 *
 * The GMS target (dropped in the original S5 design) is restored: the challenge reads Google Fit — a
 * DIFFERENT process from Pikmin — so a Pikmin-only hook can never move it, which is exactly why the
 * challenge stalled and the landing page reset to the unmoving hardware counter.
 *
 * Chokepoint: `android.hardware.SystemSensorManager` — every `SensorManager.registerListener(...)`
 * overload funnels through `registerListenerImpl`, and every `unregisterListener(...)` through
 * `unregisterListenerImpl`. Feeding is per-process narrow (one sensor type each), so we never inject a
 * sensor a process didn't ask for.
 */
class StepHook : IXposedHookLoadPackage {

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        // Feed each process only the sensor it registers: Pikmin -> detector, GMS -> counter.
        val wantedType = WANTED_SENSOR[lpparam.packageName] ?: return
        XposedBridge.log("StepHook: loaded in ${lpparam.packageName} (feeds sensor type=$wantedType)")
        try {
            val ssm = XposedHelpers.findClass(
                "android.hardware.SystemSensorManager", lpparam.classLoader,
            )
            // API 29 signature:
            // boolean registerListenerImpl(SensorEventListener, Sensor, int delayUs,
            //                              Handler, int maxReportLatencyUs, int reservedFlags)
            XposedHelpers.findAndHookMethod(
                ssm, "registerListenerImpl",
                SensorEventListener::class.java,
                Sensor::class.java,
                Integer.TYPE,
                Handler::class.java,
                Integer.TYPE,
                Integer.TYPE,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        // Defensive: an exception escaping a hook callback in the GMS persistent process
                        // is far costlier than in :app, so swallow + log rather than let it propagate.
                        try {
                            val sensor = param.args[1] as? Sensor ?: return
                            if (sensor.type != wantedType) return
                            val listener = param.args[0] as? SensorEventListener ?: return
                            if (StepInjector.isInternalReader(listener)) return // skip our own base reader
                            val handler = param.args[3] as? Handler // captured; null => main looper
                            XposedBridge.log("StepHook: registered sensor type=${sensor.type} in ${lpparam.packageName}")
                            StepInjector.attach(listener, sensor, handler)
                        } catch (t: Throwable) {
                            XposedBridge.log("StepHook: register hook error in ${lpparam.packageName}: $t")
                        }
                    }
                },
            )
            // Retire the feed on unregister (thread-leak fix, QT-1). hookAllMethods is signature-robust
            // across the unregisterListenerImpl overload(s).
            XposedBridge.hookAllMethods(
                ssm, "unregisterListenerImpl",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val listener = param.args.getOrNull(0) as? SensorEventListener ?: return
                            val sensor = param.args.getOrNull(1) as? Sensor // null => all of this listener's feeds
                            StepInjector.detach(listener, sensor)
                        } catch (t: Throwable) {
                            XposedBridge.log("StepHook: unregister hook error in ${lpparam.packageName}: $t")
                        }
                    }
                },
            )
            XposedBridge.log("StepHook: hooked register/unregister in ${lpparam.packageName}")
        } catch (t: Throwable) {
            XposedBridge.log("StepHook: hook FAILED in ${lpparam.packageName}: $t")
        }
    }

    private companion object {
        // The one sensor type to feed per process. Pikmin: game STEP_DETECTOR (landing page).
        // GMS: Google Fit STEP_COUNTER (Weekly Challenge + reconcile).
        val WANTED_SENSOR = mapOf(
            "com.nianticlabs.pikmin" to Sensor.TYPE_STEP_DETECTOR,
            "com.google.android.gms" to Sensor.TYPE_STEP_COUNTER,
        )
    }
}
