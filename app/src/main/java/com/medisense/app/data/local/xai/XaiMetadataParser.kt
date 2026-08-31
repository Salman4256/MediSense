package com.medisense.app.data.local.xai

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class XaiMetadataParser @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    private var featureImportances: Map<String, Map<String, Float>> = emptyMap()
    private var diseaseRules: Map<String, String> = emptyMap()
    private var featureDisplayNames: Map<String, String> = emptyMap()
    private var modelVersion: String = "1.0"

    init {
        loadMetadata()
    }

    private fun loadMetadata() {
        loadFeatureImportances()
        loadDiseaseRules()
        loadFeatureMetadata()
    }

    private fun loadFeatureImportances() {
        try {
            val jsonString = context.assets.open("xai/feature_importance.json")
                .bufferedReader()
                .use { it.readText() }

            val jsonObject = JSONObject(jsonString)
            val result = mutableMapOf<String, Map<String, Float>>()

            val keys = jsonObject.keys()
            while (keys.hasNext()) {
                val disease = keys.next()
                val featureObj = jsonObject.getJSONObject(disease)
                val featureMap = mutableMapOf<String, Float>()
                val featureKeys = featureObj.keys()
                while (featureKeys.hasNext()) {
                    val feature = featureKeys.next()
                    val weight = featureObj.optDouble(feature, 0.0).toFloat()
                    featureMap[normalizeKey(feature)] = weight
                }
                result[normalizeKey(disease)] = featureMap
            }
            featureImportances = result
        } catch (e: Exception) {
            println("MediSense-XAI: Error loading feature_importance.json: ${e.message}")
            featureImportances = emptyMap()
        }
    }

    private fun loadDiseaseRules() {
        try {
            val jsonString = context.assets.open("xai/disease_rules.json")
                .bufferedReader()
                .use { it.readText() }

            val jsonObject = JSONObject(jsonString)
            val result = mutableMapOf<String, String>()

            val keys = jsonObject.keys()
            while (keys.hasNext()) {
                val disease = keys.next()
                result[normalizeKey(disease)] = jsonObject.optString(disease, "")
            }
            diseaseRules = result
        } catch (e: Exception) {
            println("MediSense-XAI: Error loading disease_rules.json: ${e.message}")
            diseaseRules = emptyMap()
        }
    }

    private fun loadFeatureMetadata() {
        try {
            val jsonString = context.assets.open("xai/xai_feature_metadata.json")
                .bufferedReader()
                .use { it.readText() }

            val jsonObject = JSONObject(jsonString)
            modelVersion = jsonObject.optString("model_version", "1.0")

            val featuresArray = jsonObject.optJSONArray("features")
            val nameMap = mutableMapOf<String, String>()
            if (featuresArray != null) {
                for (i in 0 until featuresArray.length()) {
                    val obj = featuresArray.getJSONObject(i)
                    val feature = obj.optString("feature")
                    val displayName = obj.optString("displayName")
                    if (feature.isNotBlank() && displayName.isNotBlank()) {
                        nameMap[normalizeKey(feature)] = displayName
                    }
                }
            }
            featureDisplayNames = nameMap
        } catch (e: Exception) {
            println("MediSense-XAI: Error loading xai_feature_metadata.json: ${e.message}")
        }
    }

    fun getFeatureImportances(): Map<String, Map<String, Float>> = featureImportances

    fun getDiseaseRules(): Map<String, String> = diseaseRules

    fun getFeatureDisplayNames(): Map<String, String> = featureDisplayNames

    fun getModelVersion(): String = modelVersion

    private fun normalizeKey(key: String): String {
        return key.trim().lowercase().replace("\\s+".toRegex(), " ")
    }
}
