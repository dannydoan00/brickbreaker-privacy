package com.boltgame.brickbreakerroguelite.data.model

data class User(
    val id: String = "",
    val displayName: String = "",
    val email: String = "",
    val isGuest: Boolean = true
)