package com.maibill.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY kind, sortOrder")
    fun all(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE kind = :kind ORDER BY sortOrder")
    fun byKind(kind: String): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories LIMIT 1")
    suspend fun first(): CategoryEntity?

    @Query("SELECT * FROM categories WHERE kind = 'SAVING' LIMIT 1")
    suspend fun savingCategory(): CategoryEntity?

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    @Insert
    suspend fun insert(c: CategoryEntity): Long

    @Update
    suspend fun update(c: CategoryEntity)

    @Delete
    suspend fun delete(c: CategoryEntity)

    @Query("SELECT COUNT(*) FROM categories WHERE id = :id")
    suspend fun exists(id: Long): Int
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions WHERE deletedAt = 0 AND date BETWEEN :from AND :to ORDER BY date DESC, id DESC")
    fun monthTx(from: Long, to: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE deletedAt = 0 ORDER BY date DESC, id DESC")
    suspend fun allOnce(): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE deletedAt > 0 ORDER BY deletedAt DESC")
    fun deletedTx(): Flow<List<TransactionEntity>>

    @Query("UPDATE transactions SET deletedAt = :ts WHERE id = :id")
    suspend fun softDelete(id: Long, ts: Long)

    @Query("UPDATE transactions SET deletedAt = 0 WHERE id = :id")
    suspend fun restore(id: Long)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun purge(id: Long)

    @Query("DELETE FROM transactions WHERE deletedAt > 0 AND deletedAt < :cutoff")
    suspend fun purgeOld(cutoff: Long)

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun byId(id: Long): TransactionEntity?

    @Update
    suspend fun update(t: TransactionEntity)

    @Query("SELECT * FROM transactions WHERE deletedAt = 0 AND type IN ('SAVE','UNSAVE') ORDER BY id DESC LIMIT :n")
    fun recentSavings(n: Int): Flow<List<TransactionEntity>>

    @Query(
        "SELECT categoryId AS categoryId, COALESCE(SUM(amount),0) AS total FROM transactions " +
            "WHERE deletedAt = 0 AND type = 'EXPENSE' AND date BETWEEN :from AND :to GROUP BY categoryId"
    )
    fun categoryTotals(from: Long, to: Long): Flow<List<CatTotal>>

    @Query(
        "SELECT date AS day, COALESCE(SUM(amount),0) AS total FROM transactions " +
            "WHERE deletedAt = 0 AND type = 'EXPENSE' AND date BETWEEN :from AND :to GROUP BY date ORDER BY date"
    )
    fun dailyTotals(from: Long, to: Long): Flow<List<DayTotal>>

    @Query(
        "SELECT goalId AS goalId, COALESCE(SUM(CASE WHEN type='SAVE' THEN amount ELSE -amount END),0) AS total " +
            "FROM transactions WHERE deletedAt = 0 AND goalId IS NOT NULL GROUP BY goalId"
    )
    fun goalSums(): Flow<List<GoalSum>>

    @Query("SELECT COALESCE(SUM(amount),0) FROM transactions WHERE deletedAt = 0 AND type = 'SAVE' AND date BETWEEN :from AND :to")
    fun savedInMonth(from: Long, to: Long): Flow<Long>

    @Query(
        "SELECT COALESCE(SUM(CASE WHEN type='SAVE' THEN amount ELSE -amount END),0) FROM transactions " +
            "WHERE deletedAt = 0 AND type IN ('SAVE','UNSAVE') AND date BETWEEN :from AND :to"
    )
    fun netSavedInMonth(from: Long, to: Long): Flow<Long>

    @Query("SELECT COALESCE(SUM(amount),0) FROM transactions WHERE deletedAt = 0 AND type = :type AND date BETWEEN :from AND :to")
    fun monthSum(type: String, from: Long, to: Long): Flow<Long>

    @Query("SELECT COUNT(*) FROM transactions WHERE deletedAt = 0 AND categoryId = :categoryId")
    suspend fun countByCategory(categoryId: Long): Int

    @Query("SELECT categoryId AS categoryId, COUNT(*) AS count FROM transactions WHERE deletedAt = 0 GROUP BY categoryId")
    fun categoryCounts(): Flow<List<CatCount>>

    @Query("SELECT COUNT(*) FROM transactions WHERE deletedAt = 0 AND goalId = :goalId")
    suspend fun countByGoal(goalId: Long): Int

    @Insert
    suspend fun insert(t: TransactionEntity): Long

    @Delete
    suspend fun delete(t: TransactionEntity)
}

@Dao
interface SavingsGoalDao {
    @Query("SELECT * FROM savings_goals ORDER BY createdAt")
    fun all(): Flow<List<SavingsGoalEntity>>

    @Query("SELECT * FROM savings_goals WHERE id = :id")
    suspend fun byId(id: Long): SavingsGoalEntity?

    @Insert
    suspend fun insert(g: SavingsGoalEntity): Long

    @Update
    suspend fun update(g: SavingsGoalEntity)

    @Delete
    suspend fun delete(g: SavingsGoalEntity)
}

@Dao
interface SettingsDao {
    @Query("SELECT value FROM settings WHERE `key` = :key")
    fun stringFlow(key: String): Flow<String?>

    @Query("SELECT value FROM settings WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(s: SettingsEntity)
}
