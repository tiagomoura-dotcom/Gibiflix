package app.gibiflix.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ComicEntity::class,
        CollectionEntity::class,
        CollectionItemEntity::class,
        ProgressEntity::class,
        BookmarkEntity::class,
        ReadingSessionEntity::class,
        ServerEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class GibiflixDatabase : RoomDatabase() {
    abstract fun comics(): ComicDao
    abstract fun collections(): CollectionDao
    abstract fun progress(): ProgressDao
    abstract fun bookmarks(): BookmarkDao
    abstract fun sessions(): SessionDao
    abstract fun servers(): ServerDao

    companion object {
        fun create(context: Context): GibiflixDatabase =
            Room.databaseBuilder(context, GibiflixDatabase::class.java, "gibiflix.db").build()
    }
}
