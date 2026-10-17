# External Download (Extension-Based) Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add in-app music downloads from external providers by embedding SpotiFLAC-Mobile's MIT Go backend (`gobackend.aar`) and its `.spotiflac-ext` extension system.

**Architecture:** A prebuilt gomobile AAR exposes a JSON-string API. A thin `DownloadBackend` wraps it, `DownloadRepository` owns queue state, a `@HiltWorker` foreground worker runs downloads and publishes files to a user-chosen SAF folder, then triggers a local library scan. Two Compose screens: search/download and extension management.

**Tech Stack:** Kotlin, Compose, Hilt, WorkManager, kotlinx-serialization-json (already in project), gomobile AAR (arm64).

**Spec:** `docs/superpowers/specs/2026-10-17-external-download-design.md` — read it first. Key facts verified against SpotiFLAC source:
- `DownloadByStrategy(requestJSON)` requires `use_extensions: true`, `item_id`, `service`, `quality`; pass `output_path` (local file), NEVER `output_fd`. Result file comes back as `resp.file_path` with `resp.actual_extension`.
- Tagging: request sets `"embed_metadata": true` + title/artist/album/cover fields; embedding is FLAC-only.
- Progress: poll `GetAllDownloadProgressDelta(seq)` → `{"seq":N,"items":{id:{item_id,bytes_total,bytes_received,progress,speed_mbps,is_downloading,status,stage}},"removed":[...]}`; statuses: preparing/downloading/finalizing/completed.
- Search: `SearchTracksWithMetadataProvidersJSON(query, limit, true)` → `[ExtTrackMetadata]` (`id,name,artists,album_name,duration_ms,cover_url,provider_id,isrc,spotify_id,track_number,disc_number,total_tracks,total_discs,release_date,...`).
- Extensions: `GetInstalledExtensions()` → `[{id,manifest:{name,display_name,version,...},enabled,error,...}]`; install via `LoadExtensionFromPath` (refuses downgrades); `RemoveExtensionByID`, `SetExtensionEnabledByID`.
- Auth: `error_type == "verification_required"` → `GetExtensionPendingAuthJSON(extId)` → prompt → `SetExtensionAuthCodeByID(extId, code)`.

---

### Task 1: Build and wire `gobackend.aar`

**Files:**
- Create: `app/libs/gobackend.aar` (built artifact, committed)
- Modify: `app/build.gradle.kts` (dependencies block)
- Create: `scripts/build_gobackend_aar.sh`

**Step 1: Build the AAR**

```bash
export ANDROID_NDK_HOME=$HOME/Android/Sdk/ndk/<version-dir>   # ls ~/Android/Sdk/ndk first
export CGO_ENABLED=1
cd /home/kurob/code/music/SpotiFLAC-Mobile/go_backend
git rev-parse HEAD   # record this SHA
go mod download
go install golang.org/x/mobile/cmd/gomobile
gomobile init
mkdir -p /home/kurob/code/music/PixelPlayer/app/libs
gomobile bind -target=android/arm64 -androidapi 24 \
  -o /home/kurob/code/music/PixelPlayer/app/libs/gobackend.aar .
```

Expected: `gobackend.aar` exists (expect 30–60 MB). If `gomobile bind` fails on ARM-only cross-compile, check `go.mod` toolchain (`go 1.26.6`) matches installed Go.

**Step 2: Create build script** `scripts/build_gobackend_aar.sh`:

```bash
#!/usr/bin/env bash
# Rebuilds app/libs/gobackend.aar from SpotiFLAC-Mobile go_backend.
# Usage: SPOTIFLAC_DIR=/path/to/SpotiFLAC-Mobile ./scripts/build_gobackend_aar.sh
set -euo pipefail
: "${SPOTIFLAC_DIR:?Set SPOTIFLAC_DIR}"
: "${ANDROID_NDK_HOME:?Set ANDROID_NDK_HOME}"
export CGO_ENABLED=1
cd "$SPOTIFLAC_DIR/go_backend"
echo "Pinning SpotiFLAC commit: $(git rev-parse HEAD)"
go mod download
command -v gomobile >/dev/null || go install golang.org/x/mobile/cmd/gomobile
gomobile init
mkdir -p "$(dirname "$0")/../app/libs"
gomobile bind -target=android/arm64 -androidapi 24 -o "$(dirname "$0")/../app/libs/gobackend.aar" .
echo "OK"
```

Then: `chmod +x scripts/build_gobackend_aar.sh`

**Step 3: Wire gradle** — in `app/build.gradle.kts` dependencies block add:

```kotlin
implementation(files("libs/gobackend.aar"))
```

**Step 4: Verify compile** — temporary smoke: add to any existing file (do NOT commit this step):

```kotlin
private val smoke = gobackend.Gobackend::class
```

Run: `./gradlew :app:compileArm64DebugKotlin 2>&1 | tail -3` (or default debug variant). Expected: BUILD SUCCESSFUL. Remove the smoke line.

**Step 5: Commit**

```bash
git add app/libs/gobackend.aar app/build.gradle.kts scripts/build_gobackend_aar.sh
git commit -m "Add gobackend AAR from SpotiFLAC-Mobile (MIT) for extension downloads"
```

Also update `docs/superpowers/specs/2026-10-17-external-download-design.md` AAR Provenance `<FILL>` placeholders with the recorded SHA, date, and commit — include in this commit.

---

### Task 2: Download models (`DownloadModels.kt`) + JSON tests

**Files:**
- Create: `app/src/main/java/com/theveloper/pixelplay/data/download/DownloadModels.kt`
- Test: `app/src/test/java/com/theveloper/pixelplay/data/download/DownloadModelsTest.kt`

