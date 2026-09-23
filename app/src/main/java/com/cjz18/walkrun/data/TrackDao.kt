package com.cjz18.walkrun.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
    @Insert
    suspend fun insertTrack(track: TrackEntity): Long

    @Insert
    suspend fun insertPoints(points: List<TrackPointEntity>)

    @Query("SELECT * FROM tracks ORDER BY startedAtEpochMs DESC")
    fun observeTracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE id = :id")
    suspend fun track(id: Long): TrackEntity?

    @Query("SELECT * FROM track_points WHERE trackId = :trackId ORDER BY timeEpochMs ASC, id ASC")
    suspend fun pointsFor(trackId: Long): List<TrackPointEntity>
}
