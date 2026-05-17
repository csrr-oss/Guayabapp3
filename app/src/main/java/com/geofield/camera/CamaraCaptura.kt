package com.geofield.camera

// ─── DEPENDENCIAS (ya incluidas en GeoFieldSetup.kt / build.gradle.kts) ───────
// implementation("androidx.camera:camera-camera2:1.4.0")
// implementation("androidx.camera:camera-lifecycle:1.4.0")
// implementation("androidx.camera:camera-view:1.4.0")

import android.content.Context
import android.media.ExifInterface
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
import kotlinx.coroutines.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executor

// ─── PALETA ───────────────────────────────────────────────────────────────────

private val ColorFondo      = Color(0xFF0F1117)
private val ColorSuperficie = Color(0xFF181C27)
private val ColorBorde      = Color(0xFF2A3045)
private val ColorAccent     = Color(0xFF00D084)
private val ColorAccent2    = Color(0xFF0090FF)
private val ColorMuted      = Color(0xFF6B7A99)
private val ColorTexto      = Color(0xFFE8EAF2)
private val ColorWarn       = Color(0xFFF0A500)
private val ColorRed        = Color(0xFFFF4757)

// ─── MODO DE CAPTURA ──────────────────────────────────────────────────────────

enum class ModoCaptura { FOTO, VIDEO }

// ─── RESULTADO DE CAPTURA ─────────────────────────────────────────────────────

data class ResultadoCaptura(
    val rutaArchivo: String,
    val tipo: ModoCaptura,
    val lectura: LecturaGps?,       // GPS al momento de la captura
    val duracionSeg: Int = 0        // solo para video
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
    val context       = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope         = rememberCoroutineScope()

    // ── Estado de la cámara ───────────────────────────────────────────────────
    var modo          by remember { mutableStateOf(ModoCaptura.FOTO) }
    var grabando      by remember { mutableStateOf(false) }
    var segundosVideo by remember { mutableIntStateOf(0) }
    var flashActivo   by remember { mutableStateOf(false) }
    var camaraFrontal by remember { mutableStateOf(false) }
    var ultimaFoto    by remember { mutableStateOf<String?>(null) }
    var procesando    by remember { mutableStateOf(false) }

    // ── Controladores CameraX ─────────────────────────────────────────────────
    var imageCapture  by remember { mutableStateOf<ImageCapture?>(null) }
    var videoCapture  by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var grabacionActiva by remember { mutableStateOf<Recording?>(null) }

    // Lectura GPS actual para embeber en cada captura
    val lecturaGps = (estadoGps as? EstadoGps.Activo)?.lectura

    // Cronómetro de video
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

        // ── PREVIEW DE CÁMARA ─────────────────────────────────────────────────
        CamaraPreview(
            modo          = modo,
            flashActivo   = flashActivo,
            camaraFrontal = camaraFrontal,
            lifecycleOwner = lifecycleOwner,
            onImageCapture  = { imageCapture = it },
            onVideoCapture  = { videoCapture = it }
        )

        // ── OVERLAY SUPERIOR: info GPS + controles ────────────────────────────
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
                // Botón cerrar
                IconButton(onClick = onCerrar, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Cerrar",
                        tint = ColorTexto, modifier = Modifier.size(22.dp))
                }

                // GPS info compacto
                BadgeGpsCamara(estadoGps = estadoGps, modifier = Modifier.weight(1f))

                // Flash
                if (!camaraFrontal) {
                    IconButton(
                        onClick = { flashActivo = !flashActivo },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            if (flashActivo) Icons.Default.FlashOn else Icons.Default.FlashOff,
                            contentDescription = "Flash",
                            tint = if (flashActivo) ColorWarn else ColorMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Cambiar cámara
                IconButton(
                    onClick = { camaraFrontal = !camaraFrontal },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.FlipCameraAndroid, contentDescription = "Cambiar cámara",
                        tint = ColorTexto, modifier = Modifier.size(20.dp))
                }
            }

            // Cronómetro de video (visible solo al grabar)
            AnimatedVisibility(visible = grabando) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(8.dp).background(ColorRed, CircleShape))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        formatearTiempo(segundosVideo),
                        style = TextStyle(fontFamily = FontFamily.Monospace,
                            fontSize = 16.sp, color = ColorTexto, fontWeight = FontWeight.Medium)
                    )
                }
            }
        }

        // ── OVERLAY INFERIOR: selector modo + botón captura + miniatura ───────
        Column(
            Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Selector FOTO / VIDEO
            if (!grabando) {
                SelectorModoCaptura(modoActual = modo, onCambiar = { modo = it })
            }

            // Fila: miniatura última foto + botón captura + info GPS
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Miniatura última captura
                MiniaturaCapturaPrevia(ruta = ultimaFoto)

                // Botón principal de captura
                BotonCaptura(
                    modo = modo,
                    grabando = grabando,
                    procesando = procesando,
                    onClick = {
                        when (modo) {
                            ModoCaptura.FOTO -> {
                                procesando = true
                                scope.launch {
                                    val ruta = tomarFoto(
                                        context = context,
                                        imageCapture = imageCapture,
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
                                    // Detener grabación
                                    grabacionActiva?.stop()
                                    grabacionActiva = null
                                    grabando = false
                                } else {
                                    // Iniciar grabación
                                    scope.launch {
                                        val (recording, ruta) = iniciarVideo(
                                            context = context,
                                            videoCapture = videoCapture,
                                            lecturaGps = lecturaGps,
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
                                        grabando = ruta != null
                                    }
                                }
                            }
                        }
                    }
                )

                // Coordenadas GPS (derecha)
                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.width(90.dp)
                ) {
                    if (lecturaGps != null) {
                        Text("N%.4f°".format(lecturaGps.lat),
                            style = TextStyle(fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp, color = ColorAccent))
                        Text("W%.4f°".format(Math.abs(lecturaGps.lon)),
                            style = TextStyle(fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp, color = ColorAccent))
                        Text(lecturaGps.precisionTexto,
                            style = TextStyle(fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp, color = ColorMuted))
                    } else {
                        Text("Sin GPS",
                            style = TextStyle(fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp, color = ColorWarn))
                    }
                }
            }
        }

        // Indicador "procesando foto"
        AnimatedVisibility(
            visible = procesando,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            CircularProgressIndicator(color = ColorAccent, modifier = Modifier.size(48.dp))
        }
    }
}

