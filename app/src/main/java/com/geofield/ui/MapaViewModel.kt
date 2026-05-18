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
import java.lang.ref.WeakReference

// ─── EXTENSIÓN PARA CONTROLAR EL MODO DE VISUALIZACIÓN DE CAPAS ─────────────
enum class ModoCapaBase { OSM_ESTANDAR, ESRI_SATELITE, GEO_PDF }

// ─── ESTADO UI ROBUSTO (ADAPTADO A CONTROLES COMPACTOS) ──────────────────────

data class MapaUiState(
    val puntos: List<PuntoConMedia> = emptyList(),
    val mapaActivo: MapaPdfEntity? = null,
    val todosLosMapas: List<MapaPdfEntity> = emptyList(),
    val puntoSeleccionado: PuntoConMedia? = null,
    val filtroTipo: String? = null,     // null = todos
    val modoCapaBase: ModoCapaBase = ModoCapaBase.ESRI_SATELITE, // ESRI Satélite por defecto para Colombia 
    val exportando: Boolean = false,
    val mensajeSnack: String? = null
)

// ─── VIEWMODEL UNIFICADO Y PROTEGIDO DE FUGAS DE MEMORIA ─────────────────────

class MapaViewModel(
    private val db: GeoFieldDatabase,
    private val proyectoId: Long,
    context: Context
) : ViewModel() {

    // Evitamos Memory Leaks encapsulando el contexto en una referencia débil
    private val contextRef = WeakReference(context.applicationContext)

    private val puntoDao = db.puntoDao()
    private val mapaPdfDao = db.mapaPdfDao()
    private val fotoDao = db.fotoDao()

    private val _estado = MutableStateFlow(MapaUiState())
    val estado: StateFlow<MapaUiState> = _estado.asStateFlow()

    private var puntosJob: kotlinx.coroutines.Job? = null

    init {
        observarMapaActivo()
        observarPuntos()
    }

    // ── Observación reactiva de datos geoespaciales ────────────────────────────

    private fun observarMapaActivo() = viewModelScope.launch {
        mapaPdfDao.observarMapas(proyectoId)
            .distinctUntilChanged()
            .collect { mapas ->
                val activo = mapas.firstOrNull { it.activo } ?: mapas.firstOrNull()
                _estado.update { it.copy(todosLosMapas = mapas, mapaActivo = activo) }
                observarPuntos()
            }
    }

    private fun observarPuntos() {
        puntosJob?.cancel()
        puntosJob = viewModelScope.launch {
            val mapa = _estado.value.mapaActivo
            val capaBase = _estado.value.modoCapaBase

            // Si el modo de capa base es PDF, filtramos por Bbox; si es satélite global, exponemos la nube completa 
            val flow = if (capaBase == ModoCapaBase.GEO_PDF && mapa != null) {
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

    // ── Alternador unificado de capas cartográficas (Un solo botón flotante) ──

    fun alternarSiguienteCapa() {
        _estado.update { currentState ->
            val siguienteCapa = when (currentState.modoCapaBase) {
                ModoCapaBase.ESRI_SATELITE -> ModoCapaBase.OSM_ESTANDAR
                ModoCapaBase.OSM_ESTANDAR -> {
                    if (currentState.mapaActivo != null) ModoCapaBase.GEO_PDF 
                    else ModoCapaBase.ESRI_SATELITE
                }
                ModoCapaBase.GEO_PDF -> ModoCapaBase.ESRI_SATELITE
            }
            currentState.copy(modoCapaBase = siguienteCapa)
        }
        observarPuntos() // Sincroniza la nube de puntos con la estrategia de mapa base activa 
        
        val nombreCapa = when (_estado.value.modoCapaBase) {
            ModoCapaBase.ESRI_SATELITE -> "Satélite (ESRI)" 
            ModoCapaBase.OSM_ESTANDAR -> "Mapa Base (OSM)" 
            ModoCapaBase.GEO_PDF -> "Plano GeoPDF: ${_estado.value.mapaActivo?.nombre}" 
        }
        snack("Capa base: $nombreCapa")
    }

    // ── Gestión procedural de puntos técnicos de control ──────────────────────

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
            colorHex = colorHexPorTipo(tipo) // Soluciona el error de sobrecarga de nombres de color 
        )
        val id = puntoDao.insertar(punto)
        observarPuntos()
        seleccionarPunto(id)
        snack("Punto capturado · GPS ± ${"%.1f".format(precision)} m")
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
            snack("Cambios guardados exitosamente")
        }

    fun cambiarTipoPunto(puntoId: Long, nuevoTipo: String) = viewModelScope.launch {
        val punto = puntoDao.obtenerPunto(puntoId) ?: return@launch
        puntoDao.actualizar(
            punto.copy(tipo = nuevoTipo, colorHex = colorHexPorTipo(nuevoTipo))
        )
        observarPuntos()
    }

    fun eliminarPunto(puntoId: Long) = viewModelScope.launch {
        fotoDao.obtenerFotos(puntoId).forEach { foto ->
            File(foto.rutaArchivo).takeIf { it.exists() }?.delete()
        }
        puntoDao.eliminar(puntoId)
        if (_estado.value.puntoSeleccionado?.punto?.id == puntoId) {
            _estado.update { it.copy(puntoSeleccionado = null) }
        }
        snack("Punto eliminado")
    }

    // ── Persistencia Multimedia ───────────────────────────────────────────────

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

    // ── Control de mapas GeoPDF locales ───────────────────────────────────────

    fun cambiarMapaActivo(mapaId: Long) = viewModelScope.launch {
        mapaPdfDao.desactivarTodos(proyectoId)
        mapaPdfDao.activar(mapaId)
        val mapa = _estado.value.todosLosMapas.find { it.id == mapaId }
        _estado.update { it.copy(mapaActivo = mapa, modoCapaBase = ModoCapaBase.GEO_PDF) }
        observarPuntos()
        snack("Mapa: ${mapa?.nombre} · Ajustando grilla...")
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
        observarMapaActivo()
        snack("PDF importado con éxito: $nombre")
    }

    fun filtrarPorTipo(tipo: String?) {
        _estado.update { it.copy(filtroTipo = tipo) }
        observarPuntos()
    }

    // ── Exportación con Inyección de Marca Oficial de Guayabapp ────────────────

    fun exportarKml(
        tiposIncluidos: List<String> = listOf("visual", "muestra", "estructura", "otro"),
        incluirFotos: Boolean = true,
        incluirPoligonos: Boolean = false
    ) = viewModelScope.launch {
        val currentContext = contextRef.get() ?: return@launch
        _estado.update { it.copy(exportando = true) }
        try {
            val puntosFiltrados = _estado.value.puntos.filter { it.punto.tipo in tiposIncluidos }

            val kmlFile = KmlExporter.exportar(
                context = currentContext,
                proyectoNombre = "Proyecto_Guayabapp", // Identidad oficial grabada en el KML 
                puntos = puntosFiltrados,
                incluirFotos = incluirFotos,
                incluirPoligonos = incluirPoligonos
            )

            compartirArchivo(currentContext, kmlFile)
            snack("KML exportado · ${puntosFiltrados.size} registros")
        } catch (e: Exception) {
            snack("Error de procesamiento: ${e.message}")
        } finally {
            _estado.update { it.copy(exportando = false) }
        }
    }

    private fun compartirArchivo(ctx: Context, file: File) {
        val uri = FileProvider.getUriForFile(
            ctx,
            "${ctx.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.google-earth.kml+xml"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(Intent.createChooser(intent, "Compartir KML Técnico"))
    }

    // CORRECCIÓN DE SOBRECARGA: Renombrada a colorHexPorTipo para no colisionar con firmas Android e Int 
    private fun colorHexPorTipo(tipo: String) = when (tipo) {
        "visual"      -> "#87A922" // Verde Guayaba Maduro Corporativo 
        "muestra"     -> "#7C6AF7" // Morado Munsell 
        "estructura"  -> "#F0A500" // Ámbar Geológico 
        else          -> "#6B7A99" // Gris mitigado 
    }

    fun snack(msg: String) {
        _estado.update { it.copy(mensajeSnack = msg) }
    }

    fun snackConsumido() {
        _estado.update { it.copy(mensajeSnack = null) }
    }
}
