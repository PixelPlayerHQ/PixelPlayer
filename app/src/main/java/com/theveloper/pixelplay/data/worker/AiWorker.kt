package com.theveloper.pixelplay.data.worker


import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.theveloper.pixelplay.data.ai.AiNotificationManager
import com.theveloper.pixelplay.data.ai.AiOrchestrator
import com.theveloper.pixelplay.data.ai.AiSystemPromptType
import com.theveloper.pixelplay.data.ai.UserProfileDigestGenerator
import com.theveloper.pixelplay.data.database.MusicDao
import com.theveloper.pixelplay.data.database.toSongs
import com.theveloper.pixelplay.data.model.Song
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber

@HiltWorker
class AiWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val orchestrator: AiOrchestrator,
    private val notificationManager: AiNotificationManager,
    private val musicDao: MusicDao,
    private val digestGenerator: UserProfileDigestGenerator,
    private val preferencesRepo: com.theveloper.pixelplay.data.preferences.AiPreferencesRepository
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val INPUT_PROMPT = "input_prompt"
        const val INPUT_TYPE = "input_type"
        const val INPUT_TEMP = "input_temp"
        const val OUTPUT_RESULT = "output_result"
        const val WORK_NAME = "ai_generation_worker"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prompt = inputData.getString(INPUT_PROMPT) ?: return@withContext Result.failure()
        val typeStr = inputData.getString(INPUT_TYPE) ?: AiSystemPromptType.GENERAL.name
        val type = AiSystemPromptType.valueOf(typeStr)
        val temp = inputData.getFloat(INPUT_TEMP, 0.7f)

        Timber.d("AiWorker starting. Type: $type, Prompt: $prompt")
        
        notificationManager.showProgress("AI is thinking...", "Processing your request", 0)

        try {
            // Include deep user context for relevant tasks
            val context = if (type == AiSystemPromptType.PLAYLIST || 
                            type == AiSystemPromptType.TAGGING || 
                            type == AiSystemPromptType.PERSONA) {
                val allSongs = musicDao.getAllSongsList().toSongs()
                val isSafe = preferencesRepo.isSafeTokenLimitEnabled.first()
                digestGenerator.generateDigest(allSongs, isSafe)
            } else ""

            val result = orchestrator.generateContent(
                prompt = prompt,
                type = type,
                temperature = temp,
                context = context
            )

            // Handle the result based on type
            handleResult(type, result)

            notificationManager.showCompletion("AI Task Complete", "Successfully processed your request.")
            Result.success(workDataOf(OUTPUT_RESULT to result))
        } catch (e: Exception) {
            Timber.e(e, "AiWorker failed")
            notificationManager.showCompletion("AI Task Failed", e.message ?: "Unknown error")
            Result.failure()
        }
    }

    private suspend fun handleResult(type: AiSystemPromptType, result: String) {
        when (type) {
            AiSystemPromptType.PLAYLIST -> {
                // Logic to save the generated playlist could go here
                // For now we'll just log it or save to a special "AI Suggestions" table if it exists
                Timber.i("AI Generated Playlist: $result")
            }
            AiSystemPromptType.TAGGING -> {
                Timber.i("AI Generated Tags: $result")
            }
            else -> {}
        }
    }
}
