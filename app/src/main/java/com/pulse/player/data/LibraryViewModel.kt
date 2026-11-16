package com.pulse.player.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LibraryViewModel(app: Application) : AndroidViewModel(app) {
    val songs = MutableStateFlow<List<Song>>(emptyList())
    val videos = MutableStateFlow<List<VideoItem>>(emptyList())
    val albums = MutableStateFlow<List<Album>>(emptyList())
    val artists = MutableStateFlow<List<Artist>>(emptyList())
    val loading = MutableStateFlow(false)
    val favorites = MutableStateFlow(Prefs.favorites(app))
    private var loaded = false

    fun load(force: Boolean = false) {
        if (loaded && !force) return
        loaded = true
        viewModelScope.launch {
            loading.value = true
            val ctx = getApplication<Application>()
            val s = withContext(Dispatchers.IO) { MediaRepository.loadSongs(ctx) }
            val v = withContext(Dispatchers.IO) { MediaRepository.loadVideos(ctx) }
            songs.value = s
            videos.value = v
            albums.value = s.groupBy { it.albumId }.map { (id, list) ->
                Album(id, list.first().album, list.first().artist, list)
            }.sortedBy { it.name.lowercase() }
            artists.value = s.groupBy { it.artist }.map { (n, list) -> Artist(n, list) }
                .sortedBy { it.name.lowercase() }
            loading.value = false
        }
    }

    fun toggleFavorite(id: Long) {
        val set = favorites.value.toMutableSet()
        if (!set.add(id)) set.remove(id)
        favorites.value = set
        Prefs.saveFavorites(getApplication(), set)
    }
}
