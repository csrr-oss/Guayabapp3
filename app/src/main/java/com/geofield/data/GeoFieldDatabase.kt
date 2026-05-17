package com.geofield.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

// ─── ENTIDADES ────────────────────────────────────────────────────────────────

@Entity(tableName = "puntos")
data class PuntoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val proyectoId: Long,
    val nombre: String,
    val tipo: String,           // "visual", "muestra", "estructura", "otro"
    val lat: Double,
    val lon: Double,
    val altitud: Double,
    val precision: Double,      // metros
    val timestamp: Long = System.currentTimeMillis(),
    val descripcion: String = "",
    val camposJson: String = "{}",   // campos específicos del tipo serializados
    val completo: Boolean = false,
    val colorHex: String = "#00D084"
)

@Entity(tableName = "fotos")
data class FotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val puntoId: Long,
    val rutaArchivo: String,
    val lat: Double,            // coordenadas propias de la foto (Exif)
    val lon: Double,
    val altitud: Double,
    val timestamp: Long = System.currentTimeMillis(),
    val descripcion: String = ""
)

@Entity(tableName = "videos")
data class VideoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val puntoId: Long,
    val rutaArchivo: String,
    val lat: Double,
    val lon: Double,
    val duracionSeg: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "mapas_pdf")
data class MapaPdfEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val proyectoId: Long,
    val nombre: String,
    val rutaArchivo: String,
    val escala: String = "",        // ej: "1:25.000"
    val proyeccion: String = "WGS84",
    val latMin: Double,
    val latMax: Double,
    val lonMin: Double,
    val lonMax: Double,
    val widthPx: Int,               // dimensiones del PDF en píxeles
    val heightPx: Int,
    val activo: Boolean = false
)

@Entity(tableName = "proyectos")
data class ProyectoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nombre: String,
    val descripcion: String = "",
    val fechaCreacion: Long = System.currentTimeMillis()
)

@Entity(tableName = "tipos_punto")
data class TipoPuntoEntity(
    @PrimaryKey val id: String,     // "visual", "muestra", etc.
    val proyectoId: Long,
    val nombre: String,
    val colorHex: String,
    val icono: String,
    val camposJson: String          // lista de CampoFormulario serializada
)

// ─── RELACIONES ───────────────────────────────────────────────────────────────

data class PuntoConMedia(
    @Embedded val punto: PuntoEntity,
    @Relation(parentColumn = "id", entityColumn = "puntoId")
    val fotos: List<FotoEntity>,
    @Relation(parentColumn = "id", entityColumn = "puntoId")
    val videos: List<VideoEntity>
)

// ─── DAOs ─────────────────────────────────────────────────────────────────────

@Dao
interface PuntoDao {

    @Transaction
    @Query("SELECT * FROM puntos WHERE proyectoId = :proyectoId ORDER BY timestamp DESC")
    fun observarPuntos(proyectoId: Long): Flow<List<PuntoConMedia>>

    @Transaction
    @Query("SELECT * FROM puntos WHERE proyectoId = :proyectoId AND tipo = :tipo ORDER BY timestamp DESC")
    fun observarPuntosPorTipo(proyectoId: Long, tipo: String): Flow<List<PuntoConMedia>>

    // Puntos dentro del bbox de un mapa PDF
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

    // Marcar como completo
    @Query("UPDATE puntos SET completo = :completo WHERE id = :id")
    suspend fun actualizarCompleto(id: Long, completo: Boolean)

    // Actualizar campos del formulario
    @Query("UPDATE puntos SET camposJson = :json, descripcion = :desc, completo = :completo WHERE id = :id")
    suspend fun actualizarFormulario(id: Long, json: String, desc: String, completo: Boolean)

    @Query("SELECT COUNT(*) FROM puntos WHERE proyectoId = :proyectoId")
    fun contarPuntos(proyectoId: Long): Flow<Int>
}

@Dao
interface FotoDao {
    @Insert suspend fun insertar(foto: FotoEntity): Long
    @Delete suspend fun eliminar(foto: FotoEntity)
    @Query("SELECT * FROM fotos WHERE puntoId = :puntoId") suspend fun obtenerFotos(puntoId: Long): List<FotoEntity>
}

@Dao
interface MapaPdfDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertar(mapa: MapaPdfEntity): Long
    @Query("SELECT * FROM mapas_pdf WHERE proyectoId = :proyectoId ORDER BY id") fun observarMapas(proyectoId: Long): Flow<List<MapaPdfEntity>>
    @Query("SELECT * FROM mapas_pdf WHERE proyectoId = :proyectoId AND activo = 1 LIMIT 1") suspend fun obtenerActivo(proyectoId: Long): MapaPdfEntity?
    @Query("UPDATE mapas_pdf SET activo = 0 WHERE proyectoId = :proyectoId") suspend fun desactivarTodos(proyectoId: Long)
    @Query("UPDATE mapas_pdf SET activo = 1 WHERE id = :id") suspend fun activar(id: Long)
}

// ─── BASE DE DATOS ─────────────────────────────────────────────────────────────

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
    abstract fun mapaPdfDao(): MapaPdfDao

    companion object {
        @Volatile private var INSTANCE: GeoFieldDatabase? = null

        fun getInstance(context: android.content.Context): GeoFieldDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    GeoFieldDatabase::class.java,
                    "geofield.db"
                ).build().also { INSTANCE = it }
            }
    }
}
