package com.geofield.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geofield.location.LocationForegroundService
import com.geofield.navigation.ModoCapaBase
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

private object EstilosSoporte {
    val Fondo       = Color(0xFF0F1117)
    val Superficie  = Color(0xFF181C27)
    val Superficie2 = Color(0xFF1F2436)
    val Borde       = Color(0xFF2A3045)
    val Accent      = Color(0xFF87A922) 
    val Accent2     = Color(0xFF0090FF) 
    val Muted       = Color(0xFF6B7A99)
    val Texto       = Color(0xFFE8EAF2)
    val Texto2      = Color(0xFF9AA3BF)
    val Amber       = Color(0xFFF0A500) 
    val Purple      = Color(0xFF7C6AF7) 
    val Red         = Color(0xFFD80032)

    val LabelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    val TitleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    val TitleLarge  = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Black, fontSize = 32.sp)
    val BodyLarge   = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 14.sp)
}

data class ProyectoResumen(val id: Long, val nombre: String, val totalPuntos: Int, val totalFotos: Int, val ultimaActividad: Long, val modoCapa: ModoCapaBase)

@Composable
fun SplashScreen(onListo: () -> Unit) {
    LaunchedEffect(Unit) { delay(2200); onListo() }

    Box(Modifier.fillMaxSize().background(EstilosSoporte.Fondo), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Surface(
                modifier = Modifier.size(140.dp),
                color = EstilosSoporte.Superficie,
                shape = CircleShape,
                border = BorderStroke(3.dp, EstilosSoporte.Accent)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Terrain, contentDescription = "Logo", tint = EstilosSoporte.Accent, modifier = Modifier.size(72.dp))
                }
            }
            Text(text = "Guayabapp", style = EstilosSoporte.TitleLarge, color = EstilosSoporte.Texto, letterSpacing = 1.5.sp)
            Text(text = "Levantamiento Georreferenciado Offline", style = EstilosSoporte.BodyLarge, color = EstilosSoporte.Texto2)
        }
    }
}

@Composable
fun PantallaPermisos(onPermisosConcedidos: () -> Unit) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(contract = ActivityResultContracts.RequestMultiplePermissions()) { res ->
        onPermisosConcedidos()
    }

    Box(Modifier.fillMaxSize().background(EstilosSoporte.Fondo), contentAlignment = Alignment.Center) {
        Column(
            Modifier.width(360.dp).padding(16.dp), 
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // CORRECCIÓN: Cambiado GpsFine por GpsFixed nativo
            Icon(Icons.Default.GpsFixed, null, tint = EstilosSoporte.Accent, modifier = Modifier.size(48.dp))
            Text(text = "Permisos Requeridos", style = EstilosSoporte.TitleMedium, color = EstilosSoporte.Texto)
            
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                ItemPermiso(Icons.Default.GpsFixed, EstilosSoporte.Accent, "Ubicación Satelital", "Fijación WGS84 precisa.")
                ItemPermiso(Icons.Default.CameraAlt, EstilosSoporte.Accent2, "Cámara de Control", "Captura de evidencias fotográficas.")
                ItemPermiso(Icons.Default.Mic, EstilosSoporte.Purple, "Micrófono de Campo", "Notas de audio ambiental.")
                ItemPermiso(Icons.Default.FolderOpen, EstilosSoporte.Amber, "Almacenamiento Local", "Exportación de archivos KML/SIG.")
            }

            Spacer(Modifier.height(4.dp))
            Button(
                onClick = { 
                    launcher.launch(arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.CAMERA,
                        Manifest.permission.RECORD_AUDIO
                    ))
                }, 
                modifier = Modifier.fillMaxWidth().height(46.dp), 
                colors = ButtonDefaults.buttonColors(containerColor = EstilosSoporte.Accent, contentColor = Color.Black)
            ) {
                Text("Habilitar Sensores de Hardware", style = EstilosSoporte.BodyLarge.copy(fontWeight = FontWeight.Bold))
            }
        }
    }
}

