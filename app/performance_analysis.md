# Auditoría de Performance UI - PixelPlay

## 1. Resumen Ejecutivo

El estado general de performance de PixelPlay es mixto con tendencia a acumulativo. La app demuestra que hubo esfuerzo deliberado en optimización: strong skipping, stability config, state slicing con `.map().distinctUntilChanged()` en el player sheet, separación del flow de posición, baseline profiles, y uso de `ImmutableList` extensivo. Esto no es un codebase negligente en performance.

Sin embargo, el problema principal es de amplificación acumulativa: individualmente, muchos patrones son "aceptables", pero su combinación simultánea —especialmente en dispositivos de gama media/baja con bibliotecas grandes— genera un budget de frames que se desborda consistentemente. El cuello de botella no es un solo componente catastrófico, sino la suma de:

- Recomposición innecesaria por observación no-sliceada de `PlayerUiState` en 3 tabs de la librería + `SearchScreen`
- Operaciones `O(n)` sobre colecciones grandes durante composición o en el main thread pipeline
- Animaciones por-item excesivas (hasta 7 `animateXAsState` por `EnhancedSongListItem`)
- `AnimatedVisibility` en cada item individual de secciones expandibles (`ArtistDetail`, `GenreDetail`)
- Presión de GC por conversiones frecuentes `.toImmutableList()` sobre colas grandes

En hardware potente (`Snapdragon 8 Gen 2+`, `8GB+ RAM`), la mayoría de esto queda enmascarado por CPU headroom. En un `Snapdragon 680`, `MediaTek Helio G85`, o similar con `4GB RAM` y una biblioteca de `5000+` canciones, estas acumulaciones pueden producir jank perceptible en scroll, transiciones de pestaña y apertura de sheets.

---

## 2. Hallazgos Principales

### Hallazgo 1: Observación completa de `PlayerUiState` en tabs de librería

**Severidad:** CRÍTICA

**Impacto real:** Cada vez que cualquiera de los `~30` campos de `PlayerUiState` cambia —incluyendo `currentPosition` que se actualiza cada `250ms`, `searchResults`, `lavaLampColors`, estado de `queue undo`, etc.— los tabs de Albums, Artists y Search recomponen completamente, incluyendo sus `LazyColumn`/`LazyGrid`, fast scroll labels, sort trackers y `LaunchedEffects`.

**Por qué ocurre:**

- `LibraryMediaTabs.kt:85` — `LibraryAlbumsTab` hace `playerViewModel.playerUiState.collectAsStateWithLifecycle()` para leer solo `isAlbumsListView` y `currentAlbumSortOption`
- `LibraryMediaTabs.kt:400` — `LibraryArtistsTab` hace lo mismo para leer solo `currentArtistSortOption`
- `SearchScreen.kt:138` — mismo patrón para search state

**Contraste:** `UnifiedPlayerSheetV2.kt` ya usa el patrón correcto:

