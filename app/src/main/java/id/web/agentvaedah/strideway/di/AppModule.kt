package id.web.agentvaedah.strideway.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import id.web.agentvaedah.strideway.data.db.ActivityDao
import id.web.agentvaedah.strideway.data.db.ChatDao
import id.web.agentvaedah.strideway.data.db.RouteDao
import id.web.agentvaedah.strideway.data.db.SensorDao
import id.web.agentvaedah.strideway.data.db.StridewayDatabase
import id.web.agentvaedah.strideway.data.db.TrackPointDao
import id.web.agentvaedah.strideway.data.remote.HttpClientFactory
import id.web.agentvaedah.strideway.data.remote.sync.DisabledRemoteSync
import id.web.agentvaedah.strideway.data.remote.sync.RemoteSyncApi
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): StridewayDatabase =
        Room.databaseBuilder(context, StridewayDatabase::class.java, "strideway.db").build()

    @Provides fun activityDao(db: StridewayDatabase): ActivityDao = db.activityDao()
    @Provides fun trackPointDao(db: StridewayDatabase): TrackPointDao = db.trackPointDao()
    @Provides fun routeDao(db: StridewayDatabase): RouteDao = db.routeDao()
    @Provides fun chatDao(db: StridewayDatabase): ChatDao = db.chatDao()
    @Provides fun sensorDao(db: StridewayDatabase): SensorDao = db.sensorDao()

    @Provides
    @Singleton
    fun httpClient(): HttpClient = HttpClientFactory.create()

    @Provides
    @Singleton
    fun remoteSync(): RemoteSyncApi = DisabledRemoteSync()

    /** Outlives screens; used by the recording engine and BLE hub. */
    @Provides
    @Singleton
    @AppScope
    fun appScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
