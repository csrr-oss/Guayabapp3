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
import com.geofield.location.LocationRepository
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
// 1. SPLASH SCREEN: EVALUADOR LOGÍSTICO COMPLETO
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun SplashScreen(onListo: () -> Unit) {
    val context = LocalContext.current
    val alpha by animateFloatAsState(targetValue = 1f, animationSpec = tween(900, easing = EaseOut), label = "fade_in")
    val scale by animateFloatAsState(targetValue = 1f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "scale_in")

    LaunchedEffect(Unit) {
        delay(2200) // Tiempo visual de arranque garantizado
        onListo()
    }

    Box(Modifier.fillMaxSize().background(Brush.radialGradient(colors = listOf(Color(0xFF1A2416), EstilosSoporte.Fondo), radius = 900f)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.alpha(alpha).scale(scale)) {
            Box(Modifier.size(88.dp).background(Brush.radialGradient(listOf(EstilosSoporte.Accent.copy(.25f), Color.Transparent)), CircleShape).border(1.5.dp, EstilosSoporte.Accent.copy(.45f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Terrain, null, tint = EstilosSoporte.Accent, modifier = Modifier.size(42.dp))
            }
            Text(text = "Guayabapp", style = EstilosSoporte.TitleLarge, color = EstilosSoporte.Texto, letterSpacing = 1.sp)
            Text(text = "Levantamiento georreferenciado de campo", style = EstilosSoporte.BodyLarge, letterSpacing = .5.sp, color = EstilosSoporte.Texto2)
        }
        Text(text = "v1.1.0 · Sistema Resiliente", modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp), style = EstilosSoporte.LabelMedium, color = EstilosSoporte.Muted)
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// 2. PANTALLA PERMISOS ADAPTATIVA (NUNCA SE APLASTA EN HORIZONTAL)
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun PantallaPermisos(onPermisosConcedidos: () -> Unit) {
    val context = LocalContext.current
    
    // Lanzador nativo asíncrono acoplado directamente al árbol de Compose (Cero fallos de click)
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

    Box(
        Modifier.fillMaxSize().background(EstilosSoporte.Fondo),
        contentAlignment = Alignment.Center
    ) {
        // CORRECCIÓN ERGONOMÍA: verticalScroll e indicadores holgados para evitar layouts aplastados en horizontal
        Column(
            Modifier
                .widthIn(max = 420.dp)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
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
                onClick = {
                    launcher.launch(arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.CAMERA,
                        Manifest.permission.RECORD_AUDIO
                    ))
                },
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
// 3. EXPLORADOR DE PROYECTOS INDEPENDIENTES
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
        topBar = { Surface(color = EstilosSoporte.Superficie) { Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) { Text("Guayabapp", style = EstilosSoporte.TitleMedium, color = EstilosSoporte.Accent); Spacer(Modifier.weight(1f)); Text("Proyectos Activos", style = EstilosSoporte.BodyLarge, color = EstilosSoporte.Texto2) } } },
        floatingActionButton = { FloatingActionButton(onClick = onNuevoProyecto, containerColor = EstilosSoporte.Accent, contentColor = Color.Black, shape = CircleShape) { Icon(Icons.Default.Add, null, modifier = Modifier.size(26.dp)) } }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text("BANCO DE TRABAJO INDEPENDIENTE", style = EstilosSoporte.LabelMedium.copy(color = EstilosSoporte.Muted)) }
            if (proyectos.isEmpty()) item { ProyectoVacio(onNuevoProyecto) }
            else { items(proyectos, key = { it.id }) { proyecto -> TarjetaProyecto(proyecto, onClick = { onAbrirProyecto(proyecto.id) }) } }
        }
    }
}

