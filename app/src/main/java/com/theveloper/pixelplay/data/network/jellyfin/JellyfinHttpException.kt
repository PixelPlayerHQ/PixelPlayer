package com.theveloper.pixelplay.data.network.jellyfin

/**
 * A typed HTTP failure from [JellyfinApiService], carrying the status code and the raw
 * `Retry-After` header instead of burying them inside [message] (`GEN-AP-06`, R19 of the
 * downloads plan).
 *
 * [retryAfter] is passed through unparsed: HTTP allows it as either a number of seconds or an
 * HTTP-date, and deciding between the two — and clamping the result — is the caller's job
 * (`F3.3` in `docs/downloads/F3.md`), not this transport-level type's.
 */
class JellyfinHttpException(
    val statusCode: Int,
    val retryAfter: String?,
    message: String,
) : Exception(message)
