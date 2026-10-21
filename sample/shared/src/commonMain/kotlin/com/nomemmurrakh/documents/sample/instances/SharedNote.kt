package com.nomemmurrakh.documents.sample.instances

import kotlinx.serialization.Serializable

@Serializable
data class SharedNote(
    val title: String = "",
    val count: Int = 0,
)