@Composable
private fun TarjetaProyecto(proyecto: ProyectoResumen, onClick: () -> Unit) {
    val fmt = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }
    
    val colorModo = when (proyecto.modoCapa) { 
        ModoCapaBase.GEO_PDF -> EstilosSoporte.Amber 
        ModoCapaBase.OSM_ESTANDAR -> EstilosSoporte.Accent2 
        ModoCapaBase.ESRI_SATELITE -> EstilosSoporte.Accent 
    }
    
    val labelModo = when (proyecto.modoCapa) { 
        ModoCapaBase.GEO_PDF -> "PDF" 
        ModoCapaBase.OSM_ESTANDAR -> "OSM" 
        ModoCapaBase.ESRI_SATELITE -> "SAT" 
    }

    Surface(onClick = onClick, color = EstilosSoporte.Superficie, shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, EstilosSoporte.Borde)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(46.dp).background(colorModo.copy(.12f), RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) { Icon(if (proyecto.modoCapa == ModoCapaBase.GEO_PDF) Icons.Default.PictureAsPdf else Icons.Default.Map, null, tint = colorModo, modifier = Modifier.size(22.dp)) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(proyecto.nombre, style = EstilosSoporte.BodyLarge.copy(fontWeight = FontWeight.Bold), color = EstilosSoporte.Texto)
                    Surface(color = colorModo.copy(.15f), shape = RoundedCornerShape(4.dp)) { Text(labelModo, Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = EstilosSoporte.LabelMedium.copy(color = colorModo)) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("${proyecto.totalPuntos} puntos", style = EstilosSoporte.LabelMedium.copy(color = EstilosSoporte.Accent))
                    Text("${proyecto.totalFotos} fotos", style = EstilosSoporte.LabelMedium.copy(color = EstilosSoporte.Purple))
                }
                Text("Última modificación: ${fmt.format(Date(proyecto.ultimaActividad))}", style = EstilosSoporte.LabelMedium.copy(color = EstilosSoporte.Muted))
            }
            Icon(Icons.Default.ChevronRight, null, tint = EstilosSoporte.Borde)
        }
    }
}

@Composable
private fun ProyectoVacio(onNuevo: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Default.FolderOpen, null, tint = EstilosSoporte.Muted, modifier = Modifier.size(52.dp))
        Text("No se registran campañas de control", style = EstilosSoporte.BodyLarge, color = EstilosSoporte.Texto2)
        TextButton(onClick = onNuevo) { Text("Iniciar Primer Proyecto Técnico", style = EstilosSoporte.BodyLarge.copy(color = EstilosSoporte.Accent, fontWeight = FontWeight.Bold)) }
    }
}

@Composable
fun ConfiguracionScreen(onCerrar: () -> Unit) {
    Scaffold(
        containerColor = EstilosSoporte.Fondo,
        topBar = { Surface(color = EstilosSoporte.Superficie) { Row(Modifier.fillMaxWidth().height(54.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onCerrar) { Icon(Icons.Default.ArrowBack, null, tint = EstilosSoporte.Texto) }; Text("Configuración de Parámetros", style = EstilosSoporte.TitleMedium, color = EstilosSoporte.Texto) } } }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SeccionConfig("PLANTILLAS Y CATEGORÍAS ADM.") {
                ItemConfig(Icons.Default.Circle, EstilosSoporte.Accent, "Visual (Cobertura / Terreno)", "4 campos Json")
                ItemConfig(Icons.Default.Science, EstilosSoporte.Purple, "Muestra (Suelos / Geotecnia)", "8 campos Json")
                ItemConfig(Icons.Default.Layers, EstilosSoporte.Amber, "Estructura (Fallas / Rumbo)", "5 campos Json")
                ItemConfig(Icons.Default.AddCircle, EstilosSoporte.Accent, "Inyectar Nueva Etiqueta (+)", "Gestor dinámico")
            }
            SeccionConfig("EXPORTACIÓN E INTEROPERABILIDAD") {
                ItemConfig(Icons.Default.FileOpen, EstilosSoporte.Accent2, "Estructura KML Estándar", "Google Earth / QGIS")
                ItemConfig(Icons.Default.Photo, EstilosSoporte.Purple, "Embeber Registro Fotográfico", "Metadatos HTML")
                ItemConfig(Icons.Default.Folder, EstilosSoporte.Amber, "Directorio Físico Local", "GeoField/Media")
            }
            SeccionConfig("RASTREO SATELITAL (GPS)") {
                ItemConfig(Icons.Default.GpsFixed, EstilosSoporte.Accent, "Intervalo de Muestreo", "2.0 segundos")
                ItemConfig(Icons.Default.Speed, EstilosSoporte.Red, "Tolerancia Crítica Horizontal", "Alerta si > 10 metros")
                ItemConfig(Icons.Default.BatteryFull, EstilosSoporte.Amber, "Modo de Energía", "Fijación Satelital de Hardware")
            }
            SeccionConfig("SOPORTE CARTOGRÁFICO BASE") {
                ItemConfig(Icons.Default.Map, EstilosSoporte.Accent2, "Proveedor por Defecto", "ESRI Satélite Offline")
                ItemConfig(Icons.Default.CloudDownload, EstilosSoporte.Accent, "Caché Interno Almacenado", "200 MB Asignados")
                ItemConfig(Icons.Default.Storage, EstilosSoporte.Amber, "Paquetes Descentralizados Tiles", ".mbtiles cargados")
            }
            SeccionConfig("INFORMACIÓN INSTITUCIONAL") {
                ItemConfig(Icons.Default.Info, EstilosSoporte.Muted, "Versión Estable", "Guayabapp v1.1.0")
                ItemConfig(Icons.Default.Code, EstilosSoporte.Muted, "Anidación de Núcleo motor", "Kotlin · Compose · Room")
                ItemConfig(Icons.Default.Person, EstilosSoporte.Muted, "Régimen de Licenciamiento", "Uso Corporativo de Campo")
            }
        }
    }
}