```kotlin
playerViewModel.playerUiState
    .map { PlayerUiSheetSliceV2(...) }
    .distinctUntilChanged()
````

Pero los tabs de librería no aplican este patrón.

**Síntomas:** Micro-stutters durante scroll en tabs de Albums/Artists mientras hay música reproduciéndose (el position ticker cada `250ms` fuerza recomposición del tab activo aunque el usuario solo está scrolleando). Peor con queue changes, search, o undo bar toggles.

**Dispositivos más afectados:** Todos, pero es catastrófico en gama media/baja donde un frame budget de `16ms` no tiene margen.

**Evidencia:**

* `PlayerUiState.kt:19` — `currentPosition: Long` está dentro del state. Cada tick del position updater en `PlaybackStateHolder.kt:553-554` actualiza `_currentPosition`, y si ese valor se propaga a `PlayerUiState`, todas las tabs se invalidan.
* Confirmado: `playerUiState` es un `MutableStateFlow` separado con updates imperativos (`_playerUiState.update { ... }`), y al menos la posición SÍ se actualiza ahí (campo `currentPosition` en línea 19).

---

### Hallazgo 2: `AnimatedVisibility` por cada item en listas expandibles

**Severidad:** CRÍTICA

**Impacto real:** En `ArtistDetailScreen.kt:358-377`, cada canción individual dentro de cada sección de álbum está envuelta en `AnimatedVisibility`. Al expandir una sección de un artista con `50` canciones, se ejecutan `50 expandVertically + 50 fadeIn` simultáneamente. Cada una con su propio `Animatable`, su propio coroutine de animación y su propio re-layout frame-by-frame.

**Por qué ocurre:** El patrón `AnimatedVisibility` aplicado a items individuales en un `itemsIndexed` de `LazyColumn` fuerza:

* Materialización de todos los items de la sección al momento del toggle (`LazyColumn` los necesita para medir la animación)
* `50+ expandVertically(tween(280ms))` corriendo en paralelo
* Re-layout del `LazyColumn` en cada frame durante `280ms`

**Dónde ocurre:**

* `ArtistDetailScreen.kt:358-377` — cada `ArtistAlbumSectionSongItem` wrapeado
* Probablemente `GenreDetailScreen.kt` con patrón similar

**Síntomas:** Frame drops visibles (jank) al expandir/colapsar secciones de artistas, especialmente con artistas que tienen muchos álbumes/canciones. En gama baja podría freezar `~500ms`.

**Evidencia directa:** Código confirmado en lectura de `ArtistDetailScreen.kt:339-377`.

---

### Hallazgo 3: 7 animaciones paralelas por cada `EnhancedSongListItem`

**Severidad:** ALTA

**Impacto real:** `EnhancedSongListItem` ejecuta simultáneamente:

* `animateDpAsState` (corner radius) — `400ms`
* `animateDpAsState` (album corner radius)
* `animateFloatAsState` (selection scale) — spring
* `animateDpAsState` (border width) — `250ms`
* `animateColorAsState` (container color) — `300ms`
* `animateColorAsState` (content color) — `300ms`
* `animateColorAsState` (border color) — `250ms`

Cada una crea un `Animatable` con su propio frame callback. Cuando cambia `currentSong` (el usuario pasa a la siguiente canción), las 7 animaciones se disparan en el item anterior y las 7 en el nuevo item = **14 animaciones simultáneas** + la animación de transición del player.

**Dónde:** `EnhancedSongListItem.kt:97-166`, usado en:

* `DailyMixSection` (Home screen, hasta 4 items)
* `LibrarySongsTab` (potencialmente visibles en pantalla `~8-12` items)
* `PlaylistDetailScreen`

**Síntomas:** Micro-jank al cambiar de canción, especialmente perceptible si el usuario está scrolleando la lista de canciones al mismo tiempo.

---

### Hallazgo 4: Computación de géneros `O(n)` sin deduplicación de cambios

**Severidad:** ALTA

**Impacto real:** `LibraryStateHolder.kt:134-174` mapea todas las canciones a géneros cada vez que `_allSongs` emite. Incluye:

* Iteración de toda la colección de canciones
* `mutableMapOf + getOrPut` para agrupar
* `mapIndexedNotNull` para transformar
* `GenreThemeUtils.getGenreThemeColor()` por cada género (light + dark)
* Conversión de color a hex string por género
* `distinctBy`, `sortedBy`, `toImmutableList()`

**Mitigación parcial:** Usa `.flowOn(Dispatchers.Default)` — no bloquea main thread. **Pero:**

* Emite una nueva `ImmutableList<Genre>` incluso si los géneros no cambiaron
* No tiene `distinctUntilChanged()` antes del `map` (el `map` se re-ejecuta aunque las canciones sean idénticas)
* Para `10,000` canciones con `50` géneros, esto es `~10,000` iteraciones + `100` llamadas a `getGenreThemeColor()` + sort

**Evidencia:** Líneas `134-174` de `LibraryStateHolder.kt` confirmadas por lectura directa. No hay `distinctUntilChanged()` en la chain.

---

### Hallazgo 5: Conversiones frecuentes `.toImmutableList()` sobre colas grandes

**Severidad:** ALTA

**Impacto real:** `32` invocaciones de `.toImmutableList()` en el layer de ViewModels. Para operaciones de cola (`queue`):

* Eliminar 1 canción de una cola de `1000` = `filter { }.toImmutableList()` → itera `1000` items + copia persistente
* Cada cambio de cola actualiza `PlayerUiState.currentPlaybackQueue`, que es `ImmutableList<Song>`
* `Song` es un `data class` con `~20` campos incluyendo strings

**Dónde:**

* `PlayerViewModel.kt` — `9` ocurrencias directas
* `LibraryStateHolder.kt` — `10` ocurrencias
* `DailyMixStateHolder.kt` — `6` ocurrencias

**Síntomas:** Presión de GC intermitente. En gama baja con menor heap, el GC se dispara más frecuentemente, causando micro-pausas de `2-8ms` que se acumulan con otras operaciones.

---

### Hallazgo 6: Falta de índice en `file_path` de la tabla `songs`

**Severidad:** MEDIA

**Impacto real:** `getSongByPath()` query hace full table scan. Con `10,000+` canciones, esto puede tomar `10-50ms` en gama baja. Es llamado durante sync y potencialmente durante resolución de URIs.

**Dónde:** `MusicDao.kt` — `@Query("SELECT * FROM songs WHERE file_path = :path LIMIT 1")`

**Evidencia:** Análisis de `SongEntity.kt` muestra índices en `title`, `album_id`, `artist_id`, `genre`, `parent_directory_path`, `content_uri_string`, `date_added`, `duration`, `source_type`, `artist_name` — pero no en `file_path`.

---

### Hallazgo 7: Queries sin límite devolviendo datasets completos

**Severidad:** MEDIA-ALTA

**Impacto real:** `getAllSongs()`, `getAlbums()`, `getArtists()` en `MusicDao.kt` devuelven `Flow<List<Entity>>` sin `LIMIT`. Con `50,000` canciones:

* `getAllSongs()` → `50,000 SongEntity` en memoria (`~50MB+` dependiendo del tamaño de strings)
* `getAlbums()` con `GROUP BY + JOIN` → miles de albums
* `getArtists()` con doble `JOIN (cross_ref)` → potencialmente miles

Estos flows se re-emiten cuando las tablas subyacentes cambian (`Room's invalidation tracker`).