// ─── PREVIEW DE CÁMARA (AndroidView wrapper) ──────────────────────────────────

@Composable
private fun CamaraPreview(
    modo: ModoCaptura,
    flashActivo: Boolean,
    camaraFrontal: Boolean,
    lifecycleOwner: LifecycleOwner,
    onImageCapture: (ImageCapture) -> Unit,
    onVideoCapture: (VideoCapture<Recorder>) -> Unit
) {
    val context = LocalContext.current

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            PreviewView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
        },
        update = { previewView ->
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                // Preview
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                // ImageCapture (foto)
                val imgCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .setFlashMode(
                        if (flashActivo) ImageCapture.FLASH_MODE_ON
                        else ImageCapture.FLASH_MODE_OFF
                    )
                    .build()
                onImageCapture(imgCapture)

                // VideoCapture con Recorder
                val recorder = Recorder.Builder()
                    .setQualitySelector(
                        QualitySelector.fromOrderedList(
                            listOf(Quality.FHD, Quality.HD, Quality.SD)
                        )
                    )
                    .build()
                val vidCapture = VideoCapture.withOutput(recorder)
                onVideoCapture(vidCapture)

                // Selector de cámara
                val selector = if (camaraFrontal)
                    CameraSelector.DEFAULT_FRONT_CAMERA
                else
                    CameraSelector.DEFAULT_BACK_CAMERA

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        selector,
                        preview,
                        imgCapture,
                        vidCapture
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }, ContextCompat.getMainExecutor(context))
        }
    )
}

// ─── TOMAR FOTO ───────────────────────────────────────────────────────────────

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
                    // Escribir GPS en Exif justo después de guardar
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

// ─── GRABAR VIDEO ─────────────────────────────────────────────────────────────

private suspend fun iniciarVideo(
    context: Context,
    videoCapture: VideoCapture<Recorder>?,
    lecturaGps: LecturaGps?,
    onFinalizado: (ruta: String, duracionSeg: Int) -> Unit
): Pair<Recording?, String?> {
    if (videoCapture == null) return Pair(null, null)

    val archivo = crearArchivoMedia(context, "VIDEO", ".mp4")
    val tiempoInicio = System.currentTimeMillis()

    val outputOptions = FileOutputOptions.Builder(archivo).build()

    val recording = videoCapture.output
        .prepareRecording(context, outputOptions)
        .apply {
            // Solicitar audio si el permiso está concedido
            try { withAudioEnabled() } catch (_: Exception) {}
        }
        .start(ContextCompat.getMainExecutor(context)) { evento ->
            when (evento) {
                is VideoRecordEvent.Finalize -> {
                    if (!evento.hasError()) {
                        val duracion = ((System.currentTimeMillis() - tiempoInicio) / 1000).toInt()
                        // El video no tiene Exif estándar, pero guardamos las coords
                        // en el nombre del archivo y en la base de datos (via onFinalizado)
                        onFinalizado(archivo.absolutePath, duracion)
                    }
                }
                else -> { /* Start, Pause, Resume — ignorados */ }
            }
        }

    return Pair(recording, archivo.absolutePath)
}

