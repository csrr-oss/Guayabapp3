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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

// ─── PALETA ───────────────────────────────────────────────────────────────────

private val ColorFondo       = Color(0xFF0F1117)
private val ColorSuperficie  = Color(0xFF181C27)
private val ColorSuperficie2 = Color(0xFF1F2436)
private val ColorBorde       = Color(0xFF2A3045)
private val ColorAccent      = Color(0xFF00D084)
private val ColorAccent2     = Color(0xFF0090FF)
private val ColorMuted       = Color(0xFF6B7A99)
private val ColorTexto       = Color(0xFFE8EAF2)
private val ColorTexto2      = Color(0xFF9AA3BF)
private val ColorAmber       = Color(0xFFF0A500)
private val ColorPurple      = Color(0xFF7C6AF7)
private val ColorWarn        = Color(0xFFFF6B35)

// ═══════════════════════════════════════════════════════════════════════════════
// SPLASH SCREEN
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun SplashScreen(onListo: () -> Unit) {
    val alpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(800, easing = EaseOut),
        label = "fade_in"
    )
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "scale_in"
    )

    LaunchedEffect(Unit) {
        delay(1800)
        onListo()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF0D1F1A), ColorFondo),
                    radius = 800f
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.alpha(alpha).scale(scale)
        ) {
            // Logo
            Box(
                Modifier
                    .size(80.dp)
                    .background(
                        Brush.radialGradient(listOf(ColorAccent.copy(.3f), Color.Transparent)),
                        CircleShape
                    )
                    .border(1.dp, ColorAccent.copy(.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Map, contentDescription = null,
                    tint = ColorAccent, modifier = Modifier.size(38.dp))
            }

            Text("GEOFIELD",
                style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 28.sp,
                    fontWeight = FontWeight.Medium, color = ColorAccent,
                    letterSpacing = 4.sp))

            Text("Levantamiento georeferenciado de campo",
                style = TextStyle(fontSize = 12.sp, color = ColorMuted,
                    letterSpacing = .5.sp))
        }

        // Versión
        Text("v1.0.0", modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
            style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = ColorMuted))
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// PANTALLA DE PERMISOS
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun PantallaPermisos(onSolicitarPermisos: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(ColorFondo),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .widthIn(max = 360.dp)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Icon(Icons.Default.GpsFixed, contentDescription = null,
                tint = ColorAccent, modifier = Modifier.size(52.dp))

            Text("Permisos necesarios",
                style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium,
                    color = ColorTexto, fontFamily = FontFamily.Monospace))

            Text("GeoField necesita acceso a tu ubicación y cámara para funcionar en campo.",
                style = TextStyle(fontSize = 12.sp, color = ColorTexto2,
                    lineHeight = 18.sp, textAlign = TextAlign.Center))

            // Lista de permisos
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ItemPermiso(Icons.Default.GpsFixed,     ColorAccent,  "Ubicación precisa",  "Para georeferenciación de puntos")
                ItemPermiso(Icons.Default.CameraAlt,    ColorAccent2, "Cámara",             "Para fotos y videos georeferenciados")
                ItemPermiso(Icons.Default.Mic,          ColorPurple,  "Micrófono",          "Para grabación de video con audio")
                ItemPermiso(Icons.Default.FolderOpen,   ColorAmber,   "Almacenamiento",     "Para guardar y exportar archivos KML")
            }

            Spacer(Modifier.height(4.dp))

            Button(
                onClick = onSolicitarPermisos,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ColorAccent, contentColor = Color.Black),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Conceder permisos",
                    style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium))
            }
        }
    }
}

