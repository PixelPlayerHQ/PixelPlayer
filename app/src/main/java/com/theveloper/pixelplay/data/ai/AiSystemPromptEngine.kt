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

    fun buildPrompt(basePersona: String, type: AiSystemPromptType, context: String = ""): String {
        val constraints = """
- You are communicating with a programmatic parser, not a human. Output ONLY the expected structure.
- NO markdown formatting (e.g., do not wrap in ```json). NO conversational filler, greetings, or explanations.
- If no valid output can be produced, return the neutral/empty form for the schema.
        """.trimIndent()

        val requirementLayer = when (type) {
            AiSystemPromptType.PLAYLIST, AiSystemPromptType.DAILY_MIX -> """
<role>Music curation engine mapping user requests to a strict candidate pool.</role>
<strategy>
- Decode LISTENER SIGNALS from USER_PROFILE: STATS, GENRES, ARTISTS, PHASE, VAR.
- If VAR < 0.3 prioritize familiar tracks; if VAR > 0.7 lean into DISCOVERY_POOL.
- Blend pools using PHASE (morning=upbeat, evening=chill, night=deep).
- Build a journey: opening (set vibe) -> body (narrative arc) -> closing (resolve).
- Respect target_length; never repeat a song ID within a single playlist.
</strategy>
<output_schema>Return ONLY a raw JSON array of song IDs. Format: ["id_1","id_2","id_3"]. On error: []</output_schema>
            """.trimIndent()

            AiSystemPromptType.TAGGING -> """
<role>Atmospheric audio tagging engine.</role>
<strategy>
- Generate 6-10 hyphenated acoustic tags (mood, instrumentation, pace, texture).
- Consider track duration, genre, year, and play count.
- Tags must be lowercase, single words or hyphenated compounds. No punctuation or numbers.
</strategy>
<output_schema>Return ONLY a comma-separated list. Format: cinematic, atmospheric-build, dark-synth, driving-beat. On error: unknown</output_schema>
            """.trimIndent()

            AiSystemPromptType.MOOD_ANALYSIS -> """
<role>Algorithmic audio sentiment analyzer.</role>
<strategy>
- Primary moods: Joyful, Aggressive, Calm, Melancholic, Radiant, Intense, Somber, Playful, Ethereal.
- Energy: electronic/high-BPM >= 0.7, acoustic/slow <= 0.3. Valence: major/upbeat >= 0.6, minor/dark <= 0.4.
- Danceability: dance/pop high, ambient/classical low. Acousticness: electronic low, orchestral high.
</strategy>
<output_schema>Return ONE line: PrimaryMood | Energy:0.85 | Valence:0.72 | Danceability:0.64 | Acousticness:0.12. On error: Neutral | Energy:0.5 | Valence:0.5 | Danceability:0.5 | Acousticness:0.5</output_schema>
            """.trimIndent()

            AiSystemPromptType.PERSONA -> """
<role>Daily Mix curator. Persona: "$basePersona"</role>
<strategy>
- Speak directly to the listener using their data. Reference play counts, genre shifts, time-of-day habits.
- Sophisticated, empathetic tone. 2-4 paragraphs. No programmatic constraints — conversational allowed.
</strategy>
            """.trimIndent()

            AiSystemPromptType.GENERAL -> """
<role>PixelPlayer Assistant</role>
<strategy>
- Assist with any complex queries inside the user's music ecosystem.
- Provide helpful, informed answers using their profile, library stats, and available songs.
- If generating playlists or recommendations, describe your reasoning.
</strategy>
            """.trimIndent()
        }

        val contextLayer = if (context.isNotBlank()) {
            """
<user_context>$context</user_context>
<data_legend>
USER_PROFILE: STATS (total_plays|unique_songs), GENRES (top 3), ARTISTS (top 5), PHASE (Morning/Afternoon/Evening/Night), VAR (0.0-1.0), PL (playlist names)
LISTENED: song_id|play_count|total_duration_mins|is_favorite(0/1)|title-artist
DISCOVERY_POOL: song_id|title-artist — unplayed candidate tracks
</data_legend>
            """.trimIndent()
        } else ""

        val systemBlock = "<system>\n<persona>$basePersona</persona>\n$requirementLayer\n</system>"

        return buildString {
            appendLine(systemBlock)
            if (type != AiSystemPromptType.PERSONA && type != AiSystemPromptType.GENERAL && constraints.isNotBlank()) {
                appendLine()
                appendLine(constraints)
            }
            if (contextLayer.isNotBlank()) {
                appendLine()
                appendLine(contextLayer)
            }
        }
    }
}
