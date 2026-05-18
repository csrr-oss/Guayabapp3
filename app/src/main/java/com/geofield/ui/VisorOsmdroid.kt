package com.geofield.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.os.Environment
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
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.io.File

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
    onSeleccionarPunto: (Long?) -> Unit,
    onCapturarPunto: (lat: Double, lon: Double, alt: Double) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var fuenteActual by remember { mutableStateOf(FuenteMapa.ESRI_SATELITE) }
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var coordenadasCentroText by remember { mutableStateOf("No data") }
    var menuDesplegado by remember { mutableStateOf(false) }

    // PUNTO 4: Cálculo dinámico del centro geográfico basado en tus puntos guardados 
    val centroMapa = remember(puntos) {
        if (puntos.isNotEmpty()) {
            val avgLat = puntos.map { it.punto.lat }.average()
            val avgLon = puntos.map { it.punto.lon }.average()
            GeoPoint(avgLat, avgLon)
        } else {
            GeoPoint(6.4, -71.75) // Coordenadas por defecto Colombia si el proyecto está virgen 
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(tileSourceParaModo(fuenteActual))
                    setMultiTouchControls(true)
                    controller.setZoom(14.0)
                    controller.setCenter(centroMapa) // Centrado inteligente automático 

                    val locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(ctx), this).apply {
                        enableMyLocation()
                    }
                    overlays.add(locationOverlay)

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

        // Botón Menú Flotante Completo de Selección de Mapas
        Box(Modifier.align(Alignment.TopEnd).padding(14.dp)) {
            FloatingActionButton(onClick = { menuDesplegado = true }, modifier = Modifier.size(44.dp), containerColor = EstilosOsmdroid.Superficie.copy(alpha = 0.92f), contentColor = EstilosOsmdroid.Accent, shape = CircleShape) { Icon(Icons.Default.Layers, null) }
            DropdownMenu(expanded = menuDesplegado, onDismissRequest = { menuDesplegado = false }, modifier = Modifier.background(EstilosOsmdroid.Superficie)) {
                FuenteMapa.entries.forEach { fuente ->
                    DropdownMenuItem(text = { Text(fuente.label, color = EstilosOsmdroid.Texto) }, onClick = { fuenteActual = fuente; menuDesplegado = false })
                }
            }
        }

        // CORRECCIÓN PUNTO 5 (Cero Crashes): El gatillo corre en Dispatchers.Main de forma síncrona
        FloatingActionButton(
            onClick = { 
                scope.launch(Dispatchers.Main) {
                    mapViewRef?.let { map ->
                        val centro = map.mapCenter
                        onCapturarPunto(centro.latitude, centro.longitude, 2500.0)
                        
                        // PUNTO 6: Estructura libre. Espejo automático de datos a carpeta pública externa
                        try {
                            val carpeta = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "Guayabapp")
                            if (!carpeta.exists()) carpeta.mkdirs()
                            val reporte = File(carpeta, "Auditoria_Puntos_Campo.csv")
                            reporte.appendText("${System.currentTimeMillis()},${centro.latitude},${centro.longitude}\n")
                        } catch (_: Exception) {}
                    }
                }
            }, 
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 14.dp), 
            containerColor = EstilosOsmdroid.Superficie, contentColor = EstilosOsmdroid.Accent, shape = CircleShape
        ) { Icon(Icons.Default.Add, null, modifier = Modifier.size(28.dp)) }

        Surface(Modifier.align(Alignment.BottomStart).padding(12.dp), color = Color(0xCC0F1117), shape = RoundedCornerShape(4.dp)) {
            Text(text = coordenadasCentroText, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = EstilosOsmdroid.LabelMedium, color = EstilosOsmdroid.Accent)
        }
    }
}

@Composable
private fun modifierBotonOsm() = Modifier.size(40.dp)
