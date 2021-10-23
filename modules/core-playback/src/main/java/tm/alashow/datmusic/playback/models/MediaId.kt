/*
 * Copyright (C) 2021, Alashov Berkeli
 * All rights reserved.
 */
package tm.alashow.datmusic.playback.models

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.Serializable
import timber.log.Timber
import tm.alashow.datmusic.data.DatmusicSearchParams
import tm.alashow.datmusic.data.DatmusicSearchParams.Companion.withTypes
import tm.alashow.datmusic.data.db.daos.AlbumsDao
import tm.alashow.datmusic.data.db.daos.ArtistsDao
import tm.alashow.datmusic.data.db.daos.AudiosDao
import tm.alashow.datmusic.data.repos.playlist.PlaylistsRepo
import tm.alashow.datmusic.domain.entities.Audio

const val MEDIA_TYPE_AUDIO = "Media.Audio"
const val MEDIA_TYPE_ARTIST = "Media.Artist"
const val MEDIA_TYPE_DOWNLOADS = "Media.Downloads"
const val MEDIA_TYPE_PLAYLIST = "Media.Playlist"
const val MEDIA_TYPE_ALBUM = "Media.Album"
const val MEDIA_TYPE_AUDIO_QUERY = "Media.AudioQuery"
const val MEDIA_TYPE_AUDIO_MINERVA_QUERY = "Media.AudioMinervaQuery"
const val MEDIA_TYPE_AUDIO_FLACS_QUERY = "Media.AudioFlacsQuery"

private const val MEDIA_ID_SEPARATOR = " | "

@Serializable
data class MediaId(
    val type: String = MEDIA_TYPE_AUDIO,
    val value: String = "0",
    val index: Int = -1,
    val caller: String = CALLER_SELF
) {

    val hasIndex = index >= 0

    companion object {
        const val CALLER_SELF = "self"
        const val CALLER_OTHER = "other"
    }

    override fun toString(): String {
        return type +
            MEDIA_ID_SEPARATOR + value +
            MEDIA_ID_SEPARATOR + index +
            MEDIA_ID_SEPARATOR + caller
    }
}

fun String?.toMediaId(): MediaId {
    if (this == null)
        return MediaId()

    val parts = split(MEDIA_ID_SEPARATOR)
    val type = parts[0]

    val knownTypes = listOf(
        MEDIA_TYPE_AUDIO, MEDIA_TYPE_ARTIST,
        MEDIA_TYPE_ALBUM, MEDIA_TYPE_AUDIO_QUERY,
        MEDIA_TYPE_AUDIO_MINERVA_QUERY, MEDIA_TYPE_AUDIO_FLACS_QUERY,
        MEDIA_TYPE_PLAYLIST,
    )
    if (type !in knownTypes) {
        Timber.e("Unknown media type: $type")
        return MediaId()
    }

    return if (parts.size > 1)
        MediaId(type, parts[1], parts[2].toInt(), parts[3])
    else MediaId()
}

suspend fun MediaId.toAudioList(
    audiosDao: AudiosDao,
    artistsDao: ArtistsDao,
    albumsDao: AlbumsDao,
    playlistsRepo: PlaylistsRepo
): List<Audio>? = when (type) {
    MEDIA_TYPE_AUDIO -> listOfNotNull(audiosDao.entry(value).firstOrNull())
    MEDIA_TYPE_ALBUM -> albumsDao.entry(value).firstOrNull()?.audios
    MEDIA_TYPE_ARTIST -> artistsDao.entry(value).firstOrNull()?.audios
    MEDIA_TYPE_PLAYLIST -> playlistsRepo.playlistWithAudios(value.toLong()).firstOrNull()?.audios
    MEDIA_TYPE_AUDIO_QUERY, MEDIA_TYPE_AUDIO_MINERVA_QUERY, MEDIA_TYPE_AUDIO_FLACS_QUERY -> {
        val params = DatmusicSearchParams(value).run {
            when (type) {
                MEDIA_TYPE_AUDIO_MINERVA_QUERY -> withTypes(DatmusicSearchParams.BackendType.MINERVA)
                MEDIA_TYPE_AUDIO_FLACS_QUERY -> withTypes(DatmusicSearchParams.BackendType.FLACS)
                else -> this
            }
        }
        audiosDao.entries(params).first()
    }
    else -> emptyList()
}

suspend fun MediaId.toQueueTitle(
    audiosDao: AudiosDao,
    artistsDao: ArtistsDao,
    albumsDao: AlbumsDao,
    playlistsRepo: PlaylistsRepo
): QueueTitle = when (type) {
    MEDIA_TYPE_AUDIO -> QueueTitle(this, QueueTitle.Type.AUDIO, audiosDao.entry(value).firstOrNull()?.title)
    MEDIA_TYPE_ARTIST -> QueueTitle(this, QueueTitle.Type.ARTIST, artistsDao.entry(value).firstOrNull()?.name)
    MEDIA_TYPE_ALBUM -> QueueTitle(this, QueueTitle.Type.ALBUM, albumsDao.entry(value).firstOrNull()?.title)
    MEDIA_TYPE_PLAYLIST -> QueueTitle(this, QueueTitle.Type.PLAYLIST, playlistsRepo.playlistWithAudios(value.toLong()).firstOrNull()?.playlist?.name)
    MEDIA_TYPE_AUDIO_QUERY, MEDIA_TYPE_AUDIO_MINERVA_QUERY, MEDIA_TYPE_AUDIO_FLACS_QUERY -> QueueTitle(this, QueueTitle.Type.SEARCH, value)
    else -> QueueTitle()
}