**Step 1: Write the failing test**

```kotlin
package com.theveloper.pixelplay.data.download

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadModelsTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun `download request serializes required fields`() {
        val req = DownloadRequest(
            service = "spotify",
            trackName = "Song",
            artistName = "Artist",
            albumName = "Album",
            coverUrl = "https://example/cover.jpg",
            outputPath = "/tmp/work.flac",
            quality = "flac",
            embedMetadata = true,
            itemId = "item-1",
            durationMs = 200_000,
            trackNumber = 3,
            discNumber = 1,
            totalTracks = 12,
            totalDiscs = 1,
            releaseDate = "2020-01-01",
            isrc = "ISRC123",
        )
        val tree = json.parseToJsonElement(json.encodeToString(req)).jsonObject
        assertTrue(tree["use_extensions"]!!.jsonPrimitive.boolean)  // always true, spec-mandated
        assertEquals("item-1", tree["item_id"]!!.jsonPrimitive.content)
        assertEquals("flac", tree["quality"]!!.jsonPrimitive.content)
        assertEquals("/tmp/work.flac", tree["output_path"]!!.jsonPrimitive.content)
        assertFalse(tree.toString().contains("output_fd"))
    }

    @Test
    fun `download response parses file_path and actual_extension`() {
        val resp = json.decodeFromString<DownloadResponse>(
            """{"success":true,"file_path":"/tmp/x.flac","actual_extension":"flac",
                "service":"spotify","title":"Song","artist":"Artist"}"""
        )
        assertTrue(resp.success)
        assertEquals("/tmp/x.flac", resp.filePath)
        assertEquals("flac", resp.actualExtension)
    }

    @Test
    fun `download response parses error`() {
        val resp = json.decodeFromString<DownloadResponse>(
            """{"success":false,"error":"Verification required before download","error_type":"verification_required","service":"qobuz"}"""
        )
        assertFalse(resp.success)
        assertEquals("verification_required", resp.errorType)
    }

    @Test
    fun `progress delta parses items and removed`() {
        val delta = json.decodeFromString<ProgressDelta>(
            """{"seq":7,"items":{"i1":{"item_id":"i1","bytes_total":100,"bytes_received":50,
                "progress":0.5,"speed_mbps":1.2,"is_downloading":true,"status":"downloading","stage":"audio"}},
                "removed":["i0"]}"""
        )
        assertEquals(7L, delta.seq)
        assertEquals(0.5f, delta.items["i1"]!!.progress)
        assertEquals(listOf("i0"), delta.removed)
    }

    @Test
    fun `search results parse`() {
        val tracks = json.decodeFromString<List<ExtTrack>>(
            """[{"id":"t1","name":"Song","artists":"Artist","album_name":"Album",
                "duration_ms":180000,"cover_url":"https://c/1.jpg","provider_id":"spotify-metadata",
                "isrc":"X","track_number":2,"disc_number":1,"total_tracks":10,"total_discs":1,
                "release_date":"2019-05-01","spotify_id":"sp1"}]"""
        )
        assertEquals(1, tracks.size)
        assertEquals("Song", tracks[0].name)
        assertEquals("sp1", tracks[0].spotifyId)
    }

    @Test
    fun `installed extensions parse`() {
        val exts = json.decodeFromString<List<InstalledExtension>>(
            """[{"id":"qobuz-ext","manifest":{"name":"qobuz","display_name":"Qobuz","version":"1.2.0"},
                "enabled":true,"status":"ok"}]"""
        )
        assertEquals("qobuz-ext", exts[0].id)
        assertEquals("Qobuz", exts[0].displayName)
        assertEquals("1.2.0", exts[0].version)
    }
}
```

**Step 2: Run to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.theveloper.pixelplay.data.download.DownloadModelsTest" 2>&1 | tail -5`
Expected: FAIL — unresolved references.

**Step 3: Implement models**

