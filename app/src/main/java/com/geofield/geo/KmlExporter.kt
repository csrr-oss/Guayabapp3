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
     * Si el PDF usa otra proyección (ej: MAGNA-SIRGAS / CTM12),
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

// ─── EXPORTADOR KML ───────────────────────────────────────────────────────────

object KmlExporter {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
        .apply { timeZone = TimeZone.getTimeZone("UTC") }

    private val fileNameFormat = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault())

    /**
     * Genera el archivo KML con todos los puntos seleccionados.
     * Lo guarda en cache del app para compartir vía FileProvider.
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
            writer.write(estilosPorTipo())

            // Carpetas por tipo para mejor organización en Google Earth
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

    /** Genera el KML como String para vista previa en la app */
    fun previsualizarKml(
        proyectoNombre: String,
        puntos: List<PuntoConMedia>,
        maxPuntos: Int = 3
    ): String {
        val muestra = puntos.take(maxPuntos)
        val sb = StringBuilder()
        sb.append(kmlHeader(proyectoNombre))
        muestra.forEach { sb.append(placemarkDePunto(it, incluirFotos = false)) }
        if (puntos.size > maxPuntos) {
            sb.append("  <!-- ... ${puntos.size - maxPuntos} puntos más -->\n")
        }
        sb.append(kmlFooter())
        return sb.toString()
    }

    // ── Bloques KML ──────────────────────────────────────────────────────────

    private fun kmlHeader(proyectoNombre: String) = """
        <?xml version="1.0" encoding="UTF-8"?>
        <kml xmlns="http://www.opengis.net/kml/2.2"
             xmlns:gx="http://www.google.com/kml/ext/2.2">
          <Document>
            <name>$proyectoNombre</name>
            <description>Exportado desde GeoField · ${dateFormat.format(Date())}</description>

    """.trimIndent() + "\n"

    private fun estilosPorTipo(): String {
        val tipos = mapOf(
            "visual"     to "00D084",
            "muestra"    to "7C6AF7",
            "estructura" to "F0A500",
            "otro"       to "6B7A99"
        )
        return buildString {
            tipos.forEach { (tipo, colorHex) ->
                // KML usa formato AABBGGRR (invertido de RGB)
                val kmlColor = "ff${colorHex.takeLast(2)}${colorHex.substring(2,4)}${colorHex.substring(0,2)}".lowercase()
                append("""
                  <Style id="estilo_$tipo">
                    <IconStyle>
                      <color>$kmlColor</color>
                      <scale>1.0</scale>
                      <Icon><href>http://maps.google.com/mapfiles/kml/paddle/wht-circle.png</href></Icon>
                    </IconStyle>
                    <LabelStyle><scale>0.8</scale></LabelStyle>
                    <BalloonStyle>
                      <text><![CDATA[<b>${'$'}[name]</b><br/>${'$'}[description]]]></text>
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

        return buildString {
            append("  <Placemark>\n")
            append("    <name>${escaparXml(p.nombre)}</name>\n")
            append("    <description><![CDATA[$descripcionHtml]]></description>\n")
            append("    <styleUrl>#estilo_${p.tipo}</styleUrl>\n")
            append("    <TimeStamp><when>$timestamp</when></TimeStamp>\n")

            // Metadatos extendidos (visibles en Google Earth)
            append("    <ExtendedData>\n")
            append("      <Data name=\"tipo\"><value>${p.tipo}</value></Data>\n")
            append("      <Data name=\"precision_m\"><value>${p.precision}</value></Data>\n")
            append("      <Data name=\"altitud_m\"><value>${p.altitud}</value></Data>\n")
            if (p.descripcion.isNotBlank()) {
                append("      <Data name=\"descripcion\"><value>${escaparXml(p.descripcion)}</value></Data>\n")
            }
            append("    </ExtendedData>\n")

            // Coordenadas: KML usa lon,lat,alt
            append("    <Point>\n")
            append("      <altitudeMode>absolute</altitudeMode>\n")
            append("      <coordinates>${p.lon},${p.lat},${p.altitud}</coordinates>\n")
            append("    </Point>\n")
            append("  </Placemark>\n")
        }
    }

    private fun buildDescripcionHtml(puntoCM: PuntoConMedia, incluirFotos: Boolean): String {
        val p = puntoCM.punto
        return buildString {
            append("<table style='font-family:sans-serif;font-size:12px'>")
            append("<tr><td><b>Tipo</b></td><td>${p.tipo}</td></tr>")
            append("<tr><td><b>Lat</b></td><td>${p.lat}</td></tr>")
            append("<tr><td><b>Lon</b></td><td>${p.lon}</td></tr>")
            append("<tr><td><b>Altitud</b></td><td>${p.altitud} m</td></tr>")
            append("<tr><td><b>Precisión</b></td><td>±${p.precision} m</td></tr>")
            if (p.descripcion.isNotBlank()) {
                append("<tr><td colspan='2'>${p.descripcion}</td></tr>")
            }
            if (incluirFotos && puntoCM.fotos.isNotEmpty()) {
                puntoCM.fotos.forEach { foto ->
                    append("<tr><td colspan='2'><img src='${foto.rutaArchivo}' width='200'/></td></tr>")
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
}
