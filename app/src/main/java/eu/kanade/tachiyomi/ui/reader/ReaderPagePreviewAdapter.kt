package eu.kanade.tachiyomi.ui.reader

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withUIContext
import java.io.BufferedInputStream

class ReaderPagePreviewAdapter(
    private val onPageClick: (ReaderPage) -> Unit,
) : RecyclerView.Adapter<ReaderPagePreviewAdapter.PagePreviewHolder>() {

    private var pages: List<ReaderPage> = emptyList()
    private var currentPage: ReaderPage? = null

    fun submitList(newPages: List<ReaderPage>) {
        if (pages == newPages) return
        pages = newPages
        notifyDataSetChanged()
    }

    fun setCurrentPage(page: ReaderPage) {
        if (currentPage == page) return
        val oldIndex = pages.indexOf(currentPage)
        currentPage = page
        val newIndex = pages.indexOf(currentPage)

        if (oldIndex != -1) notifyItemChanged(oldIndex)
        if (newIndex != -1) notifyItemChanged(newIndex)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PagePreviewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_reader_page_preview, parent, false)
        return PagePreviewHolder(view)
    }

    override fun onBindViewHolder(holder: PagePreviewHolder, position: Int) {
        val page = pages[position]
        holder.bind(page, page == currentPage)
    }

    override fun getItemCount(): Int = pages.size

    override fun onViewRecycled(holder: PagePreviewHolder) {
        holder.recycle()
    }

    inner class PagePreviewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardView: CardView = itemView.findViewById(R.id.card_view)
        private val thumbnail: ImageView = itemView.findViewById(R.id.page_thumbnail)
        private val pageNumber: TextView = itemView.findViewById(R.id.page_number)
        private var loadJob: Job? = null
        private var scope: CoroutineScope? = null

        init {
            itemView.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onPageClick(pages[position])
                }
            }
        }

        fun bind(page: ReaderPage, isSelected: Boolean) {
            pageNumber.text = "${page.number}"

            if (isSelected) {
                val borderDrawable = ContextCompat.getDrawable(itemView.context, R.drawable.selection_border_foreground)
                cardView.foreground = borderDrawable
            } else {
                cardView.foreground = null
            }

            recycle()
            val newScope = CoroutineScope(Dispatchers.Main)
            scope = newScope

            loadJob = newScope.launch {
                page.statusFlow.collectLatest { status ->
                    when (status) {
                        Page.State.Ready -> loadThumbnail(page)
                        Page.State.Queue -> {
                            launchIO {
                                page.chapter.pageLoader?.loadPage(page)
                            }
                        }
                        else -> { }
                    }
                }
            }
        }

        fun recycle() {
            loadJob?.cancel()
            loadJob = null
            scope?.cancel()
            scope = null
            thumbnail.setImageDrawable(null)
        }

        private suspend fun loadThumbnail(page: ReaderPage) {
            val streamFn = page.stream ?: return
            withContext(Dispatchers.IO) {
                try {
                    val stream = BufferedInputStream(streamFn(), 8192)
                    stream.use {
                        stream.mark(8 * 1024 * 1024)

                        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeStream(stream, null, options)

                        // Try reset for streaming decode; fall back to re-open for archive/epub
                        val bitmap = try {
                            stream.reset()
                            options.inJustDecodeBounds = false
                            options.inSampleSize = calculateInSampleSize(options, 150, 225)
                            BitmapFactory.decodeStream(stream, null, options)
                        } catch (_: Exception) {
                            options.inJustDecodeBounds = false
                            options.inSampleSize = calculateInSampleSize(options, 150, 225)
                            streamFn().use { freshStream ->
                                BitmapFactory.decodeStream(freshStream, null, options)
                            }
                        }

                        if (bitmap != null) {
                            withUIContext {
                                thumbnail.setImageBitmap(bitmap)
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Ignore errors for previews
                }
            }
        }

        private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
            val (height: Int, width: Int) = options.run { outHeight to outWidth }
            var inSampleSize = 1

            if (height > reqHeight || width > reqWidth) {
                val halfHeight: Int = height / 2
                val halfWidth: Int = width / 2

                while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                    inSampleSize *= 2
                }
            }
            return inSampleSize
        }
    }
}