```kotlin
package com.theveloper.pixelplay.data.download

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Wire models for the gobackend JSON API. Field names MUST match the Go
 * structs in SpotiFLAC-Mobile's go_backend (exports_download.go,
 * extension_provider_types.go, progress.go, extension_manager.go).
 */
@Serializable
data class DownloadRequest(
    val service: String,
    @SerialName("track_name") val trackName: String,
    @SerialName("artist_name") val artistName: String,
    @SerialName("album_name") val albumName: String,
    @SerialName("album_artist") val albumArtist: String = "",
    @SerialName("cover_url") val coverUrl: String = "",
    @SerialName("output_path") val outputPath: String,
    @SerialName("output_ext") val outputExt: String = "",
    @SerialName("filename_format") val filenameFormat: String = "{tracknumber} - {artist} - {title}",
    val quality: String,
    @SerialName("embed_metadata") val embedMetadata: Boolean = true,
    @SerialName("item_id") val itemId: String,
    @SerialName("duration_ms") val durationMs: Int = 0,
    @SerialName("track_number") val trackNumber: Int = 0,
    @SerialName("disc_number") val discNumber: Int = 0,
    @SerialName("total_tracks") val totalTracks: Int = 0,
    @SerialName("total_discs") val totalDiscs: Int = 0,
    @SerialName("release_date") val releaseDate: String = "",
    val isrc: String = "",
    @SerialName("spotify_id") val spotifyId: String = "",
    @SerialName("provider_track_id") val providerTrackId: String = "",
    @SerialName("download_provider") val downloadProvider: String = "",
    // Spec: built-in providers are retired; extension mode is mandatory.
    @SerialName("use_extensions") val useExtensions: Boolean = true,
    @SerialName("use_fallback") val useFallback: Boolean = false,
)

@Serializable
data class DownloadResponse(
    val success: Boolean = false,
    val message: String = "",
    @SerialName("file_path") val filePath: String? = null,
    @SerialName("resolved_file_name") val resolvedFileName: String? = null,
    val error: String? = null,
    @SerialName("error_type") val errorType: String? = null,
    @SerialName("retry_after_seconds") val retryAfterSeconds: Int = 0,
    @SerialName("already_exists") val alreadyExists: Boolean = false,
    @SerialName("actual_extension") val actualExtension: String? = null,
    val service: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
)

@Serializable
data class ItemProgress(
    @SerialName("item_id") val itemId: String,
    @SerialName("bytes_total") val bytesTotal: Long = 0,
    @SerialName("bytes_received") val bytesReceived: Long = 0,
    val progress: Float = 0f,
    @SerialName("speed_mbps") val speedMbps: Float = 0f,
    @SerialName("is_downloading") val isDownloading: Boolean = false,
    val status: String = "",
    val stage: String? = null,
)

@Serializable
data class ProgressDelta(
    val seq: Long,
    val reset: Boolean = false,
    val items: Map<String, ItemProgress> = emptyMap(),
    val removed: List<String> = emptyList(),
)

@Serializable
data class ExtTrack(
    val id: String,
    val name: String,
    val artists: String = "",
    @SerialName("album_name") val albumName: String = "",
    @SerialName("album_artist") val albumArtist: String = "",
    @SerialName("duration_ms") val durationMs: Int = 0,
    @SerialName("cover_url") val coverUrl: String? = null,
    @SerialName("provider_id") val providerId: String = "",
    val isrc: String? = null,
    @SerialName("spotify_id") val spotifyId: String? = null,
    @SerialName("provider_track_id") val providerTrackId: String? = null,
    @SerialName("track_number") val trackNumber: Int = 0,
    @SerialName("disc_number") val discNumber: Int = 0,
    @SerialName("total_tracks") val totalTracks: Int = 0,
    @SerialName("total_discs") val totalDiscs: Int = 0,
    @SerialName("release_date") val releaseDate: String? = null,
    val genre: String? = null,
    @SerialName("album_type") val albumType: String? = null,
    val explicit: Boolean = false,
)

@Serializable
data class ExtensionManifest(
    val name: String = "",
    @SerialName("display_name") val displayName: String = "",
    val version: String = "",
    val description: String? = null,
)

@Serializable
data class InstalledExtension(
    val id: String,
    val manifest: ExtensionManifest = ExtensionManifest(),
    val enabled: Boolean = false,
    val status: String = "",
    val error: String? = null,
) {
    val displayName: String get() = manifest.displayName.ifEmpty { manifest.name.ifEmpty { id } }
    val version: String get() = manifest.version
}

val DownloadJson: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
```

**Step 4: Run tests**

Run: `./gradlew :app:testDebugUnitTest --tests "com.theveloper.pixelplay.data.download.DownloadModelsTest" 2>&1 | tail -3`
Expected: PASS (6 tests). Failures are read from `app/build/test-results/testDebugUnitTest/TEST-*.xml`.

**Step 5: Commit**

```bash
git add app/src/main/java/com/theveloper/pixelplay/data/download/DownloadModels.kt app/src/test/java/com/theveloper/pixelplay/data/download/DownloadModelsTest.kt
git commit -m "Add gobackend download wire models"
```

---

### Task 3: `DownloadBackend` wrapper + `DownloadSettings`

**Files:**
- Create: `app/src/main/java/com/theveloper/pixelplay/data/download/DownloadBackend.kt`
- Create: `app/src/main/java/com/theveloper/pixelplay/data/download/DownloadSettings.kt`
- Create: `app/src/main/java/com/theveloper/pixelplay/di/DownloadModule.kt`

**Design:** interface `DownloadBackendApi` (fake-able in tests) + `GobackendDownloadBackend` impl calling the static `gobackend.Gobackend.*` methods. Init is lazy + guarded.

**Step 1: Write `DownloadBackend.kt`**

