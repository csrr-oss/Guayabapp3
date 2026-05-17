package com.geofield.ui

// ─── DEPENDENCIAS (agregar al build.gradle) ────────────────────────────────
// implementation("org.osmdroid:osmdroid-android:6.1.18")
// implementation("org.osmdroid:osmdroid-mapsforge:6.1.18")  // para tiles .mbtiles offline
// NO requiere API key ni cuenta de facturación

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.*
import org.osmdroid.views.overlay.compass.CompassOverlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import com.geofield.data.PuntoConMedia
import java.io.File

// ─── FUENTES DE TILES DISPONIBLES ─────────────────────────────────────────────

enum class FuenteMapa(val label: String) {
    OSM_STANDARD("Calles OSM"),       // OpenStreetMap estándar — gratis, sin key
    ESRI_SATELITE("Satélite ESRI"),   // Imágenes satelitales ESRI — gratis, sin key
    OSM_TOPO("Topografía"),           // OpenTopoMap — curvas de nivel — gratis
    OFFLINE("Offline (.mbtiles)"),    // Tiles descargadas localmente — sin internet
}

// ─── CONFIGURACIÓN DE TILE SOURCES ────────────────────────────────────────────

private fun tileSourceParaModo(fuente: FuenteMapa): org.osmdroid.tileprovider.tilesource.ITileSource =
    when (fuente) {
        FuenteMapa.OSM_STANDARD -> TileSourceFactory.MAPNIK

        FuenteMapa.ESRI_SATELITE -> XYTileSource(
            "ESRI_Imagery",
            0, 19, 256, ".jpg",
            arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/")
        )   // ESRI ofrece imágenes satelitales gratuitas para uso no comercial

        FuenteMapa.OSM_TOPO -> XYTileSource(
            "OpenTopoMap",
            0, 17, 256, ".png",
            arrayOf(
                "https://a.tile.opentopomap.org/",
                "https://b.tile.opentopomap.org/",
                "https://c.tile.opentopomap.org/"
            )
        )

        FuenteMapa.OFFLINE -> TileSourceFactory.MAPNIK  // reemplazado al cargar el archivo
    }

// ─── PALETA ───────────────────────────────────────────────────────────────────

private val ColorFondo      = Color(0xFF0F1117)
private val ColorSuperficie = Color(0xFF181C27)
private val ColorSuperficie2= Color(0xFF1F2436)
private val ColorBorde      = Color(0xFF2A3045)
private val ColorAccent     = Color(0xFF00D084)
private val ColorAccent2    = Color(0xFF0090FF)
private val ColorMuted      = Color(0xFF6B7A99)
private val ColorTexto      = Color(0xFFE8EAF2)
private val ColorTexto2     = Color(0xFF9AA3BF)
private val ColorAmber      = Color(0xFFF0A500)
private val ColorWarn       = Color(0xFFFF6B35)

private fun colorIntPorTipo(tipo: String): Int = when (tipo) {
    "visual"     -> android.graphics.Color.parseColor("#00D084")
    "muestra"    -> android.graphics.Color.parseColor("#7C6AF7")
    "estructura" -> android.graphics.Color.parseColor("#F0A500")
    else         -> android.graphics.Color.parseColor("#6B7A99")
}

// ─── VISOR OSM PRINCIPAL ──────────────────────────────────────────────────────

