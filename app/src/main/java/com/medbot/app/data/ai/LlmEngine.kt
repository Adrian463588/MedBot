package com.medbot.app.data.ai

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LlmEngine @Inject constructor() {
    private var llmInference: LlmInference? = null

    suspend fun loadModel(context: Context, modelFile: File) = withContext(Dispatchers.IO) {
        if (!modelFile.exists()) throw IllegalArgumentException("Model file not found")
        
        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelFile.absolutePath)
            .setMaxTokens(1024)
            .setTemperature(0.7f)
            .build()
            
        llmInference = LlmInference.createFromOptions(context, options)
    }

    fun generateResponseAsync(prompt: String): Flow<String> = callbackFlow {
        val inference = llmInference ?: throw IllegalStateException("Model not loaded")
        
        inference.generateResponseAsync(prompt) { partialResult, done ->
            if (partialResult != null) {
                trySend(partialResult)
            }
            if (done) {
                close()
            }
        }
        awaitClose { }
    }

    fun isLoaded() = llmInference != null
}
