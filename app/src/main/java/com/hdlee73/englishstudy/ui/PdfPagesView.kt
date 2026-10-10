package com.hdlee73.englishstudy.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

/** The pages of a PDF file as they are (Android's PdfRenderer), one under the other, with pinch zoom and the last page remembered. */
private class PdfHolder(file: File) {
    private val lock = Mutex()
    private val descriptor: ParcelFileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = PdfRenderer(descriptor)
    val pageCount: Int = renderer.pageCount
    private var closed = false
    private val cache = object : LruCache<String, Bitmap>(56 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    /** Height / width of every page. */
    suspend fun aspects(): List<Float> = withContext(Dispatchers.IO) {
        lock.withLock {
            List(pageCount) { i ->
                if (closed) return@List 1.414f
                val page = renderer.openPage(i)
                try { page.height.toFloat() / page.width.coerceAtLeast(1) } finally { page.close() }
            }
        }
    }

    suspend fun render(index: Int, widthPx: Int): Bitmap? = withContext(Dispatchers.IO) {
        val key = "$index@$widthPx"
        cache.get(key)?.let { return@withContext it }
        lock.withLock {
            cache.get(key)?.let { return@withLock it }
            if (closed) return@withLock null
            runCatching {
                val page = renderer.openPage(index)
                try {
                    val height = (widthPx.toFloat() * page.height / page.width.coerceAtLeast(1)).roundToInt().coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(widthPx, height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(AndroidColor.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    cache.put(key, bitmap)
                    bitmap
                } finally {
                    page.close()
                }
            }.getOrNull()
        }
    }

    fun close() {
        closed = true
        runCatching { renderer.close() }
        runCatching { descriptor.close() }
        cache.evictAll()
    }
}

private fun rememberedPage(context: Context, path: String): Int =
    runCatching { context.getSharedPreferences("reading", Context.MODE_PRIVATE).getInt("pdf_page_" + File(path).name, 0) }.getOrDefault(0)

private fun rememberPage(context: Context, path: String, page: Int) {
    runCatching { context.getSharedPreferences("reading", Context.MODE_PRIVATE).edit().putInt("pdf_page_" + File(path).name, page).apply() }
}

@Composable
fun PdfPagesView(path: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val holder = remember(path) { runCatching { PdfHolder(File(path)) } }
    DisposableEffect(path) { onDispose { holder.getOrNull()?.close() } }
    val pdf = holder.getOrNull()
    if (pdf == null || pdf.pageCount == 0) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text("이 PDF를 열지 못했어요. 암호가 걸렸거나 손상된 파일일 수 있어요. ‘글자 모드’로 읽어 보세요.", color = Muted, fontSize = 14.sp, modifier = Modifier.padding(24.dp))
        }
        return
    }
    val aspects by produceState<List<Float>?>(null, pdf) { value = pdf.aspects() }
    val ratios = aspects
    BoxWithConstraints(modifier.background(Color(0xFFE9ECF2))) {
        val widthPx = constraints.maxWidth
        val density = LocalDensity.current
        var zoom by remember(path) { mutableStateOf(1f) }
        // The bitmaps are made again at the new size a moment after the pinch ends; until then the old ones are stretched.
        var renderZoom by remember(path) { mutableStateOf(1f) }
        LaunchedEffect(zoom) { delay(300); renderZoom = zoom }
        val listState = rememberLazyListState(rememberedPage(context, path).coerceIn(0, pdf.pageCount - 1))
        val current by remember { derivedStateOf { listState.firstVisibleItemIndex } }
        LaunchedEffect(current) { rememberPage(context, path, current) }
        val pageWidthDp = with(density) { (widthPx * zoom).toDp() }
        Box(Modifier.fillMaxSize().pinchZoom({ zoom }) { zoom = it }.horizontalScroll(rememberScrollState(), enabled = zoom > 1.01f)) {
            LazyColumn(state = listState, modifier = Modifier.width(pageWidthDp).fillMaxSize()) {
                itemsIndexed(List(pdf.pageCount) { it }) { index, _ ->
                    val ratio = ratios?.getOrNull(index) ?: 1.414f
                    val bitmap by produceState<ImageBitmap?>(null, index, renderZoom) {
                        val target = (widthPx * renderZoom * 1.4f).roundToInt().coerceIn(200, 3000)
                        value = pdf.render(index, target)?.asImageBitmap()
                    }
                    Box(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp).height(with(density) { (widthPx * zoom * ratio).toDp() }).background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        val image = bitmap
                        if (image != null) Image(image, "${index + 1}쪽", Modifier.fillMaxSize(), contentScale = ContentScale.FillWidth)
                        else Text("${index + 1}쪽", color = Muted, fontSize = 13.sp)
                    }
                }
            }
        }
        Text(
            "${current + 1} / ${pdf.pageCount}쪽" + if (zoom > 1.01f) " · ${(zoom * 100).roundToInt()}%" else "",
            color = Color.White, fontSize = 12.sp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xCC1B2333)).padding(horizontal = 12.dp, vertical = 5.dp)
        )
        if (zoom > 1.01f) TextButton(
            onClick = { zoom = 1f },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 6.dp, bottom = 4.dp)
        ) { Text("원래대로", fontSize = 12.sp) }
    }
}
