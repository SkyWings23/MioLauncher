package com.miolauncher.app.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.miolauncher.app.ui.theme.MioGreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.LinkedHashMap

/**
 * 从 URL 异步加载图片（内存缓存，无需第三方库）。
 */
@Composable
fun RemoteImage(
    url: String?,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    cornerRadius: Dp = 8.dp,
) {
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = url) {
        value = withContext(Dispatchers.IO) { ImageLoader.load(url) }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = contentDescription,
            modifier = modifier.clip(RoundedCornerShape(cornerRadius)),
            contentScale = contentScale,
        )
    } else {
        Box(
            modifier = modifier
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(cornerRadius)),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material3.CircularProgressIndicator(
                modifier = Modifier.size(if (cornerRadius < 8.dp) 12.dp else 22.dp),
                strokeWidth = 2.dp,
                color = MioGreen,
            )
        }
    }
}

/** 圆形图标（模组头像用） */
@Composable
fun RemoteIcon(url: String?, contentDescription: String? = null, size: Dp = 56.dp) {
    RemoteImage(
        url = url,
        contentDescription = contentDescription,
        modifier = Modifier.size(size),
        cornerRadius = 12.dp,
    )
}

/**
 * 极简图片加载器：LruCache 内存缓存 + URL 下载。
 */
private object ImageLoader {
    private val cache = object : LinkedHashMap<String, Bitmap>(0, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?): Boolean = size > 120
    }

    fun load(url: String?): Bitmap? {
        if (url.isNullOrBlank()) return null
        synchronized(cache) { cache[url]?.let { return it } }
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.setRequestProperty("User-Agent", "MioLauncher/0.1.0")
            if (conn.responseCode !in 200..299) return null
            val bmp = BitmapFactory.decodeStream(conn.inputStream)
            if (bmp != null) {
                synchronized(cache) { cache[url] = bmp }
            }
            bmp
        } catch (e: Exception) {
            null
        }
    }
}
