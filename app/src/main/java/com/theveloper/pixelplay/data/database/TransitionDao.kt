package com.theveloper.pixelplay.data.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for transition rules.
 */
@Dao
interface TransitionDao {

    /**
     * Inserts a new rule or updates an existing one if it matches a unique index.
     */
    @Upsert
    suspend fun setRule(rule: TransitionRuleEntity)

    @Upsert
    suspend fun setRules(rules: List<TransitionRuleEntity>)

    /**
     * Gets the default transition rule for a given playlist.
     * A default rule is one where fromTrackId and toTrackId are both null.
     */
    @Query("SELECT * FROM transition_rules WHERE playlistId = :playlistId AND fromTrackId IS NULL AND toTrackId IS NULL")
    fun getPlaylistDefaultRule(playlistId: String): Flow<TransitionRuleEntity?>

    /**
     * Gets a specific transition rule between two tracks in a playlist.
     */
    @Query("SELECT * FROM transition_rules WHERE playlistId = :playlistId AND fromTrackId = :fromTrackId AND toTrackId = :toTrackId")
    fun getSpecificRule(playlistId: String, fromTrackId: String, toTrackId: String): Flow<TransitionRuleEntity?>

    /**
     * Gets all rules (default and specific) for a given playlist.
     * Useful for a settings screen.
     */
    @Query("SELECT * FROM transition_rules WHERE playlistId = :playlistId")
    fun getAllRulesForPlaylist(playlistId: String): Flow<List<TransitionRuleEntity>>

    /**
     * Deletes a rule by its primary key.
     */
    @Query("DELETE FROM transition_rules WHERE id = :ruleId")
    suspend fun deleteRule(ruleId: Long)

    /**
     * Deletes the default rule for a given playlist.
     */
    @Query("DELETE FROM transition_rules WHERE playlistId = :playlistId AND fromTrackId IS NULL AND toTrackId IS NULL")
    suspend fun deletePlaylistDefaultRule(playlistId: String)

    @Query("SELECT * FROM transition_rules")
    suspend fun getAllRulesOnce(): List<TransitionRuleEntity>

    @Query("DELETE FROM transition_rules")
    suspend fun clearAllRules()

    @Transaction
    suspend fun replaceAllRules(rules: List<TransitionRuleEntity>) {
        clearAllRules()
        if (rules.isNotEmpty()) setRules(rules)
    }
}
