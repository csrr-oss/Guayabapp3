package com.geofield.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

// ================================================================================
// ─── ENTIDADES OPTIMIZADAS CON ÍNDICES Y CASCADA (MÓDULO DE PERSISTENCIA) ───────
// ================================================================================

@Entity(
    tableName = "proyectos",
    indices = [Index(value = ["fechaCreacion"])]
)
data class ProyectoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nombre: String, // Nombre asignado al proyecto independiente
    val descripcion: String = "",
    val fechaCreacion: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "puntos",
    foreignKeys = [
        ForeignKey(
            entity = ProyectoEntity::class,
            parentColumns = ["id"],
            childColumns = ["proyectoId"],
            onDelete = ForeignKey.CASCADE // Limpieza automática si se elimina el proyecto
        )
    ],
    indices = [
        Index(value = ["proyectoId"]),
        Index(value = ["tipo"]),
        Index(value = ["lat", "lon"]) // Índice compuesto vital para búsquedas espaciales Bbox instantáneas
    ]
)
data class PuntoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val proyectoId: Long,
    val nombre: String,
    val tipo: String,           // "visual", "muestra", "estructura", "otro"
    val lat: Double,            // Coordenadas absolutas universales WGS84
    val lon: Double,            // Coordenadas absolutas universales WGS84
    val altitud: Double,        // Altitud corregida (msnm) obtenida por hardware GPS
    val precision: Double,      // Margen de tolerancia en metros
    val timestamp: Long = System.currentTimeMillis(),
    val descripcion: String = "",
    val camposJson: String = "{}",   // Plantilla JSON flexible para los formularios variables por tipo
    val completo: Boolean = false,
    val colorHex: String = "#00D084" // Color dinámico asignado según la etiqueta de campo
)

@Entity(
    tableName = "fotos",
    foreignKeys = [
        ForeignKey(
            entity = PuntoEntity::class,
            parentColumns = ["id"],
            childColumns = ["puntoId"],
            onDelete = ForeignKey.CASCADE // Si se elimina el punto, se purga la referencia de la foto
        )
    ],
    indices = [Index(value = ["puntoId"])]
)
data class FotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val puntoId: Long,
    val rutaArchivo: String,     // Ubicación física del archivo JPG en almacenamiento interno
    val lat: Double,            // Coordenadas geoespaciales capturadas al instante (Exif)
    val lon: Double,            // Coordenadas geoespaciales capturadas al instante (Exif)
    val altitud: Double,
    val timestamp: Long = System.currentTimeMillis(),
    val descripcion: String = ""
)

