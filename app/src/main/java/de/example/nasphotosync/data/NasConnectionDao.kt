package de.example.nasphotosync.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NasConnectionDao {
    @Query("SELECT * FROM nas_connections ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<NasConnectionEntity>>

    @Query("SELECT * FROM nas_connections WHERE id = :id")
    suspend fun getById(id: String): NasConnectionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(connection: NasConnectionEntity)

    @Query("DELETE FROM nas_connections WHERE id = :id")
    suspend fun deleteById(id: String)
}
