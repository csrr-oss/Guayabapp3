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
import com.geofield.theme.GuayabappTypography // Importamos la nueva tipografía Nunito unificada
import kotlinx.coroutines.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

// ─── PALETA GUAYABAPP IDENTIDAD VISUAL ANTRACITA Y FRUTAL ────────────────────
private val ColorFondo      = Color(0xFF0F1117) // Fondo principal
private val ColorSuperficie = Color(0xFF181C27) // Superficie
private val ColorBorde      = Color(0xFF2A3045) // Bordes
private val ColorAccent     = Color(0xFF87A922) // Verde Guayaba Maduro (Accent principal)
private val ColorAccent2    = Color(0xFF0090FF) // Modo OSM (Azul)
private val ColorMuted      = Color(0xFF6B7A99) // Gris mitigado
private val ColorTexto      = Color(0xFFE8EAF2) // Blanco texto principal
private val ColorWarn       = Color(0xFFF0A500) // Ámbar advertencia
private val ColorRed        = Color(0xFFD80032) // Rubí pulpa de guayaba (Alertas/Grabación)

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

                // Nombre Oficial de la App inyectado con Nunito
                Text(
                    text = "Guayabapp",
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
                            tint = if (flashActivo) ColorWarn else ColorMuted,
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
                    Box(Modifier.size(8.dp).background(ColorRed, CircleShape))
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

                // CORRECCIÓN: Coordenadas WGS84 legibles con espacio reglamentario y tipografía aumentada
                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.width(110.dp)
                ) {
                    if (lecturaGps != null) {
                        Text(
                            text = "N %.4f°".format(lecturaGps.lat), 
                            style = GuayabappTypography.labelMedium, 
                            color = ColorAccent
                        )
                        Text(
                            text = "W %.4f°".format(Math.abs(lecturaGps.lon)), 
                            style = GuayabappTypography.labelMedium, 
                            color = ColorAccent
                        )
                        Text(
                            text = lecturaGps.precisionTexto, 
                            style = GuayabappTypography.labelMedium.copy(fontSize = 11.sp), 
                            color = ColorMuted
                        )
                    } else {
                        Text(
                            text = "Sin GPS", 
                            style = GuayabappTypography.labelMedium, 
                            color = ColorWarn
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
            CircularProgressIndicator(color = ColorAccent, modifier = Modifier.size(48.dp))
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

// Extension para transformar ListenableFuture a suspensión limpia de Corrutinas
private suspend fun <T> com.google.common.util.concurrent.ListenableFuture<T>.await(): T {
    return suspendCancellableCoroutine { continuation ->
        addListener({
            try {
                continuation.resume(get(), onCancellation = null)
            } catch (e: Exception) {
                continuation.resumeWith(Result.failure(e))
            }
        }, ContextCompat.getMainExecutor(contextFromContinuation(continuation)))
    }
}

private fun contextFromContinuation(cont: CancellableContinuation<*>): Context {
    return (cont.context[Job] as? Context) ?: throw IllegalStateException("Ciclo de ejecución de contexto faltante.")
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

    val archivo = crearArchivo
