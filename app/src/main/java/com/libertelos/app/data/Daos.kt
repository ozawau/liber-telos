package com.libertelos.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY type, sortOrder, id")
    fun observeAll(): Flow<List<Category>>

    @Query("SELECT * FROM categories WHERE type = :type ORDER BY sortOrder, id")
    fun observeByType(type: TxnType): Flow<List<Category>>

    @Query("SELECT * FROM categories WHERE parentId = :parentId ORDER BY sortOrder, id")
    suspend fun getChildren(parentId: Long): List<Category>

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM categories WHERE type = :type AND parentId IS NULL")
    suspend fun maxParentSortOrder(type: TxnType): Int

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM categories WHERE parentId = :parentId")
    suspend fun maxChildSortOrder(parentId: Long): Int

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(category: Category): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(categories: List<Category>)

    @Update
    suspend fun update(category: Category)

    @Delete
    suspend fun delete(category: Category)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM categories WHERE parentId = :parentId")
    suspend fun deleteChildren(parentId: Long)

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: Long): Category?

    @Query(
        """
        SELECT * FROM categories
        WHERE type = :type AND parentId IS NULL
        ORDER BY sortOrder, id
        """
    )
    suspend fun listParents(type: TxnType): List<Category>

    @Query(
        """
        SELECT * FROM categories
        WHERE type = :type AND parentId IS NOT NULL AND name = :name
        LIMIT 1
        """
    )
    suspend fun findLeafByName(type: TxnType, name: String): Category?

    @Query(
        """
        SELECT * FROM categories
        WHERE isProtected = 1 AND type = :type AND parentId IS NOT NULL
        LIMIT 1
        """
    )
    suspend fun getProtectedLeaf(type: TxnType): Category?

    @Query("UPDATE categories SET defaultAccountId = NULL WHERE defaultAccountId = :accountId")
    suspend fun clearDefaultAccount(accountId: Long)
}

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY kind, createdAt")
    fun observeAll(): Flow<List<Account>>

    @Query("SELECT * FROM accounts WHERE hidden = 0 ORDER BY kind, createdAt")
    fun observeVisible(): Flow<List<Account>>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getById(id: Long): Account?

    @Query("SELECT * FROM accounts WHERE name LIKE '%' || :keyword || '%' LIMIT 1")
    suspend fun findByNameContains(keyword: String): Account?

    @Query("SELECT COUNT(*) FROM accounts")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(account: Account): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(accounts: List<Account>)

    @Update
    suspend fun update(account: Account)

    @Delete
    suspend fun delete(account: Account)
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY occurredAt DESC")
    fun observeAll(): Flow<List<Transaction>>

    @Query(
        """
        SELECT * FROM transactions
        WHERE occurredAt >= :startMs AND occurredAt < :endMs
        ORDER BY occurredAt DESC
        """
    )
    fun observeInRange(startMs: Long, endMs: Long): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Long): Transaction?

    @Insert
    suspend fun insert(transaction: Transaction): Long

    @Update
    suspend fun update(transaction: Transaction)

    @Delete
    suspend fun delete(transaction: Transaction)

    @Query(
        """
        SELECT COALESCE(SUM(amountCents), 0) FROM transactions
        WHERE type = :type AND occurredAt >= :startMs AND occurredAt < :endMs
        """
    )
    fun observeSumByType(type: TxnType, startMs: Long, endMs: Long): Flow<Long>

    @Query(
        """
        SELECT COALESCE(SUM(amountCents), 0) FROM transactions
        WHERE type = :type AND linkedTransactionId = :linkedId
        """
    )
    suspend fun sumByLinked(type: TxnType, linkedId: Long): Long

    @Query(
        """
        SELECT accountId FROM transactions
        WHERE type = :type AND accountId IS NOT NULL
        ORDER BY occurredAt DESC LIMIT 1
        """
    )
    suspend fun latestAccountIdByType(type: TxnType): Long?

    @Query("SELECT * FROM transactions WHERE linkedTransactionId = :linkedId")
    suspend fun listLinked(linkedId: Long): List<Transaction>

    @Query("DELETE FROM transactions WHERE linkedTransactionId = :linkedId")
    suspend fun deleteLinkedTo(linkedId: Long)

    @Query("UPDATE transactions SET categoryId = :toId WHERE categoryId = :fromId")
    suspend fun reassignCategory(fromId: Long, toId: Long)

    @Query("UPDATE transactions SET categoryId = NULL WHERE categoryId = :categoryId")
    suspend fun clearCategory(categoryId: Long)

    @Query("UPDATE transactions SET categoryId = NULL WHERE categoryId IN (:categoryIds)")
    suspend fun clearCategories(categoryIds: List<Long>)

    @Query("UPDATE transactions SET accountId = NULL WHERE accountId = :accountId")
    suspend fun clearAccount(accountId: Long)

    @Query(
        """
        UPDATE transactions SET fromAccountId = NULL WHERE fromAccountId = :accountId
        """
    )
    suspend fun clearFromAccount(accountId: Long)

    @Query("UPDATE transactions SET toAccountId = NULL WHERE toAccountId = :accountId")
    suspend fun clearToAccount(accountId: Long)
}
