package id.web.agentvaedah.strideway.data.repo

import id.web.agentvaedah.strideway.data.db.ChatDao
import id.web.agentvaedah.strideway.data.db.ChatMessageEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(private val dao: ChatDao) {
    fun messages(): Flow<List<ChatMessageEntity>> = dao.observeAll()

    suspend fun history(): List<ChatMessageEntity> = dao.all()

    suspend fun add(role: String, content: String, provider: String?): Long =
        dao.insert(ChatMessageEntity(role = role, content = content, createdMs = System.currentTimeMillis(), provider = provider))

    suspend fun clear() = dao.clear()
}
