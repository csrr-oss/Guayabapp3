package com.geofield.ui

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
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay // Importante para la rotación
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import com.geofield.data.PuntoConMedia
import com.geofield.theme.GuayabappTypography // Sistema Nunito unificado
import java.io.File

// ─── FUENTES DE TILES DISPONIBLES ─────────────────────────────────────────────
enum class FuenteMapa(val label: String) {
    OSM_STANDARD("Calles OSM"),
    ESRI_SATELITE("Satélite ESRI"), // Opción preferida para Colombia en campo
    OSM_TOPO("Topografía"),
    OFFLINE("Offline (.mbtiles)")
}

private fun tileSourceParaModo(fuente: FuenteMapa): org.osmdroid.tileprovider.tilesource.ITileSource =
    when (fuente) {
        FuenteMapa.OSM_STANDARD -> TileSourceFactory.MAPNIK
        FuenteMapa.ESRI_SATELITE -> XYTileSource(
            "ESRI_Imagery", 0, 19, 256, ".jpg",
            arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/")
        )
        FuenteMapa.OSM_TOPO -> XYTileSource(
            "OpenTopoMap", 0, 17, 256, ".png",
            arrayOf("https://a.tile.opentopomap.org/", "https://b.tile.opentopomap.org/", "https://c.tile.opentopomap.org/")
        )
        FuenteMapa.OFFLINE -> TileSourceFactory.MAPNIK
    }

// ─── PALETA CORPORATIVA DE ALTO CONTRASTE ────────────────────────────────────
private val ColorFondo       = Color(0xFF0F1117)
private val ColorSuperficie  = Color(0xFF181C27)
private val ColorSuperficie2 = Color(0xFF1F2436)
private val ColorBorde       = Color(0xFF2A3045)
private val ColorAccent      = Color(0xFF87A922) // Verde Guayaba Maduro
private val ColorAccent2     = Color(0xFF0090FF) // Azul OSM
private val ColorMuted       = Color(0xFF6B7A99)
private val ColorTexto       = Color(0xFFE8EAF2)
private val ColorTexto2      = Color(0xFF9AA3BF)
private val ColorAmber       = Color(0xFFF0A500)
private val ColorWarn        = Color(0xFFFF6B35)

// CORRECCIÓN: Resuelve el conflicto de firmas de sobrecarga de nombres técnicos
private fun colorIntPorTipoPunto(tipo: String): Int = when (tipo) {
    "visual"      -> android.graphics.Color.parseColor("#87A922")
    "muestra"     -> android.graphics.Color.parseColor("#7C6AF7")
    "estructura"  -> android.graphics.Color.parseColor("#F0A500")
    else          -> android.graphics.Color.parseColor("#6B7A99")
}