```kotlin
package com.theveloper.pixelplay.data.download

import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Search result bundle */
data class SearchResult(val tracks: List<ExtTrack>)

sealed interface BackendResult<out T> {
    data class Ok<T>(val value: T) : BackendResult<T>
    data class Failed(val response: DownloadResponse) : BackendResult<Nothing>
}

interface DownloadBackendApi {
    fun initIfNeeded()
    fun listInstalled(): List<InstalledExtension>
    fun installFromFile(path: String): BackendResult<InstalledExtension?>
    fun remove(id: String)
    fun setEnabled(id: String, enabled: Boolean)
    fun search(query: String, limit: Int = 30): List<ExtTrack>
    fun download(request: DownloadRequest): BackendResult<DownloadResponse>
    fun progressDelta(sinceSeq: Long): ProgressDelta
    fun waitProgressDelta(sinceSeq: Long, timeoutMs: Long): ProgressDelta
    fun cancel(itemId: String)
    fun pendingAuth(extensionId: String): String
    fun submitAuthCode(extensionId: String, code: String)
}

@Singleton
class GobackendDownloadBackend @Inject constructor(
    private val settings: DownloadSettings,
) : DownloadBackendApi {

    private val lock = Any()
    private var initialized = false

    override fun initIfNeeded() {
        synchronized(lock) {
            if (initialized) return
            val extDir = File(settings.rootDir, "extensions")
            val dataDir = File(settings.rootDir, "download_data")
            extDir.mkdirs(); dataDir.mkdirs()
            gobackend.Gobackend.initExtensionSystem(extDir.absolutePath, dataDir.absolutePath)
            gobackend.Gobackend.loadExtensionsFromDir(extDir.absolutePath)
            initialized = true
        }
    }

    override fun listInstalled(): List<InstalledExtension> {
        initIfNeeded()
        val raw = gobackend.Gobackend.getInstalledExtensions()
        return DownloadJson.decodeFromString(raw.ifEmpty { "[]" })
    }

    override fun installFromFile(path: String): BackendResult<InstalledExtension?> {
        initIfNeeded()
        return runCatching {
            gobackend.Gobackend.loadExtensionFromPath(path)
        }.fold(
            onSuccess = { raw ->
                val id = DownloadJson.decodeFromString<InstallResult>(raw.ifEmpty { "{}" })
                BackendResult.Ok(DownloadResponse(success = true).let { null }
                    ?: listInstalled().firstOrNull { it.id == id.name })
            },
            onFailure = { BackendResult.Failed(DownloadResponse(success = false, error = it.message, errorType = "install_failed")) },
        )
    }

    override fun remove(id: String) { initIfNeeded(); gobackend.Gobackend.removeExtensionByID(id) }

    override fun setEnabled(id: String, enabled: Boolean) {
        initIfNeeded(); gobackend.Gobackend.setExtensionEnabledByID(id, enabled)
    }

    override fun search(query: String, limit: Int): List<ExtTrack> {
        initIfNeeded()
        val raw = gobackend.Gobackend.searchTracksWithMetadataProvidersJSON(query, limit, true)
        return runCatching { DownloadJson.decodeFromString<List<ExtTrack>>(raw) }.getOrDefault(emptyList())
    }

    override fun download(request: DownloadRequest): BackendResult<DownloadResponse> {
        initIfNeeded()
        return runCatching {
            val raw = gobackend.Gobackend.downloadByStrategy(DownloadJson.encodeToString(request))
            DownloadJson.decodeFromString<DownloadResponse>(raw)
        }.fold(
            onSuccess = { resp -> if (resp.success) BackendResult.Ok(resp) else BackendResult.Failed(resp) },
            onFailure = { BackendResult.Failed(DownloadResponse(success = false, error = it.message, errorType = "backend_crash")) },
        )
    }

    override fun progressDelta(sinceSeq: Long): ProgressDelta {
        val raw = gobackend.Gobackend.getAllDownloadProgressDelta(sinceSeq)
        return runCatching { DownloadJson.decodeFromString<ProgressDelta>(raw) }
            .getOrDefault(ProgressDelta(seq = sinceSeq))
    }

    override fun waitProgressDelta(sinceSeq: Long, timeoutMs: Long): ProgressDelta {
        val raw = gobackend.Gobackend.waitForAllDownloadProgressDelta(sinceSeq, timeoutMs)
        return runCatching { DownloadJson.decodeFromString<ProgressDelta>(raw) }
            .getOrDefault(ProgressDelta(seq = sinceSeq))
    }

    override fun cancel(itemId: String) { gobackend.Gobackend.cancelDownload(itemId) }

    override fun pendingAuth(extensionId: String): String {
        return runCatching { gobackend.Gobackend.getExtensionPendingAuthJSON(extensionId) }.getOrDefault("{}")
    }

    override fun submitAuthCode(extensionId: String, code: String) {
        gobackend.Gobackend.setExtensionAuthCodeByID(extensionId, code)
    }
}

@Serializable
private data class InstallResult(val name: String = "", val success: Boolean = false, val error: String? = null)
```

NOTES for implementer:
- gomobile lower-cases Go method names: `InitExtensionSystem` → `initExtensionSystem`. Verify exact generated names with: `unzip -p app/libs/gobackend.aar classes.jar > /tmp/c.jar && unzip -l /tmp/c.jar | grep -i gobackend` then `javap -classpath <extracted> gobackend.Gobackend | grep -i 'initExtension\|downloadBy\|searchTracks\|getInstalled\|loadExtension\|removeExtension\|setExtension\|progressDelta\|cancel\|PendingAuth\|AuthCode'`. Adjust call names accordingly — this is the ONLY place names are fragile.
- `installFromFile` messy line above: simplify during implementation to
  `val id = DownloadJson.decodeFromString<InstallResult>(raw.ifEmpty { "{}" }); BackendResult.Ok(listInstalled().firstOrNull { it.id == id.name })`.

**Step 2: Write `DownloadSettings.kt`**

```kotlin
package com.theveloper.pixelplay.data.download

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadSettings @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("download_prefs", Context.MODE_PRIVATE)
    val rootDir: File get() = File(context.filesDir, "downloads").apply { mkdirs() }

    /** SAF tree URI of the user-chosen destination folder; null until picked. */
    var safTreeUri: String?
        get() = prefs.getString(KEY_TREE_URI, null)
        set(value) = prefs.edit().putString(KEY_TREE_URI, value).apply()

    var defaultQuality: String
        get() = prefs.getString(KEY_QUALITY, "flac") ?: "flac"
        set(value) = prefs.edit().putString(KEY_QUALITY, value).apply()

    fun clearSafTree() = prefs.edit().remove(KEY_TREE_URI).apply()

    private companion object {
        const val KEY_TREE_URI = "saf_tree_uri"
        const val KEY_QUALITY = "default_quality"
    }
}
```

**Step 3: DI module** `app/src/main/java/com/theveloper/pixelplay/di/DownloadModule.kt`:

```kotlin
package com.theveloper.pixelplay.di

import com.theveloper.pixelplay.data.download.DownloadBackendApi
import com.theveloper.pixelplay.data.download.GobackendDownloadBackend
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DownloadModule {
    @Binds @Singleton
    abstract fun bindDownloadBackend(impl: GobackendDownloadBackend): DownloadBackendApi
}
```

