package com.geofield.camera

import android.content.Context
import android.net.Uri
import android.view.ViewGroup
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.video.VideoCapture
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.geofield.location.EstadoGps
import com.geofield.location.ExifGpsWriter
import com.geofield.location.LecturaGps
import com.geofield.theme.GuayabappTypography // Sistema Nunito unificado
import kotlinx.coroutines.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

// ─── PALETA GUAYABAPP IDENTIDAD VISUAL ANTRACITA Y FRUTAL ────────────────────
private val ColorFondo      = Color(0xFF0F1117) // Fondo principal [cite: 32]
private val ColorSuperficie = Color(0xFF181C27) // Superficie [cite: 32]
private val ColorBorde      = Color(0xFF2A3045) // Bordes [cite: 32]
private val ColorAccent     = Color(0xFF87A922) // Verde Guayaba Maduro (Accent principal) [cite: 32]
private val ColorAccent2    = Color(0xFF0090FF) // Modo OSM (Azul) [cite: 32]
private val ColorMuted      = Color(0xFF6B7A99) // Gris mitigado [cite: 32]
private val ColorTexto      = Color(0xFFE8EAF2) // Blanco texto principal
private val ColorWarn       = Color(0xFFF0A500) // Ámbar advertencia [cite: 32]
private val ColorRed        = Color(0xFFD80032) // Rubí pulpa de guayaba (Alertas/Grabación) [cite: 32]

enum class ModoCaptura { FOTO, VIDEO }

data class ResultadoCaptura(
    val rutaArchivo: String,
    val tipo: ModoCaptura,
    val lectura: LecturaGps?,
    val duracionSeg: Int = 0
)