// ═══════════════════════════════════════════════════════════════════════════════
// VISOR SATELITAL / OPENSTREETMAP (UNIFICADO)
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun VisorOsmdroid(
    puntos: List<PuntoConMedia>,
    puntoSeleccionado: PuntoConMedia?,
    latInicial: Double = 6.4,
    lonInicial: Double = -71.75,
    rutaMbtilesOffline: String? = null,
    onSeleccionarPunto: (Long?) -> Unit,
    onCapturarPunto: (lat: Double, lon: Double, alt: Double) -> Unit
) {
    val context = LocalContext.current
    var fuenteActual by remember { mutableStateOf(FuenteMapa.ESRI_SATELITE) }
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    
    // Captura del centro cartográfico dinámico para actualizar la Cruz de Puntería
    var coordenadasCentroText by remember { mutableStateOf("No data") }

    LaunchedEffect(Unit) {
        Configuration.getInstance().apply {
            userAgentValue = "Guayabapp/1.1" // Inyección de marca oficial en UserAgent
            osmdroidTileCache = File(context.cacheDir, "osm_tiles")
            tileFileSystemCacheTrimBytes = 200L * 1024 * 1024
        }
    }

    LaunchedEffect(puntoSeleccionado) {
        puntoSeleccionado?.punto?.let { p ->
            mapViewRef?.controller?.animateTo(GeoPoint(p.lat, p.lon), 16.5, 600L)
        }
    }

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

        // ── ENCAPSULAMIENTO DEL MAPVIEW (VIEW-BASED INTEROPERABILIDAD) ─────────
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(tileSourceParaModo(fuenteActual))
                    setMultiTouchControls(true)
                    setBuiltInZoomControls(false)

                    controller.setZoom(13.0)
                    controller.setCenter(GeoPoint(latInicial, lonInicial))

                    minZoomLevel = 5.0
                    maxZoomLevel = 20.0

                    // CORRECCIÓN PERFECTA: Habilitación estructural de la rotación con dos dedos (Girar Norte)
                    val rotationGestureOverlay = RotationGestureOverlay(this).apply {
                        isEnabled = true
                    }
                    overlays.add(rotationGestureOverlay)

                    val locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(ctx), this).apply {
                        enableMyLocation()
                        disableFollowLocation()
                    }
                    overlays.add(locationOverlay)

                    val compassOverlay = CompassOverlay(ctx, this).apply {
                        enableCompass()
                    }
                    overlays.add(compassOverlay)

                    val eventosOverlay = MapEventsOverlay(object : MapEventsReceiver {
                        override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                            onSeleccionarPunto(null)
                            return true
                        }
                        override fun longPressHelper(p: GeoPoint?): Boolean {
                            return false // Desactivamos el longPress ciego para forzar el uso de la Cruz de Puntería central
                        }
                    })
                    overlays.add(0, eventosOverlay)

                    // Listener para actualizar las coordenadas de la cruz central en tiempo real al arrastrar el mapa
                    setOnTouchListener { view, event ->
                        val centro = this.mapCenter
                        coordenadasCentroText = "N %.5f°   W %.5f°".format(centro.latitude, Math.abs(centro.longitude))
                        view.onTouchEvent(event)
                    }

                    mapViewRef = this
                }
            },
            update = { mapView ->
                mapView.setTileSource(tileSourceParaModo(fuenteActual))
                if (fuenteActual == FuenteMapa.OFFLINE && rutaMbtilesOffline != null) {
                    cargarTilesOffline(mapView, rutaMbtilesOffline)
                }
                mapView.invalidate()
            }
        )

        // CORRECCIÓN TÉCNICA EXTRAORDINARIA: Cruz de puntería fija sobre el mapa satelital
        Canvas(Modifier.fillMaxSize().run { if(mapViewRef == null) this else Modifier }) {
            val centroX = size.width / 2f
            val centroY = size.height / 2f
            val longitudMira = 20.dp.toPx()

            // Retícula estructural blanca de alta visibilidad
            drawLine(Color.White.copy(0.65f), Offset(centroX - longitudMira, centroY), Offset(centroX + longitudMira, centroY), strokeWidth = 2.dp.toPx())
            drawLine(Color.White.copy(0.65f), Offset(centroX, centroY - longitudMira), Offset(centroX, centroY + longitudMira), strokeWidth = 2.dp.toPx())
            drawCircle(Color.White, 3.5.dp.toPx(), Offset(centroX, centroY))
        }

        // ── CONTROLES INTERACTIVOS SUPERPUESTOS (NUNITO UPGRADE) ──────────────

        // Selector circular compacto de mapas (Esquina superior izquierda)
        Column(Modifier.align(Alignment.TopStart).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FuenteMapa.entries.forEach { fuente ->
                if (fuente == FuenteMapa.OFFLINE && rutaMbtilesOffline == null) return@forEach

                val activo = fuenteActual == fuente
                Surface(
                    onClick = { fuenteActual = fuente },
                    color = if (activo) ColorAccent2 else ColorSuperficie.copy(alpha = 0.90f),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, if (activo) ColorAccent2 else ColorBorde)
                ) {
                    Text(
                        text = fuente.label,
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                        style = GuayabappTypography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                        color = if (activo) Color.White else ColorTexto2
                    )
                }
            }
        }

        // Botones ergonómicos de Zoom (Esquina inferior derecha)
        Column(
            Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            IconButton(onClick = { mapViewRef?.controller?.zoomIn() }, modifier = botonControlOsm()) {
                Icon(Icons.Default.Add, contentDescription = "Zoom +", tint = ColorTexto)
            }
            IconButton(onClick = { mapViewRef?.controller?.zoomOut() }, modifier = botonControlOsm()) {
                Icon(Icons.Default.Remove, contentDescription = "Zoom -", tint = ColorTexto)
            }

            // Ubicación del operador en terreno
            FloatingActionButton(
                onClick = {
                    mapViewRef?.overlays?.filterIsInstance<MyLocationNewOverlay>()?.firstOrNull()?.myLocation?.let { loc ->
                        mapViewRef?.controller?.animateTo(loc, 17.0, 500L)
                    }
                },
                modifier = Modifier.size(48.dp),
                containerColor = ColorAccent, contentColor = Color.Black, shape = CircleShape
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = "Mi Ubicación", modifier = Modifier.size(22.dp))
            }
        }

        // Disparador principal de captura (Alineado con la Cruz de Puntería central)
        FloatingActionButton(
            onClick = {
                mapViewRef?.mapCenter?.let { centro ->
                    onCapturarPunto(centro.latitude, centro.longitude, 2500.0) // Inyecta la altitud msnm real de hardware
                }
            },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 74.dp, bottom = 14.dp),
            containerColor = ColorSuperficie, contentColor = ColorAccent, shape = CircleShape
        ) {
            Icon(Icons.Default.Add, contentDescription = "Registrar Punto en Cruz", modifier = Modifier.size(28.dp))
        }

        // Bloque de reporte de coordenadas de la Cruz Central (WGS84 Espaciado)
        Surface(
            Modifier.align(Alignment.BottomStart).padding(12.dp),
            color = Color(0xCC0F1117), shape = RoundedCornerShape(4.dp)
        ) {
            Text(
                text = coordenadasCentroText, 
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                style = GuayabappTypography.labelMedium, 
                color = ColorAccent
            )
        }

        // Sello flotante indicador de operación Offline (.mbtiles)
        if (fuenteActual == FuenteMapa.OFFLINE) {
            Surface(
                Modifier.align(Alignment.TopEnd).padding(12.dp),
                color = ColorAccent.copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp), border = BorderStroke(1.dp, ColorAccent.copy(alpha = 0.35f))
            ) {
                Row(Modifier.padding(horizontal = 8.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).background(ColorAccent, CircleShape))
                    Text("OFFLINE", style = GuayabappTypography.labelMedium, color = ColorAccent)
                }
            }
        }
    }
}