// ─── HELPER: CREAR ARCHIVO DE MEDIA ──────────────────────────────────────────

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

// ═══════════════════════════════════════════════════════════════════════════════
// COMPOSABLES DE UI DE LA CÁMARA
// ═══════════════════════════════════════════════════════════════════════════════

// ─── Badge GPS compacto para la barra superior de la cámara ──────────────────

@Composable
private fun BadgeGpsCamara(estadoGps: EstadoGps, modifier: Modifier = Modifier) {
    val (color, texto) = when (estadoGps) {
        is EstadoGps.Activo -> {
            val c = when {
                estadoGps.lectura.precision <= 3f  -> ColorAccent
                estadoGps.lectura.precision <= 10f -> ColorWarn
                else -> ColorRed
            }
            c to "GPS ${estadoGps.lectura.precisionTexto}"
        }
        is EstadoGps.Buscando -> ColorWarn to "GPS buscando..."
        else -> ColorMuted to "Sin GPS"
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
                tint = color, modifier = Modifier.size(11.dp))
            Text(texto,
                style = TextStyle(fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp, color = color))
        }
    }
}

// ─── Selector FOTO / VIDEO ────────────────────────────────────────────────────

@Composable
private fun SelectorModoCaptura(modoActual: ModoCaptura, onCambiar: (ModoCaptura) -> Unit) {
    Surface(
        color = Color.Black.copy(.5f),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, ColorBorde)
    ) {
        Row(Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            listOf(ModoCaptura.FOTO to "FOTO", ModoCaptura.VIDEO to "VIDEO").forEach { (modo, label) ->
                val activo = modoActual == modo
                Surface(
                    onClick = { onCambiar(modo) },
                    color = if (activo) ColorAccent else Color.Transparent,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(label,
                        Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                            fontWeight = if (activo) FontWeight.Medium else FontWeight.Normal,
                            color = if (activo) Color.Black else ColorMuted
                        )
                    )
                }
            }
        }
    }
}