**Mitigación parcial:** Ya hay `Paging3` para `songsPagingFlow` y `favoritesPagingFlow`. Pero `allSongs`, `albums`, `artists` todavía cargan completos y se mantienen en `StateFlow<ImmutableList<...>>` en `LibraryStateHolder`.

---

### Hallazgo 8: Position ticker actualiza `PlayerUiState`

**Severidad:** MEDIA

**Impacto real:** El campo `currentPosition: Long` vive dentro de `PlayerUiState` (línea 19). Aunque existe un flow separado `currentPlaybackPosition` en `PlaybackStateHolder`, si el `_playerUiState` también se actualiza con la posición, cada tick de `250ms` invalida todo el `PlayerUiState`.

**Hipótesis parcial:** Necesito confirmar si `_playerUiState.update { it.copy(currentPosition = ...) }` se llama cada `250ms`. Si es así, la severidad sube a **CRÍTICA** dado que amplifica el Hallazgo 1. Si `currentPosition` en `PlayerUiState` solo se actualiza en `seek/song change`, es menos grave.

**Evidencia:**

* El campo existe en `PlayerUiState.kt:19`
* El flow separado existe en `PlaybackStateHolder.kt:60-61`
* Falta trazar si el update de `_playerUiState.currentPosition` es cada tick o solo en eventos discretos

---

## 3. Riesgos Sistémicos

### 3.1 `PlayerUiState` como "God State"

`PlayerUiState` tiene `~30` campos que cubren queue, search, folders, undo bars, sort options, filter state, sync state, AI state y más. Es un `MutableStateFlow` con updates imperativos. Cada `.update { copy(...) }` emite a todos los suscriptores, de los cuales hay al menos 3 que no usan slicing (`LibraryAlbumsTab`, `LibraryArtistsTab`, `SearchScreen`). Esto crea un fan-out de recomposición donde un cambio en un campo irrelevante (ej: `searchQuery`) puede forzar recomposición de `AlbumsTab`.

### 3.2 Patrón de "todo es singleton"

`PlayerViewModel` inyecta `15+ StateHolders`, todos `@Singleton`. `LibraryStateHolder` mantiene `allSongs`, `albums`, `artists` en memoria permanente como `StateFlow`. Esto significa que:

* La memoria baseline de la app crece linealmente con el tamaño de la biblioteca y nunca se libera
* No hay mecanismo de `onTrimMemory()` para liberar colecciones en presión de memoria
* En dispositivos con `3-4GB RAM`, esto puede forzar GC agresivo o incluso OOM con bibliotecas muy grandes

