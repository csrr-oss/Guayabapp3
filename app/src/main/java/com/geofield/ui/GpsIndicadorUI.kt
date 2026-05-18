package com.geofield.ui

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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geofield.location.EstadoGps
import com.geofield.location.LecturaGps

// ─── CONFIGURACIÓN LOCAL DE ESTILOS UNIFICADOS GUAYABAPP ─────────────────────
private object EstilosGuayaba {
    val Fondo       = Color(0xFF0F1117)
    val Superficie  = Color(0xFF181C27)
    val Superficie2 = Color(0xFF1F2436)
    val Borde       = Color(0xFF2A3045)
    val Accent      = Color(0xFF87A922) // Verde Guayaba Maduro
    val Bueno       = Color(0xFF4CAF50)
    val Warn        = Color(0xFFF0A500)
    val Malo        = Color(0xFFD80032) // Rubí Pulpa
    val Muted       = Color(0xFF6B7A99)
    val Texto       = Color(0xFFE8EAF2)
    val Texto2      = Color(0xFF9AA3BF)

    val LabelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    val TitleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    val TitleLarge  = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Black, fontSize = 24.sp)
    val BodyLarge   = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 14.sp)
}

private fun colorPorPrecision(precision: Float) = when {
    precision <= 3f  -> EstilosGuayaba.Accent
    precision <= 5f  -> EstilosGuayaba.Bueno
    precision <= 10f -> EstilosGuayaba.Warn
    else             -> EstilosGuayaba.Malo
}

@Composable
fun GpsBadgeTopBar(estado: EstadoGps) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_gps")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(900, easing = EaseInOut), RepeatMode.Reverse),
        label = "gps_dot_alpha"
    )

    val (dotColor, texto) = when (estado) {
        is EstadoGps.Activo -> colorPorPrecision(estado.lectura.precision) to estado.lectura.precisionTexto
        is EstadoGps.Buscando -> EstilosGuayaba.Warn to "Buscando..."
        is EstadoGps.PermisosDenegados -> EstilosGuayaba.Malo to "No data"
        is EstadoGps.Error -> EstilosGuayaba.Malo to "Error GPS"
        else -> EstilosGuayaba.Muted to "No data"
    }

    Surface(
        color = dotColor.copy(alpha = 0.1f),
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, dotColor.copy(alpha = 0.25f))
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val pulseAlpha = if (estado is EstadoGps.Buscando) alpha else 1f
            Box(Modifier.size(7.dp).background(dotColor.copy(alpha = pulseAlpha), CircleShape))
            Text(text = texto, style = EstilosGuayaba.LabelMedium, color = dotColor)
        }
    }
}

@Composable
fun PanelGpsDetalle(estado: EstadoGps, onCerrar: () -> Unit) {
    Surface(
        modifier = Modifier.width(260.dp),
        color = EstilosGuayaba.Superficie,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, EstilosGuayaba.Borde),
        shadowElevation = 8.dp
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.GpsFixed, contentDescription = null, tint = EstilosGuayaba.Accent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(text = "Estado GPS", style = EstilosGuayaba.TitleMedium, color = EstilosGuayaba.Texto)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onCerrar, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = EstilosGuayaba.Muted, modifier = Modifier.size(14.dp))
                }
            }

            HorizontalDivider(color = EstilosGuayaba.Borde, thickness = 0.5.dp)

            when (estado) {
                is EstadoGps.Activo -> DetalleGpsActivo(estado.lectura)
                is EstadoGps.Buscando -> DetalleGpsBuscando()
                is EstadoGps.PermisosDenegados -> DetalleGpsError("Permisos denegados.\nHabilítalos en Ajustes → Guayabapp.")
                is EstadoGps.Error -> DetalleGpsError(estado.mensaje)
                else -> DetalleGpsError("No data")
            }
        }
    }
}

