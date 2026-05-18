package com.geofield.ui

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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geofield.theme.GuayabappTypography // Importación del sistema tipográfico Nunito
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

// ─── PALETA CORPORATIVA UNIFICADA GUAYABAPP ──────────────────────────────────
private val ColorFondo       = Color(0xFF0F1117)
private val ColorSuperficie  = Color(0xFF181C27)
private val ColorSuperficie2 = Color(0xFF1F2436)
private val ColorBorde       = Color(0xFF2A3045)
private val ColorAccent      = Color(0xFF87A922) // Verde Guayaba Maduro
private val ColorAccent2     = Color(0xFF0090FF) // Azul OSM
private val ColorMuted       = Color(0xFF6B7A99)
private val ColorTexto       = Color(0xFFE8EAF2)
private val ColorTexto2      = Color(0xFF9AA3BF)
private val ColorAmber       = Color(0xFFF0A500) // Ámbar Geológico
private val ColorPurple      = Color(0xFF7C6AF7) // Morado Munsell
private val ColorRed         = Color(0xFFD80032) // Rubí Pulpa

// ─── RESUMEN DE PROYECTO ──────────────────────────────────────────────────────
data class ProyectoResumen(
    val id: Long,
    val nombre: String,
    val totalPuntos: Int,
    val totalFotos: Int,
    val ultimaActividad: Long,
    val modoCapa: ModoCapaBase // CORRECCIÓN: Vinculado al enum global unificado
)

// ═══════════════════════════════════════════════════════════════════════════════
// 1. SPLASH SCREEN (PANTALLA DE BIENVENIDA CORPORATIVA)
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun SplashScreen(onListo: () -> Unit) {
    val alpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(900, easing = EaseOut),
        label = "fade_in"
    )
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "scale_in"
    )

    LaunchedEffect(Unit) {
        delay(2000) // Tiempo ideal para lectura de marca en el arranque
        onListo()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF1A2416), ColorFondo), // Gradiente sutil verde guayaba profundo
                    radius = 900f
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.alpha(alpha).scale(scale)
        ) {
            // Emblema Corporativo: Guayaba con montañas y río representado de forma abstracta
            Box(
                Modifier
                    .size(88.dp)
                    .background(Brush.radialGradient(listOf(ColorAccent.copy(.25f), Color.Transparent)), CircleShape)
                    .border(1.5.dp, ColorAccent.copy(.45f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Terrain, contentDescription = null, tint = ColorAccent, modifier = Modifier.size(42.dp))
            }

            // CORRECCIÓN: Nombre oficial unificado con Nunito Black pesado
            Text(
                text = "Guayabapp",
                style = GuayabappTypography.titleLarge.copy(fontSize = 32.sp, fontWeight = FontWeight.Black),
                color = ColorTexto,
                letterSpacing = 1.sp
            )

            Text(
                text = "Levantamiento georreferenciado de campo",
                style = GuayabappTypography.bodyLarge.copy(fontSize = 13.sp, color = ColorTexto2),
                letterSpacing = .5.sp
            )
        }

        Text(
            text = "v1.1.0 · Sistema Offline",
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp),
            style = GuayabappTypography.labelMedium,
            color = ColorMuted
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// 2. PANTALLA DE GESTIÓN DE PERMISOS DE HARDWARE
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun PantallaPermisos(onSolicitarPermisos: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(ColorFondo),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.widthIn(max = 380.dp).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            Icon(Icons.Default.GpsFixed, contentDescription = null, tint = ColorAccent, modifier = Modifier.size(56.dp))

            Text(
                text = "Permisos de Operación",
                style = GuayabappTypography.titleMedium.copy(fontSize = 20.sp),
                color = ColorTexto
            )

            Text(
                text = "Guayabapp requiere acceso directo a los sensores de hardware para capturar coordenadas satelitales y registrar evidencias multimedia en zonas offline.",
                style = GuayabappTypography.bodyLarge.copy(fontSize = 13.sp, lineHeight = 19.sp),
                color = ColorTexto2,
                textAlign = TextAlign.Center
            )

            // Lista técnica con incrementos en la legibilidad
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                ItemPermiso(Icons.Default.GpsFixed, ColorAccent, "Ubicación Satelital Precisa", "Captura de coordenadas universales WGS84.")
                ItemPermiso(Icons.Default.CameraAlt, ColorAccent2, "Cámara de Hardware (CameraX)", "Registro de fotografía y clips de video técnicos.")
                ItemPermiso(Icons.Default.Mic, ColorPurple, "Micrófono Integrado", "Captura de audio ambiental en notas de video.")
                ItemPermiso(Icons.Default.FolderOpen, ColorAmber, "Almacenamiento Local", "Persistencia de base de datos Room y exportación KML.")
            }

            Spacer(Modifier.height(6.dp))

            Button(
                onClick = onSolicitarPermisos,
                modifier = Modifier.fillMaxWidth().height(46.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ColorAccent, contentColor = Color.Black),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Conceder Permisos Técnicos", style = GuayabappTypography.bodyLarge.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp))
            }
        }
    }
}

