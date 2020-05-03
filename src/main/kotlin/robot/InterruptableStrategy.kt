package robot

import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.Lock

interface InterruptableStrategy {
    val lockCondition: Condition
    val lock: Lock
    var stop: Boolean
    var success: Boolean
}