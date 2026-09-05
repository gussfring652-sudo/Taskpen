package com.antakih.taskpen.domain.mlkit

import android.util.Log
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.Ink
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DigitalInkHelper @Inject constructor() {

    private var model: DigitalInkRecognitionModel? = null

    suspend fun downloadAndInitModel(languageTag: String = "es"): Boolean {
        return try {
            val modelIdentifier = DigitalInkRecognitionModelIdentifier.fromLanguageTag(languageTag)
            if (modelIdentifier == null) {
                Log.e("DigitalInkHelper", "No se encontró identificador para el idioma: $languageTag")
                return false
            }

            val currentModel = DigitalInkRecognitionModel.builder(modelIdentifier).build()
            model = currentModel

            val remoteModelManager = RemoteModelManager.getInstance()
            val isDownloaded = remoteModelManager.isModelDownloaded(currentModel).await()

            if (!isDownloaded) {
                Log.d("DigitalInkHelper", "Descargando modelo de tinta digital...")
                val conditions = DownloadConditions.Builder().build()
                remoteModelManager.download(currentModel, conditions).await()
                Log.d("DigitalInkHelper", "Modelo descargado exitosamente")
            } else {
                Log.d("DigitalInkHelper", "Modelo ya está descargado localmente")
            }
            true
        } catch (e: Throwable) {
            Log.e("DigitalInkHelper", "Error al inicializar/descargar el modelo: ${e.message}", e)
            false
        }
    }

    suspend fun recognizeText(ink: Ink): String {
        return try {
            val currentModel = model ?: run {
                val success = downloadAndInitModel()
                if (!success) return ""
                model
            } ?: return ""

            val recognizer = DigitalInkRecognition.getClient(
                DigitalInkRecognizerOptions.builder(currentModel).build()
            )
            val result = recognizer.recognize(ink).await()
            recognizer.close()
            result.candidates.firstOrNull()?.text ?: ""
        } catch (e: Throwable) {
            Log.e("DigitalInkHelper", "Error durante el reconocimiento de texto: ${e.message}", e)
            ""
        }
    }
}