@Composable
private fun ItemPermiso(icono: androidx.compose.ui.graphics.vector.ImageVector, color: Color, titulo: String, desc: String) {
    Surface(color = ColorSuperficie, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, ColorBorde)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(36.dp).background(color.copy(.12f), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                Icon(icono, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            }
            Column {
                Text(titulo, style = GuayabappTypography.bodyLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold), color = ColorTexto)
                Text(desc, style = GuayabappTypography.bodyLarge.copy(fontSize = 12.sp), color = ColorTexto2)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// 3. EXPLORADOR DE PROYECTOS INDEPENDIENTES
// ═══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProyectosScreen(
    onAbrirProyecto: (Long) -> Unit,
    onNuevoProyecto: () -> Unit
) {
    // Simulación de persistencia Room con el nuevo formato del Enum de capas base
    val proyectos = remember {
        listOf(
            ProyectoResumen(1, "Cuenca Caño Limón", 14, 27, System.currentTimeMillis() - 3600000, ModoCapaBase.ESRI_SATELITE),
            ProyectoResumen(2, "Sector Arauca Norte", 6, 8, System.currentTimeMillis() - 86400000, ModoCapaBase.GEO_PDF),
            ProyectoResumen(3, "Levantamiento Vichada", 0, 0, System.currentTimeMillis() - 172800000, ModoCapaBase.OSM_ESTANDAR),
        )
    }

    Scaffold(
        containerColor = ColorFondo,
        topBar = {
            Surface(color = ColorSuperficie, tonalElevation = 0.dp) {
                Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Guayabapp", style = GuayabappTypography.titleLarge, color = ColorAccent)
                    Spacer(Modifier.weight(1f))
                    Text("Proyectos Activos", style = GuayabappTypography.bodyLarge.copy(fontSize = 14.sp, color = ColorTexto2))
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNuevoProyecto, containerColor = ColorAccent, contentColor = Color.Black, shape = CircleShape) {
                Icon(Icons.Default.Add, contentDescription = "Nuevo Proyecto", modifier = Modifier.size(26.dp))
            }
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("BANCO DE TRABAJO INDEPENDIENTE", style = GuayabappTypography.labelMedium.copy(color = ColorMuted, fontSize = 11.sp), modifier = Modifier.padding(bottom = 4.dp))
            }

            if (proyectos.isEmpty()) {
                item { ProyectoVacio(onNuevoProyecto) }
            } else {
                items(proyectos, key = { it.id }) { proyecto ->
                    TarjetaProyecto(proyecto = proyecto, onClick = { onAbrirProyecto(proyecto.id) })
                }
            }
        }
    }
}

@Composable
private fun TarjetaProyecto(proyecto: ProyectoResumen, onClick: () -> Unit) {
    val fmt = remember { SimpleDateFormat("dd/MM/yyyy  HH:mm", Locale.getDefault()) }
    val colorModo = when (proyecto.modoCapa) {
        ModoCapaBase.GEO_PDF -> ColorAmber
        ModoCapaBase.OSM_ESTANDAR -> ColorAccent2
        ModoCapaBase.ESRI_SATELITE -> ColorAccent
    }
    val labelModo = when (proyecto.modoCapa) {
        ModoCapaBase.GEO_PDF -> "PDF"
        ModoCapaBase.OSM_ESTANDAR -> "OSM"
        ModoCapaBase.ESRI_SATELITE -> "SAT"
    }

    Surface(onClick = onClick, color = ColorSuperficie, shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, ColorBorde)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(46.dp).background(colorModo.copy(.12f), RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (proyecto.modoCapa == ModoCapaBase.GEO_PDF) Icons.Default.PictureAsPdf else Icons.Default.Map,
                    contentDescription = null, tint = colorModo, modifier = Modifier.size(22.dp)
                )
            }

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(proyecto.nombre, style = GuayabappTypography.bodyLarge.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp), color = ColorTexto)
                    Surface(color = colorModo.copy(.15f), shape = RoundedCornerShape(4.dp)) {
                        Text(labelModo, Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = GuayabappTypography.labelMedium.copy(fontSize = 10.sp, color = colorModo))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("${proyecto.totalPuntos} puntos", style = GuayabappTypography.labelMedium.copy(fontSize = 12.sp, color = ColorAccent))
                    Text("${proyecto.totalFotos} fotos", style = GuayabappTypography.labelMedium.copy(fontSize = 12.sp, color = ColorPurple))
                }
                Text("Última modificación: ${fmt.format(Date(proyecto.ultimaActividad))}", style = GuayabappTypography.labelMedium.copy(fontSize = 11.sp, color = ColorMuted))
            }

            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = ColorBorde)
        }
    }
}

