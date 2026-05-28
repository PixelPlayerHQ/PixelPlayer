package com.theveloper.pixelplay.presentation.screens.search.components

import com.theveloper.pixelplay.R
import androidx.annotation.DrawableRes
import java.text.Normalizer
import java.util.Locale

@DrawableRes
fun getGenreImageResource(genreId: String): Int {
    val raw = genreId.trim()
    if (raw.isEmpty()) return R.drawable.rounded_library_music_24

    val normalized = normalizeGenreKey(raw)

    // match exacto
    GENRE_ICON_BY_ALIAS[normalized]?.let { return it }

    // compuesto: "rock / metal", "hip hop, rap"
    for (part in splitGenreParts(normalized)) {
        GENRE_ICON_BY_ALIAS[part]?.let { return it }
    }

    // heurística por keywords
    keywordFallback(normalized)?.let { return it }

    return R.drawable.rounded_library_music_24
}

private fun splitGenreParts(normalized: String): List<String> {
    return normalized
        .split(" / ", "/", ",", ";", "|", " & ", " and ", " - ", "-", " x ", " + ", "\\", " · ")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
}

private fun keywordFallback(key: String): Int? {
    // Orden: específico -> general
    return when {
        // --- 「Catch-all」 / colecciones / charts ---
        "international" in key && ("hit" in key || "top" in key) -> R.drawable.pop_mic
        "hit" in key || "hits" in key || "top " in key || "charts" in key || "chart" in key || "top50" in key || "top 50" in key ->
            R.drawable.pop_mic
        "music" == key || key.startsWith("music ") || key.endsWith(" music") -> R.drawable.rounded_library_music_24

        // --- Metal / Rock / Punk ---
        "metal" in key || "core" in key -> R.drawable.metal_guitar
        "punk" in key || "grunge" in key || "emo" in key -> R.drawable.punk
        "rock" in key -> R.drawable.rock

        // --- Hip hop / urban ---
        "reggaeton" in key || "dembow" in key -> R.drawable.rapper
        "hip hop" in key || "rap" in key || "trap" in key || "drill" in key || "grime" in key -> R.drawable.rapper

        // --- Pop ---
        "pop" in key -> R.drawable.pop_mic

        // --- R&B / Soul / Funk ---
        "rnb" in key || "r&b" in key || "rhythm and blues" in key || "soul" in key || "funk" in key || "disco" in key ->
            R.drawable.synth_piano

        // --- Electronic ---
        "edm" in key || "electronic" in key || "electronica" in key ||
                "techno" in key || "house" in key || "trance" in key || "dubstep" in key ||
                "drum and bass" in key || "dnb" in key || "jungle" in key || "garage" in key || "synthwave" in key ->
            R.drawable.electronic_sound

        // --- Jazz / Classical ---
        "jazz" in key -> R.drawable.sax
        "classical" in key || "orchestra" in key || "symph" in key || "opera" in key || "baroque" in key ->
            R.drawable.clasic_piano

        // --- Folk / Country / Blues / Reggae ---
        "country" in key || "americana" in key || "bluegrass" in key -> R.drawable.banjo
        "folk" in key || "acoustic" in key || "singer songwriter" in key -> R.drawable.accordion
        "blues" in key -> R.drawable.harmonica
        "reggae" in key || "ska" in key || "dancehall" in key || "dub" in key -> R.drawable.maracas

        // --- Latin / world ---
        "latin" in key || "latino" in key || "urbano" in key -> R.drawable.star_angle
        "salsa" in key -> R.drawable.conga
        "bachata" in key -> R.drawable.bongos
        "merengue" in key -> R.drawable.drum
        "cumbia" in key -> R.drawable.maracas

        // --- Soundtrack / game / ambient-mood ---
        "soundtrack" in key || "ost" in key || "score" in key -> R.drawable.rounded_tv_24
        "game" in key || "vgm" in key || "video game" in key -> R.drawable.rounded_touch_app_24
        "ambient" in key || "sleep" in key || "relax" in key || "meditation" in key || "chill" in key ->
            R.drawable.rounded_alarm_24

        // --- Activity / mood ---
        "workout" in key || "gym" in key || "fitness" in key -> R.drawable.electronic_sound
        "party" in key || "club" in key -> R.drawable.rounded_celebration_24
        "focus" in key || "study" in key -> R.drawable.rounded_edit_24

        else -> null
    }
}

