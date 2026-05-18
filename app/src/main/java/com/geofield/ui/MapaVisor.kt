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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.geofield.camera.CamaraScreen
import com.geofield.data.MapaPdfEntity
import com.geofield.data.PuntoConMedia
import com.geofield.geo.GeoPdfTransform
import com.geofield.theme.GuayabappTypography // CORRECCIÓN: Estilos unificados de Nunito 
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val ColorFondo       = Color(0xFF0F1117)
private val ColorSuperficie  = Color(0xFF181C27)
private val ColorSuperficie2 = Color(0xFF1F2436)
private val ColorBorde       = Color(0xFF2A3045)
private val ColorAccent      = Color(0xFF87A922) // Verde Guayaba Maduro
private val ColorAccent2     = Color(0xFF0090FF) 
private val ColorMuted       = Color(0xFF6B7A99)
private val ColorTexto       = Color(0xFFE8EAF2)
private val ColorTexto2      = Color(0xFF9AA3BF)
private val ColorAmber       = Color(0xFFF0A500) 
private val ColorWarn        = Color(0xFFFF6B35)
private val ColorRed         = Color(0xFFD80032) 
private val ColorPurple      = Color(0xFF7C6AF7)

internal fun colorPorTipo(tipo: String) = when (tipo) {
    "visual"      -> Color(0xFF87A922)
    "muestra"     -> Color(0xFF7C6AF7)
    "estructura"  -> Color(0xFFF0A500)
    else          -> Color(0xFF6B7A99)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapaVisorScreen(viewModel: MapaViewModel, onNavegaConfiguracion: () -> Unit = {}) {
    val estado by viewModel.estado.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var mostrarCamara by remember { mutableStateOf(false) }

    LaunchedEffect(estado.mensajeSnack) {
        estado.mensajeSnack?.let { snackbarHostState.showSnackbar(it); viewModel.snackConsumido() }
    }

    Scaffold(
        containerColor = ColorFondo,
        snackbarHost = { SnackbarHost(snackbarHostState) { Snackbar(snackbarData = it, containerColor = ColorSuperficie2, contentColor = ColorTexto, actionColor = ColorAccent, shape = RoundedCornerShape(8.dp)) } },
        topBar = { TopBarVisor(onConfiguracion = onNavegaConfiguracion, onAlternarCapas = { viewModel.alternarSiguienteCapa() }) },
        bottomBar = { BottomBarExportacion(estado.puntos.size, estado.puntos.sumOf { it.fotos.size }, estado.exportando, onExportar = { viewModel.exportarKml() }) }
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val esVertical = maxWidth < maxHeight
            if (esVertical) {
                Box(Modifier.fillMaxSize()) { AreaMapaContenedor(estado.modoCapaBase, estado, viewModel) }
            } else {
                Row(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f).fillMaxHeight()) { AreaMapaContenedor(estado.modoCapaBase, estado, viewModel) }
                    SidebarPuntos(estado.puntos, estado.puntoSeleccionado, estado.filtroTipo, { viewModel.seleccionarPunto(it) }, { viewModel.filtrarPorTipo(it) })
                }
            }

            AnimatedVisibility(visible = estado.puntoSeleccionado != null, enter = slideInHorizontally(initialOffsetX = { it }), exit = slideOutHorizontally(targetOffsetX = { it }), modifier = Modifier.align(Alignment.CenterEnd)) {
                estado.puntoSeleccionado?.let { pm ->
                    PanelDetallePunto(pm, { d, j -> viewModel.actualizarDescripcion(pm.punto.id, d, j) }, { viewModel.cambiarTipoPunto(pm.punto.id, it) }, { viewModel.eliminarPunto(pm.punto.id) }, { mostrarCamara = true }, { viewModel.seleccionarPunto(null) })
                }
            }

            if (mostrarCamara) {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    CamaraScreen(com.geofield.location.EstadoGps.Inactivo, { r -> viewModel.agregarFoto(estado.puntoSeleccionado!!.punto.id, r.rutaArchivo, estado.puntoSeleccionado!!.punto.lat, estado.puntoSeleccionado!!.punto.lon, estado.puntoSeleccionado!!.punto.altitud); mostrarCamara = false }, { mostrarCamara = false })
                }
            }
        }
    }
}