// ═══════════════════════════════════════════════════════════════════════════════
// PANTALLA PRINCIPAL DE CÁMARA
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun CamaraScreen(
    estadoGps: EstadoGps,
    onCaptura: (ResultadoCaptura) -> Unit,
    onCerrar: () -> Unit
) {
    val context        = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope          = rememberCoroutineScope()

    var modo          by remember { mutableStateOf(ModoCaptura.FOTO) }
    var grabando      by remember { mutableStateOf(false) }
    var segundosVideo by remember { mutableIntStateOf(0) }
    var flashActivo   by remember { mutableStateOf(false) }
    var camaraFrontal by remember { mutableStateOf(false) }
    var ultimaFoto    by remember { mutableStateOf<String?>(null) }
    var procesando    by remember { mutableStateOf(false) }

    val imageCaptureRef = remember { mutableStateOf<ImageCapture?>(null) }
    val videoCaptureRef = remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var grabacionActiva by remember { mutableStateOf<Recording?>(null) }

    val lecturaGps = (estadoGps as? EstadoGps.Activo)?.lectura

    LaunchedEffect(grabando) {
        if (grabando) {
            segundosVideo = 0
            while (grabando) {
                delay(1000)
                segundosVideo++
            }
        } else {
            segundosVideo = 0
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {

        // Vista previa de cámara optimizada sin rebindeo infinito
        CamaraPreview(
            modo = modo,
            flashActivo = flashActivo,
            camaraFrontal = camaraFrontal,
            lifecycleOwner = lifecycleOwner,
            onImageCaptureReady = { imageCaptureRef.value = it },
            onVideoCaptureReady = { videoCaptureRef.value = it }
        )

        // ── OVERLAY SUPERIOR: Barra de estado e Identidad ────────────────────
        Column(
            Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(onClick = onCerrar, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Cerrar",
                        tint = ColorTexto, modifier = Modifier.size(22.dp))
                }

                // Nombre Oficial de la App inyectado con Nunito [cite: 2]
                Text(
                    text = "Guayabapp", [cite: 2]
                    style = GuayabappTypography.titleMedium,
                    color = ColorTexto,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                BadgeGpsCamara(estadoGps = estadoGps, modifier = Modifier.weight(1f))

                if (!camaraFrontal) {
                    IconButton(
                        onClick = { flashActivo = !flashActivo },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            if (flashActivo) Icons.Default.FlashOn else Icons.Default.FlashOff,
                            contentDescription = "Flash",
                            tint = if (flashActivo) ColorWarn else ColorMuted, [cite: 32]
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                IconButton(
                    onClick = { camaraFrontal = !camaraFrontal },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.FlipCameraAndroid, contentDescription = "Cambiar cámara",
                        tint = ColorTexto, modifier = Modifier.size(20.dp))
                }
            }

            AnimatedVisibility(visible = grabando) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(8.dp).background(ColorRed, CircleShape)) [cite: 32]
                    Spacer(Modifier.width(6.dp))
                    Text(
                        formatearTiempo(segundosVideo),
                        style = GuayabappTypography.labelMedium.copy(fontSize = 16.sp),
                        color = ColorTexto
                    )
                }
            }
        }

        // ── OVERLAY INFERIOR: Modos, Disparador y Coordenadas WGS84 ───────────
        Column(
            Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (!grabando) {
                SelectorModoCaptura(modoActual = modo, onCambiar = { modo = it })
            }

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MiniaturaCapturaPrevia(ruta = ultimaFoto)

                BotonCaptura(
                    modo = modo,
                    grabando = grabando,
                    procesando = procesando,
                    onClick = {
                        when (modo) {
                            ModoCaptura.FOTO -> {
                                if (procesando) return@BotonCaptura
                                procesando = true
                                scope.launch {
                                    val ruta = tomarFoto(
                                        context = context,
                                        imageCapture = imageCaptureRef.value,
                                        lecturaGps = lecturaGps
                                    )
                                    procesando = false
                                    ruta?.let {
                                        ultimaFoto = it
                                        onCaptura(ResultadoCaptura(it, ModoCaptura.FOTO, lecturaGps))
                                    }
                                }
                            }
                            ModoCaptura.VIDEO -> {
                                if (grabando) {
                                    grabacionActiva?.stop()
                                    grabacionActiva = null
                                    grabando = false
                                } else {
                                    scope.launch {
                                        val (recording, ruta) = iniciarVideo(
                                            context = context,
                                            videoCapture = videoCaptureRef.value,
                                            onFinalizado = { rutaFinal, duracion ->
                                                grabando = false
                                                ultimaFoto = rutaFinal
                                                onCaptura(ResultadoCaptura(
                                                    rutaFinal, ModoCaptura.VIDEO,
                                                    lecturaGps, duracion
                                                ))
                                            }
                                        )
                                        grabacionActiva = recording
                                        grabando = (recording != null)
                                    }
                                }
                            }
                        }
                    }
                )

                // Coordenadas WGS84 legibles con espacio reglamentario y tipografía aumentada [cite: 4]
                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.width(110.dp)
                ) {
                    if (lecturaGps != null) {
                        Text(
                            text = "N %.4f°".format(lecturaGps.lat), 
                            style = GuayabappTypography.labelMedium, 
                            color = ColorAccent [cite: 32]
                        )
                        Text(
                            text = "W %.4f°".format(Math.abs(lecturaGps.lon)), 
                            style = GuayabappTypography.labelMedium, 
                            color = ColorAccent [cite: 32]
                        )
                        Text(
                            text = lecturaGps.precisionTexto, 
                            style = GuayabappTypography.labelMedium.copy(fontSize = 11.sp), 
                            color = ColorMuted [cite: 32]
                        )
                    } else {
                        Text(
                            text = "No data", 
                            style = GuayabappTypography.labelMedium, 
                            color = ColorWarn [cite: 32]
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = procesando,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            CircularProgressIndicator(color = ColorAccent, modifier = Modifier.size(48.dp)) [cite: 32]
        }
    }
}

// ─── COMPOSABLE CÁMARA PREVIEW (CONTROL DE INSTANCIAS ESTABLES) ───────────────

@Composable
private fun CamaraPreview(
    modo: ModoCaptura,
    flashActivo: Boolean,
    camaraFrontal: Boolean,
    lifecycleOwner: LifecycleOwner,
    onImageCaptureReady: (ImageCapture) -> Unit,
    onVideoCaptureReady: (VideoCapture<Recorder>) -> Unit
) {
    val context = LocalContext.current
    val previewView = remember {
        PreviewView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    val preview = remember { Preview.Builder().build() }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .build()
    }
    val videoCapture = remember {
        val recorder = Recorder.Builder()
            .setQualitySelector(QualitySelector.fromOrderedList(listOf(Quality.FHD, Quality.HD)))
            .build()
        VideoCapture.withOutput(recorder)
    }

    LaunchedEffect(flashActivo) {
        imageCapture.flashMode = if (flashActivo) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
    }

    LaunchedEffect(camaraFrontal) {
        val cameraProvider = ProcessCameraProvider.getInstance(context).await()
        val selector = if (camaraFrontal) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA

        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                selector,
                preview,
                imageCapture,
                videoCapture
            )
            preview.setSurfaceProvider(previewView.surfaceProvider)
            
            onImageCaptureReady(imageCapture)
            onVideoCaptureReady(videoCapture)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = Modifier.fillMaxSize()
    )
}

