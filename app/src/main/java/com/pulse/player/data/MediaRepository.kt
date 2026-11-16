package com.pulse.player.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns

object MediaRepository {

    fun loadSongs(ctx: Context): List<Song> {
        val collection = if (Build.VERSION.SDK_INT >= 29)
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.AudioColumns.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DATE_ADDED,
        )
        val out = ArrayList<Song>()
        try {
            ctx.contentResolver.query(
                collection, projection, "${MediaStore.Audio.Media.IS_MUSIC} != 0", null,
                "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
            )?.use { c ->
                val idC = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val tC = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val arC = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val alC = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val aiC = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val dC = c.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.DURATION)
                val pC = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val daC = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                while (c.moveToNext()) {
                    val dur = c.getLong(dC)
                    if (dur in 1..9_999) continue
                    val id = c.getLong(idC)
                    var title = c.getString(tC) ?: "Bilinmeyen"
                    var artist = c.getString(arC)?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: ""
                    if (artist.isEmpty() && title.contains(" - ")) {
                        artist = title.substringBefore(" - ").trim()
                        title = title.substringAfter(" - ").trim()
                    }
                    out += Song(
                        id = id,
                        title = title,
                        artist = artist.ifEmpty { "Bilinmeyen Sanatçı" },
                        album = c.getString(alC)?.takeIf { it.isNotBlank() } ?: "Bilinmeyen Albüm",
                        albumId = c.getLong(aiC),
                        duration = dur,
                        uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id),
                        path = c.getString(pC),
                        dateAdded = c.getLong(daC),
                    )
                }
            }
        } catch (_: Exception) { }
        return out
    }

    fun loadVideos(ctx: Context): List<VideoItem> {
        val collection = if (Build.VERSION.SDK_INT >= 29)
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.VideoColumns.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.VideoColumns.BUCKET_DISPLAY_NAME,
            MediaStore.Video.Media.DATE_ADDED,
        )
        val out = ArrayList<VideoItem>()
        try {
            ctx.contentResolver.query(
                collection, projection, null, null, "${MediaStore.Video.Media.DATE_ADDED} DESC"
            )?.use { c ->
                val idC = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nC = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val dC = c.getColumnIndexOrThrow(MediaStore.Video.VideoColumns.DURATION)
                val sC = c.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val wC = c.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
                val hC = c.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
                val bC = c.getColumnIndexOrThrow(MediaStore.Video.VideoColumns.BUCKET_DISPLAY_NAME)
                val daC = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                while (c.moveToNext()) {
                    val id = c.getLong(idC)
                    out += VideoItem(
                        id = id,
                        title = (c.getString(nC) ?: "Video").substringBeforeLast('.'),
                        duration = c.getLong(dC),
                        uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id),
                        size = c.getLong(sC),
                        width = c.getInt(wC),
                        height = c.getInt(hC),
                        folder = c.getString(bC) ?: "Diğer",
                        dateAdded = c.getLong(daC),
                    )
                }
            }
        } catch (_: Exception) { }
        return out
    }

    fun displayName(ctx: Context, uri: Uri): String {
        try {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val n = c.getString(0)
                    if (!n.isNullOrBlank()) return n.substringBeforeLast('.')
                }
            }
        } catch (_: Exception) { }
        return uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.') ?: "Medya"
    }

    fun externalSong(ctx: Context, uri: Uri): Song {
        val name = displayName(ctx, uri)
        val artist = if (name.contains(" - ")) name.substringBefore(" - ").trim() else "Bilinmeyen Sanatçı"
        val title = if (name.contains(" - ")) name.substringAfter(" - ").trim() else name
        return Song(-(uri.toString().hashCode().toLong().and(0x7fffffff)) - 1, title, artist, "Harici", -1, 0, uri, null, 0)
    }

    fun externalVideo(ctx: Context, uri: Uri): VideoItem =
        VideoItem(-(uri.toString().hashCode().toLong().and(0x7fffffff)) - 1, displayName(ctx, uri), 0, uri, 0, 0, 0, "Harici", 0)
}
