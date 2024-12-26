package com.example.mymediaplayer

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import java.io.IOException

/**
 * MusicInfoDisplay 负责显示音乐信息，包括艺术家、专辑名和专辑封面
 */
class MusicInfoDisplay(
    private val context: Context,
    internal val tvArtist: TextView,
    internal val tvAlbumName: TextView,
    internal val ivAlbumCover: ImageView
) {
    private val tvLyrics: TextView? = null // 如果需要显示歌词，可以启用

    /**
     * 显示音乐信息
     * @param musicUri 音乐文件的 Uri
     */
    fun displayMusicInfo(musicUri: Uri) {
        val retriever = MediaMetadataRetriever()

        try {
            Log.d(TAG, "Initializing MediaMetadataRetriever for URI: $musicUri")

            retriever.setDataSource(context, musicUri)
            Log.d(TAG, "MediaMetadataRetriever successfully set data source.")

            // 获取歌手信息
            var artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            if (artist.isNullOrEmpty()) {
                artist = "未知艺术家"
                Log.d(TAG, "Artist metadata not found. Using default: $artist")
            } else {
                Log.d(TAG, "Artist retrieved: $artist")
            }
            tvArtist.text = "艺术家: $artist"

            // 获取专辑名
            var albumName = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            if (albumName.isNullOrEmpty()) {
                albumName = "未知专辑"
                Log.d(TAG, "Album metadata not found. Using default: $albumName")
            } else {
                Log.d(TAG, "Album name retrieved: $albumName")
            }
            tvAlbumName.text = "专辑: $albumName"

            // 获取专辑封面
            val albumArt = retriever.embeddedPicture
            if (albumArt != null) {
                val bitmap = BitmapFactory.decodeByteArray(albumArt, 0, albumArt.size)
                ivAlbumCover.setImageBitmap(bitmap)
                Log.d(TAG, "Album cover retrieved and set.")
            } else {
                ivAlbumCover.setImageResource(R.drawable.default_album_cover)
                Log.d(TAG, "Album cover not found. Using default image.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving music info: ${e.message}", e)
            // 如果有歌词视图，显示错误
            tvLyrics?.text = "无法获取音乐信息"
            tvArtist.text = "艺术家: 未知"
            tvAlbumName.text = "专辑: 未知"
            ivAlbumCover.setImageResource(R.drawable.default_album_cover)
        } finally {
            try {
                retriever.release()
                Log.d(TAG, "MediaMetadataRetriever released successfully.")
            } catch (e: IOException) {
                Log.e(TAG, "Error releasing MediaMetadataRetriever: ${e.message}", e)
            }
        }
    }

    /**
     * 显示或隐藏音乐信息控件
     * @param show 是否显示
     */
    fun toggleMusicInfo(show: Boolean) {
        val visibility = if (show) View.VISIBLE else View.GONE
        tvArtist.visibility = visibility
        tvAlbumName.visibility = visibility
        ivAlbumCover.visibility = visibility
    }

    companion object {
        private const val TAG = "MusicInfoDisplay" // 调试标识
    }
}