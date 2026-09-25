package com.waytogo.core.scan

/** Monotonic clock abstraction so the positioning core is testable on the JVM. */
fun interface Clock {
    fun nowMs(): Long

    companion object {
        /** JVM-friendly monotonic default; Android supplies elapsedRealtime(). */
        val SYSTEM: Clock = Clock { System.nanoTime() / 1_000_000 }
    }
}
