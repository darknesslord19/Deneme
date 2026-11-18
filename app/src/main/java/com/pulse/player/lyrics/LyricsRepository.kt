package com.pulse.player.lyrics

import android.content.Context
import android.net.Uri
import com.pulse.player.data.Prefs
import com.pulse.player.data.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object LyricsRepository {
    data class Result(val lines: List<LrcLine>, val synced: Boolean, val source: String)
    data class Hit(
        val track: String, val artist: String, val album: String,
        val duration: Double, val synced: String?, val plain: String?,
    )

    private fun dir(ctx: Context) = File(ctx.filesDir, "lyrics").apply { mkdirs() }
    private fun cacheFile(ctx: Context, song: Song) = File(dir(ctx), "${song.id}.lrc")

    fun getOffset(ctx: Context, song: Song): Long = Prefs.get(ctx).getLong("loff_${song.id}", 0L)
    fun setOffset(ctx: Context, song: Song, v: Long) = Prefs.get(ctx).edit().putLong("loff_${song.id}", v).apply()

    private fun toResult(text: String, source: String): Result? {
        val (lines, synced) = LrcParser.parse(text)
        if (lines.isEmpty() || lines.all { it.text.isBlank() }) return null
        return Result(lines, synced, source)
    }

    fun save(ctx: Context, song: Song, text: String) {
        runCatching { cacheFile(ctx, song).writeText(text) }
    }

    fun delete(ctx: Context, song: Song) {
        runCatching { cacheFile(ctx, song).delete() }
    }

    fun cleanTitle(t: String): String = t
        .replace(Regex("""\((?:[^)]*)(official|video|lyric|lyrics|audio|klip|clip|hd|4k|remaster|visualizer|mv)(?:[^)]*)\)""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""\[[^]]*]"""), "")
        .replace(Regex("""\s+"""), " ")
        .trim()

    private fun cleanArtist(a: String) = if (a.startsWith("Bilinmeyen")) "" else a.split(",", "&", " feat", " ft.").first().trim()

    suspend fun load(ctx: Context, song: Song): Result? = withContext(Dispatchers.IO) {
        val cache = cacheFile(ctx, song)
        if (cache.exists()) toResult(cache.readText(), "Kayıtlı")?.let { return@withContext it }
        // aynı klasördeki .lrc dosyası
        song.path?.let { p ->
            val f = File(p.substringBeforeLast('.') + ".lrc")
            if (runCatching { f.canRead() }.getOrDefault(false)) {
                val t = runCatching { f.readText() }.getOrNull()
                if (t != null) toResult(t, "Yerel .lrc")?.let { save(ctx, song, t); return@withContext it }
            }
        }
        val title = cleanTitle(song.title)
        val artist = cleanArtist(song.artist)
        val text = fetchExact(title, artist, song) ?: search(title, artist).let { hits ->
            val best = hits.firstOrNull { it.synced != null && (song.duration <= 0 || kotlin.math.abs(it.duration * 1000 - song.duration) < 8000) }
                ?: hits.firstOrNull { it.synced != null } ?: hits.firstOrNull()
            best?.synced ?: best?.plain
        }
        if (text != null) {
            save(ctx, song, text)
            return@withContext toResult(text, "LRCLIB")
        }
        null
    }

    suspend fun useHit(ctx: Context, song: Song, hit: Hit): Result? = withContext(Dispatchers.IO) {
        val text = hit.synced ?: hit.plain ?: return@withContext null
        save(ctx, song, text)
        toResult(text, "LRCLIB")
    }

    suspend fun importFile(ctx: Context, song: Song, uri: Uri): Result? = withContext(Dispatchers.IO) {
        val text = runCatching {
            ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull() ?: return@withContext null
        val r = toResult(text, "İçe aktarıldı") ?: return@withContext null
        save(ctx, song, text)
        r
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun http(url: String): String? {
        var c: HttpURLConnection? = null
        return try {
            c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 8000
            c.readTimeout = 12000
            c.setRequestProperty("User-Agent", "DarknesPlayer/1.2 (Android; https://github.com/darknesslord19/deneme)")
            if (c.responseCode != 200) null else c.inputStream.bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            null
        } finally {
            c?.disconnect()
        }
    }

    private fun JSONObject.str(k: String): String? = if (isNull(k)) null else optString(k).takeIf { it.isNotBlank() }

    private fun fetchExact(title: String, artist: String, song: Song): String? {
        if (artist.isEmpty()) return null
        val sb = StringBuilder("https://lrclib.net/api/get?track_name=${enc(title)}&artist_name=${enc(artist)}")
        if (!song.album.startsWith("Bilinmeyen") && song.album != "Harici") sb.append("&album_name=${enc(song.album)}")
        if (song.duration > 0) sb.append("&duration=${song.duration / 1000}")
        val body = http(sb.toString()) ?: return null
        return runCatching { val o = JSONObject(body); o.str("syncedLyrics") ?: o.str("plainLyrics") }.getOrNull()
    }

    suspend fun searchHits(title: String, artist: String): List<Hit> = withContext(Dispatchers.IO) { search(title, artist) }

    private fun search(title: String, artist: String): List<Hit> {
        val url = if (artist.isNotEmpty())
            "https://lrclib.net/api/search?track_name=${enc(title)}&artist_name=${enc(artist)}"
        else "https://lrclib.net/api/search?q=${enc(title)}"
        var body = http(url)
        if ((body == null || body.trim() == "[]") && artist.isNotEmpty())
            body = http("https://lrclib.net/api/search?q=${enc("$artist $title")}")
        body ?: return emptyList()
        return runCatching {
            val arr = JSONArray(body)
            (0 until minOf(arr.length(), 15)).map { i ->
                val o = arr.getJSONObject(i)
                Hit(
                    o.str("trackName") ?: "", o.str("artistName") ?: "", o.str("albumName") ?: "",
                    o.optDouble("duration", 0.0), o.str("syncedLyrics"), o.str("plainLyrics"),
                )
            }.sortedByDescending { it.synced != null }
        }.getOrDefault(emptyList())
    }
}