@Composable
private fun ItemPermiso(icono: androidx.compose.ui.graphics.vector.ImageVector, color: Color, titulo: String, desc: String) {
    Surface(modifier = Modifier.fillMaxWidth(), color = EstilosSoporte.Superficie, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, EstilosSoporte.Borde)) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(32.dp).background(color.copy(.12f), RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) { Icon(icono, null, tint = color, modifier = Modifier.size(16.dp)) }
            Column {
                Text(titulo, style = EstilosSoporte.BodyLarge.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp), color = EstilosSoporte.Texto)
                Text(desc, style = EstilosSoporte.BodyLarge.copy(fontSize = 11.sp), color = EstilosSoporte.Texto2)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProyectosScreen(proyectos: List<ProyectoResumen>, onAbrirProyecto: (Long) -> Unit, onNuevoProyecto: () -> Unit) {
    Scaffold(
        containerColor = EstilosSoporte.Fondo,
        topBar = { 
            Surface(color = EstilosSoporte.Superficie) { 
                Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) { 
                    Box(Modifier.size(28.dp).background(EstilosSoporte.Accent.copy(0.15f), CircleShape).border(1.2.dp, EstilosSoporte.Accent, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Terrain, null, tint = EstilosSoporte.Accent, modifier = Modifier.size(14.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("Guayabapp", style = EstilosSoporte.TitleMedium, color = EstilosSoporte.Accent)
                    Spacer(Modifier.weight(1f))
                    Text("Campaña Activa", style = EstilosSoporte.BodyLarge.copy(fontSize = 12.sp, color = EstilosSoporte.Texto2))
                } 
            } 
        },
        floatingActionButton = { FloatingActionButton(onClick = onNuevoProyecto, containerColor = EstilosSoporte.Accent, contentColor = Color.Black, shape = CircleShape) { Icon(Icons.Default.Add, null) } }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(proyectos, key = { it.id }) { p ->
                Surface(onClick = { onAbrirProyecto(p.id) }, color = EstilosSoporte.Superficie, shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, EstilosSoporte.Borde)) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Map, null, tint = EstilosSoporte.Accent)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.nombre, style = EstilosSoporte.BodyLarge.copy(fontWeight = FontWeight.Bold), color = EstilosSoporte.Texto)
                            Text("${p.totalPuntos} puntos colectados · ${if(p.modoCapa == ModoCapaBase.GEO_PDF) "Plano PDF" else "Satélite"}", style = EstilosSoporte.LabelMedium.copy(color = EstilosSoporte.Muted))
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = EstilosSoporte.Borde)
                    }
                }
            }
        }
    }
}

@Composable
fun NuevoProyectoScreen(onCrear: (String, ModoCapaBase) -> Unit, onCancelar: () -> Unit) {
    var nombre by remember { mutableStateOf("") }
    var modoElegido by remember { mutableStateOf(ModoCapaBase.ESRI_SATELITE) }

    Box(Modifier.fillMaxSize().background(EstilosSoporte.Fondo), contentAlignment = Alignment.Center) {
        Surface(modifier = Modifier.width(360.dp), color = EstilosSoporte.Superficie, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, EstilosSoporte.Borde)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("CREAR NUEVA CAMPAÑA", style = EstilosSoporte.LabelMedium.copy(color = EstilosSoporte.Accent))
                
                OutlinedTextField(
                    value = nombre, onValueChange = { nombre = it },
                    label = { Text("Nombre del Proyecto Técnico") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = EstilosSoporte.Accent, unfocusedBorderColor = EstilosSoporte.Borde, focusedTextColor = EstilosSoporte.Texto, unfocusedTextColor = EstilosSoporte.Texto),
                    singleLine = true
                )

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(onClick = { modoElegido = ModoCapaBase.ESRI_SATELITE }, modifier = Modifier.weight(1f), color = if (modoElegido == ModoCapaBase.ESRI_SATELITE) EstilosSoporte.Accent.copy(.15f) else EstilosSoporte.Superficie2, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.5.dp, if (modoElegido == ModoCapaBase.ESRI_SATELITE) EstilosSoporte.Accent else EstilosSoporte.Borde)) {
                        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Satellite, null, tint = EstilosSoporte.Accent)
                            Text("Satélite", style = EstilosSoporte.LabelMedium, color = EstilosSoporte.Texto)
                        }
                    }
                    Surface(onClick = { modoElegido = ModoCapaBase.GEO_PDF }, modifier = Modifier.weight(1f), color = if (modoElegido == ModoCapaBase.GEO_PDF) EstilosSoporte.Amber.copy(.15f) else EstilosSoporte.Superficie2, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.5.dp, if (modoElegido == ModoCapaBase.GEO_PDF) EstilosSoporte.Amber else EstilosSoporte.Borde)) {
                        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.PictureAsPdf, null, tint = EstilosSoporte.Amber)
                            Text("GeoPDF", style = EstilosSoporte.LabelMedium, color = EstilosSoporte.Texto)
                        }
                    }
                }

                Button(onClick = { onCrear(nombre.ifBlank { "Campaña Unificada SIG" }, modoElegido) }, modifier = Modifier.fillMaxWidth().height(44.dp), colors = ButtonDefaults.buttonColors(containerColor = EstilosSoporte.Accent, contentColor = Color.Black)) {
                    Text("Guardar e Inicializar", style = EstilosSoporte.BodyLarge.copy(fontWeight = FontWeight.Bold))
                }
            }
        }
    }
}
