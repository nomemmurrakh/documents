package io.github.nomemmurrakh.documents

import cocoapods.MMKV.MMKV
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.create
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class)
internal class MmkvStorage(private val mmkv: MMKV) : Storage {

    override fun getBytes(key: String): ByteArray? =
        mmkv.getDataForKey(key)?.toByteArray()

    override fun putBytes(key: String, value: ByteArray) {
        mmkv.setData(value.toNSData(), forKey = key)
    }

    override fun remove(key: String) {
        mmkv.removeValueForKey(key)
    }

    override fun contains(key: String): Boolean = mmkv.containsKey(key)

    override fun keys(prefix: String): List<String> =
        mmkv.allKeys()
            .filterIsInstance<String>()
            .filter { it.startsWith(prefix) }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun ByteArray.toNSData(): NSData = memScoped {
    NSData.create(bytes = allocArrayOf(this@toNSData), length = size.toULong())
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    val bytes = ByteArray(size)
    if (size > 0) {
        bytes.usePinned { pinned ->
            memcpy(pinned.addressOf(0), this.bytes, length)
        }
    }
    return bytes
}
