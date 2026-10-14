package id.web.agentvaedah.strideway.data.repo

import id.web.agentvaedah.strideway.data.db.PlaceDao
import id.web.agentvaedah.strideway.data.db.PlaceEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** Bookmarked places used as route start or end points. */
@Singleton
class PlaceRepository @Inject constructor(private val dao: PlaceDao) {
    val places: Flow<List<PlaceEntity>> = dao.observeAll()

    suspend fun save(name: String, lat: Double, lon: Double): Long =
        dao.upsert(PlaceEntity(name = name.trim().ifBlank { "Lokasi tersimpan" }.take(60), lat = lat, lon = lon, createdMs = System.currentTimeMillis()))

    suspend fun delete(id: Long) = dao.delete(id)
}
