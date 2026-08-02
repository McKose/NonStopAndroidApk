package com.gymapp.data.local.dao

import androidx.room.*
import com.gymapp.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY date DESC")
    fun getAllTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE date >= :startTime AND date <= :endTime ORDER BY date DESC")
    fun getTransactionsByPeriod(startTime: Long, endTime: Long): Flow<List<TransactionEntity>>

    @Insert
    suspend fun insertTransaction(transaction: TransactionEntity)

    @Query("SELECT SUM(amount) FROM transactions WHERE type = 'INCOME'")
    fun getTotalIncome(): Flow<Double?>

    @Query("SELECT SUM(amount) FROM transactions WHERE type = 'EXPENSE'")
    fun getTotalExpense(): Flow<Double?>
    
    @Query("SELECT * FROM transactions WHERE category = :category")
    fun getTransactionsByCategory(category: String): Flow<List<TransactionEntity>>

    // ─── Otomatik vergi kaydı için yardımcılar ────────────────────────────────
    @Query("SELECT * FROM transactions ORDER BY date ASC")
    suspend fun getAllTransactionsOnce(): List<TransactionEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM transactions WHERE description = :desc LIMIT 1)")
    suspend fun existsByDescription(desc: String): Boolean

    @Query("SELECT date FROM transactions WHERE type = 'INCOME' ORDER BY date ASC LIMIT 1")
    suspend fun getEarliestIncomeDate(): Long?

    @Query("UPDATE transactions SET isPending = 0, date = :paidAt WHERE id = :id")
    suspend fun markTransactionPaid(id: Long, paidAt: Long)
}