private fun normalizeGenreKey(input: String): String {
    var s = input.lowercase(Locale.ROOT).trim()

    s = Normalizer.normalize(s, Normalizer.Form.NFD)
        .replace("\\p{Mn}+".toRegex(), "")

    s = s
        .replace("&", " and ")
        .replace("’", "'")
        .replace("´", "'")
        .replace("–", "-")
        .replace("—", "-")

    s = s.replace("[()\\[\\]{}]".toRegex(), " ")
        .replace("[.:!؟?\"\u201C\u201D\u300C\u300D]".toRegex(), " ")
        .replace("\\s+".toRegex(), " ")
        .trim()

    // normalizaciones comunes
    s = s
        .replace("hip-hop", "hip hop")
        .replace("hiphop", "hip hop")
        .replace("r & b", "rnb")
        .replace("r and b", "rnb")
        .replace("rhythm and blues", "rnb")
        .replace("drum & bass", "drum and bass")
        .replace("d&b", "drum and bass")
        .replace("nu-metal", "nu metal")
        .replace("numetal", "nu metal")
        .replace("lofi", "lo fi")
        .replace("lo-fi", "lo fi")

    return s
}

private object GenreMapBuilder {
    fun build(): Map<String, Int> {
        val map = HashMap<String, Int>(900)

        fun putAll(@DrawableRes icon: Int, vararg aliases: String) {
            for (a in aliases) map[normalizeGenreKey(a)] = icon
        }

        // --------- CATCH-ALL / GENERIC ---------
        putAll(R.drawable.rounded_library_music_24,
            "music", "all music", "all", "general", "various", "various artists",
            "misc", "miscellaneous", "other", "unknown genre", "uncategorized"
        )
        putAll(R.drawable.pop_mic,
            "hits", "international hits", "global hits", "world hits",
            "top hits", "top 40", "top40", "top 50", "top50",
            "charts", "chart", "trending", "viral", "best of", "popular",
            "radio", "radio hits", "mainstream"
        )

        // --------- ROCK (más variantes) ---------
        putAll(R.drawable.rock,
            "rock", "new rock", "modern rock",
            "classic rock", "hard rock", "soft rock",
            "alternative rock", "alt rock", "alt-rock",
            "indie rock", "garage rock", "psychedelic rock",
            "progressive rock", "prog rock", "post rock",
            "art rock", "arena rock", "southern rock",
            "blues rock", "folk rock", "country rock",
            "glam rock", "space rock", "surf rock",
            "j rock", "j-rock", "k rock", "k-rock",
            "britpop", "shoegaze", "noise rock",
            "math rock", "stoner rock"
        )

        // --------- POP (incluye indie pop bien cubierto) ---------
        putAll(R.drawable.pop_mic,
            "pop", "pop rock", "dance pop", "electropop",
            "synthpop", "synth pop", "teen pop", "adult contemporary",
            "indie pop", "alt pop", "alternative pop",
            "dream pop", "hyperpop", "city pop",
            "k pop", "k-pop", "j pop", "j-pop", "c pop", "c-pop"
        )

        // --------- INDIE / LOFI / CHILL ---------
        putAll(R.drawable.idk_indie_ig,
            "indie", "indie rock", "indie pop",
            "lo fi", "lo-fi", "lofi",
            "bedroom pop", "chill pop"
        )

        // --------- METAL (más subgéneros típicos) ---------
        putAll(R.drawable.metal_guitar,
            "metal", "heavy metal", "thrash metal", "death metal", "black metal",
            "doom metal", "sludge metal", "stoner metal",
            "power metal", "symphonic metal", "progressive metal",
            "industrial metal", "nu metal", "nu-metal",
            "metalcore", "deathcore", "hardcore", "post metal",
            "melodic death metal", "groove metal", "folk metal", "viking metal"
        )

        // --------- PUNK / EMO / GRUNGE ---------
        putAll(R.drawable.punk,
            "punk", "punk rock", "pop punk", "hardcore punk",
            "post punk", "post-punk",
            "grunge",
            "emo", "midwest emo",
            "post hardcore", "post-hardcore",
            "ska punk"
        )

        // --------- HIP HOP / RAP / URBAN ---------
        putAll(R.drawable.rapper,
            "hip hop", "hip-hop", "rap", "trap",
            "drill", "grime", "boom bap", "conscious hip hop",
            "gangsta rap", "g funk", "cloud rap"
        )

        // --------- R&B / SOUL / FUNK / DISCO ---------
        putAll(R.drawable.synth_piano,
            "rnb", "r&b", "r&b / soul",
            "soul", "neo soul", "funk", "disco", "motown", "quiet storm"
        )

        // --------- ELECTRONIC / EDM ---------
        putAll(R.drawable.electronic_sound,
            "electronic", "electronica", "edm", "electro",
            "house", "deep house", "tech house", "progressive house",
            "techno", "trance", "psytrance",
            "dubstep", "drum and bass", "dnb", "jungle",
            "breakbeat", "uk garage", "idm",
            "synthwave", "vaporwave", "retrowave",
            "downtempo", "trip hop", "ambient"
        )

        // --------- JAZZ ---------
        putAll(R.drawable.sax,
            "jazz", "smooth jazz", "bebop", "swing", "big band",
            "cool jazz", "hard bop", "fusion", "acid jazz", "latin jazz"
        )

        // --------- CLASSICAL ---------
        putAll(R.drawable.clasic_piano,
            "classical", "orchestra", "orchestral",
            "symphony", "symphonic",
            "piano", "concerto", "sonata",
            "opera", "baroque", "romantic", "modern classical"
        )

        // --------- COUNTRY / FOLK / BLUES / REGGAE ---------
        putAll(R.drawable.banjo, "country", "americana", "bluegrass", "honky tonk", "alt country")
        putAll(R.drawable.accordion, "folk", "acoustic", "singer-songwriter", "indie folk")
        putAll(R.drawable.harmonica, "blues", "delta blues", "chicago blues", "electric blues")
        putAll(R.drawable.maracas, "reggae", "ska", "dancehall", "dub")

        // --------- LATIN / WORLD / 「INTERNATIONAL」 ---------
        putAll(R.drawable.star_angle,
            "latin", "latino", "latin pop", "urbano latino", "latin urban",
            "world", "world music", "international", "international music",
            "afro", "afrobeats", "afrobeat", "afropop"
        )
        putAll(R.drawable.rapper, "reggaeton", "latin trap", "trap latino")
        putAll(R.drawable.conga, "salsa")
        putAll(R.drawable.bongos, "bachata")
        putAll(R.drawable.drum, "merengue")
        putAll(R.drawable.maracas,
            "cumbia", "vallenato",
            "regional mexicano", "banda", "corridos", "mariachi", "ranchera", "norteno", "norteño"
        )

        // --------- OLDIES / DECADES ---------
        putAll(R.drawable.rounded_schedule_24,
            "oldies", "retro",
            "50s", "60s", "70s", "80s", "90s", "00s", "2000s",
            "throwback"
        )

        // --------- SOUNDTRACK / GAME ---------
        putAll(R.drawable.rounded_tv_24, "soundtrack", "ost", "score", "film", "movie", "anime soundtrack")
        putAll(R.drawable.rounded_touch_app_24, "gaming", "video game music", "vgm", "game soundtrack")

        // --------- MOOD / ACTIVITY ---------
        putAll(R.drawable.rounded_alarm_24,
            "sleep", "relax", "relaxation", "meditation", "calm",
            "chill", "spa", "nature", "nature sounds", "white noise", "rain", "asmr"
        )
        putAll(R.drawable.electronic_sound, "workout", "gym", "fitness", "training", "running")
        putAll(R.drawable.rounded_celebration_24, "party", "club", "dance", "celebration")
        putAll(R.drawable.rounded_edit_24, "focus", "study", "productivity")

        // --------- UNKNOWN ---------
        putAll(R.drawable.rounded_question_mark_24,
            "unknown", "none", "na", "n a", "unspecified"
        )

        return map
    }
}

private val GENRE_ICON_BY_ALIAS: Map<String, Int> by lazy(LazyThreadSafetyMode.PUBLICATION) {
    GenreMapBuilder.build()
}

