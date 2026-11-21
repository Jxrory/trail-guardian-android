package com.trailguardian.feature.map

import com.trailguardian.core.model.GeoPoint
import com.trailguardian.core.model.Poi
import com.trailguardian.core.model.PoiType
import org.json.JSONArray
import org.json.JSONObject

object MapStyle {
    fun build(tileBaseUrl: String, contourEnabled: Boolean, contourOpacity: Float): String {
        val sources = JSONObject()
        sources.put(
            "base",
            rasterSource(
                tiles = listOf("$tileBaseUrl/esri/{z}/{y}/{x}"),
                maxZoom = 19,
                attribution = "Tiles © Esri",
            ),
        )
        sources.put(
            "labels",
            rasterSource(
                tiles = listOf("$tileBaseUrl/labels/{z}/{y}/{x}"),
                maxZoom = 19,
                attribution = "Tiles © Esri",
            ),
        )
        sources.put(
            "contour",
            rasterSource(
                tiles = listOf("$tileBaseUrl/contour/{z}/{x}/{y}"),
                maxZoom = 15,
                attribution = "Elevation tiles © Mapzen / AWS Open Data",
            ),
        )
        sources.put("target", emptyGeoJson())
        sources.put("targetEndpoints", emptyGeoJson())
        sources.put("recorded", emptyGeoJson())
        sources.put("recordedEndpoints", emptyGeoJson())
        sources.put("pois", emptyGeoJson())

        val layers = JSONArray()
        layers.put(
            JSONObject().apply {
                put("id", "base")
                put("type", "raster")
                put("source", "base")
                put("minzoom", 0)
                put("maxzoom", 19)
                put("paint", JSONObject().put("raster-fade-duration", 0))
            },
        )
        layers.put(
            JSONObject().apply {
                put("id", "labels")
                put("type", "raster")
                put("source", "labels")
                put("minzoom", 0)
                put("maxzoom", 19)
                put("paint", JSONObject().put("raster-fade-duration", 0))
            },
        )
        layers.put(
            JSONObject().apply {
                put("id", "contour")
                put("type", "raster")
                put("source", "contour")
                put("minzoom", 0)
                put("maxzoom", 19)
                put(
                    "layout",
                    JSONObject().put(
                        "visibility",
                        if (contourEnabled) "visible" else "none",
                    ),
                )
                put(
                    "paint",
                    JSONObject().put(
                        "raster-opacity",
                        contourOpacity.toDouble().coerceIn(0.1, 1.0),
                    ),
                )
            },
        )
        layers.put(
            JSONObject().apply {
                put("id", "target-line")
                put("type", "line")
                put("source", "target")
                put(
                    "layout",
                    JSONObject()
                        .put("line-cap", "butt")
                        .put("line-join", "round"),
                )
                put(
                    "paint",
                    JSONObject()
                        .put("line-color", "#38BDF8")
                        .put("line-width", 3)
                        .put("line-opacity", 0.92)
                        .put("line-dasharray", JSONArray().put(7).put(6)),
                )
            },
        )
        layers.put(
            JSONObject().apply {
                put("id", "target-start")
                put("type", "circle")
                put("source", "targetEndpoints")
                put(
                    "paint",
                    JSONObject()
                        .put("circle-radius", 7)
                        .put("circle-color", JSONArray().put("get").put("color"))
                        .put("circle-stroke-width", 1)
                        .put("circle-stroke-color", JSONArray().put("get").put("stroke")),
                )
            },
        )
        layers.put(
            JSONObject().apply {
                put("id", "recorded-line")
                put("type", "line")
                put("source", "recorded")
                put(
                    "layout",
                    JSONObject()
                        .put("line-cap", "round")
                        .put("line-join", "round"),
                )
                put(
                    "paint",
                    JSONObject()
                        .put("line-color", "#10B981")
                        .put("line-width", 4)
                        .put("line-opacity", 0.95),
                )
            },
        )
        layers.put(
            JSONObject().apply {
                put("id", "recorded-start")
                put("type", "circle")
                put("source", "recordedEndpoints")
                put(
                    "paint",
                    JSONObject()
                        .put("circle-radius", 6)
                        .put("circle-color", JSONArray().put("get").put("color"))
                        .put("circle-stroke-width", 1)
                        .put("circle-stroke-color", JSONArray().put("get").put("stroke")),
                )
            },
        )
        layers.put(
            JSONObject().apply {
                put("id", "pois")
                put("type", "circle")
                put("source", "pois")
                put(
                    "paint",
                    JSONObject()
                        .put("circle-radius", 14)
                        .put("circle-color", JSONArray().put("get").put("color"))
                        .put("circle-stroke-width", 2)
                        .put("circle-stroke-color", "#FFFFFF")
                        .put("circle-opacity", 0.92),
                )
            },
        )
        return JSONObject().apply {
            put("version", 8)
            put("name", "TrailGuardian")
            put("sources", sources)
            put("layers", layers)
        }.toString()
    }

