package id.web.agentvaedah.strideway.ui.components

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import id.web.agentvaedah.strideway.core.geo.Geo
import id.web.agentvaedah.strideway.core.model.LatLon
import id.web.agentvaedah.strideway.data.prefs.MapBase
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.geometry.LatLngQuad
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.ImageSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/** Jakarta, used until the first GPS fix or a track gives the camera something better. */
val DefaultCenter = LatLon(-6.2, 106.8456)

object MapStyles {
    private const val OPENFREEMAP_LIBERTY = "https://tiles.openfreemap.org/styles/liberty"
    private const val ESRI = "https://server.arcgisonline.com/ArcGIS/rest/services"

    private fun raster(id: String, url: String, attribution: String) =
        """"$id":{"type":"raster","tiles":["$url"],"tileSize":256,"maxzoom":19,"attribution":"$attribution"}"""

    private val satelliteSources = raster(
        "esri-imagery", "$ESRI/World_Imagery/MapServer/tile/{z}/{y}/{x}",
        "Imagery &copy; Esri, Maxar, Earthstar Geographics",
    )

    private val satelliteJson = """
        {"version":8,"sources":{$satelliteSources},
         "layers":[{"id":"bg","type":"background","paint":{"background-color":"#0E1012"}},
                   {"id":"esri-imagery","type":"raster","source":"esri-imagery"}]}
    """.trimIndent()

    private val hybridJson = """
        {"version":8,"sources":{$satelliteSources,
           ${raster("esri-roads", "$ESRI/Reference/World_Transportation/MapServer/tile/{z}/{y}/{x}", "&copy; Esri")},
           ${raster("esri-labels", "$ESRI/Reference/World_Boundaries_and_Places/MapServer/tile/{z}/{y}/{x}", "&copy; Esri")}},
         "layers":[{"id":"bg","type":"background","paint":{"background-color":"#0E1012"}},
                   {"id":"esri-imagery","type":"raster","source":"esri-imagery"},
                   {"id":"esri-roads","type":"raster","source":"esri-roads"},
                   {"id":"esri-labels","type":"raster","source":"esri-labels"}]}
    """.trimIndent()

    fun builder(base: MapBase): Style.Builder = when (base) {
        MapBase.STANDARD -> Style.Builder().fromUri(OPENFREEMAP_LIBERTY)
        MapBase.SATELLITE -> Style.Builder().fromJson(satelliteJson)
        MapBase.HYBRID -> Style.Builder().fromJson(hybridJson)
    }
}

/**
 * MapLibre MapView inside Compose. [onStyleLoaded] runs after every base-style change,
 * because switching style drops all runtime sources and layers.
 */
@Composable
fun MapHost(
    base: MapBase,
    modifier: Modifier = Modifier,
    onMapReady: (MapLibreMap) -> Unit = {},
    onStyleLoaded: (MapLibreMap, Style) -> Unit,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember { MapView(context).apply { onCreate(null) } }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    val styleCallback by rememberUpdatedState(onStyleLoaded)
    val readyCallback by rememberUpdatedState(onMapReady)

    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        // addObserver replays ON_START/ON_RESUME up to the current state, so no manual catch-up here.
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    LaunchedEffect(mapView) {
        mapView.getMapAsync { m ->
            m.uiSettings.isRotateGesturesEnabled = false
            m.uiSettings.isTiltGesturesEnabled = false
            m.cameraPosition = CameraPosition.Builder()
                .target(DefaultCenter.toLatLng())
                .zoom(12.0)
                .build()
            map = m
            readyCallback(m)
        }
    }

    LaunchedEffect(map, base) {
        val m = map ?: return@LaunchedEffect
        m.setStyle(MapStyles.builder(base)) { style -> styleCallback(m, style) }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

fun LatLon.toLatLng() = LatLng(lat, lon)

/** Idempotent helpers: create the source/layer on first call, then only swap data. */
object MapLayers {
    fun setLine(style: Style, id: String, points: List<LatLon>, color: Color, widthDp: Float, below: String? = null) {
        val collection = if (points.size >= 2) {
            FeatureCollection.fromFeature(Feature.fromGeometry(LineString.fromLngLats(points.map { Point.fromLngLat(it.lon, it.lat) })))
        } else {
            FeatureCollection.fromFeatures(emptyList())
        }
        val source = style.getSourceAs<GeoJsonSource>(id)
        if (source != null) {
            source.setGeoJson(collection)
            return
        }
        style.addSource(GeoJsonSource(id, collection))
        val casing = LineLayer("$id-casing", id).withProperties(
            PropertyFactory.lineColor(Color(0xFF0E1012).toArgb()),
            PropertyFactory.lineWidth(widthDp + 3f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            PropertyFactory.lineOpacity(0.6f),
        )
        val line = LineLayer(id, id).withProperties(
            PropertyFactory.lineColor(color.toArgb()),
            PropertyFactory.lineWidth(widthDp),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        )
        if (below != null && style.getLayer(below) != null) {
            style.addLayerBelow(casing, below)
            style.addLayerBelow(line, below)
        } else {
            style.addLayer(casing)
            style.addLayer(line)
        }
    }

    fun setPoint(style: Style, id: String, point: LatLon?, color: Color, radiusDp: Float = 7f) {
        val collection = if (point != null) {
            FeatureCollection.fromFeature(Feature.fromGeometry(Point.fromLngLat(point.lon, point.lat)))
        } else {
            FeatureCollection.fromFeatures(emptyList())
        }
        val source = style.getSourceAs<GeoJsonSource>(id)
        if (source != null) {
            source.setGeoJson(collection)
            return
        }
        style.addSource(GeoJsonSource(id, collection))
        style.addLayer(
            CircleLayer(id, id).withProperties(
                PropertyFactory.circleColor(color.toArgb()),
                PropertyFactory.circleRadius(radiusDp),
                PropertyFactory.circleStrokeColor(Color.White.toArgb()),
                PropertyFactory.circleStrokeWidth(2.5f),
            ),
        )
    }

    /** Circle drawn as a polygon-ish ring of points, used for the privacy zone outline. */
    fun ring(center: LatLon, radiusM: Double, steps: Int = 72): List<LatLon> =
        (0..steps).map { i ->
            Geo.destination(center, i * 360.0 / steps, radiusM)
        }

    fun setImage(style: Style, id: String, quad: LatLngQuad?, bitmap: Bitmap?, opacity: Float, below: String? = null) {
        val existing = style.getSourceAs<ImageSource>(id)
        if (quad == null || bitmap == null) {
            style.getLayer(id)?.let { style.removeLayer(it) }
            existing?.let { style.removeSource(it) }
            return
        }
        if (existing != null) {
            existing.setCoordinates(quad)
            existing.setImage(bitmap)
            return
        }
        style.addSource(ImageSource(id, quad, bitmap))
        val layer = RasterLayer(id, id).withProperties(PropertyFactory.rasterOpacity(opacity))
        if (below != null && style.getLayer(below) != null) style.addLayerBelow(layer, below) else style.addLayer(layer)
    }

    fun fit(map: MapLibreMap, points: List<LatLon>, paddingPx: Int = 96) {
        if (points.isEmpty()) return
        if (points.size == 1 || points.all { it == points.first() }) {
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(points.first().toLatLng(), 15.0))
            return
        }
        val bounds = LatLngBounds.Builder().includes(points.map { it.toLatLng() }).build()
        map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, paddingPx))
    }
}
