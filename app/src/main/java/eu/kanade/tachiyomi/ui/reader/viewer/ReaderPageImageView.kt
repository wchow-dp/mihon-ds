package eu.kanade.tachiyomi.ui.reader.viewer

import android.content.Context
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.drawable.Animatable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.annotation.AttrRes
import androidx.annotation.CallSuper
import androidx.annotation.StyleRes
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.os.postDelayed
import androidx.core.view.isVisible
import coil3.BitmapImage
import coil3.asDrawable
import coil3.dispose
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.Disposable
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Precision
import coil3.size.ViewSizeResolver
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView.EASE_IN_OUT_QUAD
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView.EASE_OUT_QUAD
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView.SCALE_TYPE_CENTER_INSIDE
import com.github.chrisbanes.photoview.PhotoView
import eu.kanade.domain.base.BasePreferences
import eu.kanade.tachiyomi.data.coil.cropBorders
import eu.kanade.tachiyomi.data.coil.customDecoder
import eu.kanade.tachiyomi.ui.reader.panel.PanelFocusEffect
import eu.kanade.tachiyomi.ui.reader.panel.PanelReadingSettings
import eu.kanade.tachiyomi.ui.reader.panel.ReaderPanel
import eu.kanade.tachiyomi.ui.reader.viewer.webtoon.WebtoonSubsamplingImageView
import eu.kanade.tachiyomi.util.system.animatorDurationScale
import eu.kanade.tachiyomi.util.view.isVisibleOnScreen
import okio.BufferedSource
import tachiyomi.core.common.util.system.ImageUtil
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * A wrapper view for showing page image.
 *
 * Animated image will be drawn by [PhotoView] while [SubsamplingScaleImageView] will take non-animated image.
 *
 * @param isWebtoon if true, [WebtoonSubsamplingImageView] will be used instead of [SubsamplingScaleImageView]
 * and [AppCompatImageView] will be used instead of [PhotoView]
 */
