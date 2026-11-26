package com.example.financehome.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.financehome.data.model.MerchantBillRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MerchantBillRuleDao {

    @Query("SELECT * FROM merchant_bill_rules WHERE normalizedMerchant = :normalizedMerchant")
    suspend fun getRule(normalizedMerchant: String): MerchantBillRuleEntity?

    @Query("SELECT * FROM merchant_bill_rules")
    fun getAllRules(): Flow<List<MerchantBillRuleEntity>>

    @Query("SELECT * FROM merchant_bill_rules")
    suspend fun getAllRulesList(): List<MerchantBillRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRule(rule: MerchantBillRuleEntity)

    @Delete
    suspend fun deleteRule(rule: MerchantBillRuleEntity)
}