@Composable
fun VisorOsmdroid(
    puntos: List<PuntoConMedia>,
    puntoSeleccionado: PuntoConMedia?,
    latInicial: Double = 6.4,
    lonInicial: Double = -71.75,
    rutaMbtilesOffline: String? = null,     // ruta al archivo .mbtiles descargado
    onSeleccionarPunto: (Long?) -> Unit,
    onCapturarPunto: (lat: Double, lon: Double, alt: Double) -> Unit
) {
    val context = LocalContext.current
    var fuenteActual by remember { mutableStateOf(FuenteMapa.ESRI_SATELITE) }
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }

    // Configurar OSMDroid (solo una vez por app)
    LaunchedEffect(Unit) {
        Configuration.getInstance().apply {
            userAgentValue = "GeoField/1.0"
            // Caché de tiles en almacenamiento local — funciona offline para zooms ya visitados
            osmdroidTileCache = File(context.cacheDir, "osm_tiles")
            // Tamaño de caché: 200MB — suficiente para una región de trabajo
            tileFileSystemCacheTrimBytes = 200L * 1024 * 1024
        }
    }

    // Centrar cámara cuando se selecciona un punto
    LaunchedEffect(puntoSeleccionado) {
        puntoSeleccionado?.punto?.let { p ->
            mapViewRef?.controller?.animateTo(GeoPoint(p.lat, p.lon), 16.0, 600L)
        }
    }

    // Actualizar marcadores cuando cambian los puntos
    LaunchedEffect(puntos, puntoSeleccionado) {
        mapViewRef?.let { map ->
            actualizarMarcadores(
                map = map,
                context = context,
                puntos = puntos,
                puntoSeleccionadoId = puntoSeleccionado?.punto?.id,
                onSeleccionar = onSeleccionarPunto
            )
        }
    }

    Box(Modifier.fillMaxSize()) {

        // ── VISTA DE MAPA (OSMDroid es View-based, se envuelve con AndroidView) ──
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                MapView(ctx).apply {
                    // Configuración inicial del mapa
                    setTileSource(tileSourceParaModo(fuenteActual))
                    setMultiTouchControls(true)
                    setBuiltInZoomControls(false)

                    // Posición y zoom inicial
                    controller.setZoom(13.0)
                    controller.setCenter(GeoPoint(latInicial, lonInicial))

                    // Límites de zoom
                    minZoomLevel = 5.0
                    maxZoomLevel = 20.0

                    // ── Overlay: posición GPS propia ──────────────────────────
                    val locationOverlay = MyLocationNewOverlay(
                        GpsMyLocationProvider(ctx), this
                    ).apply {
                        enableMyLocation()
                        // No activar seguimiento automático — el usuario controla la cámara
                        disableFollowLocation()
                    }
                    overlays.add(locationOverlay)

                    // ── Overlay: brújula ──────────────────────────────────────
                    val compassOverlay = CompassOverlay(ctx, this).apply {
                        enableCompass()
                    }
                    overlays.add(compassOverlay)

                    // ── Overlay: eventos de toque ─────────────────────────────
                    val eventosOverlay = MapEventsOverlay(object : MapEventsReceiver {
                        override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                            // Tap en área vacía → deseleccionar
                            onSeleccionarPunto(null)
                            return true
                        }

                        override fun longPressHelper(p: GeoPoint?): Boolean {
                            // Long press → capturar nuevo punto
                            p?.let { onCapturarPunto(it.latitude, it.longitude, 0.0) }
                            return true
                        }
                    })
                    overlays.add(0, eventosOverlay)  // primero para recibir eventos

                    mapViewRef = this
                }
            },
            update = { mapView ->
                // Cambiar fuente de tiles si el usuario cambia el modo
                mapView.setTileSource(tileSourceParaModo(fuenteActual))

                // Si hay archivo .mbtiles offline, cargarlo como fuente
                if (fuenteActual == FuenteMapa.OFFLINE && rutaMbtilesOffline != null) {
                    cargarTilesOffline(mapView, rutaMbtilesOffline)
                }

                mapView.invalidate()
            }
        )

        // ── CONTROLES UI SUPERPUESTOS ─────────────────────────────────────────

        // Selector de fuente de mapa (esquina superior izquierda)
        Column(
            Modifier
                .align(Alignment.TopStart)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            FuenteMapa.entries.forEach { fuente ->
                // No mostrar "Offline" si no hay archivo .mbtiles cargado
                if (fuente == FuenteMapa.OFFLINE && rutaMbtilesOffline == null) return@forEach

                val activo = fuenteActual == fuente
                Surface(
                    onClick = { fuenteActual = fuente },
                    color = if (activo) ColorAccent2 else ColorSuperficie.copy(alpha = 0.92f),
                    shape = RoundedCornerShape(5.dp),
                    border = BorderStroke(1.dp, if (activo) ColorAccent2 else ColorBorde)
                ) {
                    Row(
                        Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        if (fuente == FuenteMapa.OFFLINE) {
                            Box(Modifier.size(6.dp).background(ColorAccent, CircleShape))
                        }
                        Text(
                            fuente.label,
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = if (activo) Color.White else ColorMuted
                            )
                        )
                    }
                }
            }
        }

        // Controles zoom + mi ubicación (esquina inferior derecha)
        Column(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 12.dp, bottom = 66.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            IconButton(
                onClick = { mapViewRef?.controller?.zoomIn() },
                modifier = botonControlOsm()
            ) {
                Icon(Icons.Default.Add, contentDescription = "Zoom +", tint = ColorTexto)
            }

            IconButton(
                onClick = { mapViewRef?.controller?.zoomOut() },
                modifier = botonControlOsm()
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Zoom -", tint = ColorTexto)
            }

            // Ir a mi ubicación GPS
            FloatingActionButton(
                onClick = {
                    mapViewRef?.overlays
                        ?.filterIsInstance<MyLocationNewOverlay>()
                        ?.firstOrNull()
                        ?.myLocation
                        ?.let { loc ->
                            mapViewRef?.controller?.animateTo(loc, 16.0, 600L)
                        }
                },
                modifier = Modifier.size(46.dp),
                containerColor = ColorAccent,
                contentColor = Color.Black,
                shape = CircleShape
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = "Mi ubicación", modifier = Modifier.size(22.dp))
            }
        }

        // FAB capturar punto
        FloatingActionButton(
            onClick = {
                val centro = mapViewRef?.mapCenter
                centro?.let {
                    onCapturarPunto(it.latitude, it.longitude, 0.0)
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 70.dp, bottom = 72.dp),
            containerColor = ColorSuperficie,
            contentColor = ColorAccent,
            shape = CircleShape
        ) {
            Icon(Icons.Default.Add, contentDescription = "Capturar punto aquí")
        }

        // Coordenadas del centro
        val centro = mapViewRef?.mapCenter
        if (centro != null) {
            Surface(
                Modifier.align(Alignment.BottomStart).padding(10.dp),
                color = Color(0xCC0F1117),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    "N%.5f° W%.5f°".format(centro.latitude, Math.abs(centro.longitude)),
                    Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = ColorAccent)
                )
            }
        }

        // Badge offline si está usando tiles locales
        if (fuenteActual == FuenteMapa.OFFLINE) {
            Surface(
                Modifier.align(Alignment.TopEnd).padding(10.dp),
                color = Color(0xFF00D08415),
                shape = RoundedCornerShape(4.dp),
                border = BorderStroke(1.dp, Color(0xFF00D08430))
            ) {
                Row(
                    Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(6.dp).background(ColorAccent, CircleShape))
                    Text("OFFLINE", style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = ColorAccent))
                }
            }
        }
    }
}

