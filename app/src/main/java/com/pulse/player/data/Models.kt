package com.pulse.player.data

import android.content.ContentUris
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

val ALBUM_ART_BASE: Uri = Uri.parse("content://media/external/audio/albumart")

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val duration: Long,
    val uri: Uri,
    val path: String?,
    val dateAdded: Long,
) {
    val artUri: Uri? get() = if (albumId > 0) ContentUris.withAppendedId(ALBUM_ART_BASE, albumId) else null

    fun toMediaItem(): MediaItem = MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setArtworkUri(artUri)
                .build()
        ).build()
}

data class VideoItem(
    val id: Long,
    val title: String,
    val duration: Long,
    val uri: Uri,
    val size: Long,
    val width: Int,
    val height: Int,
    val folder: String,
    val dateAdded: Long,
) {
    fun toMediaItem(): MediaItem = MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(uri)
        .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
        .build()
}

data class Album(val id: Long, val name: String, val artist: String, val songs: List<Song>) {
    val artUri: Uri? get() = songs.firstOrNull()?.artUri
}

data class Artist(val name: String, val songs: List<Song>) {
    val artUri: Uri? get() = songs.firstOrNull()?.artUri
}
