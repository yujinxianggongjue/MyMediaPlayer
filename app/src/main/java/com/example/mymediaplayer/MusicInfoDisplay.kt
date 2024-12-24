package com.example.mymediaplayer

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import java.io.IOException

class MusicInfoDisplay(
    private val context: Context,
    internal val tvArtist: TextView,
    internal val tvAlbumName: TextView,
    internal val ivAlbumCover: ImageView
) {
    private val tvLyrics: TextView? = null

    fun displayMusicInfo(musicUri: Uri) {
        val retriever = MediaMetadataRetriever()

        try {
            Log.d(
                TAG,
                "Initializing MediaMetadataRetriever for URI: $musicUri"
            )

            retriever.setDataSource(context, musicUri)
            Log.d(TAG, "MediaMetadataRetriever successfully set data source.")

            // 获取歌手
            var artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            if (artist == null || artist.isEmpty()) {
                artist = "Unknown Artist"
                Log.d(TAG, "Artist metadata not found. Using default: Unknown Artist")
            } else {
                Log.d(TAG, "Artist retrieved: $artist")
            }
            tvArtist.text = "Artist: $artist"

            // 获取专辑名
            var albumName = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            if (albumName == null || albumName.isEmpty()) {
                albumName = "Unknown Album"
                Log.d(TAG, "Album metadata not found. Using default: Unknown Album")
            } else {
                Log.d(
                    TAG,
                    "Album name retrieved: $albumName"
                )
            }
            tvAlbumName.text = "Album: $albumName"

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
            Log.e(TAG, "Error retrieving music info: " + e.message, e)
            tvLyrics!!.text = "Error retrieving music info"
            tvArtist.text = "Artist: Unknown"
            tvAlbumName.text = "Album: Unknown"
            ivAlbumCover.setImageResource(R.drawable.default_album_cover)
        } finally {
            try {
                retriever.release()
                Log.d(TAG, "MediaMetadataRetriever released successfully.")
            } catch (e: IOException) {
                Log.e(TAG, "Error releasing MediaMetadataRetriever: " + e.message, e)
            }
        }
    }

    companion object {
        private const val TAG = "MusicInfoDisplay" // 调试标识
    }
}