**Step 4: Verify build**

Run: `./gradlew :app:compileArm64DebugKotlin 2>&1 | tail -3` — Expected: SUCCESS (fix any gomobile name mismatches found via javap in Step 1 notes).

**Step 5: Commit**

```bash
git add app/src/main/java/com/theveloper/pixelplay/data/download/ app/src/main/java/com/theveloper/pixelplay/di/DownloadModule.kt
git commit -m "Add download backend wrapper and settings"
```

---

### Task 4: `DownloadRepository` (queue state) + tests

**Files:**
- Create: `app/src/main/java/com/theveloper/pixelplay/data/download/DownloadRepository.kt`
- Test: `app/src/test/java/com/theveloper/pixelplay/data/download/DownloadRepositoryTest.kt`

**Step 1: Write the failing test**

```kotlin
package com.theveloper.pixelplay.data.download

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadRepositoryTest {

    private fun repo() = DownloadRepository()

    @Test
    fun `enqueue adds queued job`() = runTest {
        val r = repo()
        r.enqueue(ExtTrack(id = "t1", name = "Song", artists = "A"), quality = "flac")
        val jobs = r.jobs.value
        assertEquals(1, jobs.size)
        assertEquals(JobStatus.QUEUED, jobs[0].status)
        assertEquals("Song", jobs[0].title)
    }

    @Test
    fun `applyDelta updates progress and completion`() = runTest {
        val r = repo()
        r.enqueue(ExtTrack(id = "t1", name = "Song", artists = "A"), quality = "flac")
        val itemId = r.jobs.value[0].itemId
        r.applyDelta(
            ProgressDelta(seq = 1, items = mapOf(itemId to ItemProgress(itemId, bytesTotal = 100, bytesReceived = 40, progress = 0.4f, isDownloading = true, status = "downloading"))),
        )
        assertEquals(JobStatus.DOWNLOADING, r.jobs.value[0].status)
        assertEquals(0.4f, r.jobs.value[0].progress)
        r.applyDelta(
            ProgressDelta(seq = 2, items = mapOf(itemId to ItemProgress(itemId, progress = 1f, isDownloading = false, status = "completed"))),
        )
        assertEquals(JobStatus.COMPLETED, r.jobs.value[0].status)
    }

    @Test
    fun `applyDelta removes and marks failed`() = runTest {
        val r = repo()
        r.enqueue(ExtTrack(id = "t1", name = "Song", artists = "A"), quality = "flac")
        val itemId = r.jobs.value[0].itemId
        r.markFailed(itemId, error = "boom", errorType = "http_403")
        assertEquals(JobStatus.FAILED, r.jobs.value[0].status)
        assertEquals("boom", r.jobs.value[0].error)
        // removed item disappears from active list but stays in history
        r.applyDelta(ProgressDelta(seq = 3, removed = listOf(itemId)))
        assertEquals(1, r.jobs.value.size) // history kept
        assertNull(r.claimNextQueued())   // failed not re-claimed
    }

    @Test
    fun `claimNextQueued returns oldest queued`() = runTest {
        val r = repo()
        r.enqueue(ExtTrack(id = "t1", name = "S1", artists = "A"), quality = "flac")
        r.enqueue(ExtTrack(id = "t2", name = "S2", artists = "A"), quality = "flac")
        assertEquals("S1", r.claimNextQueued()?.title)
        assertEquals("S2", r.claimNextQueued()?.title)
        assertNull(r.claimNextQueued())
    }

    @Test
    fun `retry resets failed job`() = runTest {
        val r = repo()
        r.enqueue(ExtTrack(id = "t1", name = "Song", artists = "A"), quality = "flac")
        val id = r.jobs.value[0].itemId
        r.markFailed(id, "err", "x")
        r.retry(id)
        assertEquals(JobStatus.QUEUED, r.jobs.value[0].status)
        assertNull(r.jobs.value[0].error)
    }
}
```

**Step 2: Run to verify failure** — `./gradlew :app:testDebugUnitTest --tests "*DownloadRepositoryTest*" 2>&1 | tail -3`. Expected: FAIL (unresolved).

**Step 3: Implement `DownloadRepository.kt`**