    private fun rasterSource(tiles: List<String>, maxZoom: Int, attribution: String): JSONObject =
        JSONObject().apply {
            put("type", "raster")
            put("tiles", JSONArray().apply { tiles.forEach { put(it) } })
            put("tileSize", 256)
            put("minzoom", 0)
            put("maxzoom", maxZoom)
            put("attribution", attribution)
        }

    private fun emptyGeoJson(): JSONObject = JSONObject().apply {
        put("type", "geojson")
        put(
            "data",
            JSONObject().apply {
                put("type", "FeatureCollection")
                put("features", JSONArray())
            },
        )
    }

    fun lineGeoJson(points: List<GeoPoint>): String {
        val coordinates = JSONArray()
        points.forEach { point ->
            coordinates.put(JSONArray().put(point.lng).put(point.lat))
        }
        val feature = JSONObject().apply {
            put("type", "Feature")
            put(
                "geometry",
                JSONObject().apply {
                    put("type", "LineString")
                    put("coordinates", coordinates)
                },
            )
        }
        return featureCollection(listOf(feature))
    }

    fun endpointsGeoJson(points: List<GeoPoint>, startColor: String): String {
        if (points.size < 2) return featureCollection(emptyList())
        val start = JSONObject().apply {
            put("type", "Feature")
            put(
                "geometry",
                JSONObject().apply {
                    put("type", "Point")
                    put("coordinates", JSONArray().put(points.first().lng).put(points.first().lat))
                },
            )
            put(
                "properties",
                JSONObject().put("color", startColor).put("stroke", "#0F172A"),
            )
        }
        val end = JSONObject().apply {
            put("type", "Feature")
            put(
                "geometry",
                JSONObject().apply {
                    put("type", "Point")
                    put("coordinates", JSONArray().put(points.last().lng).put(points.last().lat))
                },
            )
            put(
                "properties",
                JSONObject().put("color", "#0F172A").put("stroke", startColor),
            )
        }
        return featureCollection(listOf(start, end))
    }

    fun poiGeoJson(pois: List<Poi>): String {
        val features = pois.map { poi ->
            JSONObject().apply {
                put("type", "Feature")
                put(
                    "geometry",
                    JSONObject().apply {
                        put("type", "Point")
                        put("coordinates", JSONArray().put(poi.lng).put(poi.lat))
                    },
                )
                put(
                    "properties",
                    JSONObject()
                        .put("color", poi.type.colorHex)
                        .put("label", poi.type.label)
                        .put("id", poi.id)
                        .put("name", poi.name),
                )
            }
        }
        return featureCollection(features)
    }

    private fun featureCollection(features: List<JSONObject>): String =
        JSONObject().apply {
            put("type", "FeatureCollection")
            put("features", JSONArray().apply { features.forEach { put(it) } })
        }.toString()

    fun poiTypeMeta(type: PoiType): Pair<String, String> = type.label to type.colorHex
}
