package com.nicklewis.ballup

import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicLong

object PerfEvents {
    // updated by the app when map finishes loading
    val mapLoadedAtMs = AtomicLong(0L)

    // a latch the test can wait on
    @Volatile var mapLoadedLatch: CountDownLatch? = null

    fun signalMapLoaded() {
        mapLoadedAtMs.set(System.currentTimeMillis())
        mapLoadedLatch?.countDown()
    }
}