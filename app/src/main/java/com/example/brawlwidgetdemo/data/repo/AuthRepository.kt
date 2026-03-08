package com.example.brawlwidgetdemo.data.repo

import com.example.brawlwidgetdemo.data.db.UserAccountDao
import com.example.brawlwidgetdemo.data.db.UserAccountEntity
import com.example.brawlwidgetdemo.data.network.AuthAccountDto
import com.example.brawlwidgetdemo.data.network.AuthCredentialsRequest
import com.example.brawlwidgetdemo.data.network.AuthTagRequest
import com.example.brawlwidgetdemo.data.network.AuthVerifiedRequest
import com.example.brawlwidgetdemo.data.network.AuthResponse
import com.example.brawlwidgetdemo.data.network.ProxyApiService
import kotlinx.coroutines.flow.Flow
import retrofit2.Response
import java.security.MessageDigest

class AuthRepository(
    private val proxyApi: ProxyApiService,
    private val userAccountDao: UserAccountDao,
    private val sessionTokenStore: SessionTokenStore
) {
    fun observeAccount(): Flow<UserAccountEntity?> = userAccountDao.observe()

    suspend fun syncSession() {
        val local = userAccountDao.get() ?: return
        val token = readPersistedSessionToken(local) ?: return
        val response = runCatching { proxyApi.getCurrentAccount(authHeader(token)) }.getOrNull()
            ?: return

        if (response.isSuccessful) {
            val body = response.body() ?: return
            saveAccount(body.account, body.sessionToken ?: token, isLoggedIn = true)
        } else {
            userAccountDao.upsert(
                local.copy(
                    isLoggedIn = false,
                    updatedAt = System.currentTimeMillis()
                )
            )
            sessionTokenStore.write(null)
        }
    }

    suspend fun register(username: String, password: String): Result<Unit> {
        val login = username.trim()
        if (login.length < 3) {
            return Result.failure(IllegalArgumentException("Логин должен быть не короче 3 символов"))
        }
        if (password.length < 4) {
            return Result.failure(IllegalArgumentException("Пароль должен быть не короче 4 символов"))
        }

        val response = runCatching {
            proxyApi.register(AuthCredentialsRequest(username = login, passwordHash = hash(password)))
        }.getOrElse { return Result.failure(it) }

        if (!response.isSuccessful) {
            return Result.failure(IllegalStateException(parseError(response, "Не удалось создать аккаунт")))
        }

        val body = response.body()
            ?: return Result.failure(IllegalStateException("Пустой ответ сервера"))
        saveAccount(body.account, body.sessionToken, isLoggedIn = true)
        return Result.success(Unit)
    }

    suspend fun login(username: String, password: String): Result<Unit> {
        val login = username.trim()
        if (login.length < 3) {
            return Result.failure(IllegalArgumentException("Логин должен быть не короче 3 символов"))
        }
        if (password.length < 4) {
            return Result.failure(IllegalArgumentException("Пароль должен быть не короче 4 символов"))
        }

        val response = runCatching {
            proxyApi.login(AuthCredentialsRequest(username = login, passwordHash = hash(password)))
        }.getOrElse { return Result.failure(it) }

        if (!response.isSuccessful) {
            return Result.failure(IllegalArgumentException(parseError(response, "Неверный логин или пароль")))
        }

        val body = response.body()
            ?: return Result.failure(IllegalStateException("Пустой ответ сервера"))
        saveAccount(body.account, body.sessionToken, isLoggedIn = true)
        return Result.success(Unit)
    }

    suspend fun logout() {
        val account = userAccountDao.get() ?: return
        readPersistedSessionToken(account)?.let { token ->
            runCatching { proxyApi.setVerified(authHeader(token), AuthVerifiedRequest(false)) }
            runCatching { proxyApi.logout(authHeader(token)) }
        }
        sessionTokenStore.write(null)
        userAccountDao.upsert(
            account.copy(
                isLoggedIn = false,
                isVerified = false,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun setVerified(verified: Boolean) {
        val account = userAccountDao.get() ?: return
        val token = readPersistedSessionToken(account) ?: return
        val response = runCatching {
            proxyApi.setVerified(authHeader(token), AuthVerifiedRequest(verified))
        }.getOrNull() ?: return
        val body = response.body() ?: return
        saveAccount(body.account, token, isLoggedIn = true)
    }

    suspend fun linkPlayerTag(tag: String) {
        val account = userAccountDao.get() ?: return
        val token = readPersistedSessionToken(account) ?: return
        val response = runCatching {
            proxyApi.linkTag(authHeader(token), AuthTagRequest(tag))
        }.getOrNull() ?: return
        val body = response.body() ?: return
        saveAccount(body.account, token, isLoggedIn = true)
    }

    private fun hash(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return bytes.joinToString(separator = "") { b -> "%02x".format(b) }
    }

    private suspend fun saveAccount(account: AuthAccountDto, sessionToken: String?, isLoggedIn: Boolean) {
        sessionTokenStore.write(sessionToken)
        userAccountDao.upsert(
            UserAccountEntity(
                id = 1,
                username = account.username,
                sessionToken = null,
                isLoggedIn = isLoggedIn,
                linkedPlayerTag = account.linkedPlayerTag,
                isVerified = account.isVerified,
                createdAt = account.createdAt,
                updatedAt = account.updatedAt
            )
        )
    }

    private suspend fun readPersistedSessionToken(account: UserAccountEntity): String? {
        val secureToken = sessionTokenStore.read()
        if (!secureToken.isNullOrBlank()) {
            if (!account.sessionToken.isNullOrBlank()) {
                userAccountDao.upsert(account.copy(sessionToken = null))
            }
            return secureToken
        }

        val legacyToken = account.sessionToken ?: return null
        sessionTokenStore.write(legacyToken)
        userAccountDao.upsert(account.copy(sessionToken = null))
        return legacyToken
    }

    private fun authHeader(sessionToken: String): String = "Bearer $sessionToken"

    private fun parseError(response: Response<*>, fallback: String): String {
        val body = runCatching { response.errorBody()?.string() }.getOrNull().orEmpty()
        val quoted = "\"error\":\""
        val start = body.indexOf(quoted)
        if (start >= 0) {
            val from = start + quoted.length
            val end = body.indexOf('"', from)
            if (end > from) {
                return body.substring(from, end)
            }
        }
        return fallback
    }
}