@Composable
private fun AreaMapaContenedor(modoCapaBase: ModoCapaBase, estado: MapaUiState, viewModel: MapaViewModel) {
    AnimatedContent(targetState = modoCapaBase, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "capas") { capa ->
        when (capa) {
            ModoCapaBase.OSM_ESTANDAR, ModoCapaBase.ESRI_SATELITE -> VisorOsmdroid(estado.puntos, estado.puntoSeleccionado, onSeleccionarPunto = { viewModel.seleccionarPunto(it) }, onCapturarPunto = { lat, lon, alt -> viewModel.agregarPunto(lat, lon, alt, 2.0, "visual", "VIS-${System.currentTimeMillis() % 10000}") })
            ModoCapaBase.GEO_PDF -> if (estado.mapaActivo != null) VisorPdf(estado.mapaActivo, estado.puntos, estado.puntoSeleccionado, { viewModel.seleccionarPunto(it) }, { lat, lon, alt -> viewModel.agregarPunto(lat, lon, alt, 2.0, "visual", "VIS-${System.currentTimeMillis() % 10000}") }) else InvitacionCargarPdf({}, { viewModel.alternarSiguienteCapa() })
        }
    }
}

@Composable
private fun TopBarVisor(onConfiguracion: () -> Unit, onAlternarCapas: () -> Unit) {
    Surface(color = ColorSuperficie) { Row(Modifier.fillMaxWidth().height(54.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) { Text("Guayabapp", style = GuayabappTypography.titleLarge, color = ColorAccent); Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) { IconButton(onClick = onAlternarCapas) { Icon(Icons.Default.Layers, null, tint = ColorTexto) }; IconButton(onClick = onConfiguracion) { Icon(Icons.Default.Settings, null, tint = ColorMuted) } } } }
}

