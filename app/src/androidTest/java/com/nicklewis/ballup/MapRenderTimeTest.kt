package com.nicklewis.ballup

import android.os.Build
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class MapRenderTimeTest {

    // (optional) avoid permission dialog delays
    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        android.Manifest.permission.ACCESS_FINE_LOCATION,
        android.Manifest.permission.ACCESS_COARSE_LOCATION
    )

    @Test
    fun mapRenderTime_budgetedByEnvironment() {
        val targetMs = if (isEmulator()) 10_000L else 200L   // relax on emulator
        runWithTarget(targetMs)
    }

    private fun runWithTarget(targetMs: Long) {
        val latch = CountDownLatch(1)
        PerfEvents.mapLoadedLatch = latch

        val t0 = SystemClock.elapsedRealtime()

        ActivityScenario.launch(MainActivity::class.java).use {
            // give emulator more headroom the first run
            val ok = latch.await(20, TimeUnit.SECONDS)
            val dt = SystemClock.elapsedRealtime() - t0

            println("BallUp: map first render in ${dt} ms")

            assertTrue("Map did not finish loading in time", ok)
            assertTrue("Map first render took ${dt} ms (expected < $targetMs ms)", dt < targetMs)
        }

        PerfEvents.mapLoadedLatch = null
    }

    private fun isEmulator(): Boolean {
        val f = Build.FINGERPRINT
        val m = Build.MODEL
        return f.startsWith("generic") ||
                f.lowercase().contains("emulator") ||
                m.lowercase().contains("sdk_gphone")
    }
}
