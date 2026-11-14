package com.dogfootprint.app.data

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    prettyPrint = false
}

private object AppDataSerializer : Serializer<AppData> {
    override val defaultValue = AppData()

    override suspend fun readFrom(input: InputStream): AppData = try {
        json.decodeFromString(AppData.serializer(), input.readBytes().decodeToString())
    } catch (e: SerializationException) {
        throw CorruptionException("dogfootprint.json is corrupt", e)
    }

    override suspend fun writeTo(t: AppData, output: OutputStream) {
        output.write(json.encodeToString(AppData.serializer(), t).encodeToByteArray())
    }
}

/** One file, one instance (held by [com.dogfootprint.app.App]); all writes go through [updateData]. */
val Context.appDataStore: DataStore<AppData> by dataStore(
    fileName = "dogfootprint.json",
    serializer = AppDataSerializer,
)

private const val MAX_TRIPS = 500

class AppRepository(private val store: DataStore<AppData>) {

    val data: Flow<AppData> = store.data.catch { emit(AppData()) }

    suspend fun update(transform: (AppData) -> AppData): AppData = store.updateData(transform)

    suspend fun saveDog(dog: DogProfile) = update { d ->
        val dogs = if (d.dogs.any { it.id == dog.id }) d.dogs.map { if (it.id == dog.id) dog else it } else d.dogs + dog
        d.copy(dogs = dogs, activeDogId = d.activeDogId ?: dog.id)
    }

    suspend fun deleteDog(id: String) = update { d ->
        d.copy(
            dogs = d.dogs.filterNot { it.id == id },
            activeDogId = d.activeDogId.takeUnless { it == id },
            aboardDogId = d.aboardDogId.takeUnless { it == id },
        )
    }

    suspend fun setActiveDog(id: String?) = update { it.copy(activeDogId = id) }

    suspend fun setAboard(id: String?) = update { it.copy(aboardDogId = id) }

    suspend fun upsertTrip(trip: Trip) = update { d ->
        val trips = if (d.trips.any { it.id == trip.id }) d.trips.map { if (it.id == trip.id) trip else it } else d.trips + trip
        d.copy(trips = trips.takeLast(MAX_TRIPS))
    }

    suspend fun updateSettings(transform: (Settings) -> Settings) = update { it.copy(settings = transform(it.settings)) }

    /** Replaces everything with the given JSON (debug seed / demo reset). */
    suspend fun importJson(raw: String) = update { json.decodeFromString(AppData.serializer(), raw) }
}