@Composable
private fun SeccionConfig(titulo: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(titulo, style = EstilosSoporte.LabelMedium.copy(color = EstilosSoporte.Muted))
        Surface(color = EstilosSoporte.Superficie, shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, EstilosSoporte.Borde)) { Column(Modifier.fillMaxWidth(), content = content) }
    }
}

@Composable
private fun ColumnScope.ItemConfig(icono: androidx.compose.ui.graphics.vector.ImageVector, color: Color, titulo: String, valor: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icono, null, tint = color, modifier = Modifier.size(16.dp))
        Text(titulo, style = EstilosSoporte.BodyLarge, color = EstilosSoporte.Texto, modifier = Modifier.weight(1f))
        Text(valor, style = EstilosSoporte.LabelMedium.copy(color = EstilosSoporte.Muted))
        Icon(Icons.Default.ChevronRight, null, tint = EstilosSoporte.Borde, modifier = Modifier.size(14.dp))
    }
    HorizontalDivider(color = EstilosSoporte.Borde, thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 14.dp))
}

@Composable
fun NuevoProyectoScreen(onCrear: (nombre: String, modo: ModoCapaBase) -> Unit, onCancelar: () -> Unit) {
    var nombre by remember { mutableStateOf("") }
    var modoElegido by remember { mutableStateOf(ModoCapaBase.ESRI_SATELITE) }

    Box(Modifier.fillMaxSize().background(EstilosSoporte.Fondo), contentAlignment = Alignment.Center) {
        Surface(modifier = Modifier.width(360.dp), color = EstilosSoporte.Superficie, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, EstilosSoporte.Borde)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Text("NUEVO PROYECTO INDEPENDIENTE", style = EstilosSoporte.LabelMedium.copy(color = EstilosSoporte.Accent, fontWeight = FontWeight.Bold)); Spacer(Modifier.weight(1f)); IconButton(onClick = onCancelar) { Icon(Icons.Default.Close, null, tint = EstilosSoporte.Muted) } }
                OutlinedTextField(
                    value = nombre, onValueChange = { nombre = it },
                    label = { Text("Nombre de la Campaña de Campo", style = EstilosSoporte.BodyLarge) },
                    placeholder = { Text("Ej: Cuenca Caño Limón 2026", style = EstilosSoporte.BodyLarge.copy(color = EstilosSoporte.Muted)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = EstilosSoporte.Accent, unfocusedBorderColor = EstilosSoporte.Borde, focusedTextColor = EstilosSoporte.Texto, unfocusedTextColor = EstilosSoporte.Texto, focusedContainerColor = EstilosSoporte.Superficie2, unfocusedContainerColor = EstilosSoporte.Superficie2, focusedLabelColor = EstilosSoporte.Accent, unfocusedLabelColor = EstilosSoporte.Muted),
                    shape = RoundedCornerShape(8.dp), singleLine = true
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Estrategia Cartográfica Inicial", style = EstilosSoporte.LabelMedium.copy(color = EstilosSoporte.Muted))
                    val satActivo = modoElegido == ModoCapaBase.ESRI_SATELITE
                    Surface(onClick = { modoElegido = ModoCapaBase.ESRI_SATELITE }, color = if (satActivo) EstilosSoporte.Accent.copy(0.1f) else EstilosSoporte.Superficie2, shape = RoundedCornerShape(10.dp), border = BorderStroke(if (satActivo) 1.5.dp else 1.dp, if (satActivo) EstilosSoporte.Accent else EstilosSoporte.Borde)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(40.dp).background(EstilosSoporte.Accent.copy(0.15f), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Map, null, tint = EstilosSoporte.Accent, modifier = Modifier.size(22.dp)) }
                            Column(Modifier.weight(1f)) {
                                Text("Satélite de Alta Resolución", style = EstilosSoporte.BodyLarge.copy(fontWeight = FontWeight.Bold), color = EstilosSoporte.Texto)
                                Text("Servidor global ESRI Satélite. Ideal para geología estructural.", style = EstilosSoporte.BodyLarge.copy(color = EstilosSoporte.Texto2))
                            }
                            if (satActivo) Icon(Icons.Default.CheckCircle, null, tint = EstilosSoporte.Accent, modifier = Modifier.size(18.dp))
                        }
                    }

                    val pdfActivo = modoElegido == ModoCapaBase.GEO_PDF
                    Surface(onClick = { modoElegido = ModoCapaBase.GEO_PDF }, color = if (pdfActivo) EstilosSoporte.Amber.copy(0.1f) else EstilosSoporte.Superficie2, shape = RoundedCornerShape(10.dp), border = BorderStroke(if (pdfActivo) 1.5.dp else 1.dp, if (pdfActivo) EstilosSoporte.Amber else EstilosSoporte.Borde)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(40.dp).background(EstilosSoporte.Amber.copy(0.15f), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Default.PictureAsPdf, null, tint = EstilosSoporte.Amber, modifier = Modifier.size(22.dp)) }
                            Column(Modifier.weight(1f)) {
                                Text("Plano GeoPDF Georreferenciado", style = EstilosSoporte.BodyLarge.copy(fontWeight = FontWeight.Bold), color = EstilosSoporte.Texto)
                                Text("Mapas locales cargados al disco. Operación 100% offline.", style = EstilosSoporte.BodyLarge.copy(color = EstilosSoporte.Texto2))
                            }
                            if (pdfActivo) Icon(Icons.Default.CheckCircle, null, tint = EstilosSoporte.Amber, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                Button(onClick = { if (nombre.isNotBlank()) onCrear(nombre, modoElegido) }, enabled = nombre.isNotBlank(), modifier = Modifier.fillMaxWidth().height(44.dp), colors = ButtonDefaults.buttonColors(containerColor = EstilosSoporte.Accent, contentColor = Color.Black, disabledContainerColor = EstilosSoporte.Superficie2, disabledContentColor = EstilosSoporte.Muted), shape = RoundedCornerShape(8.dp)) {
                    Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Inicializar Banco de Trabajo", style = EstilosSoporte.BodyLarge.copy(fontWeight = FontWeight.Bold))
                }
            }
        }
    }
}

