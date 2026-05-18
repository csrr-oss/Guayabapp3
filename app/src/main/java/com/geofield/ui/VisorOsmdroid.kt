package com.geofield.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.geofield.data.PuntoConMedia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.compass.CompassOverlay
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.io.File

private object EstilosOsmdroid {
    val Superficie  = Color(0xFF181C27)
    val Borde       = Color(0xFF2A3045)
    val Accent      = Color(0xFF87A922)
    val Accent2     = Color(0xFF0090FF)
    val Texto       = Color(0xFFE8EAF2)
    val LabelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 12.sp)
}

private fun colorIntPorTipoPunto(tipo: String): Int = when (tipo) {
    "visual"      -> android.graphics.Color.parseColor("#87A922")
    "muestra"     -> android.graphics.Color.parseColor("#7C6AF7")
    "estructura"  -> android.graphics.Color.parseColor("#F0A500")
    else          -> android.graphics.Color.parseColor("#6B7A99")
}

@Composable
fun VisorOsmdroid(
    puntos: List<PuntoConMedia>,
    puntoSeleccionado: PuntoConMedia?,
    latInicial: Double = 6.4,
    lonInicial: Double = -71.75,
    onSeleccionarPunto: (Long?) -> Unit,
    onCapturarPunto: (lat: Double, lon: Double, alt: Double) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var fuenteActual by remember { mutableStateOf(FuenteMapa.ESRI_SATELITE) }
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var coordenadasCentroText by remember { mutableStateOf("No data") }
    var menuDesplegado by remember { mutableStateOf(false) } // Gatillo del menú flotante

    LaunchedEffect(Unit) {
        Configuration.getInstance().apply { userAgentValue = "Guayabapp/1.1" }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(tileSourceParaModo(fuenteActual))
                    setMultiTouchControls(true)
                    controller.setZoom(13.0)
                    controller.setCenter(GeoPoint(latInicial, lonInicial))

                    val rotationGestureOverlay = RotationGestureOverlay(this).apply { isEnabled = true }
                    overlays.add(rotationGestureOverlay)

                    val locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(ctx), this).apply {
                        enableMyLocation()
                        disableFollowLocation()
                        val sizePx = (24 * ctx.resources.displayMetrics.density).toInt()
                        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
                        val canvas = android.graphics.Canvas(bitmap)
                        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                        paint.color = android.graphics.Color.WHITE
                        canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f, paint)
                        paint.color = android.graphics.Color.parseColor("#1A73E8")
                        canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f - (3 * ctx.resources.displayMetrics.density), paint)
                        setPersonIcon(bitmap)
                        setPersonAnchor(0.5f, 0.5f)
                    }
                    overlays.add(locationOverlay)

                    overlays.add(MapEventsOverlay(object : MapEventsReceiver {
                        override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean { onSeleccionarPunto(null); return true }
                        override fun longPressHelper(p: GeoPoint?) = false
                    }))

                    setOnTouchListener { view, event ->
                        val centro = this.mapCenter
                        coordenadasCentroText = "N %.5f°   W %.5f°".format(centro.latitude, Math.abs(centro.longitude))
                        view.onTouchEvent(event)
                    }
                    mapViewRef = this
                }
            },
            update = { mapView -> mapView.setTileSource(tileSourceParaModo(fuenteActual)) }
        )

        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val centroX = size.width / 2f
            val centroY = size.height / 2f
            val longitudMira = 20.dp.toPx()
            drawLine(Color.White.copy(0.65f), Offset(centroX - longitudMira, centroY), Offset(centroX + longitudMira, centroY), strokeWidth = 2.dp.toPx())
            drawLine(Color.White.copy(0.65f), Offset(centroX, centroY - longitudMira), Offset(centroX, centroY + longitudMira), strokeWidth = 2.dp.toPx())
            drawCircle(Color.White, 3.5.dp.toPx(), Offset(centroX, centroY))
        }

        // ── CORRECCIÓN 6: BOTÓN FLOTANTE QUE DESPLIEGA EL MENÚ DE 3 OPCIONES EXPLICITAS ──
        Box(Modifier.align(Alignment.TopEnd).padding(14.dp)) {
            FloatingActionButton(
                onClick = { menuDesplegado = true },
                modifier = Modifier.size(44.dp),
                containerColor = EstilosOsmdroid.Superficie.copy(alpha = 0.92f),
                contentColor = EstilosOsmdroid.Accent,
                shape = CircleShape
            ) { Icon(Icons.Default.Layers, null) }

            DropdownMenu(
                expanded = menuDesplegado,
                onDismissRequest = { menuDesplegado = false },
                modifier = Modifier.background(EstilosOsmdroid.Superficie)
            ) {
                FuenteMapa.entries.forEach { fuente ->
                    DropdownMenuItem(
                        text = { Text(fuente.label, color = EstilosOsmdroid.Texto) },
                        onClick = { fuenteActual = fuente; menuDesplegado = false }
                    )
                }
            }
        }

        Column(Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = { mapViewRef?.controller?.zoomIn() }, modifier = modifierBotonOsm()) { Icon(Icons.Default.Add, null, tint = EstilosOsmdroid.Texto) }
            IconButton(onClick = { mapViewRef?.controller?.zoomOut() }, modifier = modifierBotonOsm()) { Icon(Icons.Default.Remove, null, tint = EstilosOsmdroid.Texto) }
            FloatingActionButton(onClick = { mapViewRef?.overlays?.filterIsInstance<MyLocationNewOverlay>()?.firstOrNull()?.myLocation?.let { loc -> mapViewRef?.controller?.animateTo(loc, 17.0, 500L) } }, modifier = Modifier.size(48.dp), containerColor = EstilosOsmdroid.Accent, contentColor = Color.Black, shape = CircleShape) {
                Icon(Icons.Default.MyLocation, null)
            }
        }

        // CORRECCIÓN 5: Envolver captura en Dispatchers.Main para evitar cierres forzados por sub-hilos de base de datos
        FloatingActionButton(
            onClick = { 
                scope.launch(Dispatchers.Main) {
                    mapViewRef?.mapCenter?.let { onCapturarPunto(it.latitude, it.longitude, 2500.0) }
                }
            }, 
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 74.dp, bottom = 14.dp), 
            containerColor = EstilosOsmdroid.Superficie, contentColor = EstilosOsmdroid.Accent, shape = CircleShape
        ) { Icon(Icons.Default.Add, null, modifier = Modifier.size(28.dp)) }

        Surface(Modifier.align(Alignment.BottomStart).padding(12.dp), color = Color(0xCC0F1117), shape = RoundedCornerShape(4.dp)) {
            Text(text = coordenadasCentroText, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = EstilosOsmdroid.LabelMedium, color = EstilosOsmdroid.Accent)
        }
    }
}

