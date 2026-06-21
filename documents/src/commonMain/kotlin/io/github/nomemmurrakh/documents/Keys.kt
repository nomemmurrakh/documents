package io.github.nomemmurrakh.documents

internal object Keys {

    const val SEPARATOR: String = "::"

    fun field(documentKey: String, fieldName: String): String {
        require(!documentKey.contains(SEPARATOR)) {
            "Document key must not contain the reserved separator '$SEPARATOR': '$documentKey'"
        }
        return "$documentKey$SEPARATOR$fieldName"
    }

    fun prefix(documentKey: String): String {
        require(!documentKey.contains(SEPARATOR)) {
            "Document key must not contain the reserved separator '$SEPARATOR': '$documentKey'"
        }
        return "$documentKey$SEPARATOR"
    }

    fun fieldName(documentKey: String, key: String): String {
        val expectedPrefix = "$documentKey$SEPARATOR"
        require(key.startsWith(expectedPrefix)) {
            "Key '$key' does not belong to document '$documentKey'"
        }
        return key.substring(expectedPrefix.length)
    }
}
