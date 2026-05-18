package com.geofield.geo

import android.content.Context
import android.graphics.PointF
import com.geofield.data.MapaPdfEntity
import com.geofield.data.PuntoConMedia
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

// ─── TRANSFORMACIÓN GPS → PÍXEL PDF ─────────────────────────────────────────

object GeoPdfTransform {

    /**
     * Convierte coordenadas GPS (WGS84) a píxeles dentro del PDF.
     * Si el PDF usa otra proyeccion (ej: MAGNA-SIRGAS / CTM12),
     * se debe reproyectar primero con proj4j antes de llamar esto.
     */
    fun gpsAPixel(lat: Double, lon: Double, mapa: MapaPdfEntity): PointF {
        val px = ((lon - mapa.lonMin) / (mapa.lonMax - mapa.lonMin)) * mapa.widthPx
        val py = ((mapa.latMax - lat) / (mapa.latMax - mapa.latMin)) * mapa.heightPx
        return PointF(px.toFloat(), py.toFloat())
    }

    /**
     * Inverso: píxel del PDF → coordenadas GPS.
     * Útil para cuando el usuario toca el mapa y queremos la coordenada real.
     */
    fun pixelAGps(px: Float, py: Float, mapa: MapaPdfEntity): Pair<Double, Double> {
        val lon = mapa.lonMin + (px / mapa.widthPx) * (mapa.lonMax - mapa.lonMin)
        val lat = mapa.latMax - (py / mapa.heightPx) * (mapa.latMax - mapa.latMin)
        return Pair(lat, lon)
    }

    /** Verifica si un punto GPS cae dentro del bbox de un mapa */
    fun dentroDeBbox(lat: Double, lon: Double, mapa: MapaPdfEntity): Boolean =
        lat in mapa.latMin..mapa.latMax && lon in mapa.lonMin..mapa.lonMax

    /**
     * Escala de visualización: transforma píxel PDF al canvas de pantalla,
     * considerando el zoom y desplazamiento actual del visor.
     */
    fun pdfACanvas(
        pdfPoint: PointF,
        zoomFactor: Float,
        offsetX: Float,
        offsetY: Float
    ): PointF = PointF(
        pdfPoint.x * zoomFactor + offsetX,
        pdfPoint.y * zoomFactor + offsetY
    )
}

// ─── EXPORTADOR KML MODIFICADO (IDENTIDAD + METADATOS COMPLETO) ───────────────

object KmlExporter {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
        .apply { timeZone = TimeZone.getTimeZone("UTC") }