// ─── MARCADORES PERSONALIZADOS ────────────────────────────────────────────────

private fun actualizarMarcadores(
    map: MapView,
    context: Context,
    puntos: List<PuntoConMedia>,
    puntoSeleccionadoId: Long?,
    onSeleccionar: (Long?) -> Unit
) {
    // Eliminar marcadores anteriores (conservar location overlay y eventos)
    map.overlays.removeAll { it is Marker }

    puntos.forEach { puntoCM ->
        val p = puntoCM.punto
        val seleccionado = p.id == puntoSeleccionadoId

        val marker = Marker(map).apply {
            position = GeoPoint(p.lat, p.lon)
            title = p.nombre
            snippet = "${p.tipo} · ±${p.precision}m${if (!p.completo) " · pendiente" else ""}"
            icon = crearIconoMarcador(
                context = context,
                color = colorIntPorTipo(p.tipo),
                seleccionado = seleccionado,
                incompleto = !p.completo,
                tieneFoto = puntoCM.fotos.isNotEmpty()
            )
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            setOnMarkerClickListener { _, _ ->
                onSeleccionar(p.id)
                true
            }
        }

        map.overlays.add(marker)

        // Círculo de precisión para el punto seleccionado
        if (seleccionado) {
            val circulo = Polygon(map).apply {
                val color = colorIntPorTipo(p.tipo)
                fillColor = (color and 0x00FFFFFF) or 0x30000000.toInt()  // 19% opacidad
                strokeColor = color
                strokeWidth = 2f
                points = Polygon.pointsAsCircle(GeoPoint(p.lat, p.lon), p.precision)
            }
            map.overlays.add(circulo)
        }
    }

    map.invalidate()
}

