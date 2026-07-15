package com.pikmin.walksim.session

import com.pikmin.model.LatLng
import com.pikmin.model.SimSample

/**
 * The platform location surface a walk session drives, factored out so [WalkSessionController] is a pure,
 * JVM-unit-testable orchestrator (tests supply a fake). The real implementation is
 * [com.pikmin.walksim.LocationInjector]; its method names map 1:1 via thin aliases.
 */
interface LocationSink {
    /** Engage mock mode. @return false if this app is not the selected mock-location app (AC-16). == LocationInjector.start(). */
    fun engage(): Boolean

    /** Push a stationary holding fix at [pos] to cover a fetch gap (AC-12). == LocationInjector.holdAt(pos). */
    fun hold(pos: LatLng)

    /** Push one fully-populated moving fix mapped from [sample]. */
    fun push(sample: SimSample)

    /** AC-15: restore the real location stack (remove test providers, disengage fused mock mode). */
    fun restore()
}