open class ReaderPageImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    @AttrRes defStyleAttrs: Int = 0,
    @StyleRes defStyleRes: Int = 0,
    private val isWebtoon: Boolean = false,
) : FrameLayout(context, attrs, defStyleAttrs, defStyleRes) {

    private val alwaysDecodeLongStripWithSSIV by lazy {
        Injekt.get<BasePreferences>().alwaysDecodeLongStripWithSSIV.get()
    }

    private var pageView: View? = null
    private var panelHighlightOverlay: PanelHighlightOverlay? = null
    private var pendingPanelFocus: ReaderPanel? = null
    private var currentPanelFocus: ReaderPanel? = null
    private var panelMapPanels: List<ReaderPanel> = emptyList()
    private var panelMapActivePanelId: String? = null
    private var panelMapShowNumbers: Boolean = false
    private var panelMapOnPanelTap: ((Int) -> Unit)? = null
    private var panelMapOnPanelSwap: ((Int, Int) -> Unit)? = null
    private var panelMapIsCorrectionMode: Boolean = false
    private var panelOverlayMode = PanelOverlayMode.NONE
    private var imageRequestDisposable: Disposable? = null

    private var config: Config? = null

    var onImageLoaded: (() -> Unit)? = null
    var onImageLoadError: ((Throwable?) -> Unit)? = null
    var onScaleChanged: ((newScale: Float) -> Unit)? = null
    var onViewClicked: (() -> Unit)? = null

    /**
     * For automatic background. Will be set as background color when [onImageLoaded] is called.
     */
    var pageBackground: Drawable? = null

    @CallSuper
    open fun onImageLoaded() {
        onImageLoaded?.invoke()
        background = pageBackground
        val panel = pendingPanelFocus
        if (panel != null) {
            pendingPanelFocus = null
            if (config?.panelFocusEffect != PanelFocusEffect.OFF || panelMapIsCorrectionMode) {
                focusOnPanel(panel, animate = false)
            } else {
                clearPanelOverlay()
            }
        }
        refreshPanelOverlay()
    }

    @CallSuper
    open fun onImageLoadError(error: Throwable?) {
        onImageLoadError?.invoke(error)
    }

    @CallSuper
    open fun onScaleChanged(newScale: Float) {
        onScaleChanged?.invoke(newScale)
    }

    @CallSuper
    open fun onViewClicked() {
        onViewClicked?.invoke()
    }

    open fun onPageSelected(forward: Boolean) {
        with(pageView as? SubsamplingScaleImageView) {
            if (this == null) return
            if (isReady) {
                landscapeZoom(forward)
            } else {
                setOnImageEventListener(
                    object : SubsamplingScaleImageView.DefaultOnImageEventListener() {
                        override fun onReady() {
                            setupZoom(config)
                            landscapeZoom(forward)
                            this@ReaderPageImageView.onImageLoaded()
                        }

                        override fun onImageLoadError(e: Exception) {
                            onImageLoadError(e)
                        }
                    },
                )
            }
        }
    }

    private fun SubsamplingScaleImageView.landscapeZoom(forward: Boolean) {
        if (
            config != null &&
            config!!.landscapeZoom &&
            config!!.minimumScaleType == SCALE_TYPE_CENTER_INSIDE &&
            sWidth > sHeight &&
            scale == minScale
        ) {
            handler?.postDelayed(500) {
                val point = when (config!!.zoomStartPosition) {
                    ZoomStartPosition.LEFT -> if (forward) PointF(0F, 0F) else PointF(sWidth.toFloat(), 0F)
                    ZoomStartPosition.RIGHT -> if (forward) PointF(sWidth.toFloat(), 0F) else PointF(0F, 0F)
                    ZoomStartPosition.CENTER -> center
                }

                val targetScale = height.toFloat() / sHeight.toFloat()
                animateScaleAndCenter(targetScale, point)!!
                    .withDuration(500)
                    .withEasing(EASE_IN_OUT_QUAD)
                    .withInterruptible(true)
                    .start()
            }
        }
    }

    fun setImage(drawable: Drawable, config: Config) {
        cancelImageRequest()
        this.config = config
        if (drawable is Animatable) {
            prepareAnimatedImageView()
            setAnimatedImage(drawable, config)
        } else {
            prepareNonAnimatedImageView()
            setNonAnimatedImage(drawable, config)
        }
    }

    fun setImage(source: BufferedSource, isAnimated: Boolean, config: Config) {
        cancelImageRequest()
        this.config = config
        if (isAnimated) {
            prepareAnimatedImageView()
            setAnimatedImage(source, config)
        } else {
            prepareNonAnimatedImageView()
            setNonAnimatedImage(source, config)
        }
    }

    fun recycle() = pageView?.let {
        cancelImageRequest()
        when (it) {
            is SubsamplingScaleImageView -> it.recycle()
            is AppCompatImageView -> it.dispose()
        }
        it.isVisible = false
    }

    /**
     * Check if the image can be panned to the left
     */
    fun canPanLeft(): Boolean = canPan { it.left }

    /**
     * Check if the image can be panned to the right
     */
    fun canPanRight(): Boolean = canPan { it.right }

    /**
     * Check whether the image can be panned.
     * @param fn a function that returns the direction to check for
     */
    private fun canPan(fn: (RectF) -> Float): Boolean {
        (pageView as? SubsamplingScaleImageView)?.let { view ->
            RectF().let {
                view.getPanRemaining(it)
                return fn(it) > 1
            }
        }
        return false
    }

    /**
     * Pans the image to the left by a screen's width worth.
     */
    fun panLeft() {
        pan { center, view -> center.also { it.x -= view.width / view.scale } }
    }

    /**
     * Pans the image to the right by a screen's width worth.
     */
    fun panRight() {
        pan { center, view -> center.also { it.x += view.width / view.scale } }
    }

    /**
     * Pans the image.
     * @param fn a function that computes the new center of the image
     */
    private fun pan(fn: (PointF, SubsamplingScaleImageView) -> PointF) {
        (pageView as? SubsamplingScaleImageView)?.let { view ->

            val target = fn(view.center ?: return, view)
            view.animateCenter(target)!!
                .withEasing(EASE_OUT_QUAD)
                .withDuration(250)
                .withInterruptible(true)
                .start()
        }
    }

    fun focusOnPanel(panel: ReaderPanel, animate: Boolean = true) {
        currentPanelFocus = panel
        showFocusedPanelOverlay(panel)
        val view = pageView as? SubsamplingScaleImageView ?: return
        if (!view.isReady) {
            pendingPanelFocus = panel
            return
        }

        if (panel.width <= 0f || panel.height <= 0f || view.width <= 0 || view.height <= 0) return

        view.setPanelFocusPanLimit(enabled = true)

        val focus = PanelFocusCalculator.calculateFocus(
            viewWidth = view.width,
            viewHeight = view.height,
            imageWidth = view.sWidth,
            imageHeight = view.sHeight,
            panelLeft = panel.bounds.left,
            panelTop = panel.bounds.top,
            panelRight = panel.bounds.right,
            panelBottom = panel.bounds.bottom,
            minScale = view.minScale,
            maxScale = view.maxScale,
            allowOverpan = true,
        )
        val targetCenter = PointF(focus.centerX, focus.centerY)
        val duration = config
            ?.panelTransitionDuration
            ?.getSystemScaledDuration(allowZero = true)
            ?.toLong()
            ?: 250L

        if (animate && duration > 0L) {
            view.animateScaleAndCenter(focus.scale, targetCenter)!!
                .withDuration(duration)
                .withEasing(EASE_IN_OUT_QUAD)
                .withInterruptible(true)
                .start()
            refreshPanelOverlayAfterFocusMovement(duration)
        } else {
            view.setScaleAndCenter(focus.scale, targetCenter)
            refreshPanelOverlayAfterFocusMovement(0L)
        }
    }

    fun clearPanelFocus() {
        currentPanelFocus = null
        pendingPanelFocus = null
        (pageView as? SubsamplingScaleImageView)?.setPanelFocusPanLimit(enabled = false)
        clearPanelOverlay()
    }

    fun applyPanelReadingDisplayConfig(
        panelTransitionDuration: Int,
        panelFocusEffect: PanelFocusEffect,
        panelFocusStrength: Int,
        panelPrimaryOverlay: Boolean = true,
    ) {
        val currentConfig = config ?: return
        val newConfig = currentConfig.copy(
            panelTransitionDuration = PanelReadingSettings.normalizeTransitionMillis(panelTransitionDuration),
            panelFocusEffect = panelFocusEffect,
            panelFocusStrength = PanelReadingSettings.normalizeFocusStrength(panelFocusStrength),
            panelPrimaryOverlay = panelPrimaryOverlay,
        )
        if (config == newConfig) return
        config = newConfig
        refreshPanelOverlay()
    }

    fun highlightPanel(panel: ReaderPanel?) {
        if (panel == null) {
            clearPanelOverlay()
            return
        }

        showPanelMap(
            panels = listOf(panel),
            activePanel = panel,
            showNumbers = false,
            onPanelTap = null,
        )
    }

    fun showPanelMap(
        panels: List<ReaderPanel>,
        activePanel: ReaderPanel?,
        showNumbers: Boolean = true,
        isCorrectionMode: Boolean = false,
        onPanelTap: ((Int) -> Unit)? = null,
        onPanelSwap: ((Int, Int) -> Unit)? = null,
    ) {
        panelMapPanels = panels
        panelMapActivePanelId = activePanel?.id
        panelMapShowNumbers = showNumbers
        panelMapIsCorrectionMode = isCorrectionMode
        panelMapOnPanelTap = onPanelTap
        panelMapOnPanelSwap = onPanelSwap
        panelOverlayMode = PanelOverlayMode.MAP
        refreshPanelOverlay()
    }

    private fun showFocusedPanelOverlay(panel: ReaderPanel) {
        if (config?.panelFocusEffect == PanelFocusEffect.OFF && !panelMapIsCorrectionMode) {
            clearPanelOverlay()
            return
        }
        panelMapPanels = listOf(panel)
        panelMapActivePanelId = panel.id
        panelMapShowNumbers = false
        panelMapOnPanelTap = null
        panelOverlayMode = PanelOverlayMode.FOCUS
        refreshPanelOverlay()
    }

    private fun clearPanelOverlay() {
        panelMapPanels = emptyList()
        panelMapActivePanelId = null
        panelMapShowNumbers = false
        panelMapOnPanelTap = null
        panelOverlayMode = PanelOverlayMode.NONE
        panelHighlightOverlay?.let { removeView(it) }
        panelHighlightOverlay = null
    }

    private fun refreshPanelOverlay() {
        val focusOff = config?.panelFocusEffect == PanelFocusEffect.OFF ||
                      uy.kohesive.injekt.Injekt.get<eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences>().panelReadingFocusEffect().get() == PanelFocusEffect.OFF

        if (
            panelMapPanels.isEmpty() ||
            shouldHidePrimaryFocusOverlay() ||
            (panelOverlayMode == PanelOverlayMode.FOCUS && focusOff && !panelMapIsCorrectionMode)
        ) {
            panelHighlightOverlay?.isVisible = false
            panelHighlightOverlay?.let { removeView(it) }
            panelHighlightOverlay = null
            return
        }

        val overlay = panelHighlightOverlay ?: PanelHighlightOverlay(context).also {
            panelHighlightOverlay = it
            addView(it, MATCH_PARENT, MATCH_PARENT)
        }
        overlay.focusEffect = config?.panelFocusEffect ?: PanelFocusEffect.DARKEN
        overlay.focusStrength = config?.panelFocusStrength ?: PanelReadingSettings.PANEL_FOCUS_STRENGTH_DEFAULT

        val activePanelId = panelMapActivePanelId
        val regions = panelMapPanels.mapIndexedNotNull { index, panel ->
            val bounds = sourcePanelToViewRect(panel) ?: return@mapIndexedNotNull null
            PanelHighlightOverlay.PanelRegion(
                panelIndex = index,
                number = if (panelMapShowNumbers) index + 1 else null,
                bounds = bounds,
                active = panel.id == activePanelId,
            )
        }
        overlay.panelRegions = regions
        overlay.isCorrectionMode = panelMapIsCorrectionMode
        overlay.onPanelClick = panelMapOnPanelTap
        overlay.onSwapPanels = panelMapOnPanelSwap
        overlay.isVisible = regions.isNotEmpty()
        if (overlay.isVisible) {
            overlay.bringToFront()
        }
    }

    private fun shouldHidePrimaryFocusOverlay(): Boolean {
        return panelOverlayMode == PanelOverlayMode.FOCUS &&
            (config?.panelPrimaryOverlay == false || config?.panelFocusEffect == PanelFocusEffect.OFF)
    }

    private fun refreshPanelOverlayAfterFocusMovement(duration: Long) {
        if (panelHighlightOverlay == null && panelMapPanels.isEmpty()) return
        post { refreshPanelOverlay() }
        if (duration > 0L) {
            handler?.postDelayed(
                { refreshPanelOverlay() },
                duration + PANEL_FOCUS_OVERLAY_SETTLE_DELAY_MS,
            )
        }
    }

    private fun sourcePanelToViewRect(panel: ReaderPanel): RectF? {
        val view = pageView as? SubsamplingScaleImageView ?: return null
        if (!view.isReady) return null

        val topLeft = view.sourceToViewCoord(panel.bounds.left, panel.bounds.top) ?: return null
        val bottomRight = view.sourceToViewCoord(panel.bounds.right, panel.bounds.bottom) ?: return null

        return RectF(topLeft.x, topLeft.y, bottomRight.x, bottomRight.y)
    }

    private fun prepareNonAnimatedImageView() {
        if (pageView is SubsamplingScaleImageView) return
        removeView(pageView)

        pageView = if (isWebtoon) {
            WebtoonSubsamplingImageView(context)
        } else {
            SubsamplingScaleImageView(context)
        }.apply {
            setMaxTileSize(ImageUtil.hardwareBitmapThreshold)
            setDoubleTapZoomStyle(SubsamplingScaleImageView.ZOOM_FOCUS_CENTER)
            setPanelFocusPanLimit(enabled = false)
            setMinimumTileDpi(180)
            setOnStateChangedListener(
                object : SubsamplingScaleImageView.OnStateChangedListener {
                    override fun onScaleChanged(newScale: Float, origin: Int) {
                        this@ReaderPageImageView.onScaleChanged(newScale)
                        refreshPanelOverlay()
                    }

                    override fun onCenterChanged(newCenter: PointF?, origin: Int) {
                        refreshPanelOverlay()
                    }
                },
            )
            setOnClickListener { this@ReaderPageImageView.onViewClicked() }
            addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                val shouldRefresh = PanelFocusCalculator.shouldRefreshForViewportChange(
                    oldWidth = oldRight - oldLeft,
                    oldHeight = oldBottom - oldTop,
                    newWidth = right - left,
                    newHeight = bottom - top,
                )
                if (!shouldRefresh) return@addOnLayoutChangeListener

                post {
                    currentPanelFocus?.let { focusOnPanel(it, animate = false) }
                    refreshPanelOverlay()
                }
            }
        }
        addView(pageView, MATCH_PARENT, MATCH_PARENT)
        panelHighlightOverlay?.bringToFront()
    }

    private fun SubsamplingScaleImageView.setupZoom(config: Config?) {
        // 5x zoom
        maxScale = scale * MAX_ZOOM_SCALE
        setDoubleTapZoomScale(scale * 2)

        when (config?.zoomStartPosition) {
            ZoomStartPosition.LEFT -> setScaleAndCenter(scale, PointF(0F, 0F))
            ZoomStartPosition.RIGHT -> setScaleAndCenter(scale, PointF(sWidth.toFloat(), 0F))
            ZoomStartPosition.CENTER -> setScaleAndCenter(scale, center)
            null -> {}
        }
    }

    private fun SubsamplingScaleImageView.setPanelFocusPanLimit(enabled: Boolean) {
        setPanLimit(
            if (enabled) {
                SubsamplingScaleImageView.PAN_LIMIT_CENTER
            } else {
                SubsamplingScaleImageView.PAN_LIMIT_INSIDE
            },
        )
    }

    private fun setNonAnimatedImage(
        data: Any,
        config: Config,
    ) = (pageView as? SubsamplingScaleImageView)?.apply {
        setDoubleTapZoomDuration(config.zoomDuration.getSystemScaledDuration())
        setMinimumScaleType(config.minimumScaleType)
        setMinimumDpi(1) // Just so that very small image will be fit for initial load
        setCropBorders(config.cropBorders)
        setOnImageEventListener(
            object : SubsamplingScaleImageView.DefaultOnImageEventListener() {
                override fun onReady() {
                    setupZoom(config)
                    if (isVisibleOnScreen()) landscapeZoom(true)
                    this@ReaderPageImageView.onImageLoaded()
                }

                override fun onImageLoadError(e: Exception) {
                    this@ReaderPageImageView.onImageLoadError(e)
                }
            },
        )

        when (data) {
            is BitmapDrawable -> {
                setImage(ImageSource.bitmap(data.bitmap))
                isVisible = true
            }
            is BufferedSource -> {
                if (!isWebtoon || alwaysDecodeLongStripWithSSIV) {
                    setHardwareConfig(ImageUtil.canUseHardwareBitmap(data))
                    setImage(ImageSource.inputStream(data.inputStream()))
                    isVisible = true
                    return@apply
                }

                ImageRequest.Builder(context)
                    .data(data)
                    .memoryCachePolicy(CachePolicy.DISABLED)
                    .diskCachePolicy(CachePolicy.DISABLED)
                    .target(
                        onSuccess = { result ->
                            val image = result as BitmapImage
                            setImage(ImageSource.bitmap(image.bitmap))
                            isVisible = true
                        },
                    )
                    .listener(
                        onError = { _, result ->
                            onImageLoadError(result.throwable)
                        },
                    )
                    .size(ViewSizeResolver(this@ReaderPageImageView))
                    .precision(Precision.INEXACT)
                    .cropBorders(config.cropBorders)
                    .customDecoder(true)
                    .crossfade(false)
                    .build()
                    .let { request ->
                        imageRequestDisposable = context.imageLoader.enqueue(request)
                    }
            }
            else -> {
                throw IllegalArgumentException("Not implemented for class ${data::class.simpleName}")
            }
        }
    }

    private fun prepareAnimatedImageView() {
        if (pageView is AppCompatImageView) return
        removeView(pageView)

        pageView = if (isWebtoon) {
            AppCompatImageView(context)
        } else {
            PhotoView(context)
        }.apply {
            adjustViewBounds = true

            if (this is PhotoView) {
                setScaleLevels(1F, 2F, MAX_ZOOM_SCALE)
                // Force 2 scale levels on double tap
                setOnDoubleTapListener(
                    object : GestureDetector.SimpleOnGestureListener() {
                        override fun onDoubleTap(e: MotionEvent): Boolean {
                            if (scale > 1F) {
                                setScale(1F, e.x, e.y, true)
                            } else {
                                setScale(2F, e.x, e.y, true)
                            }
                            return true
                        }

                        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                            this@ReaderPageImageView.onViewClicked()
                            return super.onSingleTapConfirmed(e)
                        }
                    },
                )
                setOnScaleChangeListener { _, _, _ ->
                    this@ReaderPageImageView.onScaleChanged(scale)
                }
            }
        }
        addView(pageView, MATCH_PARENT, MATCH_PARENT)
        panelHighlightOverlay?.bringToFront()
    }

    private fun setAnimatedImage(
        data: Any,
        config: Config,
    ) = (pageView as? AppCompatImageView)?.apply {
        if (this is PhotoView) {
            setZoomTransitionDuration(config.zoomDuration.getSystemScaledDuration())
        }

        val request = ImageRequest.Builder(context)
            .data(data)
            .memoryCachePolicy(CachePolicy.DISABLED)
            .diskCachePolicy(CachePolicy.DISABLED)
            .target(
                onSuccess = { result ->
                    val drawable = result.asDrawable(context.resources)
                    setImageDrawable(drawable)
                    (drawable as? Animatable)?.start()
                    isVisible = true
                    this@ReaderPageImageView.onImageLoaded()
                },
            )
            .listener(
                onError = { _, result ->
                    onImageLoadError(result.throwable)
                },
            )
            .crossfade(false)
            .build()
        imageRequestDisposable = context.imageLoader.enqueue(request)
    }

    private fun cancelImageRequest() {
        imageRequestDisposable?.dispose()
        imageRequestDisposable = null
    }

    private fun Int.getSystemScaledDuration(allowZero: Boolean = false): Int {
        if (allowZero && this <= 0) return 0
        return (this * context.animatorDurationScale).toInt().coerceAtLeast(1)
    }

    /**
     * All of the config except [zoomDuration] will only be used for non-animated image.
     */
    data class Config(
        val zoomDuration: Int,
        val panelTransitionDuration: Int = zoomDuration,
        val panelFocusEffect: PanelFocusEffect = PanelFocusEffect.DARKEN,
        val panelFocusStrength: Int = PanelReadingSettings.PANEL_FOCUS_STRENGTH_DEFAULT,
        val panelPrimaryOverlay: Boolean = true,
        val minimumScaleType: Int = SCALE_TYPE_CENTER_INSIDE,
        val cropBorders: Boolean = false,
        val zoomStartPosition: ZoomStartPosition = ZoomStartPosition.CENTER,
        val landscapeZoom: Boolean = false,
    )

    enum class ZoomStartPosition {
        LEFT,
        CENTER,
        RIGHT,
    }

    private enum class PanelOverlayMode {
        NONE,
        FOCUS,
        MAP,
    }

    /**
     * Check if the image is zoomed in
     */
    fun isZoomed(): Boolean {
        return (pageView as? SubsamplingScaleImageView)?.let { it.scale > 1f } == true
    }

    /**
     * Pan the image by the specified delta
     */
    fun panBy(dx: Float, dy: Float) {
        (pageView as? SubsamplingScaleImageView)?.scrollBy(dx.toInt(), dy.toInt())
    }

    /**
     * Reset zoom to fit screen
     */
    fun resetZoom() {
        (pageView as? SubsamplingScaleImageView)?.let {
            if (it.isReady) {
                it.setScaleAndCenter(it.minScale, PointF(it.sWidth / 2f, it.sHeight / 2f))
            }
        }
    }
}

private const val MAX_ZOOM_SCALE = 5F
private const val PANEL_FOCUS_OVERLAY_SETTLE_DELAY_MS = 80L
