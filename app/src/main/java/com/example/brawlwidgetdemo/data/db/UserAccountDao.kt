package com.example.brawlwidgetdemo.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UserAccountDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(account: UserAccountEntity)

    @Query("DELETE FROM user_account")
    suspend fun clear()

    @Query("SELECT * FROM user_account WHERE id = 1")
    fun observe(): Flow<UserAccountEntity?>

    @Query("SELECT * FROM user_account WHERE id = 1")
    suspend fun get(): UserAccountEntity?
}