@Composable
private fun ProyectoVacio(onNuevo: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Default.FolderOpen, contentDescription = null, tint = ColorMuted, modifier = Modifier.size(52.dp))
        Text("No se registran campañas de control", style = GuayabappTypography.bodyLarge, color = ColorTexto2)
        TextButton(onClick = onNuevo) {
            Text("Iniciar Primer Proyecto Técnico", style = GuayabappTypography.bodyLarge.copy(color = ColorAccent, fontWeight = FontWeight.Bold))
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// 4. PANTALLA PANEL DE CONFIGURACIÓN GLOBAL
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun ConfiguracionScreen(onCerrar: () -> Unit) {
    Scaffold(
        containerColor = ColorFondo,
        topBar = {
            Surface(color = ColorSuperficie, tonalElevation = 0.dp) {
                Row(Modifier.fillMaxWidth().height(54.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = onCerrar) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver", tint = ColorTexto)
                    }
                    Text("Configuración de Parámetros", style = GuayabappTypography.titleMedium, color = ColorTexto)
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SeccionConfig("PLANTILLAS Y CATEGORÍAS ADM.") {
                ItemConfig(Icons.Default.Circle, ColorAccent, "Visual (Cobertura / Terreno)", "4 campos Json")
                ItemConfig(Icons.Default.Science, ColorPurple, "Muestra (Suelos / Geotecnia)", "8 campos Json")
                ItemConfig(Icons.Default.Layers, ColorAmber, "Estructura (Fallas / Rumbo)", "5 campos Json")
                ItemConfig(Icons.Default.AddCircle, ColorAccent, "Inyectar Nueva Etiqueta (+)", "Gestor dinámico")
            }

            SeccionConfig("EXPORTACIÓN E INTEROPERABILIDAD") {
                ItemConfig(Icons.Default.FileOpen, ColorAccent2, "Estructura KML Estándar", "Google Earth / QGIS")
                ItemConfig(Icons.Default.Photo, ColorPurple, "Embeber Registro Fotográfico", "Metadatos HTML")
                ItemConfig(Icons.Default.Folder, ColorAmber, "Directorio Físico Local", "GeoField/Media")
            }

            SeccionConfig("RASTREO SATELITAL (GPS)") {
                ItemConfig(Icons.Default.GpsFixed, ColorAccent, "Intervalo de Muestreo", "2.0 segundos")
                ItemConfig(Icons.Default.Speed, ColorRed, "Tolerancia Crítica Horizontal", "Alerta si > 10 metros")
                ItemConfig(Icons.Default.BatteryFull, ColorAmber, "Restricción de Energía", "Fijación Satelital de Hardware")
            }

            SeccionConfig("SOPORTE CARTOGRÁFICO BASE") {
                ItemConfig(Icons.Default.Map, ColorAccent2, "Proveedor por Defecto", "ESRI Satélite Offline")
                ItemConfig(Icons.Default.CloudDownload, ColorAccent, "Caché Interno Almacenado", "200 MB Asignados")
                ItemConfig(Icons.Default.Storage, ColorAmber, "Paquetes Descentralizados Tiles", ".mbtiles cargados")
            }

            SeccionConfig("INFORMACIÓN INSTITUCIONAL") {
                ItemConfig(Icons.Default.Info, ColorMuted, "Versión Estable", "Guayabapp v1.1.0")
                ItemConfig(Icons.Default.Code, ColorMuted, "Núcleo Motor compilado", "Kotlin · Compose · Room · CameraX")
                ItemConfig(Icons.Default.Person, ColorMuted, "Régimen de Licenciamiento", "Uso Corporativo de Campo")
            }
        }
    }
}

@Composable
private fun SeccionConfig(titulo: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(titulo, style = GuayabappTypography.labelMedium.copy(color = ColorMuted, fontSize = 11.sp), modifier = Modifier.padding(bottom = 2.dp))
        Surface(color = ColorSuperficie, shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, ColorBorde)) {
            Column(Modifier.fillMaxWidth(), content = content)
        }
    }
}

@Composable
private fun ColumnScope.ItemConfig(icono: androidx.compose.ui.graphics.vector.ImageVector, color: Color, titulo: String, valor: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icono, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Text(titulo, style = GuayabappTypography.bodyLarge.copy(fontSize = 14.sp), color = ColorTexto, modifier = Modifier.weight(1f))
        Text(valor, style = GuayabappTypography.labelMedium.copy(fontSize = 13.sp, color = ColorMuted))
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = ColorBorde, modifier = Modifier.size(14.dp))
    }
    HorizontalDivider(color = ColorBorde, thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 14.dp))
}

