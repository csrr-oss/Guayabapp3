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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geofield.location.EstadoGps
import com.geofield.location.LecturaGps

// ─── PALETA ───────────────────────────────────────────────────────────────────

private val ColorFondo      = Color(0xFF0F1117)
private val ColorSuperficie = Color(0xFF181C27)
private val ColorSuperficie2= Color(0xFF1F2436)
private val ColorBorde      = Color(0xFF2A3045)
private val ColorAccent     = Color(0xFF00D084)  // GPS excelente  <3m
private val ColorBueno      = Color(0xFF4CAF50)  // GPS bueno      3–5m
private val ColorWarn       = Color(0xFFF0A500)  // GPS aceptable  5–10m
private val ColorMalo       = Color(0xFFFF6B35)  // GPS malo       >10m
private val ColorMuted      = Color(0xFF6B7A99)  // Sin señal
private val ColorTexto      = Color(0xFFE8EAF2)
private val ColorTexto2     = Color(0xFF9AA3BF)

private fun colorPorPrecision(precision: Float) = when {
    precision <= 3f  -> ColorAccent
    precision <= 5f  -> ColorBueno
    precision <= 10f -> ColorWarn
    else             -> ColorMalo
}

// ─── BADGE GPS COMPACTO (para TopBar) ────────────────────────────────────────

@Composable
fun GpsBadgeTopBar(estado: EstadoGps) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_gps")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "gps_dot_alpha"
    )

    val (dotColor, texto) = when (estado) {
        is EstadoGps.Activo -> colorPorPrecision(estado.lectura.precision) to estado.lectura.precisionTexto
        is EstadoGps.Buscando -> ColorWarn to "buscando..."
        is EstadoGps.PermisosDenegados -> ColorMalo to "sin permiso"
        is EstadoGps.Error -> ColorMalo to "error GPS"
        else -> ColorMuted to "GPS inactivo"
    }

    Surface(
        color = dotColor.copy(alpha = 0.1f),
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, dotColor.copy(alpha = 0.25f))
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            // Punto pulsante
            val pulseAlpha = if (estado is EstadoGps.Buscando) alpha else 1f
            Box(
                Modifier
                    .size(6.dp)
                    .background(dotColor.copy(alpha = pulseAlpha), CircleShape)
            )
            Text(
                texto,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    color = dotColor
                )
            )
        }
    }
}

// ─── PANEL GPS EXPANDIDO (toca el badge para ver detalles) ───────────────────

@Composable
fun PanelGpsDetalle(
    estado: EstadoGps,
    onCerrar: () -> Unit
) {
    Surface(
        modifier = Modifier.width(240.dp),
        color = ColorSuperficie,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, ColorBorde),
        shadowElevation = 8.dp
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {

            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.GpsFixed, contentDescription = null,
                    tint = ColorAccent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Estado GPS", style = TextStyle(fontSize = 12.sp,
                    fontWeight = FontWeight.Medium, color = ColorTexto))
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onCerrar, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, contentDescription = null,
                        tint = ColorMuted, modifier = Modifier.size(14.dp))
                }
            }

            HorizontalDivider(color = ColorBorde, thickness = 0.5.dp)

            when (estado) {
                is EstadoGps.Activo -> DetalleGpsActivo(estado.lectura)
                is EstadoGps.Buscando -> DetalleGpsBuscando()
                is EstadoGps.PermisosDenegados -> DetalleGpsError("Permisos de ubicación denegados.\nVe a Ajustes → GeoField → Permisos.")
                is EstadoGps.Error -> DetalleGpsError(estado.mensaje)
                else -> DetalleGpsError("GPS inactivo")
            }
        }
    }
}