@Composable
internal fun VisorPdf(mapa: MapaPdfEntity, puntos: List<PuntoConMedia>, puntoSeleccionado: PuntoConMedia?, onSeleccionarPunto: (Long?) -> Unit, onCapturarPunto: (Double, Double, Double) -> Unit) {
    var bitmapTile by remember { mutableStateOf<Bitmap?>(null) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var canvasSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }

    val coordsCentro = remember(zoom, offsetX, offsetY, canvasSize, mapa) {
        if (canvasSize.width == 0) return@remember "No data"
        val cx = (canvasSize.width / 2f - offsetX) / zoom
        val cy = (canvasSize.height / 2f - offsetY) / zoom
        val (lat, lon) = GeoPdfTransform.pixelAGps(cx, cy, mapa)
        "N %.5f°   W %.5f°".format(lat, Math.abs(lon))
    }

    LaunchedEffect(mapa.rutaArchivo, zoom, offsetX, offsetY, canvasSize) {
        if (canvasSize.width == 0) return@LaunchedEffect
        bitmapTile = renderTilePdf(mapa, canvasSize.width, canvasSize.height, zoom, offsetX, offsetY)
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF141824)).onSizeChanged { canvasSize = it }) {
        Canvas(modifier = Modifier.fillMaxSize().pointerInput(mapa, puntos) { detectTransformGestures { centroid, pan, gestureZoom, _ -> val newZoom = (zoom * gestureZoom).coerceIn(0.5f, 8f); offsetX = centroid.x - (centroid.x - offsetX) * (newZoom / zoom) + pan.x; offsetY = centroid.y - (centroid.y - offsetY) * (newZoom / zoom) + pan.y; zoom = newZoom } }.pointerInput(mapa, puntos) { detectTapGestures { tap -> val tocado = puntos.firstOrNull { pm -> val px = GeoPdfTransform.gpsAPixel(pm.punto.lat, pm.punto.lon, mapa); Math.hypot((tap.x - (px.x * zoom + offsetX)).toDouble(), (tap.y - (px.y * zoom + offsetY)).toDouble()) < 28.0 }; onSeleccionarPunto(tocado?.punto?.id) } }) {
            bitmapTile?.let { drawImage(it.asImageBitmap(), topLeft = Offset(offsetX, offsetY)) }
            puntos.forEach { pm ->
                val p = pm.punto
                val px = GeoPdfTransform.gpsAPixel(p.lat, p.lon, mapa)
                val sx = px.x * zoom + offsetX
                val sy = px.y * zoom + offsetY
                if (sx < -20 || sx > size.width + 20 || sy < -20 || sy > size.height + 20) return@forEach
                val colorBase = colorPorTipo(p.tipo)
                val sel = puntoSeleccionado?.punto?.id == p.id
                val radio = if (sel) 13.dp.toPx() else 9.dp.toPx()
                if (sel) drawCircle(colorBase.copy(0.25f), radio * 1.8f, Offset(sx, sy))
                if (!p.completo) drawCircle(ColorWarn.copy(0.3f), radio * 1.4f, Offset(sx, sy))
                drawCircle(colorBase, radio, Offset(sx, sy))
                drawCircle(Color.White, 3.dp.toPx(), Offset(sx, sy))
            }
        }

        Canvas(Modifier.fillMaxSize()) {
            val centroX = size.width / 2f
            val centroY = size.height / 2f
            val longitudMira = 20.dp.toPx()
            drawLine(Color.White.copy(0.65f), Offset(centroX - longitudMira, centroY), Offset(centroX + longitudMira, centroY), strokeWidth = 2.dp.toPx())
            drawLine(Color.White.copy(0.65f), Offset(centroX, centroY - longitudMira), Offset(centroX, centroY + longitudMira), strokeWidth = 2.dp.toPx())
            drawCircle(Color.White, 3.5.dp.toPx(), Offset(centroX, centroY))
        }

        Surface(Modifier.align(Alignment.BottomStart).padding(12.dp), color = Color(0xCC0F1117), shape = RoundedCornerShape(4.dp)) { Text(coordsCentro, Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = GuayabappTypography.labelMedium, color = ColorAccent) }
        FloatingActionButton(onClick = { if (canvasSize.width > 0) { val cx = (canvasSize.width / 2f - offsetX) / zoom; val cy = (canvasSize.height / 2f - offsetY) / zoom; val (lat, lon) = GeoPdfTransform.pixelAGps(cx, cy, mapa); onCapturarPunto(lat, lon, 2500.0) } }, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 16.dp), containerColor = ColorAccent, contentColor = Color.Black, shape = CircleShape) { Icon(Icons.Default.Add, null, modifier = Modifier.size(28.dp)) }
    }
}

private suspend fun renderTilePdf(mapa: MapaPdfEntity, canvasW: Int, canvasH: Int, zoom: Float, offsetX: Float, offsetY: Float): Bitmap? = withContext(Dispatchers.IO) {
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
        val transform = android.graphics.Matrix().apply { setScale(outW.toFloat() / (vR - vL), outH.toFloat() / (vB - vT)); preTranslate(-vL.toFloat(), -vT.toFloat()); preScale(mapa.widthPx.toFloat() / page.width, mapa.heightPx.toFloat() / page.height) }
        page.render(bitmap, null, transform, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        page.close(); renderer.close(); fd.close()
        bitmap
    } catch (e: Exception) { null }
}

