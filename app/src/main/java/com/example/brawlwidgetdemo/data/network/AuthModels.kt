package com.example.brawlwidgetdemo.data.network

data class AuthCredentialsRequest(
    val username: String,
    val passwordHash: String
)

data class AuthTagRequest(
    val tag: String
)

data class AuthVerifiedRequest(
    val verified: Boolean
)

data class AuthAccountDto(
    val username: String,
    val linkedPlayerTag: String?,
    val isVerified: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)

data class AuthResponse(
    val sessionToken: String? = null,
    val account: AuthAccountDto
)

data class LogoutResponse(
    val ok: Boolean
)
