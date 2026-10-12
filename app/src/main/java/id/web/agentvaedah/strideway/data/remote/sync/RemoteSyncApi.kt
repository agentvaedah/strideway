package id.web.agentvaedah.strideway.data.remote.sync

import id.web.agentvaedah.strideway.data.db.ActivityEntity
import id.web.agentvaedah.strideway.data.db.TrackPointEntity

/**
 * Contract for the PRD phase 4 multi-tenant backend (FastAPI + PostGIS behind
 * track.agentvaedah.web.id). The server does not exist yet, so the app binds
 * [DisabledRemoteSync] and every activity stays local with syncState = LOCAL_ONLY.
 */
interface RemoteSyncApi {
    val enabled: Boolean

    suspend fun upload(activity: ActivityEntity, points: List<TrackPointEntity>): Result<String>

    companion object {
        const val BASE_URL = "https://track.agentvaedah.web.id"
    }
}

class DisabledRemoteSync : RemoteSyncApi {
    override val enabled: Boolean = false

    override suspend fun upload(activity: ActivityEntity, points: List<TrackPointEntity>): Result<String> =
        Result.failure(UnsupportedOperationException("Sinkronisasi server belum tersedia"))
}
