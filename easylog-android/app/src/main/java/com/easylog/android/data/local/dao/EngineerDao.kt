package com.easylog.android.data.local.dao

import androidx.room.*
import com.easylog.android.data.local.entity.EngineerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EngineerDao {

    @Query("SELECT * FROM engineers WHERE isActive = 1 ORDER BY name ASC")
    fun getAllActiveEngineers(): Flow<List<EngineerEntity>>

    @Query("SELECT * FROM engineers WHERE id = :engineerId")
    suspend fun getEngineerById(engineerId: String): EngineerEntity?

    @Query("SELECT * FROM engineers WHERE id = :engineerId")
    fun getEngineerByIdFlow(engineerId: String): Flow<EngineerEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEngineer(engineer: EngineerEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEngineers(engineers: List<EngineerEntity>)

    @Update
    suspend fun updateEngineer(engineer: EngineerEntity)

    @Query("DELETE FROM engineers WHERE id = :engineerId")
    suspend fun deleteEngineer(engineerId: String)

    @Query("DELETE FROM engineers")
    suspend fun deleteAllEngineers()
}
