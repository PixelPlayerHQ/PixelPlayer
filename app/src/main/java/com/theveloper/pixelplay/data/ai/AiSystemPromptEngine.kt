package com.theveloper.pixelplay.data.ai


import javax.inject.Inject
import javax.inject.Singleton

enum class AiSystemPromptType {
    PLAYLIST,
    METADATA,
    TAGGING,
    MOOD_ANALYSIS,
    PERSONA,
    GENERAL
}

@Singleton
class AiSystemPromptEngine @Inject constructor() {

    fun buildPrompt(basePersona: String, type: AiSystemPromptType, context: String = ""): String {
        val requirementLayer = when (type) {
            AiSystemPromptType.PLAYLIST -> """
                <instructions>
                Create a cohesive musical journey. Prioritize track flow, harmonic compatibility, and genre-appropriate energy progression.
                </instructions>
                
                <format_rules>
                - Output MUST be a raw JSON array of song IDs.
                - NO markdown, NO conversational text.
                - Example: ["id1", "id2", "id3"]
                </format_rules>
            """.trimIndent()
 
            AiSystemPromptType.METADATA -> """
                <instructions>
                Provide accurate technical metadata. Use specifics (e.g. 'Nu-Jazz' instead of 'Jazz').
                </instructions>
                
                <format_rules>
                - Output MUST be a raw JSON object matching this schema:
                  {"title": "...", "artist": "...", "album": "...", "genre": "..."}
                - NO markdown, NO conversational text.
                - Example: {"title": "Levitating", "artist": "Dua Lipa", "album": "Future Nostalgia", "genre": "Dance-Pop"}
                </format_rules>
            """.trimIndent()
 
            AiSystemPromptType.TAGGING -> """
                <instructions>
                Provide 5-8 descriptive, evocative tags. Mix technical with atmospheric.
                </instructions>
                
                <format_rules>
                - Output MUST be a raw CSV string. No JSON, no markdown.
                - Example: lofi, chill, nocturnal, rainy, study, vinyl-crackle
                </format_rules>
            """.trimIndent()
 
            AiSystemPromptType.MOOD_ANALYSIS -> """
                <instructions>
                Analyze the primary mood and core valence/energy metrics.
                </instructions>
                
                <format_rules>
                - Format: Mood | Energy:0.X | Valence:0.X | Danceability:0.X | Acousticness:0.X
                - Example: Melancholic | Energy:0.3 | Valence:0.2 | Danceability:0.1 | Acousticness:0.8
                </format_rules>
            """.trimIndent()
 
            AiSystemPromptType.PERSONA -> """
                <instructions>
                Adopt the persona of a sophisticated sonic expert. Use poetic yet concise language.
                Reference the user's metrics to make recommendations feel personal.
                </instructions>
            """.trimIndent()
 
            AiSystemPromptType.GENERAL -> """
                <instructions>
                Respond clearly as a music-centric AI assistant.
                </instructions>
            """.trimIndent()
        }
 
        val contextLayer = if (context.isNotBlank()) {
            "<user_context>\n$context\n</user_context>"
        } else ""
 
        return """
            <persona>
            $basePersona
            </persona>
            
            $contextLayer
            
            <requirements>
            $requirementLayer
            </requirements>
        """.trimIndent()
    }
}
