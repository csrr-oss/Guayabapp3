package com.geofield.camera

import android.content.Context
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
import androidx.compose.ui.text.TextStyle // CORRECCIÓN IMPORTACIÓN
import androidx.compose.ui.text.font.FontFamily // CORRECCIÓN IMPORTACIÓN
import androidx.compose.ui.text.font.FontWeight // CORRECCIÓN IMPORTACIÓN
import androidx.compose.ui.text.style.TextAlign // CORRECCIÓN IMPORTACIÓN
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

private val ColorFondo      = Color(0xFF0F1117) 
private val ColorSuperficie = Color(0xFF181C27) 
private val ColorBorde      = Color(0xFF2A3045) 
private val ColorAccent     = Color(0xFF87A922) 
private val ColorAccent2    = Color(0xFF0090FF) 
private val ColorMuted      = Color(0xFF6B7A99) 
private val ColorTexto      = Color(0xFFE8EAF2) 
private val ColorWarn       = Color(0xFFF0A500) 
private val ColorRed        = Color(0xFFD80032) 

// Estilos tipográficos locales auto-contenidos para blindar la compilación
private val LocalLabelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 12.sp)
private val LocalTitleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 16.sp)
private val LocalBodyLarge   = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 14.sp)

enum class ModoCaptura { FOTO, VIDEO }

data class ResultadoCaptura(
    val rutaArchivo: String,
    val tipo: ModoCaptura,
    val lectura: LecturaGps?,
    val duracionSeg: Int = 0
)

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
        CamaraPreview(
            modo = modo,
            flashActivo = flashActivo,
            camaraFrontal = camaraFrontal,
            lifecycleOwner = lifecycleOwner,
            onImageCaptureReady = { imageCaptureRef.value = it },
            onVideoCaptureReady = { videoCaptureRef.value = it }
        )

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
                    Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = ColorTexto, modifier = Modifier.size(22.dp))
                }
                Text(text = "Guayabapp", style = LocalTitleMedium, color = ColorTexto, modifier = Modifier.padding(horizontal = 4.dp))
                BadgeGpsCamara(estadoGps = estadoGps, modifier = Modifier.weight(1f))

                if (!camaraFrontal) {
                    IconButton(onClick = { flashActivo = !flashActivo }, modifier = Modifier.size(36.dp)) {
                        Icon(if (flashActivo) Icons.Default.FlashOn else Icons.Default.FlashOff, contentDescription = "Flash", tint = if (flashActivo) ColorWarn else ColorMuted, modifier = Modifier.size(20.dp))
                    }
                }
                IconButton(onClick = { camaraFrontal = !camaraFrontal }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.FlipCameraAndroid, contentDescription = "Cambiar cámara", tint = ColorTexto, modifier = Modifier.size(20.dp))
                }
            }

            AnimatedVisibility(visible = grabando) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).background(ColorRed, CircleShape))
                    Spacer(Modifier.width(6.dp))
                    Text(formatearTiempo(segundosVideo), style = LocalLabelMedium.copy(fontSize = 16.sp), color = ColorTexto)
                }
            }
        }

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

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                MiniaturaCapturaPrevia(ruta = ultimaFoto)
                BotonCaptura(
                    modo = modo, grabando = grabando, procesando = procesando,
                    onClick = {
                        when (modo) {
                            ModoCaptura.FOTO -> {
                                if (procesando) return@BotonCaptura
                                procesando = true
                                scope.launch {
                                    val ruta = tomarFoto(context = context, imageCapture = imageCaptureRef.value, lecturaGps = lecturaGps)
                                    procesando = false
                                    ruta?.let { ultimaFoto = it; onCaptura(ResultadoCaptura(it, ModoCaptura.FOTO, lecturaGps)) }
                                }
                            }
                            ModoCaptura.VIDEO -> {
                                if (grabando) {
                                    grabacionActiva?.stop()
                                    grabacionActiva = null
                                    grabando = false
                                } else {
                                    scope.launch {
                                        val (recording, _) = iniciarVideo(
                                            context = context, videoCapture = videoCaptureRef.value,
                                            onFinalizado = { rutaFinal, duracion ->
                                                grabando = false
                                                ultimaFoto = rutaFinal
                                                onCaptura(ResultadoCaptura(rutaFinal, ModoCaptura.VIDEO, lecturaGps, duracion))
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

                Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(110.dp)) {
                    if (lecturaGps != null) {
                        Text(text = "N %.4f°".format(lecturaGps.lat), style = LocalLabelMedium, color = ColorAccent)
                        Text(text = "W %.4f°".format(Math.abs(lecturaGps.lon)), style = LocalLabelMedium, color = ColorAccent)
                        Text(text = lecturaGps.precisionTexto, style = LocalLabelMedium.copy(fontSize = 11.sp), color = ColorMuted)
                    } else {
                        Text(text = "No data", style = LocalLabelMedium, color = ColorWarn)
                    }
                }
            }
        }

        AnimatedVisibility(visible = procesando, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.Center)) {
            CircularProgressIndicator(color = ColorAccent, modifier = Modifier.size(48.dp))
        }
    }
}

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
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    val preview = remember { Preview.Builder().build() }
    val imageCapture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build() }
    val videoCapture = remember {
        val recorder = Recorder.Builder().setQualitySelector(QualitySelector.fromOrderedList(listOf(Quality.FHD, Quality.HD))).build()
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
            cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture, videoCapture)
            preview.setSurfaceProvider(previewView.surfaceProvider)
            onImageCaptureReady(imageCapture)
            onVideoCaptureReady(videoCapture)
        } catch (_: Exception) {}
    }

    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
}

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