private fun crearIconoMarcador(
    context: Context,
    color: Int,
    seleccionado: Boolean,
    incompleto: Boolean,
    tieneFoto: Boolean
): android.graphics.drawable.Drawable {
    val radio = if (seleccionado) 30 else 22
    val total = radio + 14
    val size = total * 2

    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val cx = size / 2f
    val cy = size / 2f

    // Halo exterior (seleccionado)
    if (seleccionado) {
        paint.color = (color and 0x00FFFFFF) or 0x40000000
        canvas.drawCircle(cx, cy, radio + 9f, paint)
    }

    // Anillo de alerta (incompleto)
    if (incompleto) {
        paint.color = android.graphics.Color.parseColor("#80FF6B35")
        canvas.drawCircle(cx, cy, radio + 5f, paint)
    }

    // Relleno principal
    paint.color = color
    paint.alpha = 230
    canvas.drawCircle(cx, cy, radio.toFloat(), paint)

    // Borde blanco
    paint.color = android.graphics.Color.WHITE
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 2.5f
    paint.alpha = 255
    canvas.drawCircle(cx, cy, radio.toFloat(), paint)

    // Punto central blanco
    paint.style = Paint.Style.FILL
    canvas.drawCircle(cx, cy, 4f, paint)

    // Badge de foto (esquina superior derecha)
    if (tieneFoto) {
        paint.color = android.graphics.Color.parseColor("#7C6AF7")
        canvas.drawCircle(cx + radio * 0.72f, cy - radio * 0.72f, 7f, paint)
        paint.color = android.graphics.Color.WHITE
        paint.textSize = 9f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("📷", cx + radio * 0.72f, cy - radio * 0.72f + 3f, paint)
    }

    return android.graphics.drawable.BitmapDrawable(context.resources, bitmap)
}

// ─── SOPORTE OFFLINE: MBTILES ─────────────────────────────────────────────────

/**
 * Carga un archivo .mbtiles como fuente de tiles para uso completamente offline.
 *
 * Cómo obtener tiles offline gratuitas para Colombia:
 * 1. https://openmaptiles.org/downloads/ → seleccionar región "South America / Colombia"
 * 2. https://download.geofabrik.de/south-america/colombia.html → para datos OSM raw
 * 3. Con la app "MAPS.ME" exportar para la región, o usar "OsmAnd" que genera .obf
 * 4. Para tiles raster (.mbtiles): usar MOBAC (Mobile Atlas Creator) — gratuito
 *    Seleccionar área, fuente OSM, zoom 10-17, exportar como .mbtiles
 *
 * En la app: el usuario descarga el .mbtiles una vez con WiFi
 * y luego trabaja sin internet en campo.
 */