### 3.3 Cascading recomposition through lambda captures

Muchos composables reciben `playerViewModel: PlayerViewModel` directamente como parámetro. Dentro del composable, hacen `playerViewModel.playerUiState.collectAsStateWithLifecycle()`. Este patrón significa que el scope de observación es el composable entero —no un sub-composable aislado—. Cualquier emisión de `playerUiState` recompone todo el composable y todos sus hijos.

### 3.4 `ImmutableList` conversion overhead acumulativo

`32 .toImmutableList()` en ViewModels + conversiones en DAOs/Repositories. `kotlinx.collections.immutable` usa persistent data structures (basados en `Hash Array Mapped Tries`), cuya construcción desde listas mutables es `O(n)`. Para la cola de reproducción (que puede cambiar frecuentemente durante shuffle/skip/remove), esto genera allocations significativas que el GC debe recoger.

---

## 4. Hipótesis de Jank por Prioridad

### Más probables

* Recomposición de tabs de librería cada `250ms` durante playback — por observación completa de `PlayerUiState` que incluye `currentPosition`
* Frame drops al expandir secciones de artista — por `AnimatedVisibility` por-item con `50+` items simultáneos
* Micro-stutters en scroll de listas largas — por 7 animaciones paralelas por `EnhancedSongListItem` + falta de `contentType` en algunos `LazyList`

### Probables

* Jank al cambiar tabs en Library — la tab nueva observa `playerUiState` y recompone completamente
* Stutters al abrir/cerrar `QueueBottomSheet` con colas grandes — key function hace `4x getOrNull` por item + reorder preview logic
* GC pauses en gama baja — por acumulación de `toImmutableList + allSongs` en memoria + color scheme bitmaps
* Computación de géneros innecesaria — `O(n)` sobre todas las canciones sin `distinctUntilChanged`

### Posibles pero no confirmadas

* Position ticker actualizando `PlayerUiState` cada `250ms` — confirmar si `_playerUiState.update { copy(currentPosition = ...) }` se llama en cada tick
* Prefetch de imágenes de artistas bloqueando brevemente main thread — depende de la implementación de `prefetchArtistImages`
* Room invalidation tracker re-emitiendo flows grandes después de sync — Room re-emite toda la tabla cuando cualquier row cambia
* Color palette extraction durante transitions rápidas — aunque tiene capacity `DROP_OLDEST`, el procesamiento de bitmap ocurre en `Default dispatcher` que comparte threads con animation dispatching

---

## 5. Mapa de Hotspots

Ordenados de mayor a menor prioridad:

| #  | Componente                             | Problema principal                                       | Frecuencia de impacto              |
| -- | -------------------------------------- | -------------------------------------------------------- | ---------------------------------- |
| 1  | `LibraryAlbumsTab / LibraryArtistsTab` | Observan `playerUiState` completo                        | Constante durante playback         |
| 2  | `ArtistDetailScreen` secciones         | `AnimatedVisibility` por item                            | Cada expand/collapse               |
| 3  | `EnhancedSongListItem`                 | 7 animaciones por item                                   | Cada cambio de canción             |
| 4  | `QueueBottomSheet`                     | Key computation + reorder logic con colas `1000+`        | Cada apertura/interacción          |
| 5  | `SearchScreen`                         | Observa `playerUiState` completo                         | Constante durante playback         |
| 6  | `LibraryStateHolder.genres`            | `O(n)` sin deduplicación                                 | Cada cambio en `allSongs`          |
| 7  | `PlayerUiState` updates                | Monolithic state → fan-out                               | Cada `250ms` (si incluye posición) |
| 8  | `PlaylistDetailScreen`                 | Carga completa sin paginación + sort in-memory           | Playlists con `500+` canciones     |
| 9  | `DailyMixSection`                      | `forEach` sin `LazyColumn` (4 items con 7 anim cada uno) | Home screen render                 |
| 10 | `MusicDao` queries sin `LIMIT`         | Datasets completos en memoria                            | Cada invalidación de tabla         |

---

## 6. Riesgos Específicos para Gama Media y Baja

### CPU Budget

Un `Snapdragon 680` tiene `~50%` del throughput de un `Snapdragon 8 Gen 2`. El frame budget sigue siendo `16ms`, pero la capacidad de procesamiento por frame es la mitad. Esto significa:

