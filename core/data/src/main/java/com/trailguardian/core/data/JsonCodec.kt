package com.trailguardian.core.data

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.trailguardian.core.model.GeoPoint

object JsonCodec {
    private val gson = Gson()
    private val pointsType = object : TypeToken<List<GeoPoint>>() {}.type
    private val intsType = object : TypeToken<List<Int>>() {}.type

    fun pointsToJson(points: List<GeoPoint>): String = gson.toJson(points)

    fun pointsFromJson(json: String?): List<GeoPoint> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            gson.fromJson(json, pointsType) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun intsToJson(values: List<Int>): String = gson.toJson(values)

    fun intsFromJson(json: String?): List<Int> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            gson.fromJson(json, intsType) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }
}