private suspend fun tomarFoto(context: Context, imageCapture: ImageCapture?, lecturaGps: LecturaGps?): String? = withContext(Dispatchers.IO) {
    if (imageCapture == null) return@withContext null
    val archivo = crearArchivoMedia(context, "FOTO", ".jpg")

    suspendCancellableCoroutine { cont ->
        val outputOptions = ImageCapture.OutputFileOptions.Builder(archivo).build()
        imageCapture.takePicture(
            outputOptions, ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    lecturaGps?.let { ExifGpsWriter.escribirEnFoto(archivo.absolutePath, it) }
                    cont.resume(archivo.absolutePath) {}
                }
                override fun onError(exception: ImageCaptureException) { cont.resume(null) {} }
            }
        )
    }
}

private suspend fun iniciarVideo(context: Context, videoCapture: VideoCapture<Recorder>?, onFinalizado: (String, Int) -> Unit): Pair<Recording?, String?> {
    if (videoCapture == null) return Pair(null, null)
    val archivo = crearArchivoMedia(context, "VIDEO", ".mp4")
    val tiempoInicio = System.currentTimeMillis()
    val outputOptions = FileOutputOptions.Builder(archivo).build()

    val recording = videoCapture.output
        .prepareRecording(context, outputOptions)
        .apply { try { withAudioEnabled() } catch (_: Exception) {} }
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

@Composable
private fun BadgeGpsCamara(estadoGps: EstadoGps, modifier: Modifier = Modifier) {
    val (color, texto) = when (estadoGps) {
        is EstadoGps.Activo -> {
            val c = if (estadoGps.lectura.precision <= 3f) ColorAccent else ColorWarn
            c to "GPS ${estadoGps.lectura.precisionTexto}"
        }
        is EstadoGps.Buscando -> ColorWarn to "Buscando..."
        else -> ColorMuted to "No data"
    }

    Surface(color = Color.Black.copy(.5f), shape = RoundedCornerShape(4.dp), border = BorderStroke(1.dp, color.copy(.3f)), modifier = modifier) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(Icons.Default.GpsFixed, null, tint = color, modifier = Modifier.size(12.dp))
            Text(texto, style = LocalLabelMedium.copy(fontSize = 11.sp), color = color)
        }
    }
}

@Composable
private fun SelectorModoCaptura(modoActual: ModoCaptura, onCambiar: (ModoCaptura) -> Unit) {
    Surface(color = Color.Black.copy(.5f), shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, ColorBorde)) {
        Row(Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            listOf(ModoCaptura.FOTO to "FOTO", ModoCaptura.VIDEO to "VIDEO").forEach { (modo, label) ->
                val activo = modoActual == modo
                Surface(onClick = { onCambiar(modo) }, color = if (activo) ColorAccent else Color.Transparent, shape = RoundedCornerShape(16.dp)) {
                    Text(text = label, modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp), style = LocalLabelMedium.copy(fontWeight = if (activo) FontWeight.Bold else FontWeight.Normal, color = if (activo) Color.Black else ColorMuted))
                }
            }
        }
    }
}

@Composable
private fun BotonCaptura(modo: ModoCaptura, grabando: Boolean, procesando: Boolean, onClick: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "rec_pulse")
    val recScale by infiniteTransition.animateFloat(initialValue = 1f, targetValue = 0.85f, animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "rec_scale")

    Box(Modifier.size(76.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(76.dp).border(3.dp, if (grabando) ColorRed else Color.White, CircleShape))
        val colorBoton = if (grabando || modo == ModoCaptura.VIDEO) ColorRed else Color.White
        Box(Modifier.size(58.dp).clip(if (grabando) RoundedCornerShape(8.dp) else CircleShape).background(colorBoton).scale(if (grabando) recScale else 1f).clickable(enabled = !procesando, onClick = onClick)) {
            if (procesando) CircularProgressIndicator(modifier = Modifier.align(Alignment.Center).size(24.dp), color = Color.Black, strokeWidth = 2.dp)
        }
    }
}

