package at.plankt0n.streamplay.helper

import java.util.concurrent.atomic.AtomicInteger

object ViewIdGenerator {
    private val counter = AtomicInteger(1)

    fun generate(): Int = counter.incrementAndGet()
}