@Composable
fun PantallaGestionOffline(onDescargar: (Double, Double, Double, Double, Int) -> Unit, onCargarMbtiles: () -> Unit, onCerrar: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = EstilosSoporte.Fondo) {
        Column(Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Text("MAPA OFFLINE", style = EstilosSoporte.LabelMedium.copy(color = EstilosSoporte.Accent, fontWeight = FontWeight.Bold)); Spacer(Modifier.weight(1f)); IconButton(onClick = onCerrar) { Icon(Icons.Default.Close, null, tint = EstilosSoporte.Muted) } }
            TarjetaOffline("📦", "Cargar archivo .mbtiles", "Si ya tienes un archivo .mbtiles descargado (desde MOBAC, QGIS u otra fuente), cárgalo directamente.", EstilosSoporte.Accent, "Seleccionar archivo", onCargarMbtiles)
            TarjetaOffline("💾", "Caché automático de tiles", "Navega la zona de trabajo con WiFi antes de ir al campo. OSMDroid guarda hasta 200MB de tiles visitados automáticamente.", EstilosSoporte.Accent2, "Ver caché actual", {})

            Surface(color = EstilosSoporte.Superficie2, shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, EstilosSoporte.Borde)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("FUENTES DE TILES GRATUITAS", style = EstilosSoporte.LabelMedium.copy(color = EstilosSoporte.Muted))
                    listOf(Triple("🗺️", "OpenStreetMap", "openstreetmap.org — calles y topografía"), Triple("🛰️", "ESRI World Imagery", "arcgisonline.com — satélite gratuito"), Triple("⛰️", "OpenTopoMap", "opentopomap.org — curvas de nivel")).forEach { (ico, nombre, desc) ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(ico, style = TextStyle(fontSize = 14.sp))
                            Column { Text(nombre, style = EstilosSoporte.BodyLarge.copy(fontWeight = FontWeight.Bold), color = EstilosSoporte.Texto); Text(desc, style = EstilosSoporte.BodyLarge, color = EstilosSoporte.Texto2) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TarjetaOffline(icono: String, titulo: String, descripcion: String, colorAcento: Color, accion: String, onAccion: () -> Unit) {
    Surface(color = EstilosSoporte.Superficie, shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, EstilosSoporte.Borde)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) { Text(icono, style = TextStyle(fontSize = 22.sp)); Text(titulo, style = EstilosSoporte.BodyLarge.copy(fontWeight = FontWeight.Bold), color = EstilosSoporte.Texto) }
            Text(descripcion, style = EstilosSoporte.BodyLarge, color = EstilosSoporte.Texto2)
            Button(onClick = onAccion, colors = ButtonDefaults.buttonColors(containerColor = colorAcento.copy(0.15f), contentColor = colorAcento), shape = RoundedCornerShape(6.dp), border = BorderStroke(1.dp, colorAcento.copy(0.3f))) { Text(accion, style = EstilosSoporte.LabelMedium) }
        }
    }
}