```kotlin
package com.theveloper.pixelplay.data.download

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

enum class JobStatus { QUEUED, DOWNLOADING, COMPLETED, FAILED, CANCELLED }

data class DownloadJob(
    val itemId: String = UUID.randomUUID().toString(),
    val track: ExtTrack,
    val quality: String,
    val status: JobStatus = JobStatus.QUEUED,
    val progress: Float = 0f,
    val bytesReceived: Long = 0,
    val bytesTotal: Long = 0,
    val error: String? = null,
    val errorType: String? = null,
    val resultFilePath: String? = null,
    val actualExtension: String? = null,
    val published: Boolean = false,
)

@Singleton
class DownloadRepository @Inject constructor() {

    private val _jobs = MutableStateFlow<List<DownloadJob>>(emptyList())
    val jobs: StateFlow<List<DownloadJob>> = _jobs.asStateFlow()

    fun enqueue(track: ExtTrack, quality: String): String {
        val job = DownloadJob(track = track, quality = quality)
        _jobs.update { list -> list + job }
        return job.itemId
    }

    fun claimNextQueued(): DownloadJob? =
        _jobs.value.firstOrNull { it.status == JobStatus.QUEUED }?.also { job ->
            _jobs.update { list -> list.map { if (it.itemId == job.itemId) it.copy(status = JobStatus.DOWNLOADING) else it } }
        }

    fun applyDelta(delta: ProgressDelta) {
        _jobs.update { list ->
            list.map { job ->
                val p = delta.items[job.itemId] ?: return@map job
                job.copy(
                    progress = p.progress.coerceIn(0f, 1f),
                    bytesReceived = p.bytesReceived,
                    bytesTotal = p.bytesTotal,
                    status = when (p.status) {
                        "completed" -> JobStatus.COMPLETED
                        else -> if (job.status == JobStatus.COMPLETED) job.status else JobStatus.DOWNLOADING
                    },
                )
            }
        }
    }

    fun markCompleted(itemId: String, filePath: String, actualExtension: String?) {
        _jobs.update { list -> list.map { if (it.itemId == itemId) it.copy(status = JobStatus.COMPLETED, progress = 1f, resultFilePath = filePath, actualExtension = actualExtension) else it } }
    }

    fun markPublished(itemId: String) {
        _jobs.update { list -> list.map { if (it.itemId == itemId) it.copy(published = true) else it } }
    }

    fun markFailed(itemId: String, error: String, errorType: String?) {
        _jobs.update { list -> list.map { if (it.itemId == itemId) it.copy(status = JobStatus.FAILED, error = error, errorType = errorType) else it } }
    }

    fun markCancelled(itemId: String) {
        _jobs.update { list -> list.map { if (it.itemId == itemId) it.copy(status = JobStatus.CANCELLED) else it } }
    }

    fun retry(itemId: String) {
        _jobs.update { list -> list.map { if (it.itemId == itemId) it.copy(status = JobStatus.QUEUED, error = null, errorType = null, progress = 0f) else it } }
    }

    fun dismiss(itemId: String) {
        _jobs.update { list -> list.filterNot { it.itemId == itemId } }
    }
}
```

(ConcurrentHashMap import is unused — drop it. Implementer: keep imports minimal.)

**Step 4: Run tests** — Expected: PASS (5 tests).

**Step 5: Commit**

```bash
git add app/src/main/java/com/theveloper/pixelplay/data/download/DownloadRepository.kt app/src/test/java/com/theveloper/pixelplay/data/download/DownloadRepositoryTest.kt
git commit -m "Add download queue repository"
```

---

### Task 5: `DownloadWorker` (foreground, SAF publish, library scan)

**Files:**
- Create: `app/src/main/java/com/theveloper/pixelplay/data/download/DownloadWorker.kt`
- Modify: `AndroidManifest.xml` (add `REQUEST_INSTALL_PACKAGES` NOT needed; nothing new required — SAF + WorkManager already declared)

**Behavior:** one worker pass processes the queue serially until empty. Foreground notification shows current item progress. On `verification_required`, sets job FAILED with `errorType` (UI offers auth dialog then retry).

```kotlin
package com.theveloper.pixelplay.data.download

import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.documentfile.provider.DocumentFile
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val backend: DownloadBackendApi,
    private val repository: DownloadRepository,
    private val settings: DownloadSettings,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        setForeground(createForegroundInfo(null))
        while (true) {
            val job = repository.claimNextQueued() ?: break
            val treeUri = settings.safTreeUri
                ?: run { repository.markFailed(job.itemId, "No download folder selected", "no_folder"); continue }
            process(job, treeUri)
        }
        Result.success()
    }

    private suspend fun process(job: DownloadJob, treeUri: String) {
        val workFile = File(File(applicationContext.cacheDir, "downloads").apply { mkdirs() }, "${job.itemId}.work")
        val request = DownloadRequest(
            service = "spotify",
            trackName = job.track.name,
            artistName = job.track.artists,
            albumName = job.track.albumName,
            albumArtist = job.track.albumArtist,
            coverUrl = job.track.coverUrl.orEmpty(),
            outputPath = workFile.absolutePath,
            outputExt = if (job.quality == "flac") "flac" else "",
            quality = job.quality,
            embedMetadata = true,
            itemId = job.itemId,
            durationMs = job.track.durationMs,
            trackNumber = job.track.trackNumber,
            discNumber = job.track.discNumber,
            totalTracks = job.track.totalTracks,
            totalDiscs = job.track.totalDiscs,
            releaseDate = job.track.releaseDate.orEmpty(),
            isrc = job.track.isrc.orEmpty(),
            spotifyId = job.track.spotifyId.orEmpty(),
            providerTrackId = job.track.providerTrackId.orEmpty(),
        )
        var seq = 0L
        val poller = launch { while (true) { val d = backend.waitProgressDelta(seq, 400); seq = d.seq; repository.applyDelta(d); updateNotification(job, d) } }
        val result = backend.download(request)
        poller.cancel()
        workFile.delete() // gobackend may leave it behind on failure

        when (result) {
            is BackendResult.Failed -> {
                repository.markFailed(job.itemId, result.response.error ?: "Download failed", result.response.errorType)
                return
            }
            is BackendResult.Ok -> {
                val src = File(result.value.filePath ?: return repository.markFailed(job.itemId, "No file path", "no_file"))
                val ext = (result.value.actualExtension ?: job.quality).trimStart('.')
                publish(src, treeUri, job, ext)
                src.delete()
                repository.markPublished(job.itemId)
            }
        }
    }

    private suspend fun publish(src: File, treeUri: String, job: DownloadJob, ext: String) {
        val dir = DocumentFile.fromTreeUri(applicationContext, Uri.parse(treeUri))
            ?: return repository.markFailed(job.itemId, "Download folder unavailable — reselect it", "no_folder")
        val base = sanitize("${job.track.artists.ifEmpty { "Unknown" }} - ${job.track.name}")
        var name = "$base.$ext"
        var doc = dir.findFile(name)
        var n = 1
        while (doc != null && doc.exists()) { name = "$base (${n++}).$ext"; doc = dir.findFile(name) }
        val target = dir.createFile(mimeFor(ext), name)
            ?: return repository.markFailed(job.itemId, "Could not create file in folder", "saf_error")
        applicationContext.contentResolver.openOutputStream(target.uri)?.use { out ->
            src.inputStream().use { it.copyTo(out, DEFAULT_BUFFER_SIZE * 8) }
        } ?: return repository.markFailed(job.itemId, "Could not open target file", "saf_error")
        repository.markCompleted(job.itemId, target.uri.toString(), ext)
        MediaScannerConnection.scanFile(applicationContext, arrayOf(target.uri.toString()), arrayOf(mimeFor(ext)), null)
    }

    private fun sanitize(s: String) = s.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().take(150)

    private fun mimeFor(ext: String) = when (ext) {
        "flac" -> "audio/flac"; "mp3" -> "audio/mpeg"; "m4a" -> "audio/mp4"
        "ogg", "opus" -> "audio/ogg"; else -> "audio/*"
    }

    private fun updateNotification(job: DownloadJob, d: ProgressDelta) {
        val p = d.items[job.itemId] ?: return
        NotificationManagerCompat.from(applicationContext).notify(
            NOTIF_ID,
            buildNotification("${p.status} ${p.percentText}"),
        )
    }

    private fun createForegroundInfo(title: String?) = ForegroundInfo(NOTIF_ID, buildNotification(title ?: "Download"))

    private fun buildNotification(text: String): android.app.Notification {
        val cancel = android.app.PendingIntent.getBroadcast(
            applicationContext, 0,
            Intent(applicationContext, DownloadCancelReceiver::class.java),
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading music")
            .setContentText(text)
            .setOngoing(true)
            .addAction(0, "Cancel", cancel)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "download_channel"
        const val NOTIF_ID = 4001
        const val UNIQUE_WORK = "external_download"
    }
}
```