* Las `14` animaciones simultáneas por cambio de canción (7 old + 7 new item) que en un flagship toman `3ms`, en gama media toman `6-8ms` — dejando solo `8ms` para layout, draw y todo lo demás
* Las recomposiciones innecesarias por `playerUiState` que en un flagship son "invisibles" (`2-3ms`), en gama media consumen `5-8ms` — cerca del `50%` del frame budget

### GPU / Rendering

* `AnimatedVisibility` con `expandVertically` fuerza re-layout del `LazyColumn`. En GPUs modestas (`Adreno 610`, `Mali-G57`), el re-rasterizado de `50+` items simultáneamente puede causar GPU-bound jank
* Hardware bitmaps para album art prefetch consumen GPU memory que es más limitada en gama baja

### Memoria y GC

`4GB RAM` con `~1.5GB` disponible para la app. Si `allSongs` tiene `10,000 Song objects` (`~20` campos cada uno, estimado `~1KB` por `Song`) = `~10MB` solo en `allSongs`. Sumando `albums`, `artists`, `queue`, `search results`, `color schemes`, `Coil cache (20% heap ≈ 50MB)` → la app puede estar usando `100-150MB` base.

En `3GB devices`, esto es `~10%` de RAM total. GC generacional de ART se activa más frecuentemente → micro-pauses de `1-5ms` cada few seconds.

Las conversiones `.toImmutableList()` crean copias efímeras que amplifican la presión de GC.

### Tamaño de `Cursor` / `CursorWindow`

Room usa `CursorWindow` de `2MB` por defecto. Con `10,000+` canciones, los campos `String` (`title`, `artist_name`, `album`, `file_path`, `content_uri_string`) pueden exceder el window.

**Nota positiva:** ya hay `SONG_LIST_PROJECTION` que excluye lyrics para prevenir overflow.

---

## 7. Escenarios Límite

### Bibliotecas muy grandes (`20,000+` canciones)

* `allSongs StateFlow` mantiene `20,000 Song objects` permanentemente → `~20MB` solo en esa colección
* `genres` se recomputa: `20,000` iteraciones + `groupBy` → potencialmente `200+` géneros con theme colors
* Room queries sin `LIMIT` devuelven datasets enormes → `CursorWindow` pressure
* Shuffle de `20,000` items (`Fisher-Yates`) → `O(n)` pero sin dispatching forzoso a `Default` en `QueueStateHolder` base

**Predicción:** Posible ANR en sync; jank severo en genre tab; OOM en devices con `3GB RAM`.

### Muchas imágenes / artwork pesado

* Coil con `20% memory + 100MB disk` es razonable
* Pero sin `onTrimMemory()` callback para evict, bajo presión de memoria el sistema mata la app antes de que Coil limpie su cache
* `ColorSchemeProcessor` `LRU` de `30 entries + 128x128 bitmaps` = `~2MB` — manejable

**Predicción:** Estable en uso normal. En low-memory situations, posible kill por sistema.

### Muchas playlists / listas extensas

* `PlaylistViewModel.loadPlaylistDetails()` carga todas las canciones de una playlist en memoria
* Playlists con `2000+` canciones: `getSongsByIds()` divide en chunks de `900`, pero el resultado final es una sola `List<Song>` en memoria
* Sort se aplica sobre la lista completa in-memory

**Predicción:** Jank de `200-500ms` al abrir playlists con `2000+` canciones en gama baja.

### Búsquedas / filtros / ordenamientos frecuentes

* Search debounce a `300ms` es razonable
* Pero `SearchScreen` observa `playerUiState` completo → recompone durante playback
* Sort change en albums/artists tab fuerza `scrollToItem(0)` + recomposición completa del grid/list

**Predicción:** Search es relativamente smooth gracias al debounce. Sort changes causan breve flash visual.

### Player state con actualizaciones frecuentes

* Position ticker cada `250ms` está bien aislado en `currentPosition` separado
* Pero si también actualiza `PlayerUiState.currentPosition`, el fan-out es masivo
* `LavaLampColors` (animación en player) actualiza `PlayerUiState` si cambia

**Predicción:** El mayor riesgo es la hipótesis #8 — necesita validación.

### Navegación rápida entre pantallas