@Composable
internal fun SidebarPuntos(puntos: List<PuntoConMedia>, puntoSeleccionado: PuntoConMedia?, filtroActivo: String?, onSeleccionar: (Long?) -> Unit, onFiltrar: (String?) -> Unit) {
    Surface(modifier = Modifier.width(230.dp).fillMaxHeight(), color = ColorSuperficie) {
        Column {
            Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(Triple("${puntos.size}", "Puntos", ColorAccent), Triple("${puntos.sumOf { it.fotos.size }}", "Fotos", ColorPurple), Triple("${puntos.count { !it.punto.completo }}", "Pend.", ColorWarn)).forEach { (num, lbl, color) ->
                    Surface(Modifier.weight(1f), color = ColorSuperficie2, shape = RoundedCornerShape(6.dp), border = BorderStroke(1.dp, ColorBorde)) { Column(Modifier.padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(num, style = GuayabappTypography.titleMedium.copy(color = color)); Text(lbl, style = GuayabappTypography.bodyLarge.copy(fontSize = 11.sp, color = ColorMuted)) } }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(null to "Todos", "visual" to "Vis", "muestra" to "Mues", "estructura" to "Est").forEach { (tipo, label) ->
                    val activo = filtroActivo == tipo
                    val color = if (tipo != null) colorPorTipo(tipo) else ColorTexto
                    FilterChip(selected = activo, onClick = { onFiltrar(tipo) }, label = { Text(label, style = GuayabappTypography.labelMedium.copy(fontSize = 11.sp)) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = color.copy(0.2f), selectedLabelColor = color, containerColor = ColorSuperficie2, labelColor = ColorMuted), border = FilterChipDefaults.filterChipBorder(enabled = true, selected = activo, borderColor = ColorBorde, selectedBorderColor = color))
                }
            }
            HorizontalDivider(color = ColorBorde, thickness = 0.5.dp)
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { items(puntos, key = { it.punto.id }) { pm -> TarjetaPunto(pm, puntoSeleccionado?.punto?.id == pm.punto.id, onClick = { onSeleccionar(pm.punto.id) }) } }
        }
    }
}

@Composable
private fun TarjetaPunto(pm: PuntoConMedia, seleccionado: Boolean, onClick: () -> Unit) {
    val p = pm.punto
    val color = colorPorTipo(p.tipo)
    Surface(onClick = onClick, color = if (seleccionado) color.copy(0.1f) else ColorSuperficie2, shape = RoundedCornerShape(7.dp), border = BorderStroke(1.dp, if (seleccionado) color else ColorBorde)) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(8.dp).background(color, CircleShape))
                Text(p.nombre, style = GuayabappTypography.bodyLarge.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
                Surface(color = color.copy(0.15f), shape = RoundedCornerShape(3.dp)) { Text(p.tipo.take(4).uppercase(), Modifier.padding(horizontal = 5.dp, vertical = 1.dp), style = GuayabappTypography.labelMedium.copy(fontSize = 10.sp, color = color)) }
            }
            Spacer(Modifier.height(4.dp))
            Text(text = "N %.4f°   W %.4f°".format(p.lat, Math.abs(p.lon)), style = GuayabappTypography.labelMedium.copy(color = ColorTexto2))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                Text("Alt: %.0f m".format(p.altitud), style = GuayabappTypography.bodyLarge.copy(fontSize = 12.sp, color = ColorMuted))
                if (pm.fotos.isNotEmpty()) Text("📷 ${pm.fotos.size}", style = GuayabappTypography.bodyLarge.copy(fontSize = 12.sp, color = ColorPurple))
                Text(text = if (p.completo) "✓ Completo" else "Pendiente", style = GuayabappTypography.bodyLarge.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (p.completo) ColorAccent else ColorWarn))
            }
        }
    }
}

