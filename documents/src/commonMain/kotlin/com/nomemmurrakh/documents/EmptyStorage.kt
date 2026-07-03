package com.nomemmurrakh.documents

internal object EmptyStorage : Storage {
    override fun getBytes(key: String): ByteArray? = null
    override fun putBytes(key: String, value: ByteArray): Unit =
        throw UnsupportedOperationException("EmptyStorage is read-only")
    override fun remove(key: String) {}
    override fun contains(key: String): Boolean = false
    override fun keys(prefix: String): List<String> = emptyList()
}
