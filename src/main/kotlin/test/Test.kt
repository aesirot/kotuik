package test

import java.time.LocalDateTime
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.absoluteValue

fun main() {
    val thread = Thread {
        Thread.sleep(10000)
    }
    thread.start()
    for (i in 1..12) {
        Thread.sleep(1000)
        println("${thread.isAlive}")
    }
}