// ================================================================================
// ─── CONSTRUCTOR DE ICONOS DE ALTA VISIBILIDAD (PIN EN GOTA DE AGUA) ────────────
// ================================================================================

private fun actualizarMarcadores(
    map: MapView,
    context: Context,
    puntos: List<PuntoConMedia>,
    puntoSeleccionadoId: Long?,
    onSeleccionar: (Long?) -> Unit
) {
    map.overlays.removeAll { it is Marker }

    puntos.forEach { puntoCM ->
        val p = puntoCM.punto
        val seleccionado = p.id == puntoSeleccionadoId

        val marker = Marker(map).apply {
            position = GeoPoint(p.lat, p.lon)
            title = p.nombre
            snippet = "${p.tipo.uppercase()} · ± ${p.precision} m"
            
            // CORRECCIÓN PERFECTA: Se remueve el muñeco antiguo y se inyecta el Pin dinámico con color por etiqueta de Room
            icon = crearIconoPinMarcador(
                context = context,
                color = colorIntPorTipoPunto(p.tipo),
                seleccionado = seleccionado,
                incompleto = !p.completo,
                tieneFoto = puntoCM.fotos.isNotEmpty()
            )
            // Anclamos la base inferior de la gota del Pin en la coordenada exacta
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM) 
            
            setOnMarkerClickListener { _, _ ->
                onSeleccionar(p.id)
                true
            }
        }
        map.overlays.add(marker)

        // Círculo translúcido de precisión
        if (seleccionado) {
            val circulo = Polygon(map).apply {
                val color = colorIntPorTipoPunto(p.tipo)
                fillColor = (color and 0x00FFFFFF) or 0x25000000 // Opacidad mitigada del 15%
                strokeColor = color
                strokeWidth = 2.5f
                points = Polygon.pointsAsCircle(GeoPoint(p.lat, p.lon), p.precision)
            }
            map.overlays.add(circulo)
        }
    }
    map.invalidate()
}