@Composable
private fun ItemPermiso(
    icono: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    titulo: String,
    desc: String
) {
    Surface(
        color = ColorSuperficie,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, ColorBorde)
    ) {
        Row(
            Modifier.padding(11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier.size(34.dp).background(color.copy(.15f), RoundedCornerShape(7.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icono, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            }
            Column {
                Text(titulo, style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = ColorTexto))
                Text(desc, style = TextStyle(fontSize = 10.sp, color = ColorTexto2))
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// PANTALLA DE PROYECTOS
// ═══════════════════════════════════════════════════════════════════════════════

// Modelo de vista rápida para la lista (en producción viene del DAO)
data class ProyectoResumen(
    val id: Long,
    val nombre: String,
    val totalPuntos: Int,
    val totalFotos: Int,
    val ultimaActividad: Long,
    val modoMapa: String   // "osm" | "pdf"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProyectosScreen(
    onAbrirProyecto: (Long) -> Unit,
    onNuevoProyecto: () -> Unit
) {
    // Datos de ejemplo — en producción: Flow<List<ProyectoResumen>> desde Room
    val proyectos = remember {
        listOf(
            ProyectoResumen(1, "Cuenca Caño Limón", 14, 27, System.currentTimeMillis() - 3600000, "osm"),
            ProyectoResumen(2, "Sector Arauca Norte", 6, 8, System.currentTimeMillis() - 86400000, "pdf"),
            ProyectoResumen(3, "Levantamiento Vichada", 0, 0, System.currentTimeMillis() - 172800000, "osm"),
        )
    }

    Scaffold(
        containerColor = ColorFondo,
        topBar = {
            Surface(color = ColorSuperficie, tonalElevation = 0.dp) {
                Row(
                    Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("GEOFIELD",
                        style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp,
                            color = ColorAccent, letterSpacing = 1.sp))
                    Spacer(Modifier.weight(1f))
                    Text("Proyectos",
                        style = TextStyle(fontSize = 13.sp, color = ColorTexto2))
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNuevoProyecto,
                containerColor = ColorAccent,
                contentColor = Color.Black,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "Nuevo proyecto",
                    modifier = Modifier.size(26.dp))
            }
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text("MIS PROYECTOS",
                    style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 10.sp,
                        color = ColorMuted, letterSpacing = .5.sp),
                    modifier = Modifier.padding(bottom = 4.dp))
            }

            if (proyectos.isEmpty()) {
                item { ProyectoVacio(onNuevoProyecto) }
            } else {
                items(proyectos, key = { it.id }) { proyecto ->
                    TarjetaProyecto(
                        proyecto = proyecto,
                        onClick = { onAbrirProyecto(proyecto.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TarjetaProyecto(
    proyecto: ProyectoResumen,
    onClick: () -> Unit
) {
    val fmt = remember { SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault()) }
    val colorModo = if (proyecto.modoMapa == "osm") ColorAccent2 else ColorAmber
    val labelModo = if (proyecto.modoMapa == "osm") "OSM" else "PDF"

    Surface(
        onClick = onClick,
        color = ColorSuperficie,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, ColorBorde)
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Ícono modo mapa
            Box(
                Modifier.size(44.dp).background(colorModo.copy(.12f), RoundedCornerShape(9.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (proyecto.modoMapa == "osm") Icons.Default.Map else Icons.Default.PictureAsPdf,
                    contentDescription = null, tint = colorModo, modifier = Modifier.size(22.dp)
                )
            }

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(proyecto.nombre,
                        style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium,
                            color = ColorTexto))
                    Surface(color = colorModo.copy(.15f), shape = RoundedCornerShape(3.dp)) {
                        Text(labelModo, Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                            style = TextStyle(fontFamily = FontFamily.Monospace,
                                fontSize = 8.sp, color = colorModo))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("${proyecto.totalPuntos} puntos",
                        style = TextStyle(fontSize = 10.sp, color = ColorAccent))
                    Text("${proyecto.totalFotos} fotos",
                        style = TextStyle(fontSize = 10.sp, color = ColorPurple))
                }
                Text("Última actividad: ${fmt.format(Date(proyecto.ultimaActividad))}",
                    style = TextStyle(fontSize = 9.sp, color = ColorMuted,
                        fontFamily = FontFamily.Monospace))
            }

            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = ColorMuted)
        }
    }
}

@Composable
private fun ProyectoVacio(onNuevo: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(Icons.Default.FolderOpen, contentDescription = null,
            tint = ColorMuted, modifier = Modifier.size(48.dp))
        Text("Sin proyectos aún",
            style = TextStyle(fontSize = 14.sp, color = ColorTexto2))
        TextButton(onClick = onNuevo) {
            Text("Crear el primer proyecto",
                style = TextStyle(fontSize = 12.sp, color = ColorAccent))
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// PANTALLA DE CONFIGURACIÓN
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun ConfiguracionScreen(onCerrar: () -> Unit) {
    Scaffold(
        containerColor = ColorFondo,
        topBar = {
            Surface(color = ColorSuperficie, tonalElevation = 0.dp) {
                Row(
                    Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(onClick = onCerrar) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver", tint = ColorMuted)
                    }
                    Text("Configuración",
                        style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium,
                            color = ColorTexto))
                }
            }
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SeccionConfig("TIPOS DE PUNTO") {
                ItemConfig(Icons.Default.Circle, ColorAccent,    "Visual",     "4 campos configurados")
                ItemConfig(Icons.Default.Science, ColorPurple,   "Muestra",    "8 campos configurados")
                ItemConfig(Icons.Default.Layers,  ColorAmber,    "Estructura", "5 campos configurados")
                ItemConfig(Icons.Default.Add,      ColorMuted,   "Nuevo tipo", "Agregar categoría personalizada")
            }

            SeccionConfig("EXPORTACIÓN") {
                ItemConfig(Icons.Default.FileOpen,  ColorAccent2, "Formato KML",     "Google Earth compatible")
                ItemConfig(Icons.Default.Photo,     ColorPurple,  "Fotos en KML",    "Activado")
                ItemConfig(Icons.Default.Folder,    ColorAmber,   "Carpeta destino", "Descargas / GeoField")
            }

            SeccionConfig("GPS") {
                ItemConfig(Icons.Default.GpsFixed,  ColorAccent,  "Intervalo de actualización", "2 segundos")
                ItemConfig(Icons.Default.Speed,     ColorAccent2, "Precisión mínima",           "Alertar si > 10m")
                ItemConfig(Icons.Default.BatteryFull, ColorAmber, "Modo de energía",            "Alta precisión")
            }

            SeccionConfig("MAPA BASE") {
                ItemConfig(Icons.Default.Map,          ColorAccent2, "Fuente OSM",   "ESRI Satélite (por defecto)")
                ItemConfig(Icons.Default.CloudDownload, ColorAccent, "Caché offline","200 MB reservados")
                ItemConfig(Icons.Default.Storage,       ColorAmber,  "Tiles locales","Sin archivo cargado")
            }

            SeccionConfig("ACERCA DE") {
                ItemConfig(Icons.Default.Info,    ColorMuted, "Versión", "GeoField v1.0.0")
                ItemConfig(Icons.Default.Code,    ColorMuted, "Stack",   "Kotlin · Compose · Room · OSMDroid")
                ItemConfig(Icons.Default.Person,  ColorMuted, "Licencia","Uso privado de campo")
            }
        }
    }
}

@Composable
private fun SeccionConfig(titulo: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(titulo,
            style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 9.sp,
                color = ColorMuted, letterSpacing = .5.sp),
            modifier = Modifier.padding(bottom = 2.dp))
        Surface(color = ColorSuperficie, shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, ColorBorde)) {
            Column(Modifier.fillMaxWidth(), content = content)
        }
    }
}

@Composable
private fun ColumnScope.ItemConfig(
    icono: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    titulo: String,
    valor: String
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icono, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Text(titulo, style = TextStyle(fontSize = 12.sp, color = ColorTexto), modifier = Modifier.weight(1f))
        Text(valor, style = TextStyle(fontSize = 11.sp, color = ColorMuted, fontFamily = FontFamily.Monospace))
        Icon(Icons.Default.ChevronRight, contentDescription = null,
            tint = ColorBorde, modifier = Modifier.size(14.dp))
    }
    HorizontalDivider(color = ColorBorde, thickness = 0.5.dp,
        modifier = Modifier.padding(horizontal = 14.dp))
}

