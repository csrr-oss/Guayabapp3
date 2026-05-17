package com.geofield.ui

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geofield.data.*
import com.geofield.geo.GeoPdfTransform
import com.geofield.geo.KmlExporter
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

// ─── ESTADO UI ────────────────────────────────────────────────────────────────

data class MapaUiState(
    val puntos: List<PuntoConMedia> = emptyList(),
    val mapaActivo: MapaPdfEntity? = null,
    val todosLosMapas: List<MapaPdfEntity> = emptyList(),
    val puntoSeleccionado: PuntoConMedia? = null,
    val filtroTipo: String? = null,     // null = todos
    val exportando: Boolean = false,
    val mensajeSnack: String? = null
)

// ─── VIEWMODEL ────────────────────────────────────────────────────────────────

class MapaViewModel(
    private val db: GeoFieldDatabase,
    private val proyectoId: Long,
    private val context: Context
) : ViewModel() {

    private val puntoDao = db.puntoDao()
    private val mapaPdfDao = db.mapaPdfDao()
    private val fotoDao = db.fotoDao()

    private val _estado = MutableStateFlow(MapaUiState())
    val estado: StateFlow<MapaUiState> = _estado.asStateFlow()

    init {
        observarMapaActivo()
        observarPuntos()
        cargarMapas()
    }

    // ── Observación reactiva ──────────────────────────────────────────────────

    private fun observarMapaActivo() = viewModelScope.launch {
        // Cuando cambia el mapa activo, recarga puntos del bbox de ese mapa
        mapaPdfDao.observarMapas(proyectoId)
            .distinctUntilChanged()
            .collect { mapas ->
                val activo = mapas.firstOrNull { it.activo } ?: mapas.firstOrNull()
                _estado.update { it.copy(todosLosMapas = mapas, mapaActivo = activo) }
                observarPuntos()
            }
    }

    private var puntosJob: kotlinx.coroutines.Job? = null

    private fun observarPuntos() {
        puntosJob?.cancel()
        puntosJob = viewModelScope.launch {
            val mapa = _estado.value.mapaActivo
            val flow = if (mapa != null) {
                puntoDao.observarPuntosEnBbox(
                    proyectoId,
                    mapa.latMin, mapa.latMax,
                    mapa.lonMin, mapa.lonMax
                )
            } else {
                puntoDao.observarPuntos(proyectoId)
            }

            flow.collect { puntos ->
                val filtrado = _estado.value.filtroTipo
                    ?.let { tipo -> puntos.filter { it.punto.tipo == tipo } }
                    ?: puntos
                _estado.update { it.copy(puntos = filtrado) }
            }
        }
    }

    private fun cargarMapas() = viewModelScope.launch {
        val activo = mapaPdfDao.obtenerActivo(proyectoId)
        _estado.update { it.copy(mapaActivo = activo) }
    }

    // ── Gestión de puntos ─────────────────────────────────────────────────────

    fun seleccionarPunto(puntoId: Long?) {
        val punto = if (puntoId != null) {
            _estado.value.puntos.find { it.punto.id == puntoId }
        } else null
        _estado.update { it.copy(puntoSeleccionado = punto) }
    }

    fun agregarPunto(
        lat: Double, lon: Double, altitud: Double, precision: Double,
        tipo: String, nombre: String
    ) = viewModelScope.launch {
        val punto = PuntoEntity(
            proyectoId = proyectoId,
            nombre = nombre,
            tipo = tipo,
            lat = lat,
            lon = lon,
            altitud = altitud,
            precision = precision,
            colorHex = colorPorTipo(tipo)
        )
        val id = puntoDao.insertar(punto)
        // Seleccionar automáticamente para mostrar el formulario
        observarPuntos()
        seleccionarPunto(id)
        snack("Punto capturado · GPS ±${precision}m")
    }

    fun actualizarDescripcion(puntoId: Long, descripcion: String, camposJson: String) =
        viewModelScope.launch {
            val punto = puntoDao.obtenerPunto(puntoId) ?: return@launch
            puntoDao.actualizar(
                punto.copy(
                    descripcion = descripcion,
                    camposJson = camposJson,
                    completo = descripcion.isNotBlank()
                )
            )
            snack("Cambios guardados")
        }

    fun cambiarTipoPunto(puntoId: Long, nuevoTipo: String) = viewModelScope.launch {
        val punto = puntoDao.obtenerPunto(puntoId) ?: return@launch
        puntoDao.actualizar(
            punto.copy(tipo = nuevoTipo, colorHex = colorPorTipo(nuevoTipo))
        )
    }

    fun eliminarPunto(puntoId: Long) = viewModelScope.launch {
        // Eliminar fotos del disco antes de borrar de BD
        fotoDao.obtenerFotos(puntoId).forEach { foto ->
            File(foto.rutaArchivo).takeIf { it.exists() }?.delete()
        }
        puntoDao.eliminar(puntoId)
        if (_estado.value.puntoSeleccionado?.punto?.id == puntoId) {
            _estado.update { it.copy(puntoSeleccionado = null) }
        }
        snack("Punto eliminado")
    }

    // ── Fotos ─────────────────────────────────────────────────────────────────

    fun agregarFoto(
        puntoId: Long, rutaArchivo: String,
        lat: Double, lon: Double, altitud: Double, descripcion: String = ""
    ) = viewModelScope.launch {
        fotoDao.insertar(
            FotoEntity(
                puntoId = puntoId,
                rutaArchivo = rutaArchivo,
                lat = lat, lon = lon, altitud = altitud,
                descripcion = descripcion
            )
        )
    }

    // ── Mapas PDF ─────────────────────────────────────────────────────────────

    fun cambiarMapaActivo(mapaId: Long) = viewModelScope.launch {
        mapaPdfDao.desactivarTodos(proyectoId)
        mapaPdfDao.activar(mapaId)
        val mapa = _estado.value.todosLosMapas.find { it.id == mapaId }
        _estado.update { it.copy(mapaActivo = mapa) }
        observarPuntos()
        snack("Mapa: ${mapa?.nombre} · reproyectando puntos…")
    }

    fun cargarNuevoPdf(
        nombre: String, rutaArchivo: String, escala: String, proyeccion: String,
        latMin: Double, latMax: Double, lonMin: Double, lonMax: Double,
        widthPx: Int, heightPx: Int
    ) = viewModelScope.launch {
        mapaPdfDao.insertar(
            MapaPdfEntity(
                proyectoId = proyectoId,
                nombre = nombre,
                rutaArchivo = rutaArchivo,
                escala = escala,
                proyeccion = proyeccion,
                latMin = latMin, latMax = latMax,
                lonMin = lonMin, lonMax = lonMax,
                widthPx = widthPx, heightPx = heightPx
            )
        )
        snack("PDF cargado: $nombre")
    }

    // ── Filtros ───────────────────────────────────────────────────────────────

    fun filtrarPorTipo(tipo: String?) {
        _estado.update { it.copy(filtroTipo = tipo) }
        observarPuntos()
    }

    // ── Exportación KML ───────────────────────────────────────────────────────

    fun exportarKml(
        tiposIncluidos: List<String> = listOf("visual", "muestra", "estructura", "otro"),
        incluirFotos: Boolean = true,
        incluirPoligonos: Boolean = false
    ) = viewModelScope.launch {
        _estado.update { it.copy(exportando = true) }
        try {
            val puntosFiltrados = _estado.value.puntos
                .filter { it.punto.tipo in tiposIncluidos }

            val kmlFile = KmlExporter.exportar(
                context = context,
                proyectoNombre = "Proyecto_GeoField",
                puntos = puntosFiltrados,
                incluirFotos = incluirFotos,
                incluirPoligonos = incluirPoligonos
            )

            compartirArchivo(kmlFile)
            snack("KML exportado · ${puntosFiltrados.size} puntos")
        } catch (e: Exception) {
            snack("Error al exportar: ${e.message}")
        } finally {
            _estado.update { it.copy(exportando = false) }
        }
    }

    private fun compartirArchivo(file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.google-earth.kml+xml"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Compartir KML"))
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun colorPorTipo(tipo: String) = when (tipo) {
        "visual"     -> "#00D084"
        "muestra"    -> "#7C6AF7"
        "estructura" -> "#F0A500"
        else         -> "#6B7A99"
    }

    fun snack(msg: String) {
        _estado.update { it.copy(mensajeSnack = msg) }
    }

    fun snackConsumido() {
        _estado.update { it.copy(mensajeSnack = null) }
    }
}