@Entity(
    tableName = "videos",
    foreignKeys = [
        ForeignKey(
            entity = PuntoEntity::class,
            parentColumns = ["id"],
            childColumns = ["puntoId"],
            onDelete = ForeignKey.CASCADE // Evita archivos de video huérfanos en base de datos
        )
    ],
    indices = [Index(value = ["puntoId"])]
)
data class VideoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val puntoId: Long,
    val rutaArchivo: String,
    val lat: Double,
    val lon: Double,
    val duracionSeg: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "mapas_pdf",
    foreignKeys = [
        ForeignKey(
            entity = ProyectoEntity::class,
            parentColumns = ["id"],
            childColumns = ["proyectoId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["proyectoId", "activo"])]
)
data class MapaPdfEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val proyectoId: Long,
    val nombre: String,
    val rutaArchivo: String,     // Ruta local del GeoPDF importado
    val escala: String = "",
    val proyeccion: String = "WGS84",
    val latMin: Double,          // Bounding Box del plano georreferenciado
    val latMax: Double,
    val lonMin: Double,
    val lonMax: Double,
    val widthPx: Int,           // Dimensiones de renderizado de la cuadrícula de píxeles
    val heightPx: Int,
    val activo: Boolean = false
)

@Entity(
    tableName = "tipos_punto",
    foreignKeys = [
        ForeignKey(
            entity = ProyectoEntity::class,
            parentColumns = ["id"],
            childColumns = ["proyectoId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["proyectoId"])]
)
data class TipoPuntoEntity(
    @PrimaryKey val id: String,     // ID único (ej: "visual", "muestra", "hidrologia")
    val proyectoId: Long,
    val nombre: String,             // Nombre legible de la etiqueta administrable
    val colorHex: String,           // Código hexadecimal para pintar el puntero dinámico en el visor
    val icono: String,              // Identificador de recurso gráfico
    val camposJson: String          // Estructura de campos serializada para el constructor dinámico
)

// ================================================================================
// ─── RELACIONES ESTRUCTURALES HÍBRIDAS ──────────────────────────────────────────
// ================================================================================

data class PuntoConMedia(
    @Embedded val punto: PuntoEntity,
    @Relation(parentColumn = "id", entityColumn = "puntoId")
    val fotos: List<FotoEntity>,
    @Relation(parentColumn = "id", entityColumn = "puntoId")
    val videos: List<VideoEntity>
)

// ================================================================================
// ─── INTERFACES DE ACCESO A DATOS (DAOs COMPLETO Y CORREGIDO) ───────────────────
// ================================================================================

@Dao
interface PuntoDao {
    @Transaction
    @Query("SELECT * FROM puntos WHERE proyectoId = :proyectoId ORDER BY timestamp DESC")
    fun observarPuntos(proyectoId: Long): Flow<List<PuntoConMedia>>

    @Transaction
    @Query("SELECT * FROM puntos WHERE proyectoId = :proyectoId AND tipo = :tipo ORDER BY timestamp DESC")
    fun observarPuntosPorTipo(proyectoId: Long, tipo: String): Flow<List<PuntoConMedia>>

    // Filtrado de la nube de puntos dinámico basado en el encuadre Bbox del mapa PDF activo
    @Transaction
    @Query("""
        SELECT * FROM puntos 
        WHERE proyectoId = :proyectoId
          AND lat BETWEEN :latMin AND :latMax
          AND lon BETWEEN :lonMin AND :lonMax
        ORDER BY timestamp DESC
    """)
    fun observarPuntosEnBbox(
        proyectoId: Long,
        latMin: Double, latMax: Double,
        lonMin: Double, lonMax: Double
    ): Flow<List<PuntoConMedia>>

    @Query("SELECT * FROM puntos WHERE id = :id")
    suspend fun obtenerPunto(id: Long): PuntoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertar(punto: PuntoEntity): Long

    @Update
    suspend fun actualizar(punto: PuntoEntity)

    @Query("DELETE FROM puntos WHERE id = :id")
    suspend fun eliminar(id: Long)

    @Query("UPDATE puntos SET completo = :completo WHERE id = :id")
    suspend fun actualizarCompleto(id: Long, completo: Boolean)

    @Query("UPDATE puntos SET camposJson = :json, descripcion = :desc, completo = :completo WHERE id = :id")
    suspend fun actualizarFormulario(id: Long, json: String, desc: String, completo: Boolean)

    @Query("SELECT COUNT(*) FROM puntos WHERE proyectoId = :proyectoId")
    fun contarPuntos(proyectoId: Long): Flow<Int>
}

@Dao
interface FotoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertar(foto: FotoEntity): Long
    @Delete suspend fun eliminar(foto: FotoEntity)
    @Query("SELECT * FROM fotos WHERE puntoId = :puntoId") suspend fun obtenerFotos(puntoId: Long): List<FotoEntity>
}

@Dao
interface VideoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertar(video: VideoEntity): Long
    @Delete suspend fun eliminar(video: VideoEntity)
    @Query("SELECT * FROM videos WHERE puntoId = :puntoId") suspend fun obtenerVideos(puntoId: Long): List<VideoEntity>
}

@Dao
interface MapaPdfDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertar(mapa: MapaPdfEntity): Long
    @Query("SELECT * FROM mapas_pdf WHERE proyectoId = :proyectoId ORDER BY id") fun observarMapas(proyectoId: Long): Flow<List<MapaPdfEntity>>
    @Query("SELECT * FROM mapas_pdf WHERE proyectoId = :proyectoId AND activo = 1 LIMIT 1") suspend fun obtenerActivo(proyectoId: Long): MapaPdfEntity?
    @Query("UPDATE mapas_pdf SET activo = 0 WHERE proyectoId = :proyectoId") suspend fun desactivarTodos(proyectoId: Long)
    @Query("UPDATE mapas_pdf SET activo = 1 WHERE id = :id") suspend fun activar(id: Long)
}

// NUEVO DAO: Agregado para el soporte dinámico y la administración de tipos/etiquetas de punto (+ en la UI)
@Dao
interface TipoPuntoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertarTipo(tipo: TipoPuntoEntity)
    @Query("SELECT * FROM tipos_punto WHERE proyectoId = :proyectoId") fun observarTipos(proyectoId: Long): Flow<List<TipoPuntoEntity>>
    @Query("DELETE FROM tipos_punto WHERE id = :id AND proyectoId = :proyectoId") suspend fun eliminarTipo(id: String, proyectoId: Long)
}

// ================================================================================
// ─── CLASE CENTRAL DE BASE DE DATOS (ROOM COMPILATION ENGINE) ───────────────────
// ================================================================================

@Database(
    entities = [
        ProyectoEntity::class,
        PuntoEntity::class,
        FotoEntity::class,
        VideoEntity::class,
        MapaPdfEntity::class,
        TipoPuntoEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class GeoFieldDatabase : RoomDatabase() {
    abstract fun puntoDao(): PuntoDao
    abstract fun fotoDao(): FotoDao
    abstract fun videoDao(): VideoDao     // Expuesto para la correcta persistencia de clips multimedia de campo
    abstract fun mapaPdfDao(): MapaPdfDao
    abstract fun tipoPuntoDao(): TipoPuntoDao // Expuesto para interactuar con el gestor de etiquetas personalizables

    companion object {
        @Volatile private var INSTANCE: GeoFieldDatabase? = null

        fun getInstance(context: android.content.Context): GeoFieldDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    GeoFieldDatabase::class.java,
                    "geofield.db"
                )
                // .fallbackToDestructiveMigration() // Habilitar únicamente durante el ciclo Alfa de desarrollo local
                .build().also { INSTANCE = it }
            }
    }
}
