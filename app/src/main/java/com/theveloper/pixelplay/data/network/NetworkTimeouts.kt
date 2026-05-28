package com.theveloper.pixelplay.data.network

/**
 * Central source of truth for all network timeout constants.
 *
 * Using named constants instead of magic numbers makes it obvious why each
 * value differs (e.g. AI streaming needs a longer read timeout than a REST
 * metadata call) and ensures changes are propagated everywhere consistently.
 */
object NetworkTimeouts {

    // ── Standard REST / metadata endpoints ──────────────────────────────────
    /** Default TCP connection establishment timeout (ms). */
    const val CONNECT_MS: Long = 15_000L

    /** Default response read timeout (ms). */
    const val READ_MS: Long = 30_000L

    /** Default request body write timeout (ms). */
    const val WRITE_MS: Long = 15_000L

    // ── AI / LLM providers (need extra time for streaming completions) ───────
    /** Connection timeout for AI provider calls (ms). */
    const val AI_CONNECT_MS: Long = 30_000L

    /**
     * Read timeout for AI provider calls (ms).
     * Longer because streaming completions may pause between tokens.
     */
    const val AI_READ_MS: Long = 60_000L

    /** Write timeout for AI provider calls (ms). */
    const val AI_WRITE_MS: Long = 30_000L

    /**
     * Max total AI orchestration time before we give up and try the next
     * provider in the fallback chain. Defined in [AiHandler].
     */
    const val AI_ORCHESTRATION_TIMEOUT_MS: Long = 60_000L

    // ── Cast / remote playback ───────────────────────────────────────────────
    /**
     * Fail-safe unlock for remote Cast seek operations (ms).
     * If the Cast device does not confirm a seek within this window we clear
     * the seeking lock to avoid a permanently frozen seek bar.
     */
    const val CAST_SEEK_UNLOCK_MS: Long = 1_800L

    /** Maximum time to wait for the Cast queue to be fully loaded (ms). */
    const val CAST_QUEUE_LOAD_MS: Long = 25_000L

    // ── GitHub / asset endpoints ─────────────────────────────────────────────
    /** Timeout for GitHub contributor / announcement fetches (ms). */
    const val GITHUB_CONNECT_MS: Int = 10_000
    const val GITHUB_READ_MS: Int = 10_000
}