@Composable
internal fun PanelDetallePunto(puntoConMedia: PuntoConMedia, onGuardar: (String, String) -> Unit, onCambiarTipo: (String) -> Unit, onEliminar: () -> Unit, onAgregarFoto: () -> Unit, onCerrar: () -> Unit) {
    val p = puntoConMedia.punto
    var descripcion by remember(p.id) { mutableStateOf(p.descripcion) }
    var tipoSel by remember(p.id) { mutableStateOf(p.tipo) }
    var confirmarEliminar by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.width(250.dp).fillMaxHeight(), color = ColorSuperficie, border = BorderStroke(1.dp, ColorBorde)) {
        Column {
            Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Box(Modifier.size(10.dp).background(colorPorTipo(tipoSel), CircleShape)); Text(p.nombre, style = GuayabappTypography.titleMedium, modifier = Modifier.weight(1f)); IconButton(onClick = onCerrar) { Icon(Icons.Default.Close, null, tint = ColorMuted) } }
            HorizontalDivider(color = ColorBorde, thickness = 0.5.dp)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CampoDetalle("COORDENADAS TÉCNICAS") {
                    Text("N %.6f°\nW %.6f°".format(p.lat, Math.abs(p.lon)), style = GuayabappTypography.labelMedium.copy(fontSize = 14.sp, color = ColorAccent, lineHeight = 18.sp))
                    Text("Altitud: %.1f msnm · ± %s".format(p.altitud, p.precision), style = GuayabappTypography.bodyLarge.copy(fontSize = 12.sp, color = ColorMuted))
                }
                CampoDetalle("TIPO DE REGISTRO DE CAMPO") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf("visual", "muestra", "estructura", "otro").forEach { tipo ->
                                    val activo = tipoSel == tipo
                                    val color = colorPorTipo(tipo)
                                    Surface(onClick = { tipoSel = tipo; onCambiarTipo(tipo) }, color = if (activo) color.copy(0.2f) else ColorSuperficie2, shape = RoundedCornerShape(4.dp), border = BorderStroke(1.dp, if (activo) color else ColorBorde)) { Text(tipo.take(4).uppercase(), Modifier.padding(horizontal = 6.dp, vertical = 4.dp), style = GuayabappTypography.labelMedium.copy(fontSize = 10.sp, color = if (activo) color else ColorMuted)) }
                                }
                            }
                        }
                        IconButton(onClick = {}, modifier = Modifier.size(26.dp).background(ColorSuperficie2, RoundedCornerShape(4.dp)).border(1.dp, ColorBorde, RoundedCornerShape(4.dp))) { Icon(Icons.Default.Add, null, tint = ColorAccent, modifier = Modifier.size(14.dp)) }
                    }
                }
                CampoDetalle("RECOLECCIÓN FORMULARIO (JSON)") { OutlinedTextField(value = descripcion, onValueChange = { descripcion = it }, modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp), placeholder = { Text("Anotaciones litológicas...", style = GuayabappTypography.bodyLarge.copy(color = ColorMuted)) }, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ColorAccent, unfocusedBorderColor = ColorBorde, focusedTextColor = ColorTexto, unfocusedTextColor = ColorTexto, focusedContainerColor = ColorSuperficie2, unfocusedContainerColor = ColorSuperficie2), textStyle = GuayabappTypography.bodyLarge.copy(fontSize = 13.sp), shape = RoundedCornerShape(6.dp)) }
                CampoDetalle("SOPORTE MULTIMEDIA EN PUNTO") {
                    if (puntoConMedia.fotos.isNotEmpty()) { Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 4.dp)) { puntoConMedia.fotos.take(3).forEach { _ -> Box(Modifier.size(54.dp).clip(RoundedCornerShape(6.dp)).background(ColorSuperficie2).border(1.dp, ColorBorde, RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Image, null, tint = ColorMuted, modifier = Modifier.size(22.dp)) } } } }
                    Button(onClick = onAgregarFoto, colors = ButtonDefaults.buttonColors(containerColor = ColorSuperficie2, contentColor = ColorTexto), shape = RoundedCornerShape(6.dp), border = BorderStroke(1.dp, ColorBorde), modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.CameraAlt, null, tint = ColorAccent, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Disparar Cámara (CameraX)", style = GuayabappTypography.bodyLarge.copy(fontSize = 12.sp)) }
                }
            }
            HorizontalDivider(color = ColorBorde, thickness = 0.5.dp)
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) { Button(onClick = { onGuardar(descripcion, "{}") }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = ColorAccent, contentColor = Color.Black), shape = RoundedCornerShape(6.dp)) { Text("Guardar", style = GuayabappTypography.bodyLarge.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp)) }; IconButton(onClick = { confirmarEliminar = true }, modifier = Modifier.size(40.dp).border(1.dp, ColorBorde, RoundedCornerShape(6.dp))) { Icon(Icons.Default.Delete, null, tint = ColorRed) } }
        }
    }

    if (confirmarEliminar) { AlertDialog(onDismissRequest = { confirmarEliminar = false }, containerColor = ColorSuperficie, title = { Text("¿Eliminar punto?", style = GuayabappTypography.titleMedium, color = ColorTexto) }, text = { Text("Esta acción purgará el registro técnico.", style = GuayabappTypography.bodyLarge, color = ColorTexto2) }, confirmButton = { TextButton(onClick = { confirmarEliminar = false; onEliminar() }) { Text("Eliminar", style = GuayabappTypography.bodyLarge.copy(color = ColorRed, fontWeight = FontWeight.Bold)) } }, dismissButton = { TextButton(onClick = { confirmarEliminar = false }) { Text("Cancelar", style = GuayabappTypography.bodyLarge.copy(color = ColorMuted)) } }) }
}

