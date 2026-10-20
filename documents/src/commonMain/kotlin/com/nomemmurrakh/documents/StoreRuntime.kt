package com.nomemmurrakh.documents

import kotlinx.atomicfu.locks.ReentrantLock
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock

internal class StoreRuntime(val storage: Storage) {

    val changes: ChangeBus = ChangeBus()

    private val guard = reentrantLock()
    private val stripes = arrayOfNulls<ReentrantLock>(STRIPE_COUNT)

    fun lockFor(key: String): ReentrantLock {
        val index = stripeIndex(key)
        return guard.withLock {
            stripes[index] ?: reentrantLock().also { stripes[index] = it }
        }
    }

    internal companion object {
        const val STRIPE_COUNT: Int = 64

        fun stripeIndex(key: String): Int = (key.hashCode() and Int.MAX_VALUE) % STRIPE_COUNT
    }
}

internal object Runtime {

    private val guard = reentrantLock()
    private val stores = mutableMapOf<String, StoreRuntime>()

    fun store(id: String, openStorage: () -> Storage): StoreRuntime = guard.withLock {
        stores.getOrPut(id) { StoreRuntime(openStorage()) }
    }
}
