package com.nomemmurrakh.documents.sample.session

import kotlinx.serialization.Serializable

@Serializable
data class Session(
    val userId: String,
    val displayName: String,
    val authToken: String,
)
