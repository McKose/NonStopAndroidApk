package com.gymapp.data.repository

import com.gymapp.data.local.dao.TransactionDao
import com.gymapp.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FinanceRepository @Inject constructor(
    private val transactionDao: TransactionDao
) {
    fun getAllTransactions(): Flow<List<TransactionEntity>> = transactionDao.getAllTransactions()
    
    fun getTransactionsByPeriod(startTime: Long, endTime: Long): Flow<List<TransactionEntity>> = 
        transactionDao.getTransactionsByPeriod(startTime, endTime)

    fun getTotalIncome(): Flow<Double?> = transactionDao.getTotalIncome()

    fun getTotalExpense(): Flow<Double?> = transactionDao.getTotalExpense()

    suspend fun addTransaction(transaction: TransactionEntity) {
        transactionDao.insertTransaction(transaction)
    }
}
