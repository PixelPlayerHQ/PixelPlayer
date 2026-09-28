package com.theveloper.pixelplay.data.backup.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BackupSectionTest {

    @Test
    fun `all sections have unique keys`() {
        val keys = BackupSection.entries.map { it.key }
        assertEquals(keys.size, keys.distinct().size, "BackupSection keys must be unique")
    }

    @Test
    fun `all sections have unique labels`() {
        val labels = BackupSection.entries.map { it.label }
        assertEquals(labels.size, labels.distinct().size, "BackupSection labels must be unique")
    }

    @Test
    fun `fromKey returns correct section for known keys`() {
        assertEquals(BackupSection.PLAYLISTS, BackupSection.fromKey("playlists"))
        assertEquals(BackupSection.GLOBAL_SETTINGS, BackupSection.fromKey("global_settings"))
        assertEquals(BackupSection.FAVORITES, BackupSection.fromKey("favorites"))
        assertEquals(BackupSection.LYRICS, BackupSection.fromKey("lyrics"))
        assertEquals(BackupSection.SEARCH_HISTORY, BackupSection.fromKey("search_history"))
        assertEquals(BackupSection.TRANSITIONS, BackupSection.fromKey("transitions"))
        assertEquals(BackupSection.ENGAGEMENT_STATS, BackupSection.fromKey("engagement_stats"))
        assertEquals(BackupSection.PLAYBACK_HISTORY, BackupSection.fromKey("playback_history"))
        assertEquals(BackupSection.QUICK_FILL, BackupSection.fromKey("quick_fill"))
        assertEquals(BackupSection.ARTIST_IMAGES, BackupSection.fromKey("artist_images"))
        assertEquals(BackupSection.EQUALIZER, BackupSection.fromKey("equalizer"))
        assertEquals(BackupSection.AI_USAGE_LOGS, BackupSection.fromKey("ai_usage_logs"))
    }

    @Test
    fun `fromKey returns null for unknown key`() {
        assertNull(BackupSection.fromKey("nonexistent"))
        assertNull(BackupSection.fromKey(""))
    }

    @Test
    fun `defaultSelection contains all sections`() {
        assertEquals(BackupSection.entries.toSet(), BackupSection.defaultSelection)
    }

    @Test
    fun `there are exactly 12 backup sections`() {
        assertEquals(12, BackupSection.entries.size)
    }

    @Test
    fun `new sections have sinceVersion 3`() {
        assertEquals(3, BackupSection.QUICK_FILL.sinceVersion)
        assertEquals(3, BackupSection.ARTIST_IMAGES.sinceVersion)
        assertEquals(3, BackupSection.EQUALIZER.sinceVersion)
    }

    @Test
    fun `original sections have sinceVersion 1`() {
        val originalSections = listOf(
            BackupSection.PLAYLISTS,
            BackupSection.GLOBAL_SETTINGS,
            BackupSection.FAVORITES,
            BackupSection.LYRICS,
            BackupSection.SEARCH_HISTORY,
            BackupSection.TRANSITIONS,
            BackupSection.ENGAGEMENT_STATS,
            BackupSection.PLAYBACK_HISTORY
        )
        originalSections.forEach { section ->
            assertEquals(1, section.sinceVersion, "${section.label} should have sinceVersion 1")
        }
    }

    @Test
    fun `all sections have non-empty labels and descriptions`() {
        BackupSection.entries.forEach { section ->
            assertTrue(section.label.isNotBlank(), "${section.key} should have a non-empty label")
            assertTrue(section.description.isNotBlank(), "${section.key} should have a non-empty description")
        }
    }

    @Test
    fun `all sections have valid label resource IDs`() {
        val labelResIds = BackupSection.entries.map { it.labelRes }
        BackupSection.entries.forEach { section ->
            assertTrue(section.labelRes != 0, "${section.key} should have a valid labelRes")
        }
        assertEquals(
            labelResIds.size,
            labelResIds.distinct().size,
            "BackupSection labelRes IDs must be unique"
        )
    }

    @Test
    fun `all sections have valid description resource IDs`() {
        val descriptionResIds = BackupSection.entries.map { it.descriptionRes }
        BackupSection.entries.forEach { section ->
            assertTrue(section.descriptionRes != 0, "${section.key} should have a valid descriptionRes")
        }
        assertEquals(
            descriptionResIds.size,
            descriptionResIds.distinct().size,
            "BackupSection descriptionRes IDs must be unique"
        )
    }

    @Test
    fun `every section has matching English and Russian label and description resources`() {
        val resDir = listOf(
            java.io.File("src/main/res"),
            java.io.File("app/src/main/res")
        ).firstOrNull { it.isDirectory }
            ?: error("Android res directory not found relative to working dir ${System.getProperty("user.dir")}")
        val expectedKeys = BackupSection.entries.flatMap { section ->
            listOf("backup_section_${section.key}_label", "backup_section_${section.key}_description")
        }.toSet()
        val actualKeysByLocale = mapOf(
            "default" to readStringKeys(java.io.File(resDir, "values/strings_settings.xml")),
            "ru" to readStringKeys(java.io.File(resDir, "values-ru/strings_settings.xml"))
        )
        actualKeysByLocale.forEach { (locale, keys) ->
            val missing = expectedKeys - keys
            assertTrue(missing.isEmpty(), "Missing backup_section keys in $locale resources: $missing")
        }
        val enValues = readStringValues(java.io.File(resDir, "values/strings_settings.xml"), expectedKeys)
        val ruValues = readStringValues(java.io.File(resDir, "values-ru/strings_settings.xml"), expectedKeys)
        expectedKeys.forEach { key ->
            assertTrue(
                enValues[key] != ruValues[key],
                "Russian value for $key must differ from English"
            )
            assertEquals(
                formatSpecifiers(enValues.getValue(key)),
                formatSpecifiers(ruValues.getValue(key)),
                "Placeholder mismatch for $key between English and Russian"
            )
        }
    }

    private fun readStringKeys(file: java.io.File): Set<String> {
        val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance()
        val doc = factory.newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("string")
        return (0 until nodes.length)
            .map { nodes.item(it).attributes.getNamedItem("name").nodeValue }
            .toSet()
    }

    private fun readStringValues(file: java.io.File, keys: Set<String>): Map<String, String> {
        val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance()
        val doc = factory.newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("string")
        return (0 until nodes.length)
            .map { nodes.item(it) }
            .filter { it.attributes.getNamedItem("name").nodeValue in keys }
            .associate {
                it.attributes.getNamedItem("name").nodeValue to it.textContent.trim()
            }
    }

    private fun formatSpecifiers(text: String): List<String> {
        return "%(\\d+\\$)?[#0\\- +,(]*\\d*(\\.\\d+)?[sSdDoOxXfFeEgGcCbBhHn%]".toRegex()
            .findAll(text)
            .map { it.value }
            .sorted()
            .toList()
    }
}