@Composable
private fun CampoDetalle(label: String, content: @Composable ColumnScope.() -> Unit) { Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(text = label, style = GuayabappTypography.labelMedium.copy(fontSize = 11.sp, color = ColorMuted, letterSpacing = 0.5.sp)); content() } }

@Composable
internal fun BottomBarExportacion(totalPuntos: Int, totalFotos: Int, exportando: Boolean, onExportar: () -> Unit) { Surface(color = ColorSuperficie) { Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) { Text(text = "$totalPuntos puntos registrados · $totalFotos fotos · Listo para exportación SIG", style = GuayabappTypography.labelMedium.copy(color = ColorMuted, fontSize = 12.sp), modifier = Modifier.weight(1f)); if (exportando) CircularProgressIndicator(Modifier.size(20.dp), color = ColorAccent, strokeWidth = 2.dp) else Button(onClick = onExportar, colors = ButtonDefaults.buttonColors(containerColor = ColorAccent, contentColor = Color.Black), shape = RoundedCornerShape(6.dp)) { Icon(Icons.Default.Download, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Exportar KML", style = GuayabappTypography.bodyLarge.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp)) } } } }

@Composable
fun InvitacionCargarPdf(onCargar: () -> Unit, onUsarOsm: () -> Unit) { Box(Modifier.fillMaxSize().background(ColorFondo), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(32.dp)) { Box(Modifier.size(80.dp).background(ColorSuperficie2, CircleShape).border(1.dp, ColorBorde, CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Default.PictureAsPdf, null, tint = ColorAmber, modifier = Modifier.size(36.dp)) }; Text("Sin GeoPDF cargado", style = GuayabappTypography.titleMedium, color = ColorTexto); Text("Importa un plano georreferenciado local.", style = GuayabappTypography.bodyLarge, textAlign = TextAlign.Center, color = ColorTexto2); Button(onClick = onCargar, colors = ButtonDefaults.buttonColors(containerColor = ColorAmber, contentColor = Color.Black), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Upload, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Cargar GeoPDF de Campo", style = GuayabappTypography.bodyLarge.copy(fontWeight = FontWeight.Bold)) }; OutlinedButton(onClick = onUsarOsm, border = BorderStroke(1.dp, ColorBorde), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(contentColor = ColorAccent2)) { Icon(Icons.Default.Map, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(8.dp)); Text("Cambiar a Mapa Satélite", style = GuayabappTypography.bodyLarge.copy(color = ColorAccent2)) } } } }