// ─── Botón principal de captura ───────────────────────────────────────────────

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

    Box(
        Modifier.size(76.dp),
        contentAlignment = Alignment.Center
    ) {
        // Anillo exterior
        Box(
            Modifier
                .size(76.dp)
                .border(3.dp,
                    if (grabando) ColorRed else Color.White,
                    CircleShape)
        )

        // Botón interior
        val colorBoton = when {
            grabando      -> ColorRed
            modo == ModoCaptura.VIDEO -> ColorRed.copy(.85f)
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

// ─── Miniatura de la última captura ──────────────────────────────────────────

@Composable
private fun MiniaturaCapturaPrevia(ruta: String?) {
    Box(
        Modifier
            .size(52.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(ColorSuperficie)
            .border(1.dp, ColorBorde, RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (ruta != null) {
            // En producción: AsyncImage(model = ruta, ...) con Coil
            // Por ahora: ícono de confirmación
            Icon(Icons.Default.CheckCircle, contentDescription = null,
                tint = ColorAccent, modifier = Modifier.size(24.dp))
        } else {
            Icon(Icons.Default.Photo, contentDescription = null,
                tint = ColorMuted, modifier = Modifier.size(22.dp))
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// PANTALLA DE REVISIÓN POST-CAPTURA
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
        Modifier.fillMaxSize().background(ColorFondo),
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
            // Header
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = onDescartar) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = ColorMuted)
                }
                Text("Revisar captura",
                    style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium,
                        color = ColorTexto))
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onRetomar) {
                    Text("Retomar", style = TextStyle(fontSize = 12.sp, color = ColorAccent2))
                }
            }

            // Preview del archivo capturado
            Surface(
                Modifier.fillMaxWidth().height(220.dp),
                color = ColorSuperficie,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, ColorBorde)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (resultado.tipo == ModoCaptura.FOTO) Icons.Default.Photo
                        else Icons.Default.Videocam,
                        contentDescription = null,
                        tint = ColorMuted, modifier = Modifier.size(52.dp)
                    )
                    // Badge tipo
                    Surface(
                        Modifier.align(Alignment.TopEnd).padding(8.dp),
                        color = if (resultado.tipo == ModoCaptura.FOTO)
                            ColorAccent2.copy(.2f) else ColorRed.copy(.2f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            if (resultado.tipo == ModoCaptura.FOTO) "FOTO"
                            else "VIDEO ${formatearTiempo(resultado.duracionSeg)}",
                            Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 9.sp,
                                color = if (resultado.tipo == ModoCaptura.FOTO) ColorAccent2 else ColorRed)
                        )
                    }
                }
            }

            // Info GPS embebida
            resultado.lectura?.let { gps ->
                Surface(
                    color = ColorAccent.copy(.08f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, ColorAccent.copy(.2f))
                ) {
                    Row(
                        Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.GpsFixed, contentDescription = null,
                            tint = ColorAccent, modifier = Modifier.size(16.dp))
                        Column {
                            Text("GPS embebido en archivo",
                                style = TextStyle(fontSize = 10.sp, color = ColorAccent,
                                    fontWeight = FontWeight.Medium))
                            Text("${gps.coordenadasFormateadas} · Alt: %.0fm · ${gps.precisionTexto}".format(gps.altitud),
                                style = TextStyle(fontFamily = FontFamily.Monospace,
                                    fontSize = 9.sp, color = ColorAccent.copy(.7f)))
                        }
                    }
                }
            } ?: Surface(
                color = ColorWarn.copy(.08f),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, ColorWarn.copy(.2f))
            ) {
                Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Warning, contentDescription = null,
                        tint = ColorWarn, modifier = Modifier.size(16.dp))
                    Text("Sin GPS al momento de la captura",
                        style = TextStyle(fontSize = 10.sp, color = ColorWarn))
                }
            }

            // Punto al que se asocia
            Surface(color = ColorSuperficie, shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, ColorBorde)) {
                Row(Modifier.padding(11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.PushPin, contentDescription = null,
                        tint = ColorMuted, modifier = Modifier.size(15.dp))
                    Text("Se adjunta a:",
                        style = TextStyle(fontSize = 11.sp, color = ColorMuted))
                    Text(nombrePunto,
                        style = TextStyle(fontSize = 11.sp, color = ColorTexto,
                            fontWeight = FontWeight.Medium))
                }
            }

            // Descripción opcional
            OutlinedTextField(
                value = descripcion,
                onValueChange = { descripcion = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Descripción (opcional)") },
                placeholder = { Text("Qué muestra esta foto...",
                    style = TextStyle(fontSize = 12.sp, color = ColorMuted)) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ColorAccent,
                    unfocusedBorderColor = ColorBorde,
                    focusedTextColor = ColorTexto,
                    unfocusedTextColor = ColorTexto,
                    cursorColor = ColorAccent,
                    focusedContainerColor = ColorSuperficie,
                    unfocusedContainerColor = ColorSuperficie,
                    focusedLabelColor = ColorAccent,
                    unfocusedLabelColor = ColorMuted
                ),
                shape = RoundedCornerShape(8.dp),
                maxLines = 3
            )

            Spacer(Modifier.weight(1f))

            // Acciones
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onDescartar,
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, ColorBorde),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ColorMuted)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null,
                        modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Descartar")
                }
                Button(
                    onClick = { onConfirmar(descripcion) },
                    modifier = Modifier.weight(2f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ColorAccent, contentColor = Color.Black),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null,
                        modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Guardar en punto", fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// INTEGRACIÓN CON EL VISOR — cómo lanzar la cámara desde MapaVisorUnificado
// ═══════════════════════════════════════════════════════════════════════════════

/*
En MapaVisorUnificado.kt, dentro del PanelDetallePunto,
el botón "Agregar foto" lanza la cámara así:

──────────────────────────────────────────────────────────────────────────
var mostrarCamara by remember { mutableStateOf(false) }
var resultadoCaptura by remember { mutableStateOf<ResultadoCaptura?>(null) }

// Botón en el panel detalle del punto:
Surface(onClick = { mostrarCamara = true }, ...) {
    Row { Icon(Icons.Default.CameraAlt, ...) ; Text("Agregar foto / video") }
}

// Overlay de cámara (cubre toda la pantalla)
if (mostrarCamara) {
    CamaraScreen(
        estadoGps = estadoGps,
        onCaptura = { resultado ->
            resultadoCaptura = resultado
            mostrarCamara = false
        },
        onCerrar = { mostrarCamara = false }
    )
}

// Pantalla de revisión post-captura
resultadoCaptura?.let { resultado ->
    RevisionCapturaScreen(
        resultado = resultado,
        nombrePunto = puntoSeleccionado?.punto?.nombre ?: "",
        onConfirmar = { descripcion ->
            viewModel.agregarFoto(
                puntoId    = puntoSeleccionado!!.punto.id,
                rutaArchivo = resultado.rutaArchivo,
                lat        = resultado.lectura?.lat ?: 0.0,
                lon        = resultado.lectura?.lon ?: 0.0,
                altitud    = resultado.lectura?.altitud ?: 0.0,
                descripcion = descripcion
            )
            resultadoCaptura = null
        },
        onDescartar = {
            // Borrar archivo del disco
            File(resultado.rutaArchivo).delete()
            resultadoCaptura = null
        },
        onRetomar = {
            resultadoCaptura = null
            mostrarCamara = true
        }
    )
}
──────────────────────────────────────────────────────────────────────────
*/
