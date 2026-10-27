package app.gibiflix.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

data class SeriesRow(
    val series: String,
    val count: Int,
    val readCount: Int,
    val coverId: String,
)

data class CollectionRow(
    val id: Long,
    val name: String,
    val count: Int,
    val coverId: String?,
)

@Dao
interface ComicDao {
    @Query("SELECT * FROM comics")
    fun observeAll(): Flow<List<ComicEntity>>

    @Query("SELECT * FROM comics")
    suspend fun all(): List<ComicEntity>

    @Query("SELECT * FROM comics WHERE id = :id")
    fun observe(id: String): Flow<ComicEntity?>

    @Query("SELECT * FROM comics WHERE id = :id")
    suspend fun get(id: String): ComicEntity?

    @Query("SELECT * FROM comics WHERE sourceHash = :hash LIMIT 1")
    suspend fun byHash(hash: String): ComicEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(comic: ComicEntity)

    @Upsert
    suspend fun upsert(comic: ComicEntity)

    @Query("DELETE FROM comics WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE comics SET read = :read, updatedAt = :now WHERE id = :id")
    suspend fun setRead(id: String, read: Boolean, now: Long = System.currentTimeMillis())

    @Query("UPDATE comics SET opened = 1, lastReadAt = :now WHERE id = :id")
    suspend fun markOpened(id: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE comics SET status = :status, prepareProgress = :progress WHERE id = :id")
    suspend fun setStatus(id: String, status: String, progress: Int)

    @Query(
        """SELECT series, COUNT(*) AS count, SUM(CASE WHEN read THEN 1 ELSE 0 END) AS readCount,
           (SELECT c2.id FROM comics c2 WHERE c2.series = comics.series ORDER BY c2.volume, c2.numberSort, c2.title LIMIT 1) AS coverId
           FROM comics WHERE series IS NOT NULL AND series != '' GROUP BY series ORDER BY series COLLATE NOCASE"""
    )
    fun observeSeries(): Flow<List<SeriesRow>>
}

@Dao
interface CollectionDao {
    @Query(
        """SELECT c.id, c.name, COUNT(i.comicId) AS count,
           (SELECT i2.comicId FROM collection_items i2 WHERE i2.collectionId = c.id ORDER BY i2.addedAt LIMIT 1) AS coverId
           FROM collections c LEFT JOIN collection_items i ON i.collectionId = c.id
           GROUP BY c.id ORDER BY c.name COLLATE NOCASE"""
    )
    fun observeRows(): Flow<List<CollectionRow>>

    @Query("SELECT * FROM collections ORDER BY name COLLATE NOCASE")
    suspend fun all(): List<CollectionEntity>

    @Query("SELECT * FROM collection_items")
    suspend fun allItems(): List<CollectionItemEntity>

    @Query("SELECT comicId FROM collection_items WHERE collectionId = :id")
    fun observeComicIds(id: Long): Flow<List<String>>

    @Query("SELECT collectionId FROM collection_items WHERE comicId = :comicId")
    suspend fun collectionsOf(comicId: String): List<Long>

    @Insert
    suspend fun insert(c: CollectionEntity): Long

    @Upsert
    suspend fun upsert(c: CollectionEntity)

    @Query("UPDATE collections SET name = :name, updatedAt = :now WHERE id = :id")
    suspend fun rename(id: Long, name: String, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM collections WHERE id = :id")
    suspend fun delete(id: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addItem(item: CollectionItemEntity)

    @Query("DELETE FROM collection_items WHERE collectionId = :collectionId AND comicId = :comicId")
    suspend fun removeItem(collectionId: Long, comicId: String)

    @Query("DELETE FROM collection_items WHERE comicId = :comicId")
    suspend fun removeComic(comicId: String)

    @Query("DELETE FROM collection_items WHERE collectionId = :id")
    suspend fun clear(id: Long)

    @Query("SELECT * FROM collections WHERE name = :name LIMIT 1")
    suspend fun byName(name: String): CollectionEntity?
}

@Dao
interface ProgressDao {
    @Query("SELECT * FROM progress")
    fun observeAll(): Flow<List<ProgressEntity>>

    @Query("SELECT * FROM progress")
    suspend fun all(): List<ProgressEntity>

    @Query("SELECT * FROM progress WHERE comicId = :id")
    suspend fun get(id: String): ProgressEntity?

    @Query("SELECT * FROM progress WHERE comicId = :id")
    fun observe(id: String): Flow<ProgressEntity?>

    @Upsert
    suspend fun upsert(p: ProgressEntity)

    @Upsert
    suspend fun upsertAll(p: List<ProgressEntity>)

    @Query("DELETE FROM progress WHERE comicId = :id")
    suspend fun delete(id: String)
}

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks WHERE deleted = 0 ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks WHERE comicId = :comicId AND deleted = 0 ORDER BY page, panel")
    fun observeFor(comicId: String): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks")
    suspend fun allIncludingDeleted(): List<BookmarkEntity>

    @Query("SELECT * FROM bookmarks WHERE id = :id")
    suspend fun get(id: String): BookmarkEntity?

    @Upsert
    suspend fun upsert(b: BookmarkEntity)

    @Upsert
    suspend fun upsertAll(b: List<BookmarkEntity>)
}

@Dao
interface SessionDao {
    @Query("SELECT * FROM reading_sessions ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<ReadingSessionEntity>>

    @Query("SELECT * FROM reading_sessions")
    suspend fun all(): List<ReadingSessionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(s: ReadingSessionEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(s: List<ReadingSessionEntity>)
}

@Dao
interface ServerDao {
    @Query("SELECT * FROM servers ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<ServerEntity>>

    @Query("SELECT * FROM servers")
    suspend fun all(): List<ServerEntity>

    @Query("SELECT * FROM servers WHERE id = :id")
    suspend fun get(id: Long): ServerEntity?

    @Upsert
    suspend fun upsert(s: ServerEntity): Long

    @Query("DELETE FROM servers WHERE id = :id")
    suspend fun delete(id: Long)
}
