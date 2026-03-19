package com.theveloper.pixelplay.data.backup.validation

import com.theveloper.pixelplay.data.backup.model.BackupSection
import com.theveloper.pixelplay.data.backup.model.BackupValidationResult
import com.theveloper.pixelplay.data.backup.model.Severity
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ModuleSchemaValidatorTest {

    private val sanitizer = ContentSanitizer()
    private val validator = ModuleSchemaValidator(sanitizer)

    @Test
    fun `valid favorites array passes validation`() {
        val payload = """[{"songId": 123, "addedAt": 1700000000000}]"""
        val result = validator.validate(BackupSection.FAVORITES, payload)
        assertTrue(result.isValid())
    }

    @Test
    fun `invalid JSON fails validation`() {
        val payload = "not valid json{"
        val result = validator.validate(BackupSection.FAVORITES, payload)
        assertTrue(result is BackupValidationResult.Invalid)
        val errors = (result as BackupValidationResult.Invalid).fatalErrors
        assertTrue(errors.any { it.code == "INVALID_JSON" })
    }

    @Test
    fun `non-array module fails validation`() {
        val payload = """{"key": "value"}"""
        val result = validator.validate(BackupSection.FAVORITES, payload)
        assertTrue(result is BackupValidationResult.Invalid)
        assertTrue((result as BackupValidationResult.Invalid).fatalErrors.any { it.code == "NOT_ARRAY" })
    }

    @Test
    fun `favorites with invalid songId emits warning`() {
        val payload = """[{"songId": 0, "addedAt": 1700000000000}]"""
        val result = validator.validate(BackupSection.FAVORITES, payload)
        assertTrue(result is BackupValidationResult.Invalid)
        val warnings = (result as BackupValidationResult.Invalid).warnings
        assertTrue(warnings.any { it.code == "INVALID_SONG_ID" })
    }

    @Test
    fun `favorites with snake case song id pass validation`() {
        val payload = """[{"song_id": "123", "added_at": 1700000000000}]"""
        val result = validator.validate(BackupSection.FAVORITES, payload)
        assertTrue(result.isValid())
    }

    @Test
    fun `artist images with non-https URL emits warning`() {
        val payload = """[{"artistName": "Test", "imageUrl": "http://insecure.com/img.jpg"}]"""
        val result = validator.validate(BackupSection.ARTIST_IMAGES, payload)
        assertTrue(result is BackupValidationResult.Invalid)
        val warnings = (result as BackupValidationResult.Invalid).warnings
        assertTrue(warnings.any { it.code == "INSECURE_URL" })
    }

    @Test
    fun `artist images with valid https URL passes`() {
        val payload = """[{"artistName": "Test", "imageUrl": "https://cdn.example.com/img.jpg"}]"""
        val result = validator.validate(BackupSection.ARTIST_IMAGES, payload)
        assertTrue(result.isValid())
    }

    @Test
    fun `playback history with negative duration emits warning`() {
        val payload = """[{"songId": "123", "timestamp": 1700000000000, "durationMs": -500}]"""
        val result = validator.validate(BackupSection.PLAYBACK_HISTORY, payload)
        assertTrue(result is BackupValidationResult.Invalid)
        val warnings = (result as BackupValidationResult.Invalid).warnings
        assertTrue(warnings.any { it.code == "NEGATIVE_DURATION" })
    }

    @Test
    fun `preference entries with valid types pass`() {
        val payload = """[
            {"key": "theme", "type": "string", "stringValue": "dark"},
            {"key": "count", "type": "int", "intValue": 5},
            {"key": "enabled", "type": "boolean", "booleanValue": true}
        ]"""
        val result = validator.validate(BackupSection.GLOBAL_SETTINGS, payload)
        assertTrue(result.isValid())
    }

    @Test
    fun `preference entries with invalid type emit warning`() {
        val payload = """[{"key": "theme", "type": "invalid_type", "stringValue": "dark"}]"""
        val result = validator.validate(BackupSection.GLOBAL_SETTINGS, payload)
        assertTrue(result is BackupValidationResult.Invalid)
        val warnings = (result as BackupValidationResult.Invalid).warnings
        assertTrue(warnings.any { it.code == "INVALID_PREF_TYPE" })
    }

    @Test
    fun `transitions with out-of-range duration emit warning`() {
        val payload = """[{"fromSongId": "1", "toSongId": "2", "settings": {"durationMs": 50000}}]"""
        val result = validator.validate(BackupSection.TRANSITIONS, payload)
        assertTrue(result is BackupValidationResult.Invalid)
        val warnings = (result as BackupValidationResult.Invalid).warnings
        assertTrue(warnings.any { it.code == "INVALID_TRANSITION_DURATION" })
    }

    @Test
    fun `engagement stats with negative play count emit warning`() {
        val payload = """[{"songId": "123", "playCount": -1, "totalDuration": 0}]"""
        val result = validator.validate(BackupSection.ENGAGEMENT_STATS, payload)
        assertTrue(result is BackupValidationResult.Invalid)
        val warnings = (result as BackupValidationResult.Invalid).warnings
        assertTrue(warnings.any { it.code == "NEGATIVE_PLAY_COUNT" })
    }

    @Test
    fun `engagement stats with missing song id and invalid numbers emit warnings`() {
        val payload = """[{"playCount": "oops", "totalDuration": -5, "lastPlayedTimestamp": "bad"}]"""
        val result = validator.validate(BackupSection.ENGAGEMENT_STATS, payload)
        assertTrue(result is BackupValidationResult.Invalid)
        val warnings = (result as BackupValidationResult.Invalid).warnings
        assertTrue(warnings.any { it.code == "MISSING_SONG_ID" })
        assertTrue(warnings.any { it.code == "INVALID_PLAY_COUNT" })
        assertTrue(warnings.any { it.code == "NEGATIVE_TOTAL_DURATION" })
        assertTrue(warnings.any { it.code == "INVALID_LAST_PLAYED_TIMESTAMP" })
    }

    @Test
    fun `engagement stats with duplicate song ids emit warning`() {
        val payload = """[
            {"songId": "123", "playCount": 1},
            {"songId": "123", "playCount": 2}
        ]"""
        val result = validator.validate(BackupSection.ENGAGEMENT_STATS, payload)
        assertTrue(result is BackupValidationResult.Invalid)
        val warnings = (result as BackupValidationResult.Invalid).warnings
        assertTrue(warnings.any { it.code == "DUPLICATE_SONG_ID" })
    }

    @Test
    fun `empty array passes validation`() {
        val payload = "[]"
        val result = validator.validate(BackupSection.FAVORITES, payload)
        assertTrue(result.isValid())
    }
}
