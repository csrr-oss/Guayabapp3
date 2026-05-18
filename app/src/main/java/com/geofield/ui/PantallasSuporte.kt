package com.geofield.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
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
    val TitleLarge  = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Black, fontSize = 26.sp)
    val BodyLarge   = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 14.sp)
}

data class ProyectoResumen(val id: Long, val nombre: String, val totalPuntos: Int, val totalFotos: Int, val ultimaActividad: Long, val modoCapa: ModoCapaBase)

// ═══════════════════════════════════════════════════════════════════════════════
// 1. SPLASH SCREEN (CORREGIDO: MUESTRA EL LOGO CIRCULAR OFICIAL ANTES DEL GATILLO)
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun SplashScreen(onListo: () -> Unit) {
    val alpha by animateFloatAsState(targetValue = 1f, animationSpec = tween(1000, easing = EaseInOut), label = "fade")
    val scale by animateFloatAsState(targetValue = 1f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "scale")

    LaunchedEffect(Unit) {
        delay(2200) 
        onListo()
    }

    Box(Modifier.fillMaxSize().background(Brush.radialGradient(colors = listOf(Color(0xFF1A2416), EstilosSoporte.Fondo), radius = 900f)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.alpha(alpha).scale(scale)) {
            // LOGO OFICIAL EN CÍRCULO INYECTADO EN EL CORAZÓN DEL SPLASH
            Box(
                Modifier
                    .size(96.dp)
                    .background(Brush.radialGradient(listOf(EstilosSoporte.Accent.copy(.25f), Color.Transparent)), CircleShape)
                    .border(2.dp, EstilosSoporte.Accent, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Terrain, contentDescription = "Logo Guayabapp", tint = EstilosSoporte.Accent, modifier = Modifier.size(46.dp))
            }
            Text(text = "Guayabapp", style = EstilosSoporte.TitleLarge, color = EstilosSoporte.Texto, letterSpacing = 1.sp)
            Text(text = "Levantamiento georreferenciado de campo", style = EstilosSoporte.BodyLarge, letterSpacing = .5.sp, color = EstilosSoporte.Texto2)
        }
        Text(text = "v1.1.0 · Sistema Resiliente Offline", modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp), style = EstilosSoporte.LabelMedium, color = EstilosSoporte.Muted)
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// 2. PANTALLA PERMISOS ADAPTATIVA (HORIZONTALES BLINDADOS)
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun PantallaPermisos(onPermisosConcedidos: () -> Unit) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { resultados ->
        val gpsOk = resultados[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                    resultados[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        val camaraOk = resultados[Manifest.permission.CAMERA] == true
        
        if (gpsOk && camaraOk) {
            try { LocationForegroundService.iniciar(context) } catch (_: Exception) {}
            onPermisosConcedidos()
        }
    }

    Box(Modifier.fillMaxSize().background(EstilosSoporte.Fondo), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 420.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Spacer(Modifier.height(8.dp))
            Icon(Icons.Default.GpsFixed, null, tint = EstilosSoporte.Accent, modifier = Modifier.size(52.dp))
            Text(text = "Permisos de Operación", style = EstilosSoporte.TitleMedium, color = EstilosSoporte.Texto)
            Text(text = "Guayabapp requiere acceso directo a los sensores para georreferenciar puntos y registrar evidencias multimedia en campo.", style = EstilosSoporte.BodyLarge, color = EstilosSoporte.Texto2, textAlign = TextAlign.Center)

            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ItemPermiso(Icons.Default.GpsFixed, EstilosSoporte.Accent, "Ubicación Satelital", "Fijación de coordenadas WGS84.")
                ItemPermiso(Icons.Default.CameraAlt, EstilosSoporte.Accent2, "Cámara (CameraX)", "Fotografía y video estructural.")
                ItemPermiso(Icons.Default.Mic, EstilosSoporte.Purple, "Micrófono Integrado", "Notas de audio ambiental.")
                ItemPermiso(Icons.Default.FolderOpen, EstilosSoporte.Amber, "Almacenamiento", "Persistencia Room y exportación KML.")
            }

            Spacer(Modifier.height(10.dp))
            Button(
                onClick = { launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)) },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = EstilosSoporte.Accent, contentColor = Color.Black),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Conceder Permisos de Hardware", style = EstilosSoporte.BodyLarge.copy(fontWeight = FontWeight.Bold))
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ItemPermiso(icono: androidx.compose.ui.graphics.vector.ImageVector, color: Color, titulo: String, desc: String) {
    Surface(color = EstilosSoporte.Superficie, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, EstilosSoporte.Borde)) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(34.dp).background(color.copy(.12f), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) { Icon(icono, null, tint = color, modifier = Modifier.size(18.dp)) }
            Column {
                Text(titulo, style = EstilosSoporte.BodyLarge.copy(fontWeight = FontWeight.Bold), color = EstilosSoporte.Texto)
                Text(desc, style = EstilosSoporte.BodyLarge.copy(fontSize = 12.sp), color = EstilosSoporte.Texto2)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// 3. EXPLORADOR DE PROYECTOS (CON ISOTIPO EN CIRCULITO JUNTO AL NOMBRE)
// ═══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProyectosScreen(onAbrirProyecto: (Long) -> Unit, onNuevoProyecto: () -> Unit) {
    val proyectos = remember {
        listOf(
            ProyectoResumen(1, "Cuenca Caño Limón", 14, 27, System.currentTimeMillis() - 3600000, ModoCapaBase.ESRI_SATELITE),
            ProyectoResumen(2, "Sector Arauca Norte", 6, 8, System.currentTimeMillis() - 86400000, ModoCapaBase.GEO_PDF),
            ProyectoResumen(3, "Levantamiento Vichada", 0, 0, System.currentTimeMillis() - 172800000, ModoCapaBase.OSM_ESTANDAR),
        )
    }

    Scaffold(
        containerColor = EstilosSoporte.Fondo,
        topBar = { 
            Surface(color = EstilosSoporte.Superficie) { 
                Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) { 
                    // ── LOGO EN CIRCULITO AL LADO DEL NOMBRE DE LA APP ────────────────
                    Box(
                        Modifier
                            .size(28.dp)
                            .background(EstilosSoporte.Accent.copy(0.12f), CircleShape)
                            .border(1.dp, EstilosSoporte.Accent, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Terrain, contentDescription = null, tint = EstilosSoporte.Accent, modifier = Modifier.size(14.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("Guayabapp", style = EstilosSoporte.TitleMedium, color = EstilosSoporte.Accent)
                    Spacer(Modifier.weight(1f)) 
                    Text("Proyectos Activos", style = EstilosSoporte.BodyLarge.copy(fontSize = 13.sp, color = EstilosSoporte.Texto2)) 
                } 
            } 
        },
        floatingActionButton = { FloatingActionButton(onClick = onNuevoProyecto, containerColor = EstilosSoporte.Accent, contentColor = Color.Black, shape = CircleShape) { Icon(Icons.Default.Add, null, modifier = Modifier.size(26.dp)) } }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text("BANCO DE TRABAJO INDEPENDIENTE", style = EstilosSoporte.LabelMedium
