package id.web.agentvaedah.strideway.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import id.web.agentvaedah.strideway.core.geo.Geo
import id.web.agentvaedah.strideway.core.heatmap.HeatCell
import id.web.agentvaedah.strideway.core.model.LatLon
import id.web.agentvaedah.strideway.data.prefs.MapBase
import id.web.agentvaedah.strideway.ui.theme.Brand
import id.web.agentvaedah.strideway.ui.theme.Palette
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.geometry.LatLngQuad
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.HeatmapLayer
import org.maplibre.android.style.layers.Layer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.ImageSource
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/** The user's Mapbox token, provided once at the root so every map can use Mapbox styles. */
val LocalMapboxToken = staticCompositionLocalOf<String?> { null }

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
         "layers":[{"id":"bg","type":"background","paint":{"background-color":"#0A0A0B"}},
                   {"id":"esri-imagery","type":"raster","source":"esri-imagery"}]}
    """.trimIndent()

    private val hybridJson = """
        {"version":8,"sources":{$satelliteSources,
           ${raster("esri-roads", "$ESRI/Reference/World_Transportation/MapServer/tile/{z}/{y}/{x}", "&copy; Esri")},
           ${raster("esri-labels", "$ESRI/Reference/World_Boundaries_and_Places/MapServer/tile/{z}/{y}/{x}", "&copy; Esri")}},
         "layers":[{"id":"bg","type":"background","paint":{"background-color":"#0A0A0B"}},
                   {"id":"esri-imagery","type":"raster","source":"esri-imagery"},
                   {"id":"esri-roads","type":"raster","source":"esri-roads"},
                   {"id":"esri-labels","type":"raster","source":"esri-labels"}]}
    """.trimIndent()

    // Standard OSM tiles are for light use with attribution (tile usage policy); no bulk prefetching here.
    private val osmJson = """
        {"version":8,"sources":{${raster("osm", "https://tile.openstreetmap.org/{z}/{x}/{y}.png", "&copy; OpenStreetMap contributors")}},
         "layers":[{"id":"osm","type":"raster","source":"osm"}]}
    """.trimIndent()

    /**
     * Mapbox through its Static Tiles API, which serves raster tiles any renderer can draw, so MapLibre needs no
     * Mapbox SDK. Usage is billed to the token owner's Mapbox account.
     */
    private fun mapboxJson(styleId: String, token: String) = """
        {"version":8,"sources":{"mapbox":{"type":"raster","tileSize":512,"maxzoom":22,
           "tiles":["https://api.mapbox.com/styles/v1/mapbox/$styleId/tiles/512/{z}/{x}/{y}@2x?access_token=$token"],
           "attribution":"&copy; Mapbox &copy; OpenStreetMap"}},
         "layers":[{"id":"mapbox","type":"raster","source":"mapbox"}]}
    """.trimIndent()

    // OpenTopoMap: contour lines and hill shading, fair use only and capped at zoom 17.
    private val topoJson = """
        {"version":8,"sources":{"topo":{"type":"raster","tiles":["https://tile.opentopomap.org/{z}/{x}/{y}.png"],
           "tileSize":256,"maxzoom":17,"attribution":"&copy; OpenStreetMap, SRTM | OpenTopoMap (CC-BY-SA)"}},
         "layers":[{"id":"topo","type":"raster","source":"topo"}]}
    """.trimIndent()

    /** CyclOSM lite: transparent bike lanes and cycle routes, drawn over any base map. */
    const val BIKE_LANES_URL = "https://a.tile-cyclosm.openstreetmap.fr/cyclosm-lite/{z}/{x}/{y}.png"

    /** Falls back to [MapBase.STANDARD] when a Mapbox style is picked without a token. */
    fun resolve(base: MapBase, mapboxToken: String?): MapBase =
        if (base.needsMapbox && mapboxToken.isNullOrBlank()) MapBase.STANDARD else base

    fun builder(base: MapBase, mapboxToken: String? = null): Style.Builder = when (resolve(base, mapboxToken)) {
        MapBase.STANDARD -> Style.Builder().fromUri(OPENFREEMAP_LIBERTY)
        MapBase.OSM -> Style.Builder().fromJson(osmJson)
        MapBase.SATELLITE -> Style.Builder().fromJson(satelliteJson)
        MapBase.HYBRID -> Style.Builder().fromJson(hybridJson)
        MapBase.TOPO -> Style.Builder().fromJson(topoJson)
        MapBase.MAPBOX_STREETS -> Style.Builder().fromJson(mapboxJson("streets-v12", mapboxToken!!))
        MapBase.MAPBOX_OUTDOORS -> Style.Builder().fromJson(mapboxJson("outdoors-v12", mapboxToken!!))
        MapBase.MAPBOX_SATELLITE -> Style.Builder().fromJson(mapboxJson("satellite-streets-v12", mapboxToken!!))
    }

    fun attribution(base: MapBase, mapboxToken: String?): String = when (resolve(base, mapboxToken)) {
        MapBase.STANDARD -> "© OpenFreeMap © OpenStreetMap"
        MapBase.OSM -> "© OpenStreetMap contributors"
        MapBase.SATELLITE, MapBase.HYBRID -> "Citra © Esri, Maxar, Earthstar Geographics"
        MapBase.TOPO -> "© OpenStreetMap, SRTM · OpenTopoMap (CC-BY-SA)"
        MapBase.MAPBOX_STREETS, MapBase.MAPBOX_OUTDOORS, MapBase.MAPBOX_SATELLITE -> "© Mapbox © OpenStreetMap"
    }
}

/**
 * MapLibre MapView inside Compose. [onStyleLoaded] runs after every base-style change,
 * because switching style drops all runtime sources and layers. When the style cannot be fetched
 * (offline, server down) a short notice covers the map instead of leaving a blank panel.
 */
@Composable
fun MapHost(
    base: MapBase,
    modifier: Modifier = Modifier,
    allowRotateTilt: Boolean = false,
    onMapReady: (MapLibreMap) -> Unit = {},
    onStyleLoaded: (MapLibreMap, Style) -> Unit,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember { MapView(context).apply { onCreate(null) } }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    val styleCallback by rememberUpdatedState(onStyleLoaded)
    val readyCallback by rememberUpdatedState(onMapReady)
    val mapboxToken = LocalMapboxToken.current
    var failed by remember { mutableStateOf(false) }

    DisposableEffect(mapView) {
        val onFail = MapView.OnDidFailLoadingMapListener { failed = true }
        val onLoaded = MapView.OnDidFinishLoadingStyleListener { failed = false }
        mapView.addOnDidFailLoadingMapListener(onFail)
        mapView.addOnDidFinishLoadingStyleListener(onLoaded)
        onDispose {
            mapView.removeOnDidFailLoadingMapListener(onFail)
            mapView.removeOnDidFinishLoadingStyleListener(onLoaded)
        }
    }

    DisposableEffect(lifecycle, mapView) {
        var started = false
        var resumed = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> { mapView.onStart(); started = true }
                Lifecycle.Event.ON_RESUME -> { mapView.onResume(); resumed = true }
                Lifecycle.Event.ON_PAUSE -> { mapView.onPause(); resumed = false }
                Lifecycle.Event.ON_STOP -> { mapView.onStop(); started = false }
                else -> Unit
            }
        }
        // addObserver replays ON_START/ON_RESUME up to the current state, so no manual catch-up here.
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            if (resumed) mapView.onPause()
            if (started) mapView.onStop()
            mapView.onDestroy()
        }
    }

    LaunchedEffect(mapView) {
        mapView.getMapAsync { m ->
            m.uiSettings.isRotateGesturesEnabled = allowRotateTilt
            m.uiSettings.isTiltGesturesEnabled = allowRotateTilt
            // The record screen draws its own compass that also shows direction of travel.
            m.uiSettings.isCompassEnabled = !allowRotateTilt
            m.cameraPosition = CameraPosition.Builder()
                .target(DefaultCenter.toLatLng())
                .zoom(12.0)
                .build()
            map = m
            readyCallback(m)
        }
    }

    LaunchedEffect(map, base, mapboxToken) {
        val m = map ?: return@LaunchedEffect
        m.setStyle(MapStyles.builder(base, mapboxToken)) { style -> styleCallback(m, style) }
    }

    Box(modifier) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        if (failed) {
            Box(Modifier.fillMaxSize().background(Palette.SurfaceHigh), contentAlignment = Alignment.Center) {
                Text(
                    "Peta tidak bisa dimuat. Periksa koneksi internet; data rute tetap tersimpan.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.TextMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(24.dp),
                )
            }
        }
    }
}

fun LatLon.toLatLng() = LatLng(lat, lon)

/** Idempotent helpers: create the source/layer on first call, then only swap data. */
object MapLayers {
    /**
     * How a line reads on the map. TRACK is ridden ground: a soft glow, a dark edge and a Signal-to-amber
     * gradient that runs from start to finish, so direction shows without arrows. ROUTE is ground still to
     * ride: dashed with chevrons pointing the way. PLAIN is a quiet single line for context.
     */
    enum class LineStyle { TRACK, ROUTE, PLAIN }

    private val TrackEnd = Color(0xFFFFB000)

    fun setLine(
        style: Style,
        id: String,
        points: List<LatLon>,
        color: Color,
        widthDp: Float,
        below: String? = null,
        lineStyle: LineStyle = LineStyle.PLAIN,
    ) {
        val collection = if (points.size >= 2) {
            FeatureCollection.fromFeature(Feature.fromGeometry(LineString.fromLngLats(points.map { Point.fromLngLat(it.lon, it.lat) })))
        } else {
            FeatureCollection.fromFeatures(emptyList())
        }
        val source = style.getSourceAs<GeoJsonSource>(id)
        if (source != null) {
            source.setGeoJson(collection)
            // Theme switches change the colour; existing layers are recoloured instead of rebuilt.
            style.getLayerAs<LineLayer>(id)?.let { l ->
                if (lineStyle == LineStyle.TRACK) l.setProperties(PropertyFactory.lineGradient(trackGradient(color)))
                else l.setProperties(PropertyFactory.lineColor(color.toArgb()))
            }
            style.getLayerAs<LineLayer>("$id-glow")?.setProperties(PropertyFactory.lineColor(color.toArgb()))
            if (lineStyle == LineStyle.TRACK) setEnds(style, id, points, color)
            return
        }
        // lineMetrics is required for a gradient measured along the line.
        style.addSource(GeoJsonSource(id, collection, GeoJsonOptions().withLineMetrics(lineStyle == LineStyle.TRACK)))
        val layers = ArrayList<Layer>()
        if (lineStyle == LineStyle.TRACK) {
            layers += LineLayer("$id-glow", id).withProperties(
                PropertyFactory.lineColor(color.toArgb()),
                PropertyFactory.lineWidth(widthDp + 10f),
                PropertyFactory.lineBlur(6f),
                PropertyFactory.lineOpacity(0.28f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            )
        }
        layers += LineLayer("$id-casing", id).withProperties(
            // Dark casing in both themes: map tiles are light or photographic, so a dark edge separates the line best.
            PropertyFactory.lineColor(Brand.Ink.toArgb()),
            PropertyFactory.lineWidth(widthDp + if (lineStyle == LineStyle.PLAIN) 3f else 4f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            PropertyFactory.lineOpacity(if (lineStyle == LineStyle.PLAIN) 0.6f else 0.85f),
        )
        val line = LineLayer(id, id).withProperties(
            PropertyFactory.lineWidth(widthDp),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        )
        when (lineStyle) {
            LineStyle.TRACK -> line.setProperties(PropertyFactory.lineGradient(trackGradient(color)))
            LineStyle.ROUTE -> line.setProperties(
                PropertyFactory.lineColor(color.toArgb()),
                PropertyFactory.lineDasharray(arrayOf(1.6f, 1.1f)),
                PropertyFactory.lineCap(Property.LINE_CAP_BUTT),
            )
            LineStyle.PLAIN -> line.setProperties(PropertyFactory.lineColor(color.toArgb()))
        }
        layers += line
        if (lineStyle == LineStyle.ROUTE) {
            style.addImage("$id-chevron", chevronBitmap(color))
            layers += SymbolLayer("$id-arrows", id).withProperties(
                PropertyFactory.symbolPlacement(Property.SYMBOL_PLACEMENT_LINE),
                PropertyFactory.symbolSpacing(90f),
                PropertyFactory.iconImage("$id-chevron"),
                PropertyFactory.iconSize(0.45f),
                PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
            )
        }
        layers.forEach { l -> if (below != null && style.getLayer(below) != null) style.addLayerBelow(l, below) else style.addLayer(l) }
        if (lineStyle == LineStyle.TRACK) setEnds(style, id, points, color)
    }

    private fun trackGradient(color: Color): Expression = Expression.interpolate(
        Expression.linear(),
        Expression.lineProgress(),
        Expression.stop(0f, Expression.color(color.toArgb())),
        Expression.stop(1f, Expression.color(TrackEnd.toArgb())),
    )

    /** Start (green ring) and finish (chequered square) markers for a ridden track. */
    private fun setEnds(style: Style, id: String, points: List<LatLon>, color: Color) {
        val ends = if (points.size >= 2) {
            FeatureCollection.fromFeatures(
                listOf(
                    Feature.fromGeometry(Point.fromLngLat(points.first().lon, points.first().lat)).apply { addStringProperty("kind", "start") },
                    Feature.fromGeometry(Point.fromLngLat(points.last().lon, points.last().lat)).apply { addStringProperty("kind", "end") },
                ),
            )
        } else {
            FeatureCollection.fromFeatures(emptyList())
        }
        val srcId = "$id-ends"
        val src = style.getSourceAs<GeoJsonSource>(srcId)
        if (src != null) {
            src.setGeoJson(ends)
            return
        }
        style.addImage("$srcId-start", endBitmap(start = true, color))
        style.addImage("$srcId-end", endBitmap(start = false, color))
        style.addSource(GeoJsonSource(srcId, ends))
        style.addLayer(
            SymbolLayer(srcId, srcId).withProperties(
                PropertyFactory.iconImage(
                    Expression.switchCase(
                        Expression.eq(Expression.get("kind"), Expression.literal("start")),
                        Expression.literal("$srcId-start"),
                        Expression.literal("$srcId-end"),
                    ),
                ),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
            ),
        )
    }

    private fun chevronBitmap(color: Color): Bitmap {
        val size = 48
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = android.graphics.Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 9f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        // Points along +x, which is the line direction for symbols placed on a line.
        val path = Path().apply { moveTo(14f, 10f); lineTo(32f, 24f); lineTo(14f, 38f) }
        c.drawPath(path, p.apply { this.color = Brand.Ink.toArgb(); strokeWidth = 13f })
        c.drawPath(path, p.apply { this.color = color.toArgb(); strokeWidth = 8f })
        return bmp
    }

    private fun endBitmap(start: Boolean, color: Color): Bitmap {
        val size = 44
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = android.graphics.Color.WHITE }
        val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = Brand.Ink.toArgb() }
        if (start) {
            c.drawCircle(22f, 22f, 18f, ink)
            c.drawCircle(22f, 22f, 14f, white)
            c.drawCircle(22f, 22f, 9f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = 0xFF2FB765.toInt() })
        } else {
            c.drawRect(4f, 4f, 40f, 40f, ink)
            val cell = 9f
            for (row in 0 until 4) for (col in 0 until 4) {
                c.drawRect(4f + col * cell, 4f + row * cell, 4f + (col + 1) * cell, 4f + (row + 1) * cell, if ((row + col) % 2 == 0) white else ink)
            }
            c.drawRect(4f, 4f, 40f, 40f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color.toArgb(); style = Paint.Style.STROKE; strokeWidth = 3f })
        }
        return bmp
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

    /**
     * Position marker as an arrow pointing along [bearingDeg] (map-aligned, so it stays correct while the map
     * rotates). Without a bearing it is drawn as a plain dot.
     */
    fun setHeadingPuck(style: Style, id: String, point: LatLon?, bearingDeg: Float?, color: Color) {
        val feature = point?.let {
            Feature.fromGeometry(Point.fromLngLat(it.lon, it.lat)).apply {
                addNumberProperty("bearing", bearingDeg ?: 0f)
                addBooleanProperty("arrow", bearingDeg != null)
            }
        }
        val collection = if (feature != null) FeatureCollection.fromFeature(feature) else FeatureCollection.fromFeatures(emptyList())
        val source = style.getSourceAs<GeoJsonSource>(id)
        if (source != null) {
            source.setGeoJson(collection)
            return
        }
        style.addImage("$id-arrow", puckBitmap(color, arrow = true))
        style.addImage("$id-dot", puckBitmap(color, arrow = false))
        style.addSource(GeoJsonSource(id, collection))
        style.addLayer(
            SymbolLayer(id, id).withProperties(
                PropertyFactory.iconImage(
                    Expression.switchCase(
                        Expression.get("arrow"),
                        Expression.literal("$id-arrow"),
                        Expression.literal("$id-dot"),
                    ),
                ),
                PropertyFactory.iconRotate(Expression.get("bearing")),
                PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
                PropertyFactory.iconPitchAlignment(Property.ICON_PITCH_ALIGNMENT_MAP),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
            ),
        )
    }

    private fun puckBitmap(color: Color, arrow: Boolean): Bitmap {
        val size = 72
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color.toArgb() }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = android.graphics.Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 6f
            strokeJoin = Paint.Join.ROUND
        }
        if (arrow) {
            val p = Path().apply {
                moveTo(size / 2f, 6f)
                lineTo(size - 12f, size - 8f)
                lineTo(size / 2f, size - 22f)
                lineTo(12f, size - 8f)
                close()
            }
            c.drawPath(p, fill)
            c.drawPath(p, stroke)
        } else {
            c.drawCircle(size / 2f, size / 2f, 18f, fill)
            c.drawCircle(size / 2f, size / 2f, 18f, stroke)
        }
        return bmp
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
            style.getLayerAs<RasterLayer>(id)?.setProperties(PropertyFactory.rasterOpacity(opacity))
            return
        }
        style.addSource(ImageSource(id, quad, bitmap))
        val layer = RasterLayer(id, id).withProperties(PropertyFactory.rasterOpacity(opacity))
        if (below != null && style.getLayer(below) != null) style.addLayerBelow(layer, below) else style.addLayer(layer)
    }

    /** Transparent raster tiles over the base map, or removed when [url] is null. */
    fun setRasterOverlay(style: Style, id: String, url: String?, below: String? = null) {
        val existing = style.getSource(id)
        if (url == null) {
            style.getLayer(id)?.let { style.removeLayer(it) }
            existing?.let { style.removeSource(it) }
            return
        }
        if (existing != null) return
        style.addSource(RasterSource(id, TileSet("2.2.0", url).apply { maxZoom = 18f }, 256))
        val layer = RasterLayer(id, id).withProperties(PropertyFactory.rasterOpacity(0.9f))
        if (below != null && style.getLayer(below) != null) style.addLayerBelow(layer, below) else style.addLayer(layer)
    }

    /** Personal heatmap from weighted grid cells; an empty list removes it. */
    fun setHeatmap(style: Style, id: String, cells: List<HeatCell>, color: Color) {
        val collection = FeatureCollection.fromFeatures(
            cells.map { c -> Feature.fromGeometry(Point.fromLngLat(c.lon, c.lat)).apply { addNumberProperty("w", c.weight) } },
        )
        val source = style.getSourceAs<GeoJsonSource>(id)
        if (source != null) {
            source.setGeoJson(collection)
            return
        }
        style.addSource(GeoJsonSource(id, collection))
        val layer = HeatmapLayer(id, id).withProperties(
            PropertyFactory.heatmapWeight(Expression.get("w")),
            PropertyFactory.heatmapIntensity(Expression.interpolate(Expression.linear(), Expression.zoom(), Expression.stop(8, 0.6), Expression.stop(15, 2.0))),
            PropertyFactory.heatmapRadius(Expression.interpolate(Expression.linear(), Expression.zoom(), Expression.stop(8, 4), Expression.stop(15, 14))),
            PropertyFactory.heatmapOpacity(0.85f),
            PropertyFactory.heatmapColor(
                Expression.interpolate(
                    Expression.linear(), Expression.heatmapDensity(),
                    Expression.stop(0, Expression.rgba(0, 0, 0, 0)),
                    Expression.stop(0.2, Expression.color(color.copy(alpha = 0.35f).toArgb())),
                    Expression.stop(0.6, Expression.color(color.copy(alpha = 0.75f).toArgb())),
                    Expression.stop(1.0, Expression.color(android.graphics.Color.WHITE)),
                ),
            ),
        )
        val below = "route-casing"
        if (style.getLayer(below) != null) style.addLayerBelow(layer, below) else style.addLayer(layer)
    }

    /**
     * Shows or hides points of interest. Only the vector Standard style has separate POI layers
     * (their ids contain "poi"); raster bases bake labels into the image.
     */
    fun setPoiVisible(style: Style, visible: Boolean) {
        val v = if (visible) Property.VISIBLE else Property.NONE
        style.layers.filter { it.id.contains("poi", ignoreCase = true) }.forEach { it.setProperties(PropertyFactory.visibility(v)) }
    }

    /** Frames [points]; [bottomPx] keeps them clear of a panel covering the bottom of the map. */
    /**
     * Frames [points] with a short ease, keeping the current tilt and bearing so a 3D view stays 3D.
     * [animate] false jumps instantly, for the first frame before the map is visible.
     */
    fun fit(map: MapLibreMap, points: List<LatLon>, paddingPx: Int = 96, bottomPx: Int = 0, animate: Boolean = true) {
        if (points.isEmpty()) return
        val update = if (points.size == 1 || points.all { it == points.first() }) {
            CameraUpdateFactory.newLatLngZoom(points.first().toLatLng(), 15.0)
        } else {
            val bounds = LatLngBounds.Builder().includes(points.map { it.toLatLng() }).build()
            val cam = map.cameraPosition
            CameraUpdateFactory.newLatLngBounds(bounds, cam.bearing, cam.tilt, paddingPx, paddingPx, paddingPx, paddingPx + bottomPx)
        }
        if (animate) map.animateCamera(update, 600) else map.moveCamera(update)
    }
}
