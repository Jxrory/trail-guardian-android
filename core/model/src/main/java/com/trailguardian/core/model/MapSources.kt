package com.trailguardian.core.model

data class MapSourceConfig(
    val id: String,
    val label: String,
    val shortLabel: String,
    val baseUrl: String,
    val overlayUrls: List<String>,
    val maxZoom: Int,
    val maxNativeZoom: Int,
    val attribution: String,
)

object MapSources {
    val ESRI_SATELLITE = MapSourceConfig(
        id = "esri-satellite",
        label = "Esri 卫星",
        shortLabel = "Esri 卫星",
        baseUrl = "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}",
        overlayUrls = listOf(
            "https://server.arcgisonline.com/ArcGIS/rest/services/Reference/World_Boundaries_and_Places/MapServer/tile/{z}/{y}/{x}",
        ),
        maxZoom = 19,
        maxNativeZoom = 19,
        attribution = "Tiles © Esri",
    )

    val CONTOUR_TERRAIN = MapSourceConfig(
        id = "terrain-contours",
        label = "本地等高线",
        shortLabel = "等高线",
        baseUrl = "https://s3.amazonaws.com/elevation-tiles-prod/terrarium/{z}/{x}/{y}.png",
        overlayUrls = emptyList(),
        maxZoom = 18,
        maxNativeZoom = 15,
        attribution = "Elevation tiles © Mapzen / AWS Open Data",
    )

    fun get(id: String?): MapSourceConfig = ESRI_SATELLITE

    fun getById(id: String?): MapSourceConfig =
        if (id == CONTOUR_TERRAIN.id) CONTOUR_TERRAIN else get(id)
}