@Composable
private fun DetalleGpsActivo(lectura: LecturaGps) {
    // Barra de calidad de señal
    val color = colorPorPrecision(lectura.precision)
    val calidad = when {
        lectura.esExcelente -> "Excelente"
        lectura.esPrecisa   -> "Buena"
        lectura.precision <= 20f -> "Aceptable"
        else -> "Baja"
    }
    val porcentaje = ((20f - lectura.precision.coerceAtMost(20f)) / 20f)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {

        // Precisión destacada
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Precisión horizontal",
                    style = TextStyle(fontSize = 9.sp, color = ColorMuted,
                        fontFamily = FontFamily.Monospace))
                Text(lectura.precisionTexto,
                    style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Medium,
                        color = color, fontFamily = FontFamily.Monospace))
                Text(calidad, style = TextStyle(fontSize = 10.sp, color = color))
            }
            // Indicador circular de calidad
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { porcentaje },
                    modifier = Modifier.size(46.dp),
                    color = color,
                    trackColor = ColorBorde,
                    strokeWidth = 3.dp
                )
                Text(
                    "${lectura.precision.toInt()}m",
                    style = TextStyle(fontSize = 10.sp, color = color,
                        fontFamily = FontFamily.Monospace)
                )
            }
        }

        HorizontalDivider(color = ColorBorde, thickness = 0.5.dp)

        // Grid de datos
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            FilaDatoGps("Latitud",   "%.6f°".format(lectura.lat))
            FilaDatoGps("Longitud",  "%.6f°".format(lectura.lon))
            FilaDatoGps("Altitud",   "%.1f m".format(lectura.altitud))
            FilaDatoGps("Prec. vert.", if (lectura.precisionVertical < 900f)
                "±%.1f m".format(lectura.precisionVertical) else "n/d")
            FilaDatoGps("Velocidad", if (lectura.velocidad > 0.3f)
                "%.1f m/s".format(lectura.velocidad) else "estático")
            FilaDatoGps("Proveedor", lectura.proveedor)
            if (lectura.satelites > 0) {
                FilaDatoGps("Satélites", "${lectura.satelites}")
            }
        }
    }
}

@Composable
private fun DetalleGpsBuscando() {
    val infiniteTransition = rememberInfiniteTransition(label = "searching")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)),
        label = "spin"
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Default.GpsNotFixed, contentDescription = null,
            tint = ColorWarn,
            modifier = Modifier.size(32.dp).scale(1f))
        Text("Buscando señal GPS...",
            style = TextStyle(fontSize = 11.sp, color = ColorWarn))
        Text("Sal al exterior para mejor recepción",
            style = TextStyle(fontSize = 10.sp, color = ColorMuted))
    }
}

@Composable
private fun DetalleGpsError(mensaje: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Default.GpsOff, contentDescription = null,
            tint = ColorMalo, modifier = Modifier.size(28.dp))
        Text(mensaje,
            style = TextStyle(fontSize = 10.sp, color = ColorTexto2,
                lineHeight = 15.sp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun FilaDatoGps(label: String, valor: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = TextStyle(fontSize = 10.sp, color = ColorMuted))
        Text(valor, style = TextStyle(fontSize = 10.sp, color = ColorTexto,
            fontFamily = FontFamily.Monospace))
    }
}

// ─── INDICADOR GPS INLINE (para panel de captura de punto) ───────────────────

@Composable
fun IndicadorGpsCaptura(
    estado: EstadoGps,
    modifier: Modifier = Modifier
) {
    val (bgColor, borderColor, icono, texto, subtexto) = when (estado) {
        is EstadoGps.Activo -> {
            val c = colorPorPrecision(estado.lectura.precision)
            quintuple(
                c.copy(.1f), c.copy(.3f),
                Icons.Default.GpsFixed,
                estado.lectura.coordenadasFormateadas,
                "Alt: %.0fm · %s".format(estado.lectura.altitud, estado.lectura.precisionTexto)
            )
        }
        is EstadoGps.Buscando -> quintuple(
            ColorWarn.copy(.1f), ColorWarn.copy(.3f),
            Icons.Default.GpsNotFixed,
            "Buscando señal GPS...",
            "Espera o sal al exterior"
        )
        else -> quintuple(
            ColorMuted.copy(.1f), ColorBorde,
            Icons.Default.GpsOff,
            "GPS no disponible",
            "Verifica los permisos de ubicación"
        )
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = bgColor,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(icono, contentDescription = null,
                tint = borderColor, modifier = Modifier.size(18.dp))
            Column {
                Text(texto, style = TextStyle(fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace, color = ColorTexto))
                Text(subtexto, style = TextStyle(fontSize = 9.sp, color = ColorMuted))
            }
        }
    }
}

// Helper para destructuring de 5 valores
private data class Quintuple<A,B,C,D,E>(val a:A,val b:B,val c:C,val d:D,val e:E)
private fun <A,B,C,D,E> quintuple(a:A,b:B,c:C,d:D,e:E) = Quintuple(a,b,c,d,e)
private operator fun <A,B,C,D,E> Quintuple<A,B,C,D,E>.component1() = a
private operator fun <A,B,C,D,E> Quintuple<A,B,C,D,E>.component2() = b
private operator fun <A,B,C,D,E> Quintuple<A,B,C,D,E>.component3() = c
private operator fun <A,B,C,D,E> Quintuple<A,B,C,D,E>.component4() = d
private operator fun <A,B,C,D,E> Quintuple<A,B,C,D,E>.component5() = e
