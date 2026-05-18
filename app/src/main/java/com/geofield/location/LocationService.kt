package com.geofield.location

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.media.ExifInterface
import android.os.*
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.*
import com.google.android.gms.location.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.text.DecimalFormat

// ================================================================================
// ─── MODELO DE LECTURA GPS (FORMATO DE COORDENADAS ESPACIADO) ───────────────────
// ================================================================================

data class LecturaGps(
    val lat: Double,
    val lon: Double,
    val altitud: Double,
    val precision: Float,          // metros (horizontal accuracy)
    val precisionVertical: Float,  // metros (vertical accuracy, API 26+)
    val velocidad: Float,          // m/s
    val rumbo: Float,              // grados (0-360, norte = 0)
    val timestamp: Long,
    val proveedor: String,         // "fused", "gps", "network"
    val satelites: Int = 0         // número de satélites (si disponible)
) {
    val esPrecisa: Boolean get() = precision <= 10f    // <10m = aceptable para campo [cite: 1]
    val esExcelente: Boolean get() = precision <= 3f   // <3m  = excelente [cite: 1]

    // CORRECCIÓN: Espacio reglamentario explícito entre la letra y el número coordinado
    val coordenadasFormateadas: String
        get() = "N %.6f°  W %.6f°".format(lat, Math.abs(lon))

    val precisionTexto: String
        get() = "± ${DecimalFormat("0.#").format(precision)} m"

    /** Convierte a grados/minutos/segundos para metadatos Exif */
    fun latExif(): Pair<String, String> = decimalADms(lat) to if (lat >= 0) "N" else "S"
    fun lonExif(): Pair<String, String> = decimalADms(lon) to if (lon >= 0) "E" else "W"

    private fun decimalADms(decimal: Double): String {
        val abs = Math.abs(decimal)
        val grados = abs.toInt()
        val minutos = ((abs - grados) * 60).toInt()
        val segundos = ((abs - grados) * 60 - minutos) * 60
        return "$grados/1,$minutos/1,${(segundos * 1000).toInt()}/1000"
    }
}

// ================================================================================
// ─── ESTADO DEL GPS ─────────────────────────────────────────────────────────────
// ================================================================================

sealed class EstadoGps {
    object Inactivo : EstadoGps()
    object Buscando : EstadoGps()
    data class Activo(val lectura: LecturaGps) : EstadoGps()
    data class Error(val mensaje: String) : EstadoGps()
    object PermisosDenegados : EstadoGps()
}

// ================================================================================
// ─── REPOSITORIO GPS ROBUSTO (FILTRO DE ALTITUD PARA TRABAJO EN CAMPO) ──────────
// ================================================================================

class LocationRepository(private val context: Context) {

    private val fusedClient = LocationServices.getFusedLocationProviderClient(context)

    private val _estado = MutableStateFlow<EstadoGps>(EstadoGps.Inactivo)
    val estado: StateFlow<EstadoGps> = _estado.asStateFlow()

    private var ultimaLectura: LecturaGps? = null
    private var callbackActivo: LocationCallback? = null

    // ── Configuración de solicitud de ubicación optimizada para hardware ──────

    private fun crearRequest(intervaloMs: Long = 2000L, prioridadAlta: Boolean = true) =
        LocationRequest.Builder(
            if (prioridadAlta) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            intervaloMs
        ).apply {
            setMinUpdateIntervalMillis(1000L)       // Mínimo 1 seg entre updates
            setMinUpdateDistanceMeters(0.5f)        // Update al moverse medio metro
            
            // CORRECCIÓN: Forzamos al chip a esperar lecturas satelitales tridimensionales reales
            setWaitForAccurateLocation(true)        
            setMaxUpdateDelayMillis(4000L)          
        }.build()

    // ── Iniciar escucha GPS ───────────────────────────────────────────────────

    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    fun iniciar(scope: CoroutineScope, intervaloMs: Long = 2000L) {
        if (!tienePermisos()) {
            _estado.value = EstadoGps.PermisosDenegados
            return
        }

        _estado.value = EstadoGps.Buscando

        val callback = object : LocationCallback() {
            override fun onLocationResult(resultado: LocationResult) {
                resultado.lastLocation?.let { location ->
                    val lectura = locationALectura(location)
                    
                    // CORRECCIÓN: Si la altitud viene rota en 0.0m por triangulación celular transitoria,
                    // preservamos de manera inteligente la elevación real previa calculada por hardware.
                    if (lectura.altitud == 0.0 && ultimaLectura != null && ultimaLectura!!.altitud > 0.0) {
                        val lecturaCorregida = lectura.copy(altitud = ultimaLectura!!.altitud)
                        ultimaLectura = lecturaCorregida
                        _estado.value = EstadoGps.Activo(lecturaCorregida)
                    } else {
                        ultimaLectura = lectura
                        _estado.value = EstadoGps.Activo(lectura)
                    }
                }
            }

            override fun onLocationAvailability(disponibilidad: LocationAvailability) {
                if (!disponibilidad.isLocationAvailable && _estado.value !is EstadoGps.Activo) {
                    _estado.value = EstadoGps.Buscando
                }
            }
        }

        callbackActivo = callback

        fusedClient.requestLocationUpdates(
            crearRequest(intervaloMs),
            callback,
            Looper.getMainLooper()
        )

        scope.launch {
            obtenerUltimaUbicacionRapida()
        }
    }