@Composable
private fun MiniaturaCapturaPrevia(ruta: String?) {
    Box(Modifier.size(52.dp).clip(RoundedCornerShape(6.dp)).background(ColorSuperficie).border(1.dp, ColorBorde, RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
        Icon(if (ruta != null) Icons.Default.CheckCircle else Icons.Default.Photo, null, tint = if (ruta != null) ColorAccent else ColorMuted, modifier = Modifier.size(22.dp))
    }
}

@Composable
fun RevisionCapturaScreen(resultado: ResultadoCaptura, nombrePunto: String, onConfirmar: (String) -> Unit, onDescartar: () -> Unit, onRetomar: () -> Unit) {
    var descripcion by remember { mutableStateOf("") }

    Box(Modifier.fillMaxSize().background(ColorFondo), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 400.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = onDescartar) { Icon(Icons.Default.Close, null, tint = ColorMuted) }
                Text(text = "Revisar captura", style = LocalTitleMedium, color = ColorTexto)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onRetomar) { Text("Retomar", style = LocalLabelMedium, color = ColorAccent2) }
            }

            Surface(Modifier.fillMaxWidth().height(220.dp), color = ColorSuperficie, shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, ColorBorde)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(if (resultado.tipo == ModoCaptura.FOTO) Icons.Default.Photo else Icons.Default.Videocam, null, tint = ColorMuted, modifier = Modifier.size(52.dp))
                    Surface(Modifier.align(Alignment.TopEnd).padding(8.dp), color = if (resultado.tipo == ModoCaptura.FOTO) ColorAccent2.copy(.2f) else ColorRed.copy(.2f), shape = RoundedCornerShape(4.dp)) {
                        Text(text = if (resultado.tipo == ModoCaptura.FOTO) "FOTO" else "VIDEO ${formatearTiempo(resultado.duracionSeg)}", modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp), style = LocalLabelMedium, color = if (resultado.tipo == ModoCaptura.FOTO) ColorAccent2 else ColorRed)
                    }
                }
            }

            resultado.lectura?.let { gps ->
                Surface(color = ColorAccent.copy(.08f), shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, ColorAccent.copy(.2f))) {
                    Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.GpsFixed, null, tint = ColorAccent, modifier = Modifier.size(16.dp))
                        Column {
                            Text(text = "GPS embebido en metadatos del archivo", style = LocalLabelMedium, color = ColorAccent)
                            Text(text = "N %.4f°  W %.4f°  · Alt: %.1f msnm · %s".format(gps.lat, Math.abs(gps.lon), gps.altitud, gps.precisionTexto), style = LocalLabelMedium.copy(fontSize = 11.sp), color = ColorAccent.copy(.7f))
                        }
                    }
                }
            } ?: Surface(color = ColorWarn.copy(.08f), shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, ColorWarn.copy(.2f))) {
                Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Warning, null, tint = ColorWarn, modifier = Modifier.size(16.dp))
                    Text("Sin datos de posicionamiento satelital", style = LocalBodyLarge, color = ColorWarn)
                }
            }

            Surface(color = ColorSuperficie, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, ColorBorde)) {
                Row(Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.PushPin, null, tint = ColorMuted, modifier = Modifier.size(15.dp))
                    Text("Se adjunta a:", style = LocalBodyLarge.copy(fontSize = 13.sp), color = ColorMuted)
                    Text(nombrePunto, style = LocalBodyLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold), color = ColorTexto)
                }
            }

            // CORRECCIÓN: Se sustituyó 'ColorSuperficie2' por 'ColorSuperficie' evitando el error sintáctico
            OutlinedTextField(
                value = descripcion, onValueChange = { descripcion = it }, modifier = Modifier.fillMaxWidth(),
                label = { Text("Descripción técnica (opcional)", style = LocalBodyLarge) },
                placeholder = { Text("Anotaciones geológicas, observaciones del terreno...", style = LocalBodyLarge, color = ColorMuted) },
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ColorAccent, unfocusedBorderColor = ColorBorde, focusedTextColor = ColorTexto, unfocusedTextColor = ColorTexto, cursorColor = ColorAccent, focusedContainerColor = ColorSuperficie, unfocusedContainerColor = ColorSuperficie, focusedLabelColor = ColorAccent, unfocusedLabelColor = ColorMuted),
                shape = RoundedCornerShape(8.dp), maxLines = 3
            )

            Spacer(Modifier.weight(1f))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDescartar, modifier = Modifier.weight(1f), border = BorderStroke(1.dp, ColorBorde), shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = ColorMuted)) {
                    Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(5.dp)); Text("Descartar", style = LocalBodyLarge)
                }
                Button(onClick = { onConfirmar(descripcion) }, modifier = Modifier.weight(2f), colors = ButtonDefaults.buttonColors(containerColor = ColorAccent, contentColor = Color.Black), shape = RoundedCornerShape(8.dp)) {
                    Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(5.dp)); Text("Guardar en punto", style = LocalBodyLarge, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
