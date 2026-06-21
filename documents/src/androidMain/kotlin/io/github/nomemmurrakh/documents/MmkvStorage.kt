package io.github.nomemmurrakh.documents

import com.tencent.mmkv.MMKV

internal class MmkvStorage(private val mmkv: MMKV) : Storage {

    override fun getBytes(key: String): ByteArray? = mmkv.decodeBytes(key)

    override fun putBytes(key: String, value: ByteArray) {
        mmkv.encode(key, value)
    }

    override fun remove(key: String) {
        mmkv.removeValueForKey(key)
    }

    override fun contains(key: String): Boolean = mmkv.containsKey(key)

    override fun keys(prefix: String): List<String> =
        mmkv.allKeys()?.filter { it.startsWith(prefix) } ?: emptyList()
}
