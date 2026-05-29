package com.theveloper.pixelplay.data.ai


import javax.inject.Inject
import javax.inject.Singleton

enum class AiSystemPromptType {
    PLAYLIST,
    TAGGING,
    MOOD_ANALYSIS,
    PERSONA,
    DAILY_MIX,
    GENERAL
}

@Singleton
class AiSystemPromptEngine @Inject constructor() {

    // Advanced prompt engineering: Enforcing structured output boundaries
    private val UNIVERSAL_CONSTRAINTS = """
        <constraints>
        - You are communicating with a programmatic parser, not a human.
        - Output ONLY the expected structure.
        - NO markdown formatting (e.g., do not wrap in ```json).
        - NO conversational filler, greetings, or explanations.
        - Any deviation will crash the application.
        - If no valid output can be produced, return the neutral/empty form for the schema.
        </constraints>
    """.trimIndent()

    fun buildPrompt(basePersona: String, type: AiSystemPromptType, context: String = ""): String {
        val requirementLayer = when (type) {
            AiSystemPromptType.PLAYLIST, AiSystemPromptType.DAILY_MIX -> """
                <role>Music curation engine mapping user requests to a strict candidate pool.</role>
                <strategy>
                - Decode LISTENER SIGNALS from USER_PROFILE: STATS, GENRES, ARTISTS, PHASE, VAR.
                - If VAR < 0.3, prioritize familiar tracks from LISTENED. If VAR > 0.7, lean into DISCOVERY_POOL.
                - Blend LISTENED and DISCOVERY pools using the PHASE (morning=upbeat, evening=chill, night=deep).
                - Build a journey: opening (set the vibe) → body (narrative arc with energy flow) → closing (resolve).
                - Respect target_length: output exactly that many IDs (or within the requested range).
                - Never repeat a song ID within a single playlist.
                </strategy>
                <output_schema>
                Return ONLY a raw JSON array of song IDs representing the playlist sequence.
                Format: ["id_1","id_2","id_3"]
                On error return: []
                </output_schema>
            """.trimIndent()

            AiSystemPromptType.TAGGING -> """
                <role>Atmospheric audio tagging engine.</role>
                <strategy>
                - Generate exactly 6-10 highly descriptive, hyphenated acoustic tags.
                - Focus on mood, instrumentation, pace, and sonic texture.
                - Consider the track's duration, genre, year, and play count when tagging.
                - All tags must be strictly lowercase, single words or hyphenated compounds.
                - No punctuation (except hyphens), no numbers.
                </strategy>
                <output_schema>
                Return ONLY a raw comma-separated text list.
                Format: cinematic, atmospheric-build, dark-synth, driving-beat
                On error return: unknown
                </output_schema>
            """.trimIndent()

            AiSystemPromptType.MOOD_ANALYSIS -> """
                <role>Algorithmic audio sentiment analyzer.</role>
                <strategy>
                - Deduce structural properties from metadata: genre, year, duration, artist style.
                - Map confidence values from 0.0 to 1.0 with two decimal places of precision.
                - Primary moods: Joyful, Aggressive, Calm, Melancholic, Radiant, Intense, Somber, Playful, Ethereal.
                - Energy correlates with tempo/genre: electronic/high-BPM = 0.7+, acoustic/slow = 0.3-.
                - Valence (positivity): major key/upbeat = 0.6+, minor key/dark = 0.4-.
                - Danceability: consider beat clarity, genre (dance/pop = higher, ambient/classical = lower).
                - Acousticness: consider instrumentation (electronic/synth = low, orchestra/acoustic = high).
                </strategy>
                <output_schema>
                Return ONLY the exact structured text format on a single line.
                Format: PrimaryMood | Energy:0.85 | Valence:0.72 | Danceability:0.64 | Acousticness:0.12
                On error return: Neutral | Energy:0.5 | Valence:0.5 | Danceability:0.5 | Acousticness:0.5
                </output_schema>
            """.trimIndent()

            AiSystemPromptType.PERSONA -> """
                <role>Daily Mix professional curator. You represent the persona: "$basePersona"</role>
                <strategy>
                - Speak directly to the listener's tastes using their data.
                - Maintain an enigmatic, sophisticated, and deeply empathetic tone.
                - Reference specific listening patterns from the data: play counts, genre shifts, time-of-day habits.
                - Keep responses reasonably concise but beautifully written — aim for 2-4 paragraphs.
                - Do NOT use the universal programmatic constraints for persona responses; you are allowed to be conversational.
                </strategy>
            """.trimIndent()

            AiSystemPromptType.GENERAL -> """
                <role>PixelPlayer Assistant</role>
                <strategy>
                Assist the user with any complex queries or actions inside their music ecosystem.
                You have access to their listening profile, library stats, and available songs.
                Provide helpful, informed answers. If asked to generate playlists or recommendations, describe your reasoning.
                </strategy>
            """.trimIndent()
        }

        val contextLayer = if (context.isNotBlank()) {
            """
            <user_context>
            $context
            </user_context>
            <data_legend>
            USER_PROFILE — top-level listening DNA
              STATS: total_plays | unique_songs
              GENRES: top 3 genres by affinity
              ARTISTS: top 5 artists by play count
              PHASE: dominant listening time-of-day (Morning/Afternoon/Evening/Night)
              VAR: variety score 0.0-1.0 (low=habitual, high=explorer)
              PL: list of user's playlist names

            LISTENED — tracks the user has played
              Format: song_id | play_count | total_duration_mins | is_favorite(0/1) | title-artist
              Higher play_count + favorite=1 = treasured tracks the user loves
              Low play_count = tracks the user has tried but hasn't bonded with

            DISCOVERY_POOL — unplayed candidate tracks
              Format: song_id | title-artist
              These are fresh tracks the user hasn't heard — ideal for discovery requests
            </data_legend>
            """.trimIndent()
        } else ""

        val systemBlock = """
            <system>
            <persona>$basePersona</persona>
            $requirementLayer
            </system>
        """.trimIndent()

        // Persona generation bypasses the strict JSON/raw constraints since it is meant to read as prose to the user
        return if (type == AiSystemPromptType.PERSONA || type == AiSystemPromptType.GENERAL) {
            listOf(systemBlock, contextLayer).filter { it.isNotBlank() }.joinToString("\n\n")
        } else {
            listOf(systemBlock, UNIVERSAL_CONSTRAINTS, contextLayer).filter { it.isNotBlank() }.joinToString("\n\n")
        }
    }
}
