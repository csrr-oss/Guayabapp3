package com.geofield.ui

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.geofield.data.MapaPdfEntity
import com.geofield.data.PuntoConMedia
import com.geofield.geo.GeoPdfTransform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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
private val ColorWarn        = Color(0xFFFF6B35)
private val ColorRed         = Color(0xFFFF4757)
private val ColorPurple      = Color(0xFF7C6AF7)

internal fun colorPorTipo(tipo: String) = when (tipo) {
    "visual"     -> Color(0xFF00D084)
    "muestra"    -> Color(0xFF7C6AF7)
    "estructura" -> Color(0xFFF0A500)
    else         -> Color(0xFF6B7A99)
}

// ─── MODO DE MAPA BASE ────────────────────────────────────────────────────────

enum class ModoMapaBase { OSM, PDF_GEOREF }

// ═══════════════════════════════════════════════════════════════════════════════
// PANTALLA PRINCIPAL UNIFICADA
// ═══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapaVisorScreen(
    viewModel: MapaViewModel,
    onNavegaConfiguracion: () -> Unit = {}
) {
    val estado by viewModel.estado.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var modoMapa by remember { mutableStateOf(ModoMapaBase.OSM) }

    LaunchedEffect(estado.mensajeSnack) {
        estado.mensajeSnack?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.snackConsumido()
        }
    }

    Scaffold(
        containerColor = ColorFondo,
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = ColorSuperficie2,
                    contentColor = ColorTexto,
                    actionColor = ColorAccent,
                    shape = RoundedCornerShape(8.dp)
                )
            }
        },
        topBar = {
            TopBarVisor(
                mapas = estado.todosLosMapas,
                mapaActivo = estado.mapaActivo,
                modoActual = modoMapa,
                onCambiarModo = { modoMapa = it },
                onCambiarPdf = { viewModel.cambiarMapaActivo(it) },
                onConfiguracion = onNavegaConfiguracion
            )
        },
        bottomBar = {
            BottomBarExportacion(
                totalPuntos = estado.puntos.size,
                totalFotos = estado.puntos.sumOf { it.fotos.size },
                exportando = estado.exportando,
                onExportar = { viewModel.exportarKml() }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxSize()) {

                // ── ÁREA MAPA ─────────────────────────────────────────────────
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    AnimatedContent(
                        targetState = modoMapa,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "mapa_transition"
                    ) { modo ->
                        when (modo) {
                            ModoMapaBase.OSM ->
                                VisorOsmdroid(
                                    puntos = estado.puntos,
                                    puntoSeleccionado = estado.puntoSeleccionado,
                                    onSeleccionarPunto = { viewModel.seleccionarPunto(it) },
                                    onCapturarPunto = { lat, lon, alt ->
                                        viewModel.agregarPunto(lat, lon, alt, 2.0, "visual",
                                            "VIS-${System.currentTimeMillis() % 10000}")
                                    }
                                )
                            ModoMapaBase.PDF_GEOREF ->
                                if (estado.mapaActivo != null) {
                                    VisorPdf(
                                        mapa = estado.mapaActivo!!,
                                        puntos = estado.puntos,
                                        puntoSeleccionado = estado.puntoSeleccionado,
                                        onSeleccionarPunto = { viewModel.seleccionarPunto(it) },
                                        onCapturarPunto = { lat, lon, alt ->
                                            viewModel.agregarPunto(lat, lon, alt, 2.0, "visual",
                                                "VIS-${System.currentTimeMillis() % 10000}")
                                        }
                                    )
                                } else {
                                    InvitacionCargarPdf(
                                        onCargar = { },
                                        onUsarOsm = { modoMapa = ModoMapaBase.OSM }
                                    )
                                }
                        }
                    }
                }

                // ── SIDEBAR ───────────────────────────────────────────────────
                SidebarPuntos(
                    puntos = estado.puntos,
                    puntoSeleccionado = estado.puntoSeleccionado,
                    filtroActivo = estado.filtroTipo,
                    onSeleccionar = { viewModel.seleccionarPunto(it) },
                    onFiltrar = { viewModel.filtrarPorTipo(it) }
                )
            }

            // ── PANEL DETALLE ─────────────────────────────────────────────────
            AnimatedVisibility(
                visible = estado.puntoSeleccionado != null,
                enter = slideInHorizontally(initialOffsetX = { it }),
                exit = slideOutHorizontally(targetOffsetX = { it }),
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                estado.puntoSeleccionado?.let { puntoConMedia ->
                    PanelDetallePunto(
                        puntoConMedia = puntoConMedia,
                        onGuardar = { desc, json ->
                            viewModel.actualizarDescripcion(puntoConMedia.punto.id, desc, json)
                        },
                        onCambiarTipo = { viewModel.cambiarTipoPunto(puntoConMedia.punto.id, it) },
                        onEliminar = { viewModel.eliminarPunto(puntoConMedia.punto.id) },
                        onAgregarFoto = { ruta ->
                            val p = puntoConMedia.punto
                            viewModel.agregarFoto(p.id, ruta, p.lat, p.lon, p.altitud)
                        },
                        onCerrar = { viewModel.seleccionarPunto(null) }
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// TOPBAR
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun TopBarVisor(
    mapas: List<MapaPdfEntity>,
    mapaActivo: MapaPdfEntity?,
    modoActual: ModoMapaBase,
    onCambiarModo: (ModoMapaBase) -> Unit,
    onCambiarPdf: (Long) -> Unit,
    onConfiguracion: () -> Unit
) {
    Surface(color = ColorSuperficie, tonalElevation = 0.dp) {
        Column {
            Row(
                Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "GUAYABAPP",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                        color = ColorAccent, letterSpacing = 1.sp
                    ),
                    modifier = Modifier.width(100.dp)
                )

                // Selector OSM / PDF
                Surface(
                    color = ColorSuperficie2,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, ColorBorde)
                ) {
                    Row(Modifier.padding(3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        listOf(ModoMapaBase.OSM to "Satélite", ModoMapaBase.PDF_GEOREF to "PDF Georef.").forEach { (modo, label) ->
                            val activo = modoActual == modo
                            val color = if (modo == ModoMapaBase.OSM) ColorAccent2 else ColorAmber
                            Surface(
                                onClick = { onCambiarModo(modo) },
                                color = if (activo) color.copy(0.2f) else Color.Transparent,
                                shape = RoundedCornerShape(6.dp),
                                border = if (activo) BorderStroke(1.dp, color.copy(0.5f)) else null
                            ) {
                                Text(
                                    label,
                                    Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    style = TextStyle(
                                        fontFamily = FontFamily.Monospace, fontSize = 10.sp,
                                        color = if (activo) color else ColorMuted
                                    )
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.weight(1f))

                // GPS badge animado
                val infiniteTransition = rememberInfiniteTransition(label = "gps")
                val alpha by infiniteTransition.animateFloat(
                    initialValue = 1f, targetValue = 0.3f,
                    animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
                    label = "pulse"
                )
                Surface(
                    color = ColorAccent.copy(0.1f),
                    shape = RoundedCornerShape(4.dp),
                    border = BorderStroke(1.dp, ColorAccent.copy(0.25f))
                ) {
                    Row(
                        Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(Modifier.size(6.dp).background(ColorAccent.copy(alpha = alpha), CircleShape))
                        Text("GPS ±2m", style = TextStyle(
                            fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = ColorAccent
                        ))
                    }
                }

                IconButton(onClick = onConfiguracion) {
                    Icon(Icons.Default.Settings, contentDescription = "Config", tint = ColorMuted)
                }
            }

            // Fila PDFs (solo en modo PDF con mapas cargados)
            AnimatedVisibility(visible = modoActual == ModoMapaBase.PDF_GEOREF && mapas.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().background(ColorSuperficie2)
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("PDF:", style = TextStyle(
                        fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = ColorMuted
                    ))
                    mapas.forEach { mapa ->
                        val activo = mapa.id == mapaActivo?.id
                        FilterChip(
                            selected = activo,
                            onClick = { onCambiarPdf(mapa.id) },
                            label = { Text(mapa.nombre.take(14), style = TextStyle(
                                fontFamily = FontFamily.Monospace, fontSize = 9.sp
                            ))},
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ColorAmber.copy(0.2f),
                                selectedLabelColor = ColorAmber,
                                containerColor = ColorSuperficie,
                                labelColor = ColorMuted
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true, selected = activo,
                                borderColor = ColorBorde, selectedBorderColor = ColorAmber
                            )
                        )
                    }
                    IconButton(onClick = { }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Add, contentDescription = "Cargar PDF",
                            tint = ColorAmber, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// VISOR PDF CON TILES + OVERLAY GPS
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
internal fun VisorPdf(
    mapa: MapaPdfEntity,
    puntos: List<PuntoConMedia>,
    puntoSeleccionado: PuntoConMedia?,
    onSeleccionarPunto: (Long?) -> Unit,
    onCapturarPunto: (lat: Double, lon: Double, alt: Double) -> Unit
) {
    val scope = rememberCoroutineScope()
    var bitmapTile by remember { mutableStateOf<Bitmap?>(null) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var canvasSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }

    val coordsCentro = remember(zoom, offsetX, offsetY, canvasSize, mapa) {
        if (canvasSize.width == 0) return@remember ""
        val cx = (canvasSize.width / 2f - offsetX) / zoom
        val cy = (canvasSize.height / 2f - offsetY) / zoom
        val (lat, lon) = GeoPdfTransform.pixelAGps(cx, cy, mapa)
        "N%.4f° W%.4f°".format(lat, Math.abs(lon))
    }

    LaunchedEffect(mapa.rutaArchivo, zoom, offsetX, offsetY, canvasSize) {
        if (canvasSize.width == 0) return@LaunchedEffect
        bitmapTile = renderTilePdf(mapa, canvasSize.width, canvasSize.height, zoom, offsetX, offsetY)
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF141824)).onSizeChanged { canvasSize = it }) {

        Canvas(
            modifier = Modifier.fillMaxSize()
                .pointerInput(mapa, puntos) {
                    detectTransformGestures { centroid, pan, gestureZoom, _ ->
                        val newZoom = (zoom * gestureZoom).coerceIn(0.5f, 8f)
                        offsetX = centroid.x - (centroid.x - offsetX) * (newZoom / zoom) + pan.x
                        offsetY = centroid.y - (centroid.y - offsetY) * (newZoom / zoom) + pan.y
                        zoom = newZoom
                    }
                }
                .pointerInput(mapa, puntos) {
                    detectTapGestures(
                        onLongPress = { tap ->
                            val (lat, lon) = GeoPdfTransform.pixelAGps(
                                (tap.x - offsetX) / zoom, (tap.y - offsetY) / zoom, mapa
                            )
                            onCapturarPunto(lat, lon, 0.0)
                        },
                        onTap = { tap ->
                            val tocado = puntos.firstOrNull { pm ->
                                val px = GeoPdfTransform.gpsAPixel(pm.punto.lat, pm.punto.lon, mapa)
                                val sx = px.x * zoom + offsetX
                                val sy = px.y * zoom + offsetY
                                Math.hypot((tap.x - sx).toDouble(), (tap.y - sy).toDouble()) < 24.0
                            }
                            onSeleccionarPunto(tocado?.punto?.id)
                        }
                    )
                }
        ) {
            bitmapTile?.let { drawImage(it.asImageBitmap(), topLeft = Offset(offsetX, offsetY)) }

            puntos.forEach { pm ->
                val p = pm.punto
                val px = GeoPdfTransform.gpsAPixel(p.lat, p.lon, mapa)
                val sx = px.x * zoom + offsetX
                val sy = px.y * zoom + offsetY
                if (sx < -20 || sx > size.width + 20 || sy < -20 || sy > size.height + 20) return@forEach

                val color = colorPorTipo(p.tipo)
                val sel = puntoSeleccionado?.punto?.id == p.id
                val radio = if (sel) 14.dp.toPx() else 10.dp.toPx()

                if (sel) drawCircle(color.copy(0.25f), radio * 1.8f, Offset(sx, sy))
                if (!p.completo) drawCircle(ColorWarn.copy(0.3f), radio * 1.5f, Offset(sx, sy))
                drawCircle(color.copy(0.9f), radio, Offset(sx, sy))
                drawCircle(Color.White.copy(0.15f), radio, Offset(sx, sy), style = Stroke(1.dp.toPx()))
                drawCircle(Color.White, 3.dp.toPx(), Offset(sx, sy))
                if (pm.fotos.isNotEmpty())
                    drawCircle(ColorPurple, 5.dp.toPx(), Offset(sx + radio * 0.8f, sy - radio * 0.8f))
            }
        }

        Surface(
            Modifier.align(Alignment.BottomStart).padding(8.dp),
            color = Color(0xCC0F1117), shape = RoundedCornerShape(4.dp)
        ) {
            Text(coordsCentro, Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = ColorAccent))
        }

        Surface(
            Modifier.align(Alignment.BottomEnd).padding(8.dp),
            color = Color(0xCC0F1117), shape = RoundedCornerShape(4.dp)
        ) {
            Text(mapa.escala, Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = ColorMuted))
        }

        FloatingActionButton(
            onClick = {
                if (canvasSize.width > 0) {
                    val cx = (canvasSize.width / 2f - offsetX) / zoom
                    val cy = (canvasSize.height / 2f - offsetY) / zoom
                    val (lat, lon) = GeoPdfTransform.pixelAGps(cx, cy, mapa)
                    onCapturarPunto(lat, lon, 0.0)
                }
            },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 56.dp),
            containerColor = ColorAccent, contentColor = Color.Black, shape = CircleShape
        ) {
            Icon(Icons.Default.Add, contentDescription = "Capturar punto", modifier = Modifier.size(28.dp))
        }
    }
}

private suspend fun renderTilePdf(
    mapa: MapaPdfEntity, canvasW: Int, canvasH: Int,
    zoom: Float, offsetX: Float, offsetY: Float
): Bitmap? = withContext(Dispatchers.IO) {
    try {
        val file = File(mapa.rutaArchivo)
        if (!file.exists()) return@withContext null
        val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(fd)
        val page = renderer.openPage(0)
        val vL = ((-offsetX) / zoom).coerceAtLeast(0f).toInt()
        val vT = ((-offsetY) / zoom).coerceAtLeast(0f).toInt()
        val vR = ((canvasW - offsetX) / zoom).coerceAtMost(mapa.widthPx.toFloat()).toInt()
        val vB = ((canvasH - offsetY) / zoom).coerceAtMost(mapa.heightPx.toFloat()).toInt()
        if (vR <= vL || vB <= vT) { page.close(); renderer.close(); fd.close(); return@withContext null }
        val outW = ((vR - vL) * zoom).toInt().coerceIn(1, 2048)
        val outH = ((vB - vT) * zoom).toInt().coerceIn(1, 2048)
        val bitmap = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val transform = android.graphics.Matrix().apply {
            val sX = outW.toFloat() / (vR - vL)
            val sY = outH.toFloat() / (vB - vT)
            setScale(sX, sY)
            preTranslate(-vL.toFloat(), -vT.toFloat())
            preScale(mapa.widthPx.toFloat() / page.width, mapa.heightPx.toFloat() / page.height)
        }
        page.render(bitmap, null, transform, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        page.close(); renderer.close(); fd.close()
        bitmap
    } catch (e: Exception) { null }
}

// ═══════════════════════════════════════════════════════════════════════════════
// SIDEBAR DE PUNTOS
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
internal fun SidebarPuntos(
    puntos: List<PuntoConMedia>,
    puntoSeleccionado: PuntoConMedia?,
    filtroActivo: String?,
    onSeleccionar: (Long?) -> Unit,
    onFiltrar: (String?) -> Unit
) {
    Surface(
        modifier = Modifier.width(220.dp).fillMaxHeight(),
        color = ColorSuperficie, tonalElevation = 0.dp
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(
                    Triple("${puntos.size}", "puntos", ColorAccent),
                    Triple("${puntos.sumOf { it.fotos.size }}", "fotos", ColorPurple),
                    Triple("${puntos.count { !it.punto.completo }}", "pend.", ColorWarn)
                ).forEach { (num, lbl, color) ->
                    Surface(
                        Modifier.weight(1f), color = ColorSuperficie2,
                        shape = RoundedCornerShape(6.dp), border = BorderStroke(1.dp, ColorBorde)
                    ) {
                        Column(Modifier.padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(num, style = TextStyle(fontFamily = FontFamily.Monospace,
                                fontSize = 15.sp, fontWeight = FontWeight.Medium, color = color))
                            Text(lbl, style = TextStyle(fontSize = 8.sp, color = ColorMuted))
                        }
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(null to "todos", "visual" to "vis", "muestra" to "mues", "estructura" to "est")
                    .forEach { (tipo, label) ->
                        val activo = filtroActivo == tipo
                        val color = if (tipo != null) colorPorTipo(tipo) else ColorTexto
                        FilterChip(
                            selected = activo,
                            onClick = { onFiltrar(tipo) },
                            label = { Text(label, style = TextStyle(
                                fontFamily = FontFamily.Monospace, fontSize = 8.sp
                            ))},
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = color.copy(0.2f),
                                selectedLabelColor = color,
                                containerColor = ColorSuperficie2,
                                labelColor = ColorMuted
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true, selected = activo,
                                borderColor = ColorBorde,
                                selectedBorderColor = color
                            )
                        )
                    }
            }

            HorizontalDivider(color = ColorBorde, thickness = 0.5.dp)

            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                items(puntos, key = { it.punto.id }) { pm ->
                    TarjetaPunto(
                        pm = pm,
                        seleccionado = puntoSeleccionado?.punto?.id == pm.punto.id,
                        onClick = { onSeleccionar(pm.punto.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TarjetaPunto(pm: PuntoConMedia, seleccionado: Boolean, onClick: () -> Unit) {
    val p = pm.punto
    val color = colorPorTipo(p.tipo)
    Surface(
        onClick = onClick,
        color = if (seleccionado) color.copy(0.1f) else ColorSuperficie2,
        shape = RoundedCornerShape(7.dp),
        border = BorderStroke(1.dp, if (seleccionado) color else ColorBorde)
    ) {
        Column(Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(8.dp).background(color, CircleShape))
                Text(p.nombre, style = TextStyle(fontSize = 11.sp,
                    fontWeight = FontWeight.Medium, color = ColorTexto), modifier = Modifier.weight(1f))
                Surface(color = color.copy(0.15f), shape = RoundedCornerShape(3.dp)) {
                    Text(p.tipo.take(4), Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                        style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 8.sp, color = color))
                }
            }
            Spacer(Modifier.height(3.dp))
            Text("N%.4f° W%.4f°".format(p.lat, Math.abs(p.lon)),
                style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = ColorMuted))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 2.dp)) {
                Text("±${p.precision}m", style = TextStyle(fontSize = 9.sp, color = ColorMuted))
                if (pm.fotos.isNotEmpty())
                    Text("📷 ${pm.fotos.size}", style = TextStyle(fontSize = 9.sp, color = ColorPurple))
                Text(
                    if (p.completo) "✓ completo" else "pendiente",
                    style = TextStyle(fontSize = 9.sp, color = if (p.completo) ColorAccent else ColorWarn)
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// PANEL DETALLE DEL PUNTO
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
internal fun PanelDetallePunto(
    puntoConMedia: PuntoConMedia,
    onGuardar: (desc: String, json: String) -> Unit,
    onCambiarTipo: (String) -> Unit,
    onEliminar: () -> Unit,
    onAgregarFoto: (ruta: String) -> Unit,
    onCerrar: () -> Unit
) {
    val p = puntoConMedia.punto
    var descripcion by remember(p.id) { mutableStateOf(p.descripcion) }
    var tipoSel by remember(p.id) { mutableStateOf(p.tipo) }
    var confirmarEliminar by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.width(230.dp).fillMaxHeight(),
        color = ColorSuperficie, tonalElevation = 0.dp,
        border = BorderStroke(1.dp, ColorBorde)
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(Modifier.size(10.dp).background(colorPorTipo(tipoSel), CircleShape))
                Text(p.nombre, style = TextStyle(fontSize = 12.sp,
                    fontWeight = FontWeight.Medium, color = ColorTexto), modifier = Modifier.weight(1f))
                IconButton(onClick = onCerrar, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = ColorMuted)
                }
            }

            HorizontalDivider(color = ColorBorde, thickness = 0.5.dp)

            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Coordenadas
                CampoDetalle("COORDENADAS GPS") {
                    Text("N${p.lat}\nW${Math.abs(p.lon)}",
                        style = TextStyle(fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp, color = ColorAccent, lineHeight = 16.sp))
                    Text("Alt: ${p.altitud}m · ±${p.precision}m",
                        style = TextStyle(fontSize = 9.sp, color = ColorMuted))
                }

                // Tipo
                CampoDetalle("TIPO DE PUNTO") {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("visual", "muestra", "estructura", "otro").forEach { tipo ->
                            val activo = tipoSel == tipo
                            val color = colorPorTipo(tipo)
                            Surface(
                                onClick = { tipoSel = tipo; onCambiarTipo(tipo) },
                                color = if (activo) color.copy(0.2f) else ColorSuperficie2,
                                shape = RoundedCornerShape(4.dp),
                                border = BorderStroke(1.dp, if (activo) color else ColorBorde)
                            ) {
                                Text(tipo.take(4), Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                    style = TextStyle(fontFamily = FontFamily.Monospace,
                                        fontSize = 8.sp, color = if (activo) color else ColorMuted))
                            }
                        }
                    }
                }

                // Descripción
                CampoDetalle("DESCRIPCIÓN") {
                    OutlinedTextField(
                        value = descripcion,
                        onValueChange = { descripcion = it },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
                        placeholder = { Text("Observaciones...",
                            style = TextStyle(fontSize = 10.sp, color = ColorMuted)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ColorAccent2,
                            unfocusedBorderColor = ColorBorde,
                            focusedTextColor = ColorTexto,
                            unfocusedTextColor = ColorTexto,
                            cursorColor = ColorAccent2,
                            focusedContainerColor = ColorSuperficie2,
                            unfocusedContainerColor = ColorSuperficie2
                        ),
                        textStyle = TextStyle(fontSize = 11.sp),
                        shape = RoundedCornerShape(6.dp),
                        maxLines = 5
                    )
                }

                // Fotos
                CampoDetalle("FOTOS (${puntoConMedia.fotos.size})") {
                    if (puntoConMedia.fotos.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            puntoConMedia.fotos.take(3).forEach { _ ->
                                Box(
                                    Modifier.size(52.dp).clip(RoundedCornerShape(4.dp))
                                        .background(ColorSuperficie2)
                                        .border(1.dp, ColorBorde, RoundedCornerShape(4.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Image, null, tint = ColorMuted,
                                        modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                    Surface(
                        onClick = { onAgregarFoto("") },
                        color = ColorSuperficie2, shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, ColorBorde),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(Modifier.padding(8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CameraAlt, null, tint = ColorMuted,
                                modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("Agregar foto / video",
                                style = TextStyle(fontSize = 10.sp, color = ColorMuted))
                        }
                    }
                }
            }

            HorizontalDivider(color = ColorBorde, thickness = 0.5.dp)

            Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(
                    onClick = { onGuardar(descripcion, "{}") },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = ColorAccent, contentColor = Color.Black),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("Guardar", style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium))
                }
                IconButton(
                    onClick = { confirmarEliminar = true },
                    modifier = Modifier.size(40.dp).border(1.dp, ColorBorde, RoundedCornerShape(6.dp))
                ) {
                    Icon(Icons.Default.Delete, null, tint = ColorMuted)
                }
            }
        }
    }

    if (confirmarEliminar) {
        AlertDialog(
            onDismissRequest = { confirmarEliminar = false },
            containerColor = ColorSuperficie,
            title = { Text("¿Eliminar punto?", color = ColorTexto) },
            text = { Text("Se eliminará \"${p.nombre}\" y todas sus fotos.", color = ColorTexto2) },
            confirmButton = {
                TextButton(onClick = { confirmarEliminar = false; onEliminar() }) {
                    Text("Eliminar", color = ColorRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmarEliminar = false }) {
                    Text("Cancelar", color = ColorMuted)
                }
            }
        )
    }
}

@Composable
private fun CampoDetalle(label: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = TextStyle(fontFamily = FontFamily.Monospace,
            fontSize = 8.sp, color = ColorMuted, letterSpacing = 0.5.sp))
        content()
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// BOTTOM BAR EXPORTACIÓN
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
internal fun BottomBarExportacion(
    totalPuntos: Int,
    totalFotos: Int,
    exportando: Boolean,
    onExportar: () -> Unit
) {
    Surface(color = ColorSuperficie, tonalElevation = 0.dp) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "$totalPuntos puntos · $totalFotos fotos · listo para exportar",
                style = TextStyle(fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp, color = ColorMuted),
                modifier = Modifier.weight(1f)
            )
            if (exportando) {
                CircularProgressIndicator(Modifier.size(20.dp), color = ColorAccent, strokeWidth = 2.dp)
            } else {
                Button(
                    onClick = onExportar,
                    colors = ButtonDefaults.buttonColors(containerColor = ColorAccent, contentColor = Color.Black),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Icon(Icons.Default.Download, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Exportar KML", style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium))
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// PANTALLA INVITACIÓN A CARGAR PDF
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun InvitacionCargarPdf(onCargar: () -> Unit, onUsarOsm: () -> Unit) {
    Box(Modifier.fillMaxSize().background(ColorFondo), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Box(
                Modifier.size(80.dp).background(ColorSuperficie2, CircleShape)
                    .border(1.dp, ColorBorde, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.PictureAsPdf, null, tint = ColorAmber, modifier = Modifier.size(36.dp))
            }
            Text("Sin PDF cargado", style = TextStyle(fontSize = 18.sp,
                fontWeight = FontWeight.Medium, color = ColorTexto, fontFamily = FontFamily.Monospace))
            Text("Carga un GeoPDF con capas técnicas o usa el mapa base de satélite.",
                style = TextStyle(fontSize = 12.sp, color = ColorTexto2,
                    lineHeight = 18.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center))
            Button(onClick = onCargar,
                colors = ButtonDefaults.buttonColors(containerColor = ColorAmber, contentColor = Color.Black),
                shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Upload, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Cargar PDF georeferenciado", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium))
            }
            OutlinedButton(onClick = onUsarOsm,
                border = BorderStroke(1.dp, ColorBorde), shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ColorAccent2)
            ) {
                Icon(Icons.Default.Map, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Usar mapa satélite", style = TextStyle(fontSize = 13.sp, color = ColorAccent2))
            }
        }
    }
}
