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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geofield.location.EstadoGps
import com.geofield.location.LecturaGps
import com.geofield.theme.GuayabappTypography // CORRECCIÓN: Ruta de importación unificada

// ─── PALETA GUAYABAPP ────────────────────────────────────────────────────────
private val ColorFondo       = Color(0xFF0F1117)
private val ColorSuperficie  = Color(0xFF181C27)
private val ColorSuperficie2 = Color(0xFF1F2436)
private val ColorBorde       = Color(0xFF2A3045)
private val ColorAccent      = Color(0xFF87A922) // Verde Guayaba Maduro
private val ColorBueno       = Color(0xFF4CAF50) 
private val ColorWarn        = Color(0xFFF0A500) 
private val ColorMalo        = Color(0xFFD80032) // Rubí Pulpa
private val ColorMuted       = Color(0xFF6B7A99) 
private val ColorTexto       = Color(0xFFE8EAF2)
private val ColorTexto2      = Color(0xFF9AA3BF)

private fun colorPorPrecision(precision: Float) = when {
    precision <= 3f  -> ColorAccent
    precision <= 5f  -> ColorBueno
    precision <= 10f -> ColorWarn
    else             -> ColorMalo
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
        is EstadoGps.Buscando -> ColorWarn to "Buscando..."
        is EstadoGps.PermisosDenegados -> ColorMalo to "No data"
        is EstadoGps.Error -> ColorMalo to "Error GPS"
        else -> ColorMuted to "No data"
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
            Text(text = texto, style = GuayabappTypography.labelMedium, color = dotColor)
        }
    }
}

@Composable
fun PanelGpsDetalle(estado: EstadoGps, onCerrar: () -> Unit) {
    Surface(
        modifier = Modifier.width(260.dp),
        color = ColorSuperficie,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, ColorBorde),
        shadowElevation = 8.dp
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.GpsFixed, contentDescription = null, tint = ColorAccent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(text = "Estado GPS", style = GuayabappTypography.titleMedium.copy(fontSize = 14.sp), color = ColorTexto)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onCerrar, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = ColorMuted, modifier = Modifier.size(14.dp))
                }
            }

            HorizontalDivider(color = ColorBorde, thickness = 0.5.dp)

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
                Text(text = "Precisión horizontal", style = GuayabappTypography.labelMedium.copy(fontSize = 11.sp, color = ColorMuted))
                Text(text = lectura.precisionTexto, style = GuayabappTypography.titleLarge.copy(fontSize = 22.sp, color = color))
                Text(text = calidad, style = GuayabappTypography.bodyLarge.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = color))
            }
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(progress = { porcentaje }, modifier = Modifier.size(48.dp), color = color, trackColor = ColorBorde, strokeWidth = 3.dp)
                Text(text = "${lectura.precision.toInt()}m", style = GuayabappTypography.labelMedium.copy(fontSize = 11.sp, color = color))
            }
        }

        HorizontalDivider(color = ColorBorde, thickness = 0.5.dp)

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
        Icon(Icons.Default.GpsNotFixed, contentDescription = null, tint = ColorWarn, modifier = Modifier.size(32.dp))
        Text(text = "Buscando señal GPS...", style = GuayabappTypography.bodyLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium), color = ColorWarn)
        Text(text = "Ubícate en un espacio abierto", style = GuayabappTypography.bodyLarge.copy(fontSize = 12.sp), color = ColorMuted)
    }
}

@Composable
private fun DetalleGpsError(mensaje: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Icon(Icons.Default.GpsOff, contentDescription = null, tint = ColorMalo, modifier = Modifier.size(30.dp))
        Text(text = mensaje, style = GuayabappTypography.bodyLarge.copy(fontSize = 12.sp, lineHeight = 16.sp), color = ColorTexto2, textAlign = TextAlign.Center)
    }
}

@Composable
private fun FilaDatoGps(label: String, valor: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(text = label, style = GuayabappTypography.bodyLarge.copy(fontSize = 13.sp), color = ColorMuted)
        Text(text = valor, style = GuayabappTypography.labelMedium.copy(fontSize = 13.sp), color = ColorTexto)
    }
}

@Composable
fun IndicadorGpsCaptura(estado: EstadoGps, modifier: Modifier = Modifier) {
    val (bgColor, borderColor, icono, texto, subtexto) = when (estado) {
        is EstadoGps.Activo -> {
            val c = colorPorPrecision(estado.lectura.precision)
            quintuple(c.copy(.1f), c.copy(.3f), Icons.Default.GpsFixed, estado.lectura.coordenadasFormateadas, "Alt: %.1f msnm  ·  %s".format(estado.lectura.altitud, estado.lectura.precisionTexto))
        }
        is EstadoGps.Buscando -> quintuple(ColorWarn.copy(.1f), ColorWarn.copy(.3f), Icons.Default.GpsNotFixed, "Buscando señal GPS...", "Calculando posición satelital")
        else -> quintuple(ColorMuted.copy(.1f), ColorBorde, Icons.Default.GpsOff, "No data", "Verifica la configuración del dispositivo")
    }

    Surface(modifier = modifier.fillMaxWidth(), color = bgColor, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, borderColor)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icono, contentDescription = null, tint = borderColor, modifier = Modifier.size(20.dp))
            Column {
                Text(text = texto, style = GuayabappTypography.labelMedium.copy(fontSize = 14.sp), color = ColorTexto)
                Text(text = subtexto, style = GuayabappTypography.bodyLarge.copy(fontSize = 12.sp), color = ColorMuted)
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