// CORRECCIÓN ACTIONS: Función asíncrona totalmente desacoplada basada en el Looper principal (Cero errores KSP)
private suspend fun <T> com.google.common.util.concurrent.ListenableFuture<T>.await(): T {
    return suspendCancellableCoroutine { continuation ->
        addListener({
            try {
                continuation.resume(get(), onCancellation = null)
            } catch (e: Exception) {
                continuation.resumeWith(Result.failure(e))
            }
        }, android.os.Handler(android.os.Looper.getMainLooper()).run { { command: Runnable -> post(command) } })
    }
}

// ─── PROCEDIMIENTO PROCEDURAL DE CAPTURA DE FOTO ──────────────────────────────

private suspend fun tomarFoto(
    context: Context,
    imageCapture: ImageCapture?,
    lecturaGps: LecturaGps?
): String? = withContext(Dispatchers.IO) {
    if (imageCapture == null) return@withContext null

    val archivo = crearArchivoMedia(context, "FOTO", ".jpg")

    suspendCancellableCoroutine { cont ->
        val outputOptions = ImageCapture.OutputFileOptions.Builder(archivo).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    lecturaGps?.let {
                        ExifGpsWriter.escribirEnFoto(archivo.absolutePath, it)
                    }
                    cont.resume(archivo.absolutePath) {}
                }

                override fun onError(exception: ImageCaptureException) {
                    cont.resume(null) {}
                }
            }
        )
    }
}

// ─── PROCEDIMIENTO PROCEDURAL DE GRABACIÓN DE VIDEO ───────────────────────────

private suspend fun iniciarVideo(
    context: Context,
    videoCapture: VideoCapture<Recorder>?,
    onFinalizado: (ruta: String, duracionSeg: Int) -> Unit
): Pair<Recording?, String?> {
    if (videoCapture == null) return Pair(null, null)

    val archivo = crearArchivoMedia(context, "VIDEO", ".mp4")
    val tiempoInicio = System.currentTimeMillis()
    val outputOptions = FileOutputOptions.Builder(archivo).build()

    val recording = videoCapture.output
        .prepareRecording(context, outputOptions)
        .apply {
            try { withAudioEnabled() } catch (_: Exception) {}
        }
        .start(ContextCompat.getMainExecutor(context)) { evento ->
            if (evento is VideoRecordEvent.Finalize) {
                if (!evento.hasError()) {
                    val duracion = ((System.currentTimeMillis() - tiempoInicio) / 1000).toInt()
                    onFinalizado(archivo.absolutePath, duracion)
                }
            }
        }

    return Pair(recording, archivo.absolutePath)
}

private fun crearArchivoMedia(context: Context, prefijo: String, extension: String): File {
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    val directorio = File(context.getExternalFilesDir(null), "GeoField/Media").apply { mkdirs() }
    return File(directorio, "${prefijo}_${timestamp}${extension}")
}

private fun formatearTiempo(segundos: Int): String {
    val min = segundos / 60
    val seg = segundos % 60
    return "%02d:%02d".format(min, seg)
}

// ─── COMPOSABLE COMPONENTES DE INTERFAZ GRÁFICA (UI) ──────────────────────────

