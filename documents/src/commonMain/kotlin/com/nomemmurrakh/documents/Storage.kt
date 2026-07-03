package com.nomemmurrakh.documents

internal interface Storage {
    fun getBytes(key: String): ByteArray?
    fun putBytes(key: String, value: ByteArray)
    fun remove(key: String)
    fun contains(key: String): Boolean
    fun keys(prefix: String): List<String>
}
