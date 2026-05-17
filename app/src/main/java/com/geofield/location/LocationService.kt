package com.geofield.location

// ─── DEPENDENCIAS (build.gradle) ───────────────────────────────────────────────
// implementation("com.google.android.gms:play-services-location:21.2.0")
// implementation("androidx.lifecycle:lifecycle-service:2.7.0")

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

// ─── MODELO DE LECTURA GPS ────────────────────────────────────────────────────

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
    val esPrecisa: Boolean get() = precision <= 10f    // <10m = aceptable para campo
    val esExcelente: Boolean get() = precision <= 3f   // <3m  = excelente

    val coordenadasFormateadas: String
        get() = "N%.6f° W%.6f°".format(lat, Math.abs(lon))

    val precisionTexto: String
        get() = "±${DecimalFormat("0.#").format(precision)}m"

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

// ─── ESTADO DEL GPS ───────────────────────────────────────────────────────────

sealed class EstadoGps {
    object Inactivo : EstadoGps()
    object Buscando : EstadoGps()
    data class Activo(val lectura: LecturaGps) : EstadoGps()
    data class Error(val mensaje: String) : EstadoGps()
    object PermisosDenegados : EstadoGps()
}

// ─── REPOSITORIO GPS (inyectable en ViewModel) ────────────────────────────────

class LocationRepository(private val context: Context) {

    private val fusedClient = LocationServices.getFusedLocationProviderClient(context)

    private val _estado = MutableStateFlow<EstadoGps>(EstadoGps.Inactivo)
    val estado: StateFlow<EstadoGps> = _estado.asStateFlow()

    // Última lectura válida — útil para captura instantánea de punto
    private var ultimaLectura: LecturaGps? = null

    private var callbackActivo: LocationCallback? = null

    // ── Configuración de solicitud de ubicación ───────────────────────────────

    private fun crearRequest(intervaloMs: Long = 2000L, prioridadAlta: Boolean = true) =
        LocationRequest.Builder(
            if (prioridadAlta) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            intervaloMs
        ).apply {
            setMinUpdateIntervalMillis(1000L)       // mínimo 1 seg entre updates
            setMinUpdateDistanceMeters(0.5f)        // o al moverse 0.5m
            setWaitForAccurateLocation(false)       // no esperar — entregar lo que haya
            setMaxUpdateDelayMillis(5000L)          // máximo delay de batería
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
                    ultimaLectura = lectura
                    _estado.value = EstadoGps.Activo(lectura)
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

        // Obtener última ubicación conocida inmediatamente (sin esperar nueva lectura)
        scope.launch {
            obtenerUltimaUbicacionRapida()
        }
    }

    fun detener() {
        callbackActivo?.let { fusedClient.removeLocationUpdates(it) }
        callbackActivo = null
        _estado.value = EstadoGps.Inactivo
    }

    // ── Obtener posición actual (única, sin stream) ───────────────────────────

    suspend fun obtenerPosicionActual(): LecturaGps? {
        if (!tienePermisos()) return ultimaLectura

        return try {
            withTimeoutOrNull(10_000L) {
                suspendCancellableCoroutine { cont ->
                    val request = crearRequest(intervaloMs = 0L, prioridadAlta = true)
                    val callback = object : LocationCallback() {
                        override fun onLocationResult(result: LocationResult) {
                            fusedClient.removeLocationUpdates(this)
                            val lectura = result.lastLocation?.let { locationALectura(it) }
                                ?: ultimaLectura
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

    // ── Última ubicación del caché del sistema (instantánea) ─────────────────

    @Suppress("MissingPermission")
    private suspend fun obtenerUltimaUbicacionRapida() {
        try {
            fusedClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    val lectura = locationALectura(it)
                    ultimaLectura = lectura
                    // Solo actualizar si aún estamos en "Buscando"
                    if (_estado.value is EstadoGps.Buscando) {
                        _estado.value = EstadoGps.Activo(lectura)
                    }
                }
            }
        } catch (_: Exception) {}
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    fun tienePermisos(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

    fun ultimaLecturaValida(): LecturaGps? = ultimaLectura

    private fun locationALectura(loc: Location) = LecturaGps(
        lat = loc.latitude,
        lon = loc.longitude,
        altitud = if (loc.hasAltitude()) loc.altitude else 0.0,
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

// ─── VIEWMODEL GPS ────────────────────────────────────────────────────────────

class LocationViewModel(private val repo: LocationRepository) : ViewModel() {

    val estado: StateFlow<EstadoGps> = repo.estado

    /** Lectura actual si el GPS está activo */
    val lecturaActual: StateFlow<LecturaGps?> = estado
        .map { if (it is EstadoGps.Activo) it.lectura else null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    /** Precisión como texto para mostrar en UI */
    val precisionTexto: StateFlow<String> = lecturaActual
        .map { it?.precisionTexto ?: "sin señal" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), "sin señal")

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

// ─── ESCRITURA GPS EN EXIF (fotos y videos) ───────────────────────────────────

object ExifGpsWriter {

    /**
     * Escribe las coordenadas GPS en los metadatos Exif de una foto JPEG.
     * Llamar justo después de guardar la foto con CameraX.
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
            exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE,
                "${(lectura.altitud * 100).toLong()}/100")
            exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE_REF,
                if (lectura.altitud >= 0) "0" else "1")

            // Precisión horizontal (DOP aproximado)
            exif.setAttribute(ExifInterface.TAG_GPS_DOP,
                "${(lectura.precision * 10).toInt()}/10")

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
     * Útil para importar fotos ya tomadas con otro dispositivo.
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
                precision = 999f,   // no almacenada en Exif estándar
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

// ─── SERVICIO EN PRIMER PLANO (GPS con app en segundo plano) ─────────────────
// Mantiene el GPS activo cuando el usuario bloquea la pantalla en campo.

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
        return START_STICKY   // el sistema reinicia el servicio si lo mata
    }

    @Suppress("MissingPermission")
    private fun iniciarGps() {
        repo.iniciar(scope, intervaloMs = 3000L)

        // Actualizar la notificación con la precisión actual
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

    // ── Notificación persistente (requerida por Android para servicios foreground)

    private fun iniciarNotificacion() {
        val channel = NotificationChannel(
            CANAL_ID, "GPS GeoField", NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Rastreo GPS activo en campo" }

        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)

        startForeground(NOTIF_ID, construirNotificacion("buscando señal..."))
    }

    private fun actualizarNotificacion(precision: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIF_ID, construirNotificacion("GPS activo · $precision"))
    }

    private fun construirNotificacion(texto: String): Notification {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CANAL_ID)
            .setContentTitle("GeoField")
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
        private const val CANAL_ID = "geofield_gps"
        private const val NOTIF_ID = 1001
        const val ACCION_INICIAR = "com.geofield.START_GPS"
        const val ACCION_DETENER = "com.geofield.STOP_GPS"

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

// ─── COMPOSABLE: INDICADOR GPS EN UI ─────────────────────────────────────────

// Uso en cualquier pantalla:
//
// val locationVm: LocationViewModel = viewModel()
// val estado by locationVm.estado.collectAsState()
//
// IndicadorGps(estado = estado)
//
// Para capturar un punto:
// val lectura = locationVm.capturarPosicion()
// lectura?.let { viewModel.agregarPunto(it.lat, it.lon, it.altitud, it.precision, ...) }