// ═══════════════════════════════════════════════════════════════════════════════
// 5. CONSTRUCTOR DE NUEVOS PROYECTOS INDEPENDIENTES
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun NuevoProyectoScreen(
    onCrear: (nombre: String, modo: ModoCapaBase) -> Unit, // Sincronizado con el enum global corregido
    onCancelar: () -> Unit
) {
    var nombre by remember { mutableStateOf("") }
    var modoElegido by remember { mutableStateOf(ModoCapaBase.ESRI_SATELITE) } // Satélite por defecto

    Box(Modifier.fillMaxSize().background(ColorFondo), contentAlignment = Alignment.Center) {
        Surface(modifier = Modifier.width(360.dp), color = ColorSuperficie, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, ColorBorde)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("NUEVO PROYECTO INDEPENDIENTE", style = GuayabappTypography.labelMedium.copy(color = ColorAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold))
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onCancelar, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = ColorMuted)
                    }
                }

                OutlinedTextField(
                    value = nombre,
                    onValueChange = { nombre = it },
                    label = { Text("Nombre de la Campaña de Campo", style = GuayabappTypography.bodyLarge) },
                    placeholder = { Text("Ej: Cuenca Caño Limón 2026", style = GuayabappTypography.bodyLarge.copy(color = ColorMuted)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ColorAccent,
                        unfocusedBorderColor = ColorBorde,
                        focusedTextColor = ColorTexto,
                        unfocusedTextColor = ColorTexto,
                        focusedContainerColor = ColorSuperficie2,
                        unfocusedContainerColor = ColorSuperficie2,
                        focusedLabelColor = ColorAccent,
                        unfocusedLabelColor = ColorMuted
                    ),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Estrategia Cartográfica Inicial", style = GuayabappTypography.labelMedium.copy(color = ColorMuted, fontSize = 11.sp))

                    // 1. Capa de Satélite Global (ESRI)
                    val satActivo = modoElegido == ModoCapaBase.ESRI_SATELITE
                    Surface(
                        onClick = { modoElegido = ModoCapaBase.ESRI_SATELITE },
                        color = if (satActivo) ColorAccent.copy(0.1f) else ColorSuperficie2,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(if (satActivo) 1.5.dp else 1.dp, if (satActivo) ColorAccent else ColorBorde)
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(40.dp).background(ColorAccent.copy(0.15f), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Map, null, tint = ColorAccent, modifier = Modifier.size(22.dp))
                            }
                            Column(Modifier.weight(1f)) {
                                Text("Satélite de Alta Resolución", style = GuayabappTypography.bodyLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold), color = ColorTexto)
                                Text("Servidor global ESRI Satélite. Ideal para geología estructural de campo libre.", style = GuayabappTypography.bodyLarge.copy(fontSize = 11.sp, color = ColorTexto2, lineHeight = 15.sp))
                            }
                            if (satActivo) Icon(Icons.Default.CheckCircle, null, tint = ColorAccent, modifier = Modifier.size(18.dp))
                        }
                    }

                    // 2. Capa Local Importada (GeoPDF)
                    val pdfActivo = modoElegido == ModoCapaBase.GEO_PDF
                    Surface(
                        onClick = { modoElegido = ModoCapaBase.GEO_PDF },
                        color = if (pdfActivo) ColorAmber.copy(0.1f) else ColorSuperficie2,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(if (pdfActivo) 1.5.dp else 1.dp, if (pdfActivo) ColorAmber else ColorBorde)
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(40.dp).background(ColorAmber.copy(0.15f), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.PictureAsPdf, null, tint = ColorAmber, modifier = Modifier.size(22.dp))
                            }
                            Column(Modifier.weight(1f)) {
                                Text("Plano GeoPDF Georreferenciado", style = GuayabappTypography.bodyLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold), color = ColorTexto)
                                Text("Mapas locales cargados al disco. Operación 100% offline sin conectividad.", style = GuayabappTypography.bodyLarge.copy(fontSize = 11.sp, color = ColorTexto2, lineHeight = 15.sp))
                            }
                            if (pdfActivo) Icon(Icons.Default.CheckCircle, null, tint = ColorAmber, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                Button(
                    onClick = { if (nombre.isNotBlank()) onCrear(nombre, modoElegido) },
                    enabled = nombre.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ColorAccent, contentColor = Color.Black, disabledContainerColor = ColorSuperficie2, disabledContentColor = ColorMuted),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Inicializar Banco de Trabajo", style = GuayabappTypography.bodyLarge.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp))
                }
            }
        }
    }
}