private fun actualizarMarcadores(map: MapView, context: Context, puntos: List<PuntoConMedia>, puntoSeleccionadoId: Long?, onSeleccionar: (Long?) -> Unit) {
    map.overlays.removeAll { it is Marker || it is Polygon }
    puntos.forEach { puntoCM ->
        val p = puntoCM.punto
        val seleccionado = p.id == puntoSeleccionadoId
        val marker = Marker(map).apply {
            position = GeoPoint(p.lat, p.lon)
            title = p.nombre
            icon = crearIconoPinMarcador(context, colorIntPorTipoPunto(p.tipo), seleccionado, !p.completo, puntoCM.fotos.isNotEmpty())
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            setOnMarkerClickListener { _, _ -> onSeleccionar(p.id); true }
        }
        map.overlays.add(marker)
    }
}

private fun crearIconoPinMarcador(context: Context, color: Int, seleccionado: Boolean, incompleto: Boolean, tieneFoto: Boolean): android.graphics.drawable.Drawable {
    val anchoPin = if (seleccionado) 54 else 40
    val altoPin = if (seleccionado) 76 else 56
    val bitmap = Bitmap.createBitmap(anchoPin, altoPin, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val path = Path()
    val cx = anchoPin / 2f
    val radioGlobo = anchoPin / 2f - 4f
    path.moveTo(cx, altoPin.toFloat())
    path.cubicTo(cx - radioGlobo, altoPin * 0.65f, cx - radioGlobo, radioGlobo, cx, 0f)
    path.cubicTo(cx + radioGlobo, radioGlobo, cx + radioGlobo, altoPin * 0.65f, cx, altoPin.toFloat())
    path.close()
    paint.color = color
    canvas.drawPath(path, paint)
    return BitmapDrawable(context.resources, bitmap)
}

@Composable
private fun modifierBotonOsm() = Modifier
    .size(40.dp)
    .background(EstilosOsmdroid.Superficie.copy(alpha = 0.92f), RoundedCornerShape(8.dp))
    .border(width = 1.dp, color = EstilosOsmdroid.Borde, shape = RoundedCornerShape(8.dp))