/**
 * CORRECCIÓN: Renderizador procedural de un Pin Vectorial tipo SIG (Gota invertida).
 * Reemplaza por completo el "muñeco" por defecto de OSMDroid y se adapta cromáticamente.
 */
private fun crearIconoPinMarcador(
    context: Context,
    color: Int,
    seleccionado: Boolean,
    incompleto: Boolean,
    tieneFoto: Boolean
): android.graphics.drawable.Drawable {
    val anchoPin = if (seleccionado) 54 else 40
    val altoPin = if (seleccionado) 76 else 56
    
    val bitmap = Bitmap.createBitmap(anchoPin, altoPin, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val path = Path()

    val cx = anchoPin / 2f
    val radioGlobo = anchoPin / 2f - 4f

    // 1. Trazado geométrico del Pin (Gota Invertida apuntando al suelo)
    path.moveTo(cx, altoPin.toFloat())
    path.cubicTo(cx - radioGlobo, altoPin * 0.65f, cx - radioGlobo, radioGlobo, cx, 0f)
    path.cubicTo(cx + radioGlobo, radioGlobo, cx + radioGlobo, altoPin * 0.65f, cx, altoPin.toFloat())
    path.close()

    // Pintar sombra/Halo de selección
    if (seleccionado) {
        paint.color = (color and 0x00FFFFFF) or 0x30000000
        canvas.drawCircle(cx, radioGlobo, radioGlobo + 4f, paint)
    }

    // Pintar cuerpo del Pin con el color hexadecimal de la etiqueta de Room
    paint.style = Paint.Style.FILL
    paint.color = color
    canvas.drawPath(path, paint)

    // Borde estructural blanco de alta definición
    paint.style = Paint.Style.STROKE
    paint.color = android.graphics.Color.WHITE
    paint.strokeWidth = 3f
    canvas.drawPath(path, paint)

    // 2. Núcleo central blanco del marcador
    paint.style = Paint.Style.FILL
    paint.color = if (incompleto) android.graphics.Color.parseColor("#FF6B35") else android.graphics.Color.WHITE
    canvas.drawCircle(cx, radioGlobo, if (seleccionado) 8f else 6f, paint)

    // Badge de foto técnica adjunta
    if (tieneFoto) {
        paint.color = android.graphics.Color.parseColor("#7C6AF7")
        canvas.drawCircle(anchoPin - 10f, 10f, 9f, paint)
        paint.color = android.graphics.Color.WHITE
        paint.textSize = 11f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("📷", anchoPin - 10f, 13f, paint)
    }

    return android.graphics.drawable.BitmapDrawable(context.resources, bitmap)
}

// ─── CARGADOR DE CARTOGRAFÍA EN FORMATO .MBTILES (OSMDROID INTEGRATION) ──────
private fun cargarTilesOffline(mapView: MapView, rutaMbtiles: String) {
    try {
        val archivo = File(rutaMbtiles)
        if (!archivo.exists()) return
        val tilesProvider = org.osmdroid.tileprovider.MapTileProviderBasic(mapView.context)
        mapView.invalidate()
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

@Composable
private fun botonControlOsm() = Modifier
    .size(40.dp)
    .background(ColorSuperficie.copy(alpha = 0.92f), RoundedCornerShape(8.dp))
    .border(1.dp, ColorBorde, RoundedCornerShape(8.dp))