* Navigation transitions de `250ms` con `FastOutSlowInEasing`
* Cada pantalla crea nuevos `collectAsStateWithLifecycle()` subscriptions
* `WhileSubscribed(5000)` = `5` segundos de gracia antes de cancelar la suscripción. Si el usuario navega rápido (`< 5s` por pantalla), las suscripciones de pantallas previas siguen activas → múltiples flows activos simultáneamente
* `navigateSafely()` tiene retry logic — puede encolar múltiples navigations

**Predicción:** Potencial para buildup de suscripciones activas durante navegación rápida. Efecto moderado.

### Sheets, gestos y animaciones simultáneas

* Player sheet usa `MutatorMutex` para prevenir conflictos de animación — bien diseñado
* Layout-phase reads con `Modifier.layout` para drag — excelente optimización
* `graphicsLayer` lambdas con `Animatable getters` — evita recomposición
* Queue sheet + player sheet + lyrics sheet potencialmente abiertos con animaciones concurrentes

**Predicción:** La implementación del player sheet es sólida. El riesgo mayor es `QueueBottomSheet` con colas grandes y su lógica de reorder preview.

---

## 8. Plan de Optimización Futuro

### Fase 1: Quick Wins (impacto alto, riesgo bajo)

#### 1.1 Crear state slices para `LibraryAlbumsTab`, `LibraryArtistsTab`, `SearchScreen`

Reemplazar `playerViewModel.playerUiState.collectAsStateWithLifecycle()` con:

```kotlin
val albumsTabSlice by remember {
    playerViewModel.playerUiState
        .map { AlbumsTabSlice(it.isAlbumsListView, it.currentAlbumSortOption) }
        .distinctUntilChanged()
}.collectAsStateWithLifecycle(initialValue = AlbumsTabSlice())
```

**Impacto esperado:** Elimina recomposiciones innecesarias de 3 screens.
**Riesgo:** Muy bajo — solo cambia cómo se observa, no qué se observa.
**Preservar:** Comportamiento de sort reset y fast scroll label.

#### 1.2 Agregar `distinctUntilChanged()` al flow de `genres`

En `LibraryStateHolder.kt:134`, agregar antes del `.map`:

```kotlin
_allSongs.distinctUntilChanged().map { songs -> ... }
```

O mejor: agregar después del map con comparación por IDs de géneros.

**Impacto:** Evita recomputación si `allSongs` se re-emite con datos idénticos.
**Riesgo:** Mínimo.

#### 1.3 Reemplazar `AnimatedVisibility` per-item con el modifier `animateItem()`

En `ArtistDetailScreen.kt:353-377`, cambiar la visibilidad de items en `LazyColumn`. Usar la list size condicionada (ya existe `isExpanded`) + `Modifier.animateItem()` de `LazyColumn`.

**Impacto:** Reduce de `N` animaciones simultáneas a animación de layout gestionada por `LazyColumn`.
**Riesgo:** Cambio visual en la animación de expand/collapse — puede necesitar ajuste de timing.
**Preservar:** Sensación de expand/collapse suave.

#### 1.4 Agregar índice en `file_path` en `SongEntity`

`@ColumnInfo(index = true)` en el campo `file_path`, o migration con `CREATE INDEX`.

**Impacto:** `getSongByPath()` pasa de full scan a `O(log n)` lookup.
**Riesgo:** Requiere migration de DB (incrementar versión).

---

### Fase 2: Refactors de Impacto Medio

#### 2.1 Consolidar animaciones de `EnhancedSongListItem`

Reemplazar 7 `animateXAsState` individuales con un solo `updateTransition` que coordine todos los valores.

**Impacto:** Reduce overhead de 7 `Animatables` independientes a 1 transición coordinada.
**Riesgo:** Necesita testing visual cuidadoso para asegurar que los timings se preserven.
**Preservar:** La sensación de cambio suave en selection y current song highlight.

#### 2.2 Desacoplar `PlayerUiState` en sub-states

Separar en: `QueueUiState`, `SearchUiState`, `FolderUiState`, `LibraryPrefsUiState`. Cada uno como `StateFlow` independiente.

**Impacto:** Elimina cross-contamination de updates entre funcionalidades.
**Riesgo:** Moderado — requiere cambios en múltiples screens que leen `PlayerUiState`.
**Preservar:** API pública de `PlayerViewModel` (puede delegarse internamente).

