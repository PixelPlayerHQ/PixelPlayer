package com.theveloper.pixelplay.data.github

import com.theveloper.pixelplay.data.network.NetworkTimeouts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class GitHubContributor(
    val login: String,
    val avatar_url: String,
    val html_url: String,
    val contributions: Int,
    val type: String = "User"  // "User" or "Bot"
)

@Singleton
class GitHubContributorService @Inject constructor() {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Fetches contributors from the GitHub repository
     */
    suspend fun fetchContributors(
        owner: String = "theovilardo",
        repo: String = "PixelPlay"
    ): Result<List<GitHubContributor>> {
        return withContext(Dispatchers.IO) {
            try {
                val url = "https://api.github.com/repos/$owner/$repo/contributors?per_page=100"
                val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection

                connection.requestMethod = "GET"
                connection.addRequestProperty("Accept", "application/vnd.github.v3+json")
                connection.connectTimeout = NetworkTimeouts.GITHUB_CONNECT_MS
                connection.readTimeout = NetworkTimeouts.GITHUB_READ_MS

                val responseCode = connection.responseCode
                if (responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val contributors = json.decodeFromString<List<GitHubContributor>>(response)
                    // Filter out bots and sort by contributions descending
                    val sorted = contributors
                        .filter { it.type != "Bot" }
                        .sortedByDescending { it.contributions }
                    Timber.d("Fetched ${sorted.size} contributors from GitHub (excluding bots)")
                    Result.success(sorted)
                } else {
                    val errorMessage = connection.errorStream?.bufferedReader()?.use { it.readText() }
                    Timber.e("Failed to fetch contributors: $responseCode - $errorMessage")
                    Result.failure(Exception("Failed to fetch contributors: $responseCode"))
                }
            } catch (e: Exception) {
                Timber.e(e, "Exception fetching contributors")
                Result.failure(e)
            }
        }
    }
}
