package id.web.agentvaedah.strideway.data.repo

import id.web.agentvaedah.strideway.core.gear.ComponentType
import id.web.agentvaedah.strideway.core.gear.Gear
import id.web.agentvaedah.strideway.core.gear.GearComponent
import id.web.agentvaedah.strideway.core.gear.GearEvent
import id.web.agentvaedah.strideway.core.gear.GearEventKind
import id.web.agentvaedah.strideway.core.gear.GearRide
import id.web.agentvaedah.strideway.core.gear.GearStatus
import id.web.agentvaedah.strideway.core.model.Sport
import id.web.agentvaedah.strideway.data.db.ComponentEntity
import id.web.agentvaedah.strideway.data.db.ComponentEventEntity
import id.web.agentvaedah.strideway.data.db.GearDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

data class GarageItem(val entity: ComponentEntity, val status: GearStatus, val events: List<ComponentEventEntity>)

data class Garage(val items: List<GarageItem>, val totalRideKm: Double) {
    val active: List<GarageItem> get() = items.filter { it.entity.active }
}

@Singleton
class GearRepository @Inject constructor(
    private val dao: GearDao,
    activities: ActivityRepository,
) {
    /** Parts with their wear, recomputed when a ride is saved or a part is serviced. */
    val garage: Flow<Garage> = combine(dao.observeComponents(), dao.observeEvents(), activities.activities()) { comps, events, acts ->
        val rides = acts.map { GearRide(it.sport, it.startMs, it.distanceM) }
        val byComponent = events.groupBy { it.componentId }
        val items = comps.mapNotNull { c ->
            val type = runCatching { ComponentType.valueOf(c.type) }.getOrNull() ?: return@mapNotNull null
            val ev = byComponent[c.id].orEmpty()
            val status = Gear.status(
                GearComponent(c.id, type, c.installMs, c.kmAtInstall, c.serviceIntervalKm),
                ev.mapNotNull { e -> runCatching { GearEventKind.valueOf(e.kind) }.getOrNull()?.let { GearEvent(it, e.atMs) } },
                rides,
            )
            GarageItem(c, status, ev.sortedByDescending { it.atMs })
        }
        Garage(items, rides.filter { it.sport == Sport.RIDE }.sumOf { it.distanceM } / 1000.0)
    }.flowOn(Dispatchers.Default)

    suspend fun save(c: ComponentEntity): Long = dao.upsert(c)

    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun log(componentId: Long, kind: GearEventKind, atMs: Long, note: String?) {
        dao.insertEvent(ComponentEventEntity(componentId = componentId, kind = kind.name, atMs = atMs, note = note?.trim()?.ifBlank { null }))
    }
}
