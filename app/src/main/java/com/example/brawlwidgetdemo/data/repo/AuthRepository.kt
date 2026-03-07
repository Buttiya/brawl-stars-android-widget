package com.example.brawlwidgetdemo.data.repo

import com.example.brawlwidgetdemo.data.db.UserAccountDao
import com.example.brawlwidgetdemo.data.db.UserAccountEntity
import kotlinx.coroutines.flow.Flow
import java.security.MessageDigest

class AuthRepository(
    private val userAccountDao: UserAccountDao
) {
    fun observeAccount(): Flow<UserAccountEntity?> = userAccountDao.observe()

    suspend fun register(username: String, password: String): Result<Unit> {
        val login = username.trim()
        if (login.length < 3) {
            return Result.failure(IllegalArgumentException("Логин должен быть не короче 3 символов"))
        }
        if (password.length < 4) {
            return Result.failure(IllegalArgumentException("Пароль должен быть не короче 4 символов"))
        }

        val current = userAccountDao.get()
        if (current != null) {
            return Result.failure(IllegalStateException("Аккаунт уже создан"))
        }

        val now = System.currentTimeMillis()
        userAccountDao.upsert(
            UserAccountEntity(
                username = login,
                passwordHash = hash(password),
                isLoggedIn = true,
                linkedPlayerTag = null,
                isVerified = false,
                createdAt = now,
                updatedAt = now
            )
        )
        return Result.success(Unit)
    }

    suspend fun login(username: String, password: String): Result<Unit> {
        val account = userAccountDao.get()
            ?: return Result.failure(IllegalStateException("Сначала зарегистрируйте аккаунт"))

        if (!account.username.equals(username.trim(), ignoreCase = true)) {
            return Result.failure(IllegalArgumentException("Неверный логин или пароль"))
        }

        if (account.passwordHash != hash(password)) {
            return Result.failure(IllegalArgumentException("Неверный логин или пароль"))
        }

        userAccountDao.upsert(account.copy(isLoggedIn = true, updatedAt = System.currentTimeMillis()))
        return Result.success(Unit)
    }

    suspend fun logout() {
        val account = userAccountDao.get() ?: return
        userAccountDao.upsert(account.copy(isLoggedIn = false, updatedAt = System.currentTimeMillis()))
    }

    suspend fun setVerified(verified: Boolean) {
        val account = userAccountDao.get() ?: return
        userAccountDao.upsert(account.copy(isVerified = verified, updatedAt = System.currentTimeMillis()))
    }

    suspend fun linkPlayerTag(tag: String) {
        val account = userAccountDao.get() ?: return
        userAccountDao.upsert(account.copy(linkedPlayerTag = tag, updatedAt = System.currentTimeMillis()))
    }

    private fun hash(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return bytes.joinToString(separator = "") { b -> "%02x".format(b) }
    }
}