@Composable
private fun BadgeGpsCamara(estadoGps: EstadoGps, modifier: Modifier = Modifier) {
    val (color, texto) = when (estadoGps) {
        is EstadoGps.Activo -> {
            val c = when {
                estadoGps.lectura.precision <= 3f  -> ColorAccent [cite: 32]
                estadoGps.lectura.precision <= 10f -> ColorWarn [cite: 32]
                else -> ColorRed [cite: 32]
            }
            c to "GPS ${estadoGps.lectura.precisionTexto}"
        }
        is EstadoGps.Buscando -> ColorWarn to "Buscando..." [cite: 32]
        else -> ColorMuted to "No data" [cite: 32]
    }

    Surface(
        modifier = modifier,
        color = Color.Black.copy(.5f),
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, color.copy(.3f))
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(Icons.Default.GpsFixed, contentDescription = null,
                tint = color, modifier = Modifier.size(12.dp))
            Text(texto, style = GuayabappTypography.labelMedium.copy(fontSize = 11.sp), color = color)
        }
    }
}

@Composable
private fun SelectorModoCaptura(modoActual: ModoCaptura, onCambiar: (ModoCaptura) -> Unit) {
    Surface(
        color = Color.Black.copy(.5f),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, ColorBorde) [cite: 32]
    ) {
        Row(Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            listOf(ModoCaptura.FOTO to "FOTO", ModoCaptura.VIDEO to "VIDEO").forEach { (modo, label) ->
                val activo = modoActual == modo
                Surface(
                    onClick = { onCambiar(modo) },
                    color = if (activo) ColorAccent else Color.Transparent, [cite: 32]
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = label,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
                        style = GuayabappTypography.labelMedium.copy(
                            fontWeight = if (activo) FontWeight.Bold else FontWeight.Normal,
                            color = if (activo) Color.Black else ColorMuted [cite: 32]
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun BotonCaptura(
    modo: ModoCaptura,
    grabando: Boolean,
    procesando: Boolean,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "rec_pulse")
    val recScale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 0.85f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "rec_scale"
    )

    Box(Modifier.size(76.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(76.dp)
                .border(3.dp, if (grabando) ColorRed else Color.White, CircleShape) [cite: 32]
        )

        val colorBoton = when {
            grabando      -> ColorRed [cite: 32]
            modo == ModoCaptura.VIDEO -> ColorRed.copy(.85f) [cite: 32]
            else          -> Color.White
        }
        val escalaBoton = if (grabando) recScale else 1f
        val radioBoton  = if (grabando) RoundedCornerShape(8.dp) else CircleShape

        Box(
            Modifier
                .size(58.dp)
                .clip(radioBoton)
                .background(colorBoton)
                .scale(escalaBoton)
                .clickable(enabled = !procesando, onClick = onClick)
        ) {
            if (procesando) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center).size(24.dp),
                    color = Color.Black, strokeWidth = 2.dp
                )
            }
        }
    }
}