    fun detener() {
        callbackActivo?.let { fusedClient.removeLocationUpdates(it) }
        callbackActivo = null
        _estado.value = EstadoGps.Inactivo
    }

    suspend fun obtenerPosicionActual(): LecturaGps? {
        if (!tienePermisos()) return ultimaLectura

        return try {
            withTimeoutOrNull(10_000L) {
                suspendCancellableCoroutine { cont ->
                    val request = crearRequest(intervaloMs = 0L, prioridadAlta = true)
                    val callback = object : LocationCallback() {
                        override fun onLocationResult(result: LocationResult) {
                            fusedClient.removeLocationUpdates(this)
                            val lectura = result.lastLocation?.let { locationALectura(it) } ?: ultimaLectura
                            cont.resume(lectura) {}
                        }
                    }
                    @Suppress("MissingPermission")
                    fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
                    cont.invokeOnCancellation { fusedClient.removeLocationUpdates(callback) }
                }
            } ?: ultimaLectura
        } catch (e: Exception) {
            ultimaLectura
        }
    }

    @Suppress("MissingPermission")
    private suspend fun obtenerUltimaUbicacionRapida() {
        try {
            fusedClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    val lectura = locationALectura(it)
                    ultimaLectura = lectura
                    if (_estado.value is EstadoGps.Buscando) {
                        _estado.value = EstadoGps.Activo(lectura)
                    }
                }
            }
        } catch (_: Exception) {}
    }

    fun tienePermisos(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

    fun ultimaLecturaValida(): LecturaGps? = ultimaLectura

    private fun locationALectura(loc: Location): LecturaGps {
        // CORRECCIÓN ALTITUD: Validamos la elevación topográfica real filtrando falsos positivos en 0.0
        val altitudReal = if (loc.hasAltitude() && loc.altitude != 0.0) loc.altitude else {
            if (ultimaLectura != null && ultimaLectura!!.altitud > 0.0) ultimaLectura!!.altitud else 0.0
        }

        return LecturaGps(
            lat = loc.latitude,
            lon = loc.longitude,
            altitud = altitudReal,
            precision = if (loc.hasAccuracy()) loc.accuracy else 999f,
            precisionVertical = if (Build.VERSION.SDK_INT >= 26 && loc.hasVerticalAccuracy())
                loc.verticalAccuracyMeters else 999f,
            velocidad = if (loc.hasSpeed()) loc.speed else 0f,
            rumbo = if (loc.hasBearing()) loc.bearing else 0f,
            timestamp = loc.time,
            proveedor = loc.provider ?: "fused",
            satelites = loc.extras?.getInt("satellites") ?: 0
        )
    }
}

// ================================================================================
// ─── VIEWMODEL GPS (CORRECCIÓN OPTIMIZACIÓN TEXTO "NO DATA") ────────────────────
// ================================================================================

class LocationViewModel(private val repo: LocationRepository) : ViewModel() {

    val estado: StateFlow<EstadoGps> = repo.estado

    /** Lectura actual si el GPS está activo */
    val lecturaActual: StateFlow<LecturaGps?> = estado
        .map { if (it is EstadoGps.Activo) it.lectura else null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    /** CORRECCIÓN: Precisión compacta como texto para ahorrar pantalla mediante "No data" */
    val precisionTexto: StateFlow<String> = lecturaActual
        .map { it?.precisionTexto ?: "No data" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), "No data")

    fun iniciarGps() {
        @Suppress("MissingPermission")
        repo.iniciar(viewModelScope, intervaloMs = 2000L)
    }

    fun detenerGps() = repo.detener()

    /** Captura la posición actual para un punto de campo */
    suspend fun capturarPosicion(): LecturaGps? = repo.obtenerPosicionActual()

    override fun onCleared() {
        super.onCleared()
        repo.detener()
    }
}

// ================================================================================
// ─── ESCRITURA GPS EN METADATOS EXIF (FOTOS) ────────────────────────────────────
// ================================================================================

object ExifGpsWriter {