    private val fileNameFormat = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault())

    /**
     * Genera el archivo KML con todos los puntos seleccionados.
     */
    fun exportar(
        context: Context,
        proyectoNombre: String,
        puntos: List<PuntoConMedia>,
        incluirFotos: Boolean = true,
        incluirPoligonos: Boolean = false
    ): File {
        val timestamp = fileNameFormat.format(Date())
        val fileName = "${proyectoNombre.replace(" ", "_")}_$timestamp.kml"
        val outputFile = File(context.cacheDir, fileName)

        outputFile.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write(kmlHeader(proyectoNombre))
            writer.write(estilosDinamicosPorPuntos(puntos)) // CORRECCIÓN: Estilos dinámicos expansibles

            // Carpetas por tipo para organización en Google Earth
            val tiposPresentes = puntos.map { it.punto.tipo }.distinct()
            tiposPresentes.forEach { tipo ->
                val puntosDeTipo = puntos.filter { it.punto.tipo == tipo }
                writer.write("""
                    |  <Folder>
                    |    <name>${tipo.replaceFirstChar { it.uppercase() }} (${puntosDeTipo.size})</name>
                """.trimMargin())

                puntosDeTipo.forEach { puntoCM ->
                    writer.write(placemarkDePunto(puntoCM, incluirFotos))
                }

                writer.write("  </Folder>\n")
            }

            writer.write(kmlFooter())
        }

        return outputFile
    }

    fun previsualizarKml(
        proyectoNombre: String,
        puntos: List<PuntoConMedia>,
        maxPuntos: Int = 3
    ): String {
        val muestra = puntos.take(maxPuntos)
        val sb = StringBuilder()
        sb.append(kmlHeader(proyectoNombre))
        sb.append(estilosDinamicosPorPuntos(puntos))
        muestra.forEach { sb.append(placemarkDePunto(it, incluirFotos = false)) }
        if (puntos.size > maxPuntos) {
            sb.append("  \n")
        }
        sb.append(kmlFooter())
        return sb.toString()
    }

    // ── Bloques KML con Inyección de Marca Oficial ───────────────────────────

    private fun kmlHeader(proyectoNombre: String) = """
        <?xml version="1.0" encoding="UTF-8"?>
        <kml xmlns="http://www.opengis.net/kml/2.2"
             xmlns:gx="http://www.google.com/kml/ext/2.2">
          <Document>
            <name>$proyectoNombre</name>
            <description>Exportado desde Guayabapp · ${dateFormat.format(Date())}</description>

    """.trimIndent() + "\n"

    /**
     * CORRECCIÓN: Genera los estilos KML de forma dinámica extrayendo el colorHex real de los puntos.
     * Esto permite que las nuevas etiquetas del botón (+) se pinten con su color correspondiente en el SIG.
     */
    private fun estilosDinamicosPorPuntos(puntos: List<PuntoConMedia>): String {
        val tiposMapeados = puntos.map { it.punto.tipo to it.punto.colorHex }.distinctBy { it.first }
        
        return buildString {
            tiposMapeados.forEach { (tipo, colorHex) ->
                // Limpiamos el # por si viene formateado de Compose
                val hexLimpio = colorHex.replace("#", "")
                // KML usa formato AABBGGRR (invertido de RGB tradicional)
                val kmlColor = if (hexLimpio.length == 6) {
                    "ff${hexLimpio.takeLast(2)}${hexLimpio.substring(2, 4)}${hexLimpio.substring(0, 2)}".lowercase()
                } else {
                    "ff00d084" // Fallback verde guayaba seguro
                }
                
                append("""
                  <Style id="estilo_$tipo">
                    <IconStyle>
                      <color>$kmlColor</color>
                      <scale>1.1</scale>
                      <Icon><href>http://maps.google.com/mapfiles/kml/shapes/placemark_circle.png</href></Icon>
                    </IconStyle>
                    <LabelStyle><scale>0.9</scale></LabelStyle>
                    <BalloonStyle>
                      <text><![CDATA[<b>$[name]</b><br/>$[description]]]></text>
                    </BalloonStyle>
                  </Style>

                """.trimIndent())
            }
        }
    }

    private fun placemarkDePunto(puntoCM: PuntoConMedia, incluirFotos: Boolean): String {
        val p = puntoCM.punto
        val timestamp = dateFormat.format(Date(p.timestamp))
        val descripcionHtml = buildDescripcionHtml(puntoCM, incluirFotos)

        // CORRECCIÓN: Si el GPS marca altitud 0, evitamos enterrar el marcador en relieves de alta montaña (Andes)
        val modoAltitud = if (p.altitud == 0.0) "clampToGround" else "absolute"

        return buildString {
            append("  <Placemark>\n")
            append("    <name>${escaparXml(p.nombre)}</name>\n")
            append("    <description><![CDATA[$descripcionHtml]]></description>\n")
            append("    <styleUrl>#estilo_${p.tipo}</styleUrl>\n")
            append("    <TimeStamp><when>$timestamp</when></TimeStamp>\n")

            // Metadatos extendidos para tablas internas SIG
            append("    <ExtendedData>\n")
            append("      <Data name=\"tipo\"><value>${p.tipo}</value></Data>\n")
            append("      <Data name=\"precision_m\"><value>${p.precision}</value></Data>\n")
            append("      <Data name=\"altitud_m\"><value>${p.altitud}</value></Data>\n")
            if (p.descripcion.isNotBlank()) {
                append("      <Data name=\"descripcion\"><value>${escaparXml(p.descripcion)}</value></Data>\n")
            }
            append("    </ExtendedData>\n")

            // Coordenadas estructuradas en el estándar KML (lon, lat, alt)
            append("    <Point>\n")
            append("      <altitudeMode>$modoAltitud</altitudeMode>\n")
            append("      <coordinates>${p.lon},${p.lat},${p.altitud}</coordinates>\n")
            append("    </Point>\n")
            append("  </Placemark>\n")
        }
    }

    private fun buildDescripcionHtml(puntoCM: PuntoConMedia, incluirFotos: Boolean): String {
        val p = puntoCM.punto
        return buildString {
            append("<table style='font-family:sans-serif;font-size:13px;border-collapse:collapse;' border='1' cellpadding='5'>")
            append("<tr style='background-color:#0f1117;color:#e8eaf2;'><th colspan='2'>Datos de Campo — Guayabapp</th></tr>")
            append("<tr><td><b>Tipo de Registro</b></td><td>${p.tipo.replaceFirstChar { it.uppercase() }}</td></tr>")
            append("<tr><td><b>Latitud (WGS84)</b></td><td>${p.lat}</td></tr>")
            append("<tr><td><b>Longitud (WGS84)</b></td><td>${p.lon}</td></tr>")
            append("<tr><td><b>Altitud</b></td><td>${if (p.altitud == 0.0) "Calculada por terreno" else "${p.altitud} msnm"}</td></tr>")
            append("<tr><td><b>Margen de Precisión</b></td><td>± ${p.precision} m</td></tr>")
            
            if (p.descripcion.isNotBlank()) {
                append("<tr><td colspan='2'><b>Observaciones Técnicas:</b><br/>${p.descripcion}</td></tr>")
            }
            if (incluirFotos && puntoCM.fotos.isNotEmpty()) {
                puntoCM.fotos.forEach { foto ->
                    append("<tr><td colspan='2' align='center'><img src='file://${foto.rutaArchivo}' width='250' style='border-radius:4px;'/></td></tr>")
                }
            }
            append("</table>")
        }
    }

    private fun kmlFooter() = "  </Document>\n</kml>\n"

    private fun escaparXml(texto: String) = texto
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
