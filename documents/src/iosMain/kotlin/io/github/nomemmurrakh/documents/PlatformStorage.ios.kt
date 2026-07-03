package io.github.nomemmurrakh.documents

import cocoapods.MMKV.MMKV
import cocoapods.MMKV.MMKVSingleProcess
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

private val initLock = reentrantLock()
private var initialized = false

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
internal actual fun ensureInitialized() {
    if (initialized) return
    initLock.withLock {
        if (initialized) return
        MMKV.initializeMMKV(mmkvRootDir())
        initialized = true
    }
}

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
private fun mmkvRootDir(): String {
    val documents = NSSearchPathForDirectoriesInDomains(
        NSDocumentDirectory,
        NSUserDomainMask,
        true,
    ).firstOrNull() as? String ?: error("Unable to resolve the iOS Documents directory for MMKV")
    return "$documents/MMKV"
}

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
internal actual fun platformStorage(name: String): Storage {
    val mmkv = requireNotNull(MMKV.mmkvWithID(name, mode = MMKVSingleProcess)) {
        "MMKV.mmkvWithID returned null for '$name'"
    }
    return MmkvStorage(mmkv)
}