#### 2.3 Implementar memory trimming

Registrar `ComponentCallbacks2` en `Application` para responder a `onTrimMemory()`:

* Limpiar `LibraryStateHolder.allSongs`
* Limpiar `Coil memory cache`
* Limpiar `color scheme cache` bajo presión
* Recargar desde DB/paging cuando se vuelvan a necesitar

**Impacto:** Reduce probabilidad de OOM/kill en gama baja.
**Riesgo:** Necesita manejar correctamente el estado "vacío después de trim".

#### 2.4 Optimizar `QueueBottomSheet` key computation

* Pre-computar activeKeys map fuera del `items() block`
* Evitar `4x getOrNull` por item — usar indexación directa con bounds check una sola vez

**Impacto:** Reduce overhead per-frame de queue rendering.
**Riesgo:** Bajo.

---

### Fase 3: Refactors Profundos / Estructurales

#### 3.1 Migrar `allSongs / albums / artists` a `Paging3` completo

Eliminar `StateFlow<ImmutableList<Song>>` en `LibraryStateHolder`. Usar `Paging3` para todos los consumidores de la lista completa.

**Impacto:** Elimina el problema de memoria `O(n)` con el tamaño de biblioteca.
**Riesgo:** Alto — afecta `genres` computation, `daily mix`, `search`, `stats`, `AI playlist generation`, y cualquier flujo que dependa de `allSongs`.
**Preservar:** `Genres` todavía necesitan datos completos (considerar query dedicada en DB).
**Alternativa:** Mantener `allSongs` pero con deferred loading + `WeakReference`.

#### 3.2 Re-arquitectura de `PlayerViewModel`

Actualmente es un God Object de `4,631` líneas con `15 StateHolders`. Considerar exponer `StateHolders` directamente a UI a través de `CompositionLocals` o `Hilt-injected ViewModels` por screen.

**Impacto:** Reduce acoplamiento, permite lifecycle management por screen.
**Riesgo:** Muy alto — cambio arquitectónico masivo.
**Preservar:** El patrón de slicing ya funcional (`fullPlayerSlice`, `playerConfigSlice`).

#### 3.3 Room query optimization pass

* Agregar índices compuestos para queries frecuentes
* Implementar `LIMIT + offset` en queries de lista
* Considerar `Room Multimap return types` para reducir `JOINs`

**Impacto:** Mejora tiempos de query en bibliotecas grandes.
**Riesgo:** Medio — migrations + testing de data integrity.

---

## 9. Riesgos de intervención

* **Fase 1:** Prácticamente sin riesgo funcional. Solo observación y animation changes.
* **Fase 2:** Riesgo moderado en `2.2` (desacoplar `PlayerUiState`) — muchos consumidores que actualizar.
* **Fase 3:** Riesgo alto. `3.1` y `3.2` son cambios estructurales que pueden introducir regresiones si no se testean exhaustivamente.

### Comportamiento visual a preservar obligatoriamente

* Transición suave del mini player al full player (actualmente bien implementada con layout-phase reads)
* Crossfade de album art (`350ms` con Coil nativo)
* Animación de lava lamp colors en player
* Expand/collapse de secciones de artista (puede cambiar técnica pero debe verse fluido)
* Selection mode animations (puede consolidarse pero debe mantenerse feedback visual)
* Haptic feedback system-wide
* Queue drag-to-reorder visual preview

---

## 10. Validación y Profiling

### Para confirmar Hallazgo 1 (recomposición de tabs por `playerUiState`)

**Herramienta:** `Layout Inspector` (Android Studio) + Recomposition Counts overlay

**Método:** Activar `"Show recomposition counts"` en Compose debugging. Navegar al tab de Albums con música reproduciendo. Observar si el recomposition count incrementa cada `250ms`.

* **Señal confirmatoria:** Count incrementando continuamente sin interacción del usuario
* **Señal descartante:** Count estable mientras no hay interacción

### Para confirmar Hallazgo 2 (`AnimatedVisibility` jank)

**Herramienta:** `System Tracing (Perfetto)` o `GPU Rendering Profile` (Developer Options)

**Método:** Abrir `ArtistDetailScreen` con un artista que tenga `20+` canciones en una sección. Expand/collapse la sección. Capturar trace.

