package com.example.financehome.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.financehome.data.model.CategoryRule
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryRuleDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: CategoryRule)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRules(rules: List<CategoryRule>)

    @Update
    suspend fun updateRule(rule: CategoryRule)

    @Delete
    suspend fun deleteRule(rule: CategoryRule)

    @Query("DELETE FROM category_rules WHERE id = :id")
    suspend fun deleteRuleById(id: String)

    @Query("SELECT * FROM category_rules ORDER BY createdAtMillis DESC")
    fun getAllRules(): Flow<List<CategoryRule>>

    @Query("SELECT * FROM category_rules ORDER BY createdAtMillis DESC")
    suspend fun getAllRulesList(): List<CategoryRule>
}