Implementer notes:
- `launch { }` inside CoroutineWorker: use `coroutineScope { launch { ... } }` around the download call — keep the poller scoped so cancellation propagates.
- Create `DownloadCancelReceiver` (small BroadcastReceiver calling `repository`… can't inject easily; simplest: it reads all current DOWNLOADING job ids from a static holder in `DownloadRepository` — add `fun cancelAllActive()` calling `backend.cancel(itemId)` — wire via `EntryPoint` or make the receiver use `ApplicationContext` + Hilt `@AndroidEntryPoint`). Simplest compliant approach: skip the receiver; instead set the notification's deleteIntent/Cancel action via `WorkManager.cancelUniqueWork(UNIQUE_WORK)` from the UI, and drop the addAction in v1. (ponytail: notification cancel action is UI sugar; the queue screen has cancel buttons. Add receiver later if missed.)
- Notification channel: create in a small `DownloadNotifications` helper called from `Application.onCreate` (find the existing Application class — PixelPlayer has one; grep `class .*Application` — add channel creation there, pattern already used for other channels).
- Enqueue pattern (same as S3MetadataWorker): `WorkManager.getInstance(context).enqueueUniqueWork(DownloadWorker.UNIQUE_WORK, ExistingWorkPolicy.APPEND_OR_REPLACE, OneTimeWorkRequestBuilder<DownloadWorker>().build())`.

**Step 1:** Implement per above; fix compile errors.
**Step 2:** `./gradlew :app:compileArm64DebugKotlin 2>&1 | tail -3` — Expected: SUCCESS.
**Step 3:** Commit: `git commit -m "Add download worker with SAF publish and library scan"`.

---

### Task 6: `DownloadViewModel` + `DownloadScreen`

**Files:**
- Create: `app/src/main/java/com/theveloper/pixelplay/presentation/download/DownloadViewModel.kt`
- Create: `app/src/main/java/com/theveloper/pixelplay/presentation/download/DownloadScreen.kt`
- Modify: `app/src/main/res/values/strings_cloud_services.xml`, `values-in/strings_cloud_services.xml`

**ViewModel** (follow `S3DashboardViewModel` patterns):

```kotlin
@HiltViewModel
class DownloadViewModel @Inject constructor(
    private val backend: DownloadBackendApi,
    private val repository: DownloadRepository,
    private val settings: DownloadSettings,
    private val workManager: // inject via @ApplicationContext + WorkManager.getInstance
) : ViewModel() {

    data class UiState(
        val query: String = "",
        val results: List<ExtTrack> = emptyList(),
        val searching: Boolean = false,
        val error: String? = null,
        val jobs: List<DownloadJob> = emptyList(),
        val safFolderSet: Boolean = false,
        val authRequestId: String? = null,   // extension id awaiting auth code
        val authPrompt: String? = null,
    )

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            repository.jobs.collect { jobs -> _ui.update { it.copy(jobs = jobs, safFolderSet = settings.safTreeUri != null) } }
        }
    }

    fun search(query: String) = viewModelScope.launch(Dispatchers.IO) {
        _ui.update { it.copy(query = query, searching = true) }
        val tracks = runCatching { backend.search(query) }.getOrElse { emptyList() }
        _ui.update { it.copy(results = tracks, searching = false, error = if (tracks.isEmpty()) "No results / no extensions installed" else null) }
    }

    fun enqueue(track: ExtTrack) {
        if (settings.safTreeUri == null) { _ui.update { it.copy(error = "Pick a download folder first") }; return }
        repository.enqueue(track, settings.defaultQuality)
        WorkManager.getInstance(context).enqueueUniqueWork(DownloadWorker.UNIQUE_WORK, ExistingWorkPolicy.APPEND_OR_REPLACE, OneTimeWorkRequestBuilder<DownloadWorker>().build())
    }

    fun cancel(itemId: String) { backend.cancel(itemId); repository.markCancelled(itemId) }
    fun retry(itemId: String) { repository.retry(itemId); reEnqueueWorker() }
    fun dismiss(itemId: String) = repository.dismiss(itemId)
    fun submitAuthCode(code: String) { val id = _ui.value.authRequestId ?: return; backend.submitAuthCode(id, code); _ui.update { it.copy(authRequestId = null, authPrompt = null) } }
}
```

**Screen:** single scaffold:
- Top: search TextField + search action
- Middle: results LazyColumn (AsyncImage cover, name, artists, album; trailing download IconButton) — reuse Coil `AsyncImage` already used across the app
- Bottom section: active queue LazyRow/LazyColumn from `ui.jobs` (LinearProgressIndicator(progress), status text, cancel/retry/dismiss buttons)
- If `safFolderSet == false`: banner with "Choose folder" → `ActivityResultContracts.OpenDocumentTree` persisted with `takePersistableUriPermission`, then `settings.safTreeUri = uri.toString()` via ViewModel callback `onFolderPicked(uri)`
- Auth dialog when `authRequestId != null`: TextField + submit → `submitAuthCode`
- Strings: add `download_title`, `download_search_hint`, `download_pick_folder`, `download_no_folder`, `download_queue`, etc. to BOTH `values/strings_cloud_services.xml` (EN) and `values-in/` (ID) — follow existing key naming in those files.

**Steps:** implement → `./gradlew :app:compileArm64DebugKotlin` SUCCESS → commit `git commit -m "Add download screen and view model"`.

---

### Task 7: `ExtensionsViewModel` + `ExtensionsScreen`

**Files:**
- Create: `app/src/main/java/com/theveloper/pixelplay/presentation/download/ExtensionsViewModel.kt`
- Create: `app/src/main/java/com/theveloper/pixelplay/presentation/download/ExtensionsScreen.kt`

ViewModel: `listInstalled()` → StateFlow; `install(uri)` — copy picked document to cacheDir temp file (`.spotiflac-ext` may have opaque MIME; always copy bytes then call `backend.installFromFile(tempPath)`), refresh list, surface error string on failure (e.g. downgrade refusal); `remove(id)`, `toggle(id, enabled)`.

Screen: LazyColumn of installed extensions (name, version, enabled Switch, remove IconButton), top app bar with "Install from file" (`ActivityResultContracts.OpenDocument`), Material3 card footer disclaimer string `extensions_disclaimer` (EN: "Extensions contain third-party code that runs on your device. Only install extensions you trust."; ID: translate). Empty state → "Install an extension to enable downloads."

**Steps:** implement → compile SUCCESS → commit `git commit -m "Add extensions management screen"`.

---

### Task 8: Navigation + entry points + strings

**Files:**
- Modify: `app/src/main/java/com/theveloper/pixelplay/presentation/navigation/Screen.kt` — add:

```kotlin
object Download : Screen("download")
object Extensions : Screen("download_extensions")
```

- Modify: `app/src/main/java/com/theveloper/pixelplay/presentation/navigation/AppNavigation.kt` — add two `composable(...)` routes following the existing pattern (hiltViewModel, navigateSafely). Extensions reachable from Download screen top bar action.
- Modify: `app/src/main/java/com/theveloper/pixelplay/presentation/components/StreamingProviderSheet.kt` — add a "Download music" row (icon `Icons.Outlined.Download`) navigating to `Screen.Download`, same pattern as the S3/Drive rows (see how `onOpenS3Dashboard` is threaded in AppNavigation.kt:239-245).
- Optional: a row in Settings → Accounts style screen if pattern demands; keep to StreamingProviderSheet only in v1 (YAGNI).

**Steps:** implement → compile → install on device (`adb -s RR8N205B17Y install -r app/build/outputs/apk/arm64/debug/app-arm64-v8a-debug.apk` or release per current variant) → verify both screens open → commit `git commit -m "Wire download screens into navigation"`.

---

### Task 9: End-to-end verification on device

Prerequisites: at least one working `.spotiflac-ext` file (obtain from the SpotiFLAC community repo listed in its README extensions docs; save to device Downloads).

Checklist (manual, on device RR8N205B17Y):
1. Open Download screen → Extensions → install the `.spotiflac-ext` → appears in list, enabled
2. Back to search → type a known track → results appear with provider chips
3. Tap download → folder picker (first time) → choose e.g. `Music/` → download starts
4. Notification progress → completion
5. Verify file in folder via Files app: plays, has embedded cover+tags (check with a tag viewer or re-import)
6. Song appears in PixelPlayer local library after scan
7. Cancel mid-download works; retry after airplane-mode failure works
8. Duplicate download → ` (1)` file created, no crash

If `verification_required` error appears: dialog shows, entering the extension's auth code clears it (provider-dependent).

**Step:** fix any findings → final `./gradlew :app:assembleRelease` → `adb -s RR8N205B17Y install -r app/build/outputs/apk/release/app-arm64-v8a-release.apk` → re-run checklist quickly → commit any fixes.

---

## Risk register

| Risk | Mitigation |
|---|---|
| gomobile method name casing differs from expected | `javap` the AAR classes first (Task 3 notes) — single point of adjustment |
| AAR bloats APK 30–60 MB | Accepted in spec; arm64-only |
| Extension pipelines vary (auth, region) | `verification_required` + auth dialog path covered; per-provider quirks are extension's problem |
| `searchTracksWithMetadataProvidersJSON` slow (blocking) | All calls on Dispatchers.IO; progress polling on separate coroutine |