// ═══════════════════════════════════════════════════════════════════════════════
// NUEVO PROYECTO
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun NuevoProyectoScreen(
    onCrear: (nombre: String, modo: com.geofield.ui.ModoMapaBase) -> Unit,
    onCancelar: () -> Unit
) {
    var nombre by remember { mutableStateOf("") }
    var modoElegido by remember { mutableStateOf(com.geofield.ui.ModoMapaBase.OSM) }

    Box(
        Modifier.fillMaxSize().background(Color(0xFF0F1117)),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.width(360.dp),
            color = Color(0xFF181C27),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, Color(0xFF2A3045))
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("NUEVO PROYECTO",
                        style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp,
                            color = Color(0xFF00D084), letterSpacing = 0.5.sp,
                            fontWeight = FontWeight.Medium))
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onCancelar, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar",
                            tint = Color(0xFF6B7A99))
                    }
                }

                OutlinedTextField(
                    value = nombre,
                    onValueChange = { nombre = it },
                    label = { Text("Nombre del proyecto") },
                    placeholder = { Text("Ej: Cuenca Caño Limón 2024",
                        style = TextStyle(color = Color(0xFF6B7A99))) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00D084),
                        unfocusedBorderColor = Color(0xFF2A3045),
                        focusedTextColor = Color(0xFFE8EAF2),
                        unfocusedTextColor = Color(0xFFE8EAF2),
                        cursorColor = Color(0xFF00D084),
                        focusedContainerColor = Color(0xFF1F2436),
                        unfocusedContainerColor = Color(0xFF1F2436),
                        focusedLabelColor = Color(0xFF00D084),
                        unfocusedLabelColor = Color(0xFF6B7A99)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Mapa base inicial",
                        style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 9.sp,
                            color = Color(0xFF6B7A99), letterSpacing = 0.5.sp))

                    // Opción OSM
                    val osmActivo = modoElegido == com.geofield.ui.ModoMapaBase.OSM
                    Surface(
                        onClick = { modoElegido = com.geofield.ui.ModoMapaBase.OSM },
                        color = if (osmActivo) Color(0xFF0090FF).copy(0.1f) else Color(0xFF1F2436),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(
                            if (osmActivo) 1.5.dp else 1.dp,
                            if (osmActivo) Color(0xFF0090FF) else Color(0xFF2A3045)
                        )
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(40.dp).background(Color(0xFF0090FF).copy(0.15f),
                                RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Map, null, tint = Color(0xFF0090FF),
                                    modifier = Modifier.size(22.dp))
                            }
                            Column(Modifier.weight(1f)) {
                                Text("Satélite / Calles",
                                    style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium,
                                        color = Color(0xFFE8EAF2)))
                                Text("OpenStreetMap · ESRI · sin API key · offline disponible",
                                    style = TextStyle(fontSize = 10.sp, color = Color(0xFF9AA3BF),
                                        lineHeight = 14.sp))
                            }
                            if (osmActivo) Icon(Icons.Default.CheckCircle, null,
                                tint = Color(0xFF0090FF), modifier = Modifier.size(18.dp))
                        }
                    }

                    // Opción PDF
                    val pdfActivo = modoElegido == com.geofield.ui.ModoMapaBase.PDF_GEOREF
                    Surface(
                        onClick = { modoElegido = com.geofield.ui.ModoMapaBase.PDF_GEOREF },
                        color = if (pdfActivo) Color(0xFFF0A500).copy(0.1f) else Color(0xFF1F2436),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(
                            if (pdfActivo) 1.5.dp else 1.dp,
                            if (pdfActivo) Color(0xFFF0A500) else Color(0xFF2A3045)
                        )
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(40.dp).background(Color(0xFFF0A500).copy(0.15f),
                                RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.PictureAsPdf, null, tint = Color(0xFFF0A500),
                                    modifier = Modifier.size(22.dp))
                            }
                            Column(Modifier.weight(1f)) {
                                Text("PDF Georeferenciado",
                                    style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium,
                                        color = Color(0xFFE8EAF2)))
                                Text("Capas técnicas · geología · catastro · 100% offline",
                                    style = TextStyle(fontSize = 10.sp, color = Color(0xFF9AA3BF),
                                        lineHeight = 14.sp))
                            }
                            if (pdfActivo) Icon(Icons.Default.CheckCircle, null,
                                tint = Color(0xFFF0A500), modifier = Modifier.size(18.dp))
                        }
                    }
                }

                Button(
                    onClick = { if (nombre.isNotBlank()) onCrear(nombre, modoElegido) },
                    enabled = nombre.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00D084), contentColor = Color.Black,
                        disabledContainerColor = Color(0xFF1F2436),
                        disabledContentColor = Color(0xFF6B7A99)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Crear proyecto",
                        style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium))
                }
            }
        }
    }
}
