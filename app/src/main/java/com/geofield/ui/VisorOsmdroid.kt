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
import androidx.compose.foundation.shape.RoundedCornerShape // CORRECCIÓN: Importación re-establecida
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
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
import com.geofield.navigation.ModoCapaBase
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

private fun tileSourceParaModo(fuente: ModoCapaBase): org.osmdroid.tileprovider.tilesource.ITileSource =
    when (fuente) {
        ModoCapaBase.OSM_ESTANDAR -> org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK
        ModoCapaBase.ESRI_SATELITE -> org.osmdroid.tileprovider.tilesource.XYTileSource("ESRI_Imagery", 0, 19, 256, ".jpg", arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/"))
        ModoCapaBase.GEO_PDF -> org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK
    }

private object EstilosOsmdroid {
    val Superficie  = Color(0xFF181C27)
    val Borde       = Color(0xFF2A3045)
    val Accent      = Color(0xFF87A922)
    val Texto       = Color(0xFFE8EAF2)
    val LabelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
    var fuenteActual by remember { mutableStateOf(ModoCapaBase.ESRI_SATELITE) }
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var coordenadasCentroText by remember { mutableStateOf("No data") }
    var menuDesplegado by remember { mutableStateOf(false) }

    val centroMapa = remember(puntoSeleccionado) {
        puntoSeleccionado?.punto?.let { GeoPoint(it.lat, it.lon) } ?: GeoPoint(4.624, -74.063) 
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(tileSourceParaModo(fuenteActual))
                    setMultiTouchControls(true)
                    controller.setZoom(16.0) 
                    controller.setCenter(centroMapa)

                    val markerGps = Marker(this).apply {
                        position = centroMapa
                        title = "Mi Ubicación Real"
                        
                        val sizePx = (22 * ctx.resources.displayMetrics.density).toInt()
                        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
                        val canvas = android.graphics.Canvas(bitmap)
                        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                        
                        paint.color = android.graphics.Color.WHITE
                        canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f, paint)
                        
                        paint.color = android.graphics.Color.parseColor("#1A73E8") 
                        canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f - (3 * ctx.resources.displayMetrics.density), paint)
                        
                        icon = BitmapDrawable(ctx.resources, bitmap)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    }
                    overlays.add(markerGps)

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
                mapView.controller.setCenter(centroMapa)
            }
        )

        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val centroX = size.width / 2f
            val centroY = size.height / 2f
            drawCircle(Color.White, 4.dp.toPx(), Offset(centroX, centroY))
        }

        Box(Modifier.align(Alignment.TopEnd).padding(14.dp)) {
            FloatingActionButton(onClick = { menuDesplegado = true }, modifier = Modifier.size(44.dp), containerColor = EstilosOsmdroid.Superficie, contentColor = EstilosOsmdroid.Accent, shape = CircleShape) { Icon(Icons.Default.Layers, null) }
            DropdownMenu(expanded = menuDesplegado, onDismissRequest = { menuDesplegado = false }, modifier = Modifier.background(EstilosOsmdroid.Superficie)) {
                listOf(ModoCapaBase.ESRI_SATELITE to "Satélite (ESRI)", ModoCapaBase.OSM_ESTANDAR to "Mapa Base (OSM)", ModoCapaBase.GEO_PDF to "Plano GeoPDF").forEach { (capa, label) ->
                    DropdownMenuItem(text = { Text(label, color = EstilosOsmdroid.Texto) }, onClick = { fuenteActual = capa; menuDesplegado = false })
                }
            }
        }

        Column(Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FloatingActionButton(onClick = { mapViewRef?.controller?.animateTo(centroMapa, 17.0, 500L) }, modifier = Modifier.size(48.dp), containerColor = EstilosOsmdroid.Accent, contentColor = Color.Black, shape = CircleShape) {
                Icon(Icons.Default.MyLocation, null)
            }
        }

        FloatingActionButton(
            onClick = { 
                mapViewRef?.let { map ->
                    val c = map.mapCenter
                    onCapturarPunto(c.latitude, c.longitude, 2600.0)
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

@Composable
private fun modifierBotonOsm() = Modifier.size(40.dp)