@Composable
private fun MiniaturaCapturaPrevia(ruta: String?) {
    Box(
        Modifier
            .size(52.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(ColorSuperficie) [cite: 32]
            .border(1.dp, ColorBorde, RoundedCornerShape(6.dp)), [cite: 32]
        contentAlignment = Alignment.Center
    ) {
        if (ruta != null) {
            Icon(Icons.Default.CheckCircle, contentDescription = null,
                tint = ColorAccent, modifier = Modifier.size(24.dp)) [cite: 32]
        } else {
            Icon(Icons.Default.Photo, contentDescription = null,
                tint = ColorMuted, modifier = Modifier.size(22.dp)) [cite: 32]
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// PANTALLA DE REVISIÓN POST-CAPTURA (CON SOPORTE NUNITO E INDICADORES FORMULARIO)
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun RevisionCapturaScreen(
    resultado: ResultadoCaptura,
    nombrePunto: String,
    onConfirmar: (descripcion: String) -> Unit,
    onDescartar: () -> Unit,
    onRetomar: () -> Unit
) {
    var descripcion by remember { mutableStateOf("") }

    Box(
        Modifier.fillMaxSize().background(ColorFondo), [cite: 32]
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .widthIn(max = 400.dp)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = onDescartar) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = ColorMuted) [cite: 32]
                }
                Text(
                    text = "Revisar captura",
                    style = GuayabappTypography.titleMedium,
                    color = ColorTexto
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onRetomar) {
                    Text("Retomar", style = GuayabappTypography.labelMedium, color = ColorAccent2) [cite: 32]
                }
            }

            Surface(
                Modifier.fillMaxWidth().height(220.dp),
                color = ColorSuperficie, [cite: 32]
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, ColorBorde) [cite: 32]
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (resultado.tipo == ModoCaptura.FOTO) Icons.Default.Photo
                        else Icons.Default.Videocam,
                        contentDescription = null,
                        tint = ColorMuted, modifier = Modifier.size(52.dp) [cite: 32]
                    )
                    Surface(
                        Modifier.align(Alignment.TopEnd).padding(8.dp),
                        color = if (resultado.tipo == ModoCaptura.FOTO)
                            ColorAccent2.copy(.2f) else ColorRed.copy(.2f), [cite: 32]
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = if (resultado.tipo == ModoCaptura.FOTO) "FOTO"
                                   else "VIDEO ${formatearTiempo(resultado.duracionSeg)}",
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            style = GuayabappTypography.labelMedium,
                            color = if (resultado.tipo == ModoCaptura.FOTO) ColorAccent2 else ColorRed [cite: 32]
                        )
                    }
                }
            }

            resultado.lectura?.let { gps ->
                Surface(
                    color = ColorAccent.copy(.08f), [cite: 32]
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, ColorAccent.copy(.2f)) [cite: 32]
                ) {
                    Row(
                        Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.GpsFixed, contentDescription = null,
                            tint = ColorAccent, modifier = Modifier.size(16.dp)) [cite: 32]
                        Column {
                            Text(
                                text = "GPS embebido en metadatos del archivo",
                                style = GuayabappTypography.labelMedium,
                                color = ColorAccent [cite: 32]
                            )
                            Text(
                                text = "N %.4f°  W %.4f°  · Alt: %.1f msnm · %s".format(gps.lat, Math.abs(gps.lon), gps.altitud, gps.precisionTexto),
                                style = GuayabappTypography.labelMedium.copy(fontSize = 11.sp),
                                color = ColorAccent.copy(.7f) [cite: 32]
                            )
                        }
                    }
                }
            } ?: Surface(
                color = ColorWarn.copy(.08f), [cite: 32]
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, ColorWarn.copy(.2f)) [cite: 32]
            ) {
                Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = ColorWarn, modifier = Modifier.size(16.dp)) [cite: 32]
                    Text("Sin datos de posicionamiento satelital", style = GuayabappTypography.bodyLarge, color = ColorWarn) [cite: 32]
                }
            }

            Surface(color = ColorSuperficie, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, ColorBorde)) { [cite: 32]
                Row(Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.PushPin, contentDescription = null, tint = ColorMuted, modifier = Modifier.size(15.dp)) [cite: 32]
                    Text("Se adjunta a:", style = GuayabappTypography.bodyLarge.copy(fontSize = 13.sp), color = ColorMuted) [cite: 32]
                    Text(nombrePunto, style = GuayabappTypography.bodyLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold), color = ColorTexto)
                }
            }

            // Hook indicado a futuro para formularios dinámicos variables por JSON de tipo de punto [cite: 8]
            OutlinedTextField(
                value = descripcion,
                onValueChange = { descripcion = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Descripción técnica (opcional)", style = GuayabappTypography.bodyLarge) },
                placeholder = { Text("Anotaciones geológicas, observaciones del terreno...", style = GuayabappTypography.bodyLarge, color = ColorMuted) }, [cite: 32]
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ColorAccent, [cite: 32]
                    unfocusedBorderColor = ColorBorde, [cite: 32]
                    focusedTextColor = ColorTexto,
                    unfocusedTextColor = ColorTexto,
                    cursorColor = ColorAccent, [cite: 32]
                    focusedContainerColor = ColorSuperficie2, [cite: 32]
                    unfocusedContainerColor = ColorSuperficie2 [cite: 32]
                    focusedLabelColor = ColorAccent, [cite: 32]
                    unfocusedLabelColor = ColorMuted [cite: 32]
                ),
                shape = RoundedCornerShape(8.dp),
                maxLines = 3
            )

            Spacer(Modifier.weight(1f))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onDescartar,
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, ColorBorde), [cite: 32]
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ColorMuted) [cite: 32]
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Descartar", style = GuayabappTypography.bodyLarge)
                }
                Button(
                    onClick = { onConfirmar(descripcion) },
                    modifier = Modifier.weight(2f),
                    colors = ButtonDefaults.buttonColors(containerColor = ColorAccent, contentColor = Color.Black), [cite: 32]
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Guardar en punto", style = GuayabappTypography.bodyLarge, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