    /**
     * Escribe las coordenadas GPS en los metadatos Exif de una foto JPEG.
     */
    fun escribirEnFoto(rutaFoto: String, lectura: LecturaGps) {
        try {
            val exif = ExifInterface(rutaFoto)

            // Latitud
            val (latDms, latRef) = lectura.latExif()
            exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, latDms)
            exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, latRef)

            // Longitud
            val (lonDms, lonRef) = lectura.lonExif()
            exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, lonDms)
            exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, lonRef)

            // Altitud
            exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE, "${(lectura.altitud * 100).toLong()}/100")
            exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE_REF, if (lectura.altitud >= 0) "0" else "1")

            // Precisión horizontal
            exif.setAttribute(ExifInterface.TAG_GPS_DOP, "${(lectura.precision * 10).toInt()}/10")

            // Timestamp GPS
            val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
                timeInMillis = lectura.timestamp
            }
            exif.setAttribute(ExifInterface.TAG_GPS_DATESTAMP,
                "%04d:%02d:%02d".format(cal.get(java.util.Calendar.YEAR),
                    cal.get(java.util.Calendar.MONTH) + 1,
                    cal.get(java.util.Calendar.DAY_OF_MONTH)))
            exif.setAttribute(ExifInterface.TAG_GPS_TIMESTAMP,
                "%02d/1,%02d/1,%02d/1".format(
                    cal.get(java.util.Calendar.HOUR_OF_DAY),
                    cal.get(java.util.Calendar.MINUTE),
                    cal.get(java.util.Calendar.SECOND)))

            exif.saveAttributes()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Lee las coordenadas GPS desde los metadatos Exif de una foto.
     */
    fun leerDeFoto(rutaFoto: String): LecturaGps? {
        return try {
            val exif = ExifInterface(rutaFoto)
            val latLon = FloatArray(2)
            if (!exif.getLatLong(latLon)) return null

            LecturaGps(
                lat = latLon[0].toDouble(),
                lon = latLon[1].toDouble(),
                altitud = exif.getAttributeDouble(ExifInterface.TAG_GPS_ALTITUDE, 0.0),
                precision = 999f,
                precisionVertical = 999f,
                velocidad = 0f,
                rumbo = 0f,
                timestamp = System.currentTimeMillis(),
                proveedor = "exif"
            )
        } catch (e: Exception) {
            null
        }
    }
}

// ================================================================================
// ─── SERVICIO EN PRIMER PLANO (REGISTRO EN SEGUNDO PLANO - MARCA GUAYABAPP) ──────
// ================================================================================

class LocationForegroundService : LifecycleService() {

    private lateinit var repo: LocationRepository
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        repo = LocationRepository(applicationContext)
        iniciarNotificacion()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACCION_INICIAR -> iniciarGps()
            ACCION_DETENER -> { detenerGps(); stopSelf() }
        }
        return START_STICKY
    }

    @Suppress("MissingPermission")
    private fun iniciarGps() {
        repo.iniciar(scope, intervaloMs = 3000L)
        scope.launch {
            repo.estado.collect { estado ->
                if (estado is EstadoGps.Activo) {
                    actualizarNotificacion(estado.lectura.precisionTexto)
                }
            }
        }
    }

    private fun detenerGps() {
        repo.detener()
        scope.cancel()
    }

    private fun iniciarNotificacion() {
        // CORRECCIÓN: Nombre oficial del canal adaptado a Guayabapp [cite: 2]
        val channel = NotificationChannel(
            CANAL_ID, "GPS Guayabapp", NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Rastreo satelital activo en campo" }

        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)

        startForeground(NOTIF_ID, construirNotificacion("No data"))
    }

    private fun actualizarNotificacion(precision: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIF_ID, construirNotificacion("Rastreo de campo activo · $precision"))
    }

    private fun construirNotificacion(texto: String): Notification {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // CORRECCIÓN: Titular oficial unificado para Guayabapp [cite: 2]
        return NotificationCompat.Builder(this, CANAL_ID)
            .setContentTitle("Guayabapp")
            .setContentText(texto)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        detenerGps()
    }

    companion object {
        private const val CANAL_ID = "guayabapp_gps"
        private const val NOTIF_ID = 1001
        const val ACCION_INICIAR = "com.guayabapp.START_GPS"
        const val ACCION_DETENER = "com.guayabapp.STOP_GPS"

        fun iniciar(context: Context) {
            val intent = Intent(context, LocationForegroundService::class.java)
                .apply { action = ACCION_INICIAR }
            ContextCompat.startForegroundService(context, intent)
        }

        fun detener(context: Context) {
            val intent = Intent(context, LocationForegroundService::class.java)
                .apply { action = ACCION_DETENER }
            context.startService(intent)
        }
    }
}