@Composable
private fun DetalleGpsActivo(lectura: LecturaGps) {
    val color = colorPorPrecision(lectura.precision)
    val calidad = when {
        lectura.esExcelente -> "Excelente"
        lectura.esPrecisa   -> "Buena"
        lectura.precision <= 20f -> "Aceptable"
        else -> "Baja"
    }
    val porcentaje = ((20f - lectura.precision.coerceAtMost(20f)) / 20f)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(text = "Precisión horizontal", style = EstilosGuayaba.LabelMedium.copy(color = EstilosGuayaba.Muted))
                Text(text = lectura.precisionTexto, style = EstilosGuayaba.TitleLarge.copy(color = color))
                Text(text = calidad, style = EstilosGuayaba.BodyLarge.copy(fontWeight = FontWeight.Medium, color = color))
            }
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(progress = { porcentaje }, modifier = Modifier.size(48.dp), color = color, trackColor = EstilosGuayaba.Borde, strokeWidth = 3.dp)
                Text(text = "${lectura.precision.toInt()}m", style = EstilosGuayaba.LabelMedium.copy(color = color))
            }
        }

        HorizontalDivider(color = EstilosGuayaba.Borde, thickness = 0.5.dp)

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FilaDatoGps("Latitud", "%.6f°".format(lectura.lat))
            FilaDatoGps("Longitud", "%.6f°".format(lectura.lon))
            FilaDatoGps("Altitud", "%.1f m".format(lectura.altitud))
            FilaDatoGps("Prec. vert.", if (lectura.precisionVertical < 900f) "± %.1f m".format(lectura.precisionVertical) else "No data")
            FilaDatoGps("Velocidad", if (lectura.velocidad > 0.3f) "%.1f m/s".format(lectura.velocidad) else "Estático")
            FilaDatoGps("Proveedor", lectura.proveedor.uppercase())
            if (lectura.satelites > 0) {
                FilaDatoGps("Satélites", "${lectura.satelites}")
            }
        }
    }
}

@Composable
private fun DetalleGpsBuscando() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Icon(Icons.Default.GpsNotFixed, contentDescription = null, tint = EstilosGuayaba.Warn, modifier = Modifier.size(32.dp))
        Text(text = "Buscando señal GPS...", style = EstilosGuayaba.BodyLarge.copy(fontWeight = FontWeight.Medium), color = EstilosGuayaba.Warn)
        Text(text = "Ubícate en un espacio abierto", style = EstilosGuayaba.BodyLarge.copy(color = EstilosGuayaba.Muted))
    }
}

@Composable
private fun DetalleGpsError(mensaje: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Icon(Icons.Default.GpsOff, contentDescription = null, tint = EstilosGuayaba.Malo, modifier = Modifier.size(30.dp))
        Text(text = mensaje, style = EstilosGuayaba.BodyLarge, color = EstilosGuayaba.Texto2, textAlign = TextAlign.Center)
    }
}

@Composable
private fun FilaDatoGps(label: String, valor: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(text = label, style = EstilosGuayaba.BodyLarge, color = EstilosGuayaba.Muted)
        Text(text = valor, style = EstilosGuayaba.LabelMedium, color = EstilosGuayaba.Texto)
    }
}

@Composable
fun IndicadorGpsCaptura(estado: EstadoGps, modifier: Modifier = Modifier) {
    val (bgColor, borderColor, icono, texto, subtexto) = when (estado) {
        is EstadoGps.Activo -> {
            val c = colorPorPrecision(estado.lectura.precision)
            quintuple(c.copy(.1f), c.copy(.3f), Icons.Default.GpsFixed, estado.lectura.coordenadasFormateadas, "Alt: %.1f msnm  ·  %s".format(estado.lectura.altitud, estado.lectura.precisionTexto))
        }
        is EstadoGps.Buscando -> quintuple(EstilosGuayaba.Warn.copy(.1f), EstilosGuayaba.Warn.copy(.3f), Icons.Default.GpsNotFixed, "Buscando señal GPS...", "Calculando posición satelital")
        else -> quintuple(EstilosGuayaba.Muted.copy(.1f), EstilosGuayaba.Borde, Icons.Default.GpsOff, "No data", "Verifica la configuración del dispositivo")
    }

    Surface(modifier = modifier.fillMaxWidth(), color = bgColor, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, borderColor)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icono, contentDescription = null, tint = borderColor, modifier = Modifier.size(20.dp))
            Column {
                Text(text = texto, style = EstilosGuayaba.LabelMedium, color = EstilosGuayaba.Texto)
                Text(text = subtexto, style = EstilosGuayaba.BodyLarge, color = EstilosGuayaba.Muted)
            }
        }
    }
}

private data class Quintuple<A,B,C,D,E>(val a:A,val b:B,val c:C,val d:D,val e:E)
private fun <A,B,C,D,E> quintuple(a:A,b:B,c:C,d:D,e:E) = Quintuple(a,b,c,d,e)
private operator fun <A,B,C,D,E> Quintuple<A,B,C,D,E>.component1() = a
private operator fun <A,B,C,D,E> Quintuple<A,B,C,D,E>.component2() = b
private operator fun <A,B,C,D,E> Quintuple<A,B,C,D,E>.component3() = c
private operator fun <A,B,C,D,E> Quintuple<A,B,C,D,E>.component4() = d
private operator fun <A,B,C,D,E> Quintuple<A,B,C,D,E>.component5() = e