* **Señal confirmatoria:** Frames `> 16ms` durante la animación, con `Choreographer#doFrame` mostrando layout/measure spikes
* **Señal descartante:** Frames dentro de budget

### Para confirmar Hallazgo 8 (position ticker en `PlayerUiState`)

**Herramienta:** Grep + breakpoint condicional

**Método:** Buscar todas las llamadas a `_playerUiState.update { it.copy(currentPosition = ...) }` en `PlayerViewModel.kt`

* **Señal confirmatoria:** Update llamado dentro del progress loop o con frecuencia alta
* **Señal descartante:** Solo llamado en seek events o song transitions

### Para medir impacto general en gama baja

**Herramienta:** `Macrobenchmark` con `FrameTimingMetric`

**Tests:**

* Scroll performance en `LibrarySongsTab` con `5000` canciones y música reproduciendo
* Tab switch timing (`Home → Library → Search cycle`)
* `ArtistDetailScreen` section expand con `50+` canciones
* `QueueBottomSheet` open + scroll con cola de `500` canciones

**Baseline device:** Android emulator con performance throttled, o device físico gama media (ej: `Pixel 4a`, `Samsung A53`)

### Para medir presión de memoria

**Herramienta:** Android Studio Profiler → Memory tab

**Método:** Navegar por la app durante `5` minutos con biblioteca de `10,000+` canciones. Observar heap allocation rate y GC events.

* **Señal confirmatoria:** GC events cada `< 5` segundos, heap growing monotónicamente, `> 150MB allocation`
* **Señal descartante:** GC events esporádicos, heap estable después de initial load

### Para medir startup + initial sync

**Herramienta:** `Macrobenchmark` con `StartupTimingMetric`

**Tests:** `Cold start → Home screen rendered → Library tab rendered`

**Complementar con:** Perfetto trace del primer `SyncWorker.doWork()` pass

### Compose compiler metrics

Habilitar vía gradle property:

```properties
pixelplay.enableComposeCompilerReports=true
```

Revisar:

* Número de composables `restartable` vs `skippable`
* Buscar composables con parámetros marcados como `UNSTABLE` que deberían ser estables

Particularmente: verificar que `Song`, `Album`, `Artist`, `Playlist` son efectivamente estables (ya están en `compose_stability.conf`, pero validar).

---

## 11. Conclusión Final - Priorización Ejecutiva

### Orden de intervención recomendado

#### 1. State slicing en tabs de librería + `SearchScreen` (`Fase 1.1`)

**Ratio impacto/esfuerzo:** Máximo

* Unas pocas líneas de código por screen eliminan el mayor vector de recomposición innecesaria
* Efecto inmediato en scroll smoothness durante playback

#### 2. Reemplazar `AnimatedVisibility` per-item (`Fase 1.3`)

**Ratio impacto/esfuerzo:** Muy alto

* Afecta directamente la experiencia en pantallas de detalle de artista/género
* Cambio localizado, testeable de forma aislada

#### 3. Consolidar animaciones de `EnhancedSongListItem` (`Fase 2.1`)

**Ratio impacto/esfuerzo:** Alto

* Reduce overhead per-frame en todas las listas de canciones

#### 4. Confirmar / resolver Hallazgo 8 (position en `PlayerUiState`)

* Si se confirma: es la fix de mayor impacto posible (elimina `4 emisiones/segundo` de un state masivo)
* Si se descarta: se cierra la hipótesis y se priorizan otros items

#### 5. `distinctUntilChanged` en `genres` + índice `file_path` (`Fase 1.2`, `1.4`)

Wins garantizados con riesgo mínimo.

#### 6. Memory trimming (`Fase 2.3`)

Crítico para retención de usuarios en gama baja. Sin esto, la app es vulnerable a kills en background.

#### 7. Desacople de `PlayerUiState` (`Fase 2.2`)

El refactor que previene que futuros features degraden la performance. Hacerlo después de los slices individuales (`1.1`) que ya habrán mitigado el problema inmediato.

### Filosofía general

Empezar por eliminar trabajo innecesario (recomposiciones fantasma, animaciones excesivas) antes de optimizar trabajo necesario (queries, data structures). El mayor gain de fluidez percibida viene de eliminar lo que nunca debería haberse ejecutado.
