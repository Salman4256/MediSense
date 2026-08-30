package com.medisense.app.ml

import android.content.Context
import org.tensorflow.lite.Interpreter
import kotlinx.serialization.json.Json
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DiseasePredictionEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var interpreter: Interpreter? = null
    private var symptomsList: List<String> = emptyList()
    private var labelsList: List<String> = emptyList()

    init {
        loadModelAndMetadata()
    }

    private fun loadModelAndMetadata() {
        try {
            val modelBuffer = loadModelFile(context, "DiseasePredictionModel.tflite")
            interpreter = Interpreter(modelBuffer).apply {
                allocateTensors()
            }

            val inputTensor = interpreter?.getInputTensor(0)
            val outputTensor = interpreter?.getOutputTensor(0)
            println("MediSense-ML Model Input: DataType=${inputTensor?.dataType()}, Shape=${inputTensor?.shape()?.contentToString()}")
            println("MediSense-ML Model Output: DataType=${outputTensor?.dataType()}, Shape=${outputTensor?.shape()?.contentToString()}")

            val json = Json { ignoreUnknownKeys = true }
            val symptomsJson = context.assets.open("symptoms.json").bufferedReader().use { it.readText() }
            val labelsJson = context.assets.open("labels.json").bufferedReader().use { it.readText() }

            symptomsList = json.decodeFromString(symptomsJson)
            labelsList = json.decodeFromString(labelsJson)
        } catch (e: Exception) {
            println("MediSense-ML Load Error: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun loadModelFile(context: Context, modelName: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    fun getSymptoms(): List<String> = symptomsList

    fun predict(symptomVector: FloatArray): FloatArray {
        if (interpreter == null) {
            throw IllegalStateException("LiteRT Interpreter is not initialized")
        }
        try {
            val paddedInput = if (symptomVector.size == 377) {
                symptomVector
            } else {
                FloatArray(377).apply {
                    System.arraycopy(symptomVector, 0, this, 0, minOf(symptomVector.size, 377))
                }
            }
            val output = Array(1) { FloatArray(773) }
            interpreter?.run(arrayOf(paddedInput), output)
            return output[0]
        } catch (e: Exception) {
            println("MediSense-ML Inference Error: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }

    fun getLabels(): List<String> = labelsList

    fun close() {
        interpreter?.close()
        interpreter = null
    }
}
