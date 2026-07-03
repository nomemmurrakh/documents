package com.nomemmurrakh.documents

import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock

internal class InMemoryStorage : Storage {

    private val lock = reentrantLock()
    private val map = mutableMapOf<String, ByteArray>()

    override fun getBytes(key: String): ByteArray? = lock.withLock {
        map[key]?.copyOf()
    }

    override fun putBytes(key: String, value: ByteArray): Unit = lock.withLock {
        map[key] = value.copyOf()
    }

    override fun remove(key: String): Unit = lock.withLock {
        map.remove(key)
        Unit
    }

    override fun contains(key: String): Boolean = lock.withLock {
        map.containsKey(key)
    }

    override fun keys(prefix: String): List<String> = lock.withLock {
        map.keys.filter { it.startsWith(prefix) }
    }
}