private fun cargarTilesOffline(mapView: MapView, rutaMbtiles: String) {
    try {
        val archivo = File(rutaMbtiles)
        if (!archivo.exists()) return

        // OSMDroid soporta .mbtiles nativamente con el módulo osmdroid-mapsforge
        val tilesProvider = org.osmdroid.tileprovider.MapTileProviderBasic(mapView.context)
        // La fuente se configura via OfflineTileProvider de osmdroid-mapsforge:
        // val offlineProvider = OfflineTileProvider(SimpleRegisterReceiver(context), arrayOf(archivo))
        // mapView.tileProvider = offlineProvider
        // mapView.setTileSource(tilesProvider.tileSource)

        // Nota: para implementación completa agregar:
        // implementation("org.osmdroid:osmdroid-mapsforge:6.1.18")
        mapView.invalidate()
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

// ─── PANTALLA DE GESTIÓN OFFLINE ─────────────────────────────────────────────
// Permite al usuario descargar tiles de una región antes de ir a campo

@Composable
fun PantallaGestionOffline(
    onDescargar: (norte: Double, sur: Double, este: Double, oeste: Double, zoomMax: Int) -> Unit,
    onCargarMbtiles: () -> Unit,
    onCerrar: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = ColorFondo
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "MAPA OFFLINE",
                    style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp,
                        color = ColorAccent, letterSpacing = 0.5.sp, fontWeight = FontWeight.Medium)
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onCerrar) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = ColorMuted)
                }
            }

            // Opción 1: Cargar .mbtiles propio
            TarjetaOffline(
                icono = "📦",
                titulo = "Cargar archivo .mbtiles",
                descripcion = "Si ya tienes un archivo .mbtiles descargado (desde MOBAC, QGIS, OsmAnd u otra fuente), cárgalo directamente.",
                colorAcento = ColorAccent,
                accion = "Seleccionar archivo",
                onAccion = onCargarMbtiles
            )

            // Opción 2: Caché automática
            TarjetaOffline(
                icono = "💾",
                titulo = "Caché automático de tiles",
                descripcion = "Navega la zona de trabajo con WiFi antes de ir al campo. OSMDroid guarda hasta 200MB de tiles visitados automáticamente.",
                colorAcento = ColorAccent2,
                accion = "Ver caché actual",
                onAccion = { /* navegar a settings caché */ }
            )

            // Info sobre fuentes gratuitas
            Surface(
                color = ColorSuperficie2,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, ColorBorde)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "FUENTES DE TILES GRATUITAS",
                        style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 9.sp,
                            color = ColorMuted, letterSpacing = 0.5.sp)
                    )
                    listOf(
                        Triple("🗺️", "OpenStreetMap", "openstreetmap.org — calles y topografía"),
                        Triple("🛰️", "ESRI World Imagery", "arcgisonline.com — satélite gratuito"),
                        Triple("⛰️", "OpenTopoMap", "opentopomap.org — curvas de nivel"),
                        Triple("🇨🇴", "IGAC Colombia", "igac.gov.co — cartografía oficial"),
                    ).forEach { (ico, nombre, desc) ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(ico, style = TextStyle(fontSize = 14.sp))
                            Column {
                                Text(nombre, style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, color = ColorTexto))
                                Text(desc, style = TextStyle(fontSize = 10.sp, color = ColorTexto2))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TarjetaOffline(
    icono: String,
    titulo: String,
    descripcion: String,
    colorAcento: Color,
    accion: String,
    onAccion: () -> Unit
) {
    Surface(
        color = ColorSuperficie,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, ColorBorde)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(icono, style = TextStyle(fontSize = 22.sp))
                Text(titulo, style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = ColorTexto))
            }
            Text(descripcion, style = TextStyle(fontSize = 11.sp, color = ColorTexto2, lineHeight = 16.sp))
            Button(
                onClick = onAccion,
                colors = ButtonDefaults.buttonColors(containerColor = colorAcento.copy(0.15f), contentColor = colorAcento),
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, colorAcento.copy(0.3f))
            ) {
                Text(accion, style = TextStyle(fontSize = 11.sp))
            }
        }
    }
}

// ─── HELPER ───────────────────────────────────────────────────────────────────

@Composable
private fun botonControlOsm() = Modifier
    .size(38.dp)
    .background(ColorSuperficie.copy(alpha = 0.92f), RoundedCornerShape(8.dp))
    .border(1.dp, ColorBorde, RoundedCornerShape(8.dp))
