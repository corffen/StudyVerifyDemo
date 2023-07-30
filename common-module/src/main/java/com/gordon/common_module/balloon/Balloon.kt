/*
 * Copyright (C) 2019 skydoves
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:Suppress("unused", "RedundantVisibilityModifier")

package com.gordon.common_module.balloon

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Point
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.TextView
import androidx.annotation.AnimRes
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.annotation.DimenRes
import androidx.annotation.DrawableRes
import androidx.annotation.FloatRange
import androidx.annotation.LayoutRes
import androidx.annotation.MainThread
import androidx.annotation.Px
import androidx.annotation.StyleRes
import androidx.core.view.ViewCompat
import androidx.core.view.forEach
import androidx.core.widget.ImageViewCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.gordon.common_module.R
import com.gordon.common_module.balloon.arrow.ArrowOrientation
import com.gordon.common_module.balloon.arrow.ArrowOrientationRules
import com.gordon.common_module.balloon.arrow.ArrowPositionRules
import com.gordon.common_module.balloon.extensions.NO_Float_VALUE
import com.gordon.common_module.balloon.extensions.NO_INT_VALUE
import com.gordon.common_module.balloon.extensions.NO_LONG_VALUE
import com.gordon.common_module.balloon.extensions.SIZE_ARROW_BOUNDARY
import com.gordon.common_module.balloon.extensions.circularRevealed
import com.gordon.common_module.balloon.extensions.circularUnRevealed
import com.gordon.common_module.balloon.extensions.contextColor
import com.gordon.common_module.balloon.extensions.contextDrawable
import com.gordon.common_module.balloon.extensions.dimen
import com.gordon.common_module.balloon.extensions.dimenPixel
import com.gordon.common_module.balloon.extensions.displaySize
import com.gordon.common_module.balloon.extensions.dp
import com.gordon.common_module.balloon.extensions.getIntrinsicHeight
import com.gordon.common_module.balloon.extensions.getStatusBarHeight
import com.gordon.common_module.balloon.extensions.getSumOfIntrinsicWidth
import com.gordon.common_module.balloon.extensions.getViewPointOnScreen
import com.gordon.common_module.balloon.extensions.isExistHorizontalDrawable
import com.gordon.common_module.balloon.extensions.isFinishing
import com.gordon.common_module.balloon.extensions.runOnAfterSDK21
import com.gordon.common_module.balloon.extensions.runOnAfterSDK22
import com.gordon.common_module.balloon.extensions.sumOfCompoundPadding
import com.gordon.common_module.balloon.extensions.visible
import com.gordon.common_module.balloon.listeners.OnBalloonClickListener
import com.gordon.common_module.balloon.listeners.OnBalloonDismissListener
import com.gordon.common_module.balloon.listeners.OnBalloonInitializedListener
import com.gordon.common_module.balloon.listeners.OnBalloonOutsideTouchListener
import com.gordon.common_module.balloon.listeners.OnBalloonOverlayClickListener
import com.gordon.common_module.balloon.overlay.BalloonOverlayAnimation
import com.gordon.common_module.balloon.overlay.BalloonOverlayOval
import com.gordon.common_module.balloon.overlay.BalloonOverlayShape
import com.gordon.common_module.databinding.BalloonLayoutBodyBinding
import com.gordon.common_module.databinding.BalloonLayoutOverlayBinding
import kotlin.math.max
import kotlin.math.roundToInt

@DslMarker
internal annotation class BalloonInlineDsl

@MainThread
@BalloonInlineDsl
public inline fun createBalloon(
    context: Context,
    crossinline block: Balloon.Builder.() -> Unit
): Balloon =
    Balloon.Builder(context).apply(block).build()

public class Balloon private constructor(
    private val context: Context,
    private val builder: Builder
) : DefaultLifecycleObserver {

    private val binding: BalloonLayoutBodyBinding =
        BalloonLayoutBodyBinding.inflate(LayoutInflater.from(context), null, false)

    private val overlayBinding: BalloonLayoutOverlayBinding =
        BalloonLayoutOverlayBinding.inflate(LayoutInflater.from(context), null, false)

    public val bodyWindow: PopupWindow = PopupWindow(
        binding.root,
        FrameLayout.LayoutParams.WRAP_CONTENT,
        FrameLayout.LayoutParams.WRAP_CONTENT
    )

    public val overlayWindow: PopupWindow = PopupWindow(
        overlayBinding.root,
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
    )

    public var isShowing: Boolean = false
        private set

    private var destroyed: Boolean = false

    @JvmField
    public var onBalloonInitializedListener: OnBalloonInitializedListener? =
        builder.onBalloonInitializedListener

    private val handler: Handler by lazy(LazyThreadSafetyMode.NONE) {
        Handler(Looper.getMainLooper())
    }

    private val autoDismissRunnable: AutoDismissRunnable by lazy(
        LazyThreadSafetyMode.NONE
    ) { AutoDismissRunnable(this) }


    init {
        createByBuilder()
    }

    private fun createByBuilder() {
        initializeBackground()
        initializeBalloonRoot()
        initializeBalloonWindow()
        initializeBalloonLayout()
        initializeBalloonContent()
        initializeBalloonOverlay()
        initializeBalloonListeners()

        adjustFitsSystemWindows(binding.root)

        if (builder.lifecycleOwner == null && context is LifecycleOwner) {
            builder.setLifecycleOwner(context)
            context.lifecycle.addObserver(builder.lifecycleObserver ?: this@Balloon)
        } else {
            builder.lifecycleOwner?.lifecycle?.addObserver(
                builder.lifecycleObserver ?: this@Balloon
            )
        }
    }

    private fun adjustFitsSystemWindows(parent: ViewGroup) {
        parent.fitsSystemWindows = false
        (0 until parent.childCount).map { parent.getChildAt(it) }.forEach { child ->
            child.fitsSystemWindows = false
            if (child is ViewGroup) {
                adjustFitsSystemWindows(child)
            }
        }
    }

    private fun getMinArrowPosition(): Float {
        return (builder.arrowSize.toFloat() * builder.arrowAlignAnchorPaddingRatio) +
                builder.arrowAlignAnchorPadding
    }

    private fun getDoubleArrowSize(): Int {
        return builder.arrowSize * 2
    }

    private fun initializeArrow(anchor: View) {
        with(binding.balloonArrow) {
            layoutParams =
                if (builder.arrowWidthSize != 0 && builder.arrowHeightSize != 0) FrameLayout
                    .LayoutParams(
                        builder
                            .arrowWidthSize, builder.arrowHeightSize
                    )
                else {
                    FrameLayout
                        .LayoutParams(
                            builder
                                .arrowSize, builder.arrowSize
                        )
                }
            alpha = builder.alpha
            builder.arrowDrawable?.let { setImageDrawable(it) }
            setPadding(
                builder.arrowLeftPadding,
                builder.arrowTopPadding,
                builder.arrowRightPadding,
                builder.arrowBottomPadding
            )
            if (builder.arrowColor != NO_INT_VALUE) {
                ImageViewCompat.setImageTintList(this, ColorStateList.valueOf(builder.arrowColor))
            } else {
                ImageViewCompat.setImageTintList(
                    this,
                    ColorStateList.valueOf(builder.backgroundColor)
                )
            }
            runOnAfterSDK21 {
                outlineProvider = ViewOutlineProvider.BOUNDS
            }
            binding.balloonCard.post {
                onBalloonInitializedListener?.onBalloonInitialized(getContentView())

                adjustArrowOrientationByRules(anchor)

                @SuppressLint("NewApi")
                when (builder.arrowOrientation) {
                    ArrowOrientation.BOTTOM -> {
                        rotation =
                            if (builder.arrowDrawableDirection == ArrowOrientation.BOTTOM) 0f else 180f
                        x = getArrowConstraintPositionX(anchor)
                        y = binding.balloonCard.y + binding.balloonCard.height - SIZE_ARROW_BOUNDARY
                        ViewCompat.setElevation(this, builder.arrowElevation)
                        if (builder.arrowColorMatchBalloon) {
                            foreground = BitmapDrawable(
                                resources,
                                adjustArrowColorByMatchingCardBackground(
                                    this, x,
                                    binding.balloonCard.height.toFloat()
                                )
                            )
                        }
                    }

                    ArrowOrientation.TOP -> {
                        rotation =
                            if (builder.arrowDrawableDirection == ArrowOrientation.BOTTOM) 180f else 0f
                        x = getArrowConstraintPositionX(anchor)
                        y = binding.balloonCard.y - builder.arrowSize + SIZE_ARROW_BOUNDARY
                        if (builder.arrowColorMatchBalloon) {
                            foreground =
                                BitmapDrawable(
                                    resources,
                                    adjustArrowColorByMatchingCardBackground(this, x, 0f)
                                )
                        }
                    }

                    ArrowOrientation.START -> {
                        rotation = -90f
                        x = binding.balloonCard.x - builder.arrowSize + SIZE_ARROW_BOUNDARY
                        y = getArrowConstraintPositionY(anchor)
                        if (builder.arrowColorMatchBalloon) {
                            foreground =
                                BitmapDrawable(
                                    resources,
                                    adjustArrowColorByMatchingCardBackground(this, 0f, y)
                                )
                        }
                    }

                    ArrowOrientation.END -> {
                        rotation = 90f
                        x = binding.balloonCard.x + binding.balloonCard.width - SIZE_ARROW_BOUNDARY
                        y = getArrowConstraintPositionY(anchor)
                        if (builder.arrowColorMatchBalloon) {
                            foreground = BitmapDrawable(
                                resources,
                                adjustArrowColorByMatchingCardBackground(
                                    this, binding.balloonCard.width.toFloat(),
                                    y
                                )
                            )
                        }
                    }
                }
                visible(builder.isVisibleArrow)
            }
        }
    }

    private fun adjustArrowColorByMatchingCardBackground(
        imageView: ImageView,
        x: Float,
        y: Float
    ): Bitmap {
        imageView.setColorFilter(builder.backgroundColor, PorterDuff.Mode.SRC_IN)
        val oldBitmap = drawableToBitmap(
            imageView.drawable, imageView.drawable.intrinsicWidth,
            imageView.drawable.intrinsicHeight
        )
        val colors: Pair<Int, Int>
        try {
            colors = getColorsFromBalloonCard(x, y)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException(
                "Arrow attached outside balloon. Could not get a matching color."
            )
        }
        val startColor = colors.first
        val endColor = colors.second

        val updatedBitmap =
            Bitmap.createBitmap(oldBitmap.width, oldBitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(updatedBitmap)
        canvas.drawBitmap(oldBitmap, 0f, 0f, null)
        val paint = Paint()
        val shader: LinearGradient = when (builder.arrowOrientation) {
            ArrowOrientation.BOTTOM, ArrowOrientation.START -> {
                LinearGradient(
                    oldBitmap.width.toFloat() / 2 - builder.arrowHalfSize, 0f,
                    oldBitmap.width.toFloat(), 0f, startColor, endColor, Shader.TileMode.CLAMP
                )
            }

            ArrowOrientation.END, ArrowOrientation.TOP -> {
                LinearGradient(
                    oldBitmap.width.toFloat() / 2 + builder.arrowHalfSize, 0f, 0f, 0f,
                    startColor, endColor, Shader.TileMode.CLAMP
                )
            }
        }
        paint.shader = shader
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawRect(0f, 0f, oldBitmap.width.toFloat(), oldBitmap.height.toFloat(), paint)
        imageView.setColorFilter(Color.TRANSPARENT, PorterDuff.Mode.SRC_IN)
        return updatedBitmap
    }

    private fun getColorsFromBalloonCard(x: Float, y: Float): Pair<Int, Int> {
        val bitmap = drawableToBitmap(
            binding.balloonCard.background, binding.balloonCard.width + 1,
            binding.balloonCard.height + 1
        )
        val startColor: Int
        val endColor: Int
        when (builder.arrowOrientation) {
            ArrowOrientation.BOTTOM, ArrowOrientation.TOP -> {
                startColor = bitmap.getPixel((x + builder.arrowHalfSize).toInt(), y.toInt())
                endColor = bitmap.getPixel((x - builder.arrowHalfSize).toInt(), y.toInt())
            }

            ArrowOrientation.START, ArrowOrientation.END -> {
                startColor = bitmap.getPixel(x.toInt(), (y + builder.arrowHalfSize).toInt())
                endColor = bitmap.getPixel(x.toInt(), (y - builder.arrowHalfSize).toInt())
            }
        }
        return Pair(startColor, endColor)
    }

    private fun drawableToBitmap(drawable: Drawable, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    private fun adjustArrowOrientationByRules(anchor: View) {
        if (builder.arrowOrientationRules == ArrowOrientationRules.ALIGN_FIXED) return

        val anchorRect = Rect()
        anchor.getGlobalVisibleRect(anchorRect)

        val location: IntArray = intArrayOf(0, 0)
        bodyWindow.contentView.getLocationOnScreen(location)

        if (builder.arrowOrientation == ArrowOrientation.TOP &&
            location[1] < anchorRect.bottom
        ) {
            builder.setArrowOrientation(ArrowOrientation.BOTTOM)
        } else if (builder.arrowOrientation == ArrowOrientation.BOTTOM &&
            location[1] > anchorRect.top
        ) {
            builder.setArrowOrientation(ArrowOrientation.TOP)
        }

        initializeBalloonContent()
    }

    private fun getArrowConstraintPositionX(anchor: View): Float {
        val balloonX: Int = binding.balloonContent.getViewPointOnScreen().x
        val anchorX: Int = anchor.getViewPointOnScreen().x
        val minPosition = getMinArrowPosition()
        val maxPosition =
            getMeasuredWidth() - minPosition - builder.marginRight - builder.marginLeft
        return when (builder.arrowPositionRules) {
            ArrowPositionRules.ALIGN_BALLOON -> binding.balloonWrapper.width * builder.arrowPosition - builder.arrowHalfSize
            ArrowPositionRules.ALIGN_ANCHOR -> {
                when {
                    anchorX + anchor.width < balloonX -> minPosition
                    balloonX + getMeasuredWidth() < anchorX -> maxPosition
                    else -> {
                        val position =
                            (anchor.width) * builder.arrowPosition + anchorX - balloonX - builder.arrowHalfSize
                        when {
                            position <= getDoubleArrowSize() -> minPosition
                            position > getMeasuredWidth() - getDoubleArrowSize() -> maxPosition
                            else -> position
                        }
                    }
                }
            }
        }
    }

    private fun getArrowConstraintPositionY(anchor: View): Float {
        val statusBarHeight = anchor.getStatusBarHeight(builder.isStatusBarVisible)
        val balloonY: Int = binding.balloonContent.getViewPointOnScreen().y - statusBarHeight
        val anchorY: Int = anchor.getViewPointOnScreen().y - statusBarHeight
        val minPosition = getMinArrowPosition()
        val maxPosition =
            getMeasuredHeight() - minPosition - builder.marginTop - builder.marginBottom
        val arrowHalfSize = builder.arrowSize / 2
        return when (builder.arrowPositionRules) {
            ArrowPositionRules.ALIGN_BALLOON -> binding.balloonWrapper.height * builder.arrowPosition - arrowHalfSize
            ArrowPositionRules.ALIGN_ANCHOR -> {
                when {
                    anchorY + anchor.height < balloonY -> minPosition
                    balloonY + getMeasuredHeight() < anchorY -> maxPosition
                    else -> {
                        val position =
                            (anchor.height) * builder.arrowPosition + anchorY - balloonY - arrowHalfSize
                        when {
                            position <= getDoubleArrowSize() -> minPosition
                            position > getMeasuredHeight() - getDoubleArrowSize() -> maxPosition
                            else -> position
                        }
                    }
                }
            }
        }
    }

    private fun initializeBackground() {
        with(binding.balloonCard) {
            alpha = builder.alpha
            radius = builder.cornerRadius
            ViewCompat.setElevation(this, builder.elevation)
            background = builder.backgroundDrawable ?: GradientDrawable().apply {
                setColor(builder.backgroundColor)
                cornerRadius = builder.cornerRadius
            }
            setPadding(
                builder.paddingLeft,
                builder.paddingTop,
                builder.paddingRight,
                builder.paddingBottom
            )
        }
    }

    private fun initializeBalloonWindow() {
        with(this.bodyWindow) {
            isOutsideTouchable = true
            isFocusable = builder.isFocusable
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            runOnAfterSDK21 {
                elevation = builder.elevation
            }
            setIsAttachedInDecor(builder.isAttachedInDecor)
        }
    }

    private fun initializeBalloonListeners() {
        setOnBalloonClickListener(builder.onBalloonClickListener)
        setOnBalloonDismissListener(builder.onBalloonDismissListener)
        setOnBalloonOutsideTouchListener(builder.onBalloonOutsideTouchListener)
        setOnBalloonTouchListener(builder.onBalloonTouchListener)
        setOnBalloonOverlayClickListener(builder.onBalloonOverlayClickListener)
        setOnBalloonOverlayTouchListener(builder.onBalloonOverlayTouchListener)
    }

    private fun initializeBalloonRoot() {
        with(binding.balloonWrapper) {
            (layoutParams as ViewGroup.MarginLayoutParams).setMargins(
                builder.marginLeft,
                builder.marginTop,
                builder.marginRight,
                builder.marginBottom
            )
        }
    }

    private fun initializeBalloonContent() {
        val paddingSize = builder.arrowSize - SIZE_ARROW_BOUNDARY
        val elevation = builder.elevation.toInt()
        with(binding.balloonContent) {
            when (builder.arrowOrientation) {
                ArrowOrientation.START -> setPadding(paddingSize, elevation, paddingSize, elevation)
                ArrowOrientation.END -> setPadding(paddingSize, elevation, paddingSize, elevation)
                ArrowOrientation.TOP ->
                    setPadding(
                        elevation,
                        paddingSize,
                        elevation,
                        paddingSize.coerceAtLeast(elevation)
                    )

                ArrowOrientation.BOTTOM ->
                    setPadding(
                        elevation,
                        paddingSize,
                        elevation,
                        paddingSize.coerceAtLeast(elevation)
                    )
            }
        }
    }

    private fun initializeBalloonLayout() {
        if (hasCustomLayout()) {
            initializeCustomLayout()
        }
    }

    private fun hasCustomLayout(): Boolean {
        require(builder.layoutRes != null || builder.layout != null) { "The custom layout is null." }
        return true
    }

    private fun initializeCustomLayout() {
        val layout = builder.layoutRes?.let {
            LayoutInflater.from(context).inflate(it, binding.balloonCard, false)
        } ?: builder.layout ?: throw IllegalArgumentException("The custom layout is null.")
        val parentView = layout.parent as? ViewGroup
        parentView?.removeView(layout)
        binding.balloonCard.removeAllViews()
        binding.balloonCard.addView(layout)
        traverseAndMeasureTextWidth(binding.balloonCard)
    }

    private fun initializeBalloonOverlay() {
        if (builder.isVisibleOverlay) with(overlayBinding.balloonOverlayView) {
            overlayColor = builder.overlayColor
            overlayPadding = builder.overlayPadding
            overlayPosition = builder.overlayPosition
            balloonOverlayShape = builder.overlayShape
            overlayPaddingColor = builder.overlayPaddingColor
            overlayWindow.isClippingEnabled = false
        }
    }

    private fun applyBalloonAnimation() {
        if (builder.balloonAnimationStyle == NO_INT_VALUE) {
            when (builder.balloonAnimation) {
                BalloonAnimation.ELASTIC -> bodyWindow.animationStyle = R.style.Balloon_Elastic_Anim
                BalloonAnimation.CIRCULAR -> {
                    bodyWindow.contentView.circularRevealed(builder.circularDuration)
                    bodyWindow.animationStyle = R.style.Balloon_Normal_Dispose_Anim
                }

                BalloonAnimation.FADE -> bodyWindow.animationStyle = R.style.Balloon_Fade_Anim
                BalloonAnimation.OVERSHOOT -> bodyWindow.animationStyle =
                    R.style.Balloon_Overshoot_Anim

                BalloonAnimation.NONE -> bodyWindow.animationStyle = R.style.Balloon_Normal_Anim
            }
        } else {
            bodyWindow.animationStyle = builder.balloonAnimationStyle
        }
    }

    private fun applyBalloonOverlayAnimation() {
        if (builder.balloonOverlayAnimationStyle == NO_INT_VALUE) {
            when (builder.balloonOverlayAnimation) {
                BalloonOverlayAnimation.FADE -> overlayWindow.animationStyle =
                    R.style.Balloon_Fade_Anim

                else -> overlayWindow.animationStyle = R.style.Balloon_Normal_Anim
            }
        } else {
            overlayWindow.animationStyle = builder.balloonAnimationStyle
        }
    }

    @SuppressLint("ResourceType")
    private fun getBalloonHighlightAnimation(): Animation? {
        val animRes = if (builder.balloonHighlightAnimationStyle == NO_INT_VALUE) {
            when (builder.balloonHighlightAnimation) {
                BalloonHighlightAnimation.HEARTBEAT -> {
                    if (builder.isVisibleArrow) {
                        when (builder.arrowOrientation) {
                            ArrowOrientation.TOP -> R.anim.balloon_heartbeat_bottom
                            ArrowOrientation.BOTTOM -> R.anim.balloon_heartbeat_top
                            ArrowOrientation.START -> R.anim.balloon_heartbeat_right
                            ArrowOrientation.END -> R.anim.balloon_heartbeat_left
                        }
                    } else {
                        R.anim.balloon_heartbeat_center
                    }
                }

                BalloonHighlightAnimation.SHAKE -> {
                    when (builder.arrowOrientation) {
                        ArrowOrientation.TOP -> R.anim.balloon_shake_bottom
                        ArrowOrientation.BOTTOM -> R.anim.balloon_shake_top
                        ArrowOrientation.START -> R.anim.balloon_shake_right
                        ArrowOrientation.END -> R.anim.balloon_shake_left
                    }
                }

                BalloonHighlightAnimation.BREATH -> R.anim.balloon_fade
                else -> return null
            }
        } else {
            builder.balloonHighlightAnimationStyle
        }

        return AnimationUtils.loadAnimation(context, animRes)
    }

    private fun startBalloonHighlightAnimation() {
        binding.balloon.post {
            handler.postDelayed(
                {
                    getBalloonHighlightAnimation()?.let { animation ->
                        binding.balloon.startAnimation(animation)
                    }
                },
                builder.balloonHighlightAnimationStartDelay
            )
        }
    }

    private fun stopBalloonHighlightAnimation() {
        binding.balloon.apply {
            animation?.apply {
                cancel()
                reset()
            }
            clearAnimation()
        }
    }


    @MainThread
    private inline fun show(vararg anchors: View, crossinline block: () -> Unit) {
        val mainAnchor = anchors[0]
        if (canShowBalloonWindow(mainAnchor)) {
            mainAnchor.post {
                canShowBalloonWindow(mainAnchor).takeIf { it } ?: return@post

                this.isShowing = true

                val dismissDelay = this.builder.autoDismissDuration
                if (dismissDelay != NO_LONG_VALUE) {
                    dismissWithDelay(dismissDelay)
                }

                if (hasCustomLayout()) {
                    traverseAndMeasureTextWidth(binding.balloonCard)
                }
                this.binding.root.measure(
                    View.MeasureSpec.UNSPECIFIED,
                    View.MeasureSpec.UNSPECIFIED
                )
                this.bodyWindow.width = getMeasuredWidth()
                this.bodyWindow.height = getMeasuredHeight()
                initializeArrow(mainAnchor)
                initializeBalloonContent()

                applyBalloonOverlayAnimation()
                showOverlayWindow(*anchors)
                passTouchEventToAnchor(mainAnchor)

                applyBalloonAnimation()
                startBalloonHighlightAnimation()
                block()
            }
        } else if (builder.dismissWhenShowAgain) {
            dismiss()
        }
    }

    private fun canShowBalloonWindow(anchor: View): Boolean {
        return !isShowing &&
                // If the balloon is already destroyed depending on the lifecycle,
                // We should not allow showing the popupWindow, it's related to `relay()` method. (#46)
                !destroyed &&
                // We should check the current Activity is running.
                // If the Activity is finishing, we can't attach the popupWindow to the Activity's window. (#92)
                !context.isFinishing &&
                // We should check the contentView is already attached to the decorView or backgroundView in the popupWindow.
                // Sometimes there is a concurrency issue between show and dismiss the popupWindow. (#149)
                bodyWindow.contentView.parent == null &&
                // we should check the anchor view is attached to the parent's window.
                ViewCompat.isAttachedToWindow(anchor)
    }

    private fun showOverlayWindow(vararg anchors: View) {
        if (builder.isVisibleOverlay) {
            val mainAnchor = anchors[0]
            if (anchors.size == 1) {
                overlayBinding.balloonOverlayView.anchorView = mainAnchor
            } else {
                overlayBinding.balloonOverlayView.anchorViewList = anchors.toList()
            }
            overlayWindow.showAtLocation(mainAnchor, Gravity.CENTER, 0, 0)
        }
    }

    @MainThread
    private inline fun relay(
        balloon: Balloon,
        crossinline block: (balloon: Balloon) -> Unit
    ): Balloon {
        this.setOnBalloonDismissListener {
            if (!destroyed) {
                block(balloon)
            }
        }
        return balloon
    }

    @JvmOverloads
    public fun showAtCenter(
        anchor: View,
        xOff: Int = 0,
        yOff: Int = 0,
        centerAlign: BalloonCenterAlign = BalloonCenterAlign.TOP
    ) {
        val halfAnchorWidth = (anchor.measuredWidth * 0.5f).roundToInt()
        val halfAnchorHeight = (anchor.measuredHeight * 0.5f).roundToInt()
        val halfBalloonWidth = (getMeasuredWidth() * 0.5f).roundToInt()
        val halfBalloonHeight = (getMeasuredHeight() * 0.5f).roundToInt()
        show(anchor) {
            when (centerAlign) {
                BalloonCenterAlign.TOP ->
                    bodyWindow.showAsDropDown(
                        anchor,
                        (halfAnchorWidth - halfBalloonWidth + xOff),
                        -(getMeasuredHeight() + halfAnchorHeight) + yOff
                    )

                BalloonCenterAlign.BOTTOM ->
                    bodyWindow.showAsDropDown(
                        anchor,
                        (halfAnchorWidth - halfBalloonWidth + xOff),
                        -halfBalloonHeight + halfAnchorWidth + yOff
                    )

                BalloonCenterAlign.START ->
                    bodyWindow.showAsDropDown(
                        anchor,
                        (halfAnchorWidth - getMeasuredWidth() + xOff),
                        (-getMeasuredHeight() + halfAnchorHeight) + yOff
                    )

                BalloonCenterAlign.END ->
                    bodyWindow.showAsDropDown(
                        anchor,
                        (halfAnchorWidth + getMeasuredWidth() + xOff),
                        (-getMeasuredHeight() + halfAnchorHeight) + yOff
                    )
            }
        }
    }

    @JvmOverloads
    public fun relayShowAtCenter(
        balloon: Balloon,
        anchor: View,
        xOff: Int = 0,
        yOff: Int = 0,
        centerAlign: BalloonCenterAlign = BalloonCenterAlign.TOP
    ): Balloon = relay(balloon) { it.showAtCenter(anchor, xOff, yOff, centerAlign) }

    @JvmOverloads
    public fun showAsDropDown(anchor: View, xOff: Int = 0, yOff: Int = 0) {
        show(anchor) { bodyWindow.showAsDropDown(anchor, xOff, yOff) }
    }

    @JvmOverloads
    public fun relayShowAsDropDown(
        balloon: Balloon,
        anchor: View,
        xOff: Int = 0,
        yOff: Int = 0
    ): Balloon =
        relay(balloon) { it.showAsDropDown(anchor, xOff, yOff) }

    @JvmOverloads
    public fun showAlignTop(anchor: View, xOff: Int = 0, yOff: Int = 0) {
        show(anchor) {
            bodyWindow.showAsDropDown(
                anchor,
                ((anchor.measuredWidth / 2) - (getMeasuredWidth() / 2) + xOff),
                -getMeasuredHeight() - anchor.measuredHeight + yOff
            )
        }
    }

    @JvmOverloads
    public fun relayShowAlignTop(
        balloon: Balloon,
        anchor: View,
        xOff: Int = 0,
        yOff: Int = 0
    ): Balloon =
        relay(balloon) { it.showAlignTop(anchor, xOff, yOff) }

    @JvmOverloads
    public fun showAlignBottom(anchor: View, xOff: Int = 0, yOff: Int = 0) {
        show(anchor) {
            bodyWindow.showAsDropDown(
                anchor,
                ((anchor.measuredWidth / 2) - (getMeasuredWidth() / 2) + xOff),
                yOff
            )
        }
    }

    @JvmOverloads
    public fun relayShowAlignBottom(
        balloon: Balloon,
        anchor: View,
        xOff: Int = 0,
        yOff: Int = 0
    ): Balloon =
        relay(balloon) { it.showAlignBottom(anchor, xOff, yOff) }

    @JvmOverloads
    public fun showAlignRight(anchor: View, xOff: Int = 0, yOff: Int = 0) {
        show(anchor) {
            bodyWindow.showAsDropDown(
                anchor,
                anchor.measuredWidth + xOff,
                -(getMeasuredHeight() / 2) - (anchor.measuredHeight / 2) + yOff
            )
        }
    }

    @JvmOverloads
    public fun relayShowAlignRight(
        balloon: Balloon,
        anchor: View,
        xOff: Int = 0,
        yOff: Int = 0
    ): Balloon = relay(
        balloon
    ) {
        it.showAlignRight(anchor, xOff, yOff)
    }

    @JvmOverloads
    public fun showAlignLeft(anchor: View, xOff: Int = 0, yOff: Int = 0) {
        show(anchor) {
            bodyWindow.showAsDropDown(
                anchor,
                -(getMeasuredWidth()) + xOff,
                -(getMeasuredHeight() / 2) - (anchor.measuredHeight / 2) + yOff
            )
        }
    }

    @JvmOverloads
    public fun relayShowAlignLeft(
        balloon: Balloon,
        anchor: View,
        xOff: Int = 0,
        yOff: Int = 0
    ): Balloon =
        relay(balloon) { it.showAlignLeft(anchor, xOff, yOff) }

    @JvmOverloads
    public fun showAlign(
        align: BalloonAlign,
        mainAnchor: View,
        subAnchorList: List<View> = listOf(),
        xOff: Int = 0,
        yOff: Int = 0
    ) {
        val anchors = listOf(mainAnchor) + subAnchorList
        show(*anchors.toTypedArray()) {
            when (align) {
                BalloonAlign.TOP -> bodyWindow.showAsDropDown(
                    mainAnchor,
                    ((mainAnchor.measuredWidth / 2) - (getMeasuredWidth() / 2) + xOff),
                    -getMeasuredHeight() - mainAnchor.measuredHeight + yOff
                )

                BalloonAlign.BOTTOM -> bodyWindow.showAsDropDown(
                    mainAnchor,
                    ((mainAnchor.measuredWidth / 2) - (getMeasuredWidth() / 2) + xOff),
                    yOff
                )

                BalloonAlign.END -> bodyWindow.showAsDropDown(
                    mainAnchor,
                    mainAnchor.measuredWidth + xOff,
                    -(getMeasuredHeight() / 2) - (mainAnchor.measuredHeight / 2) + yOff
                )

                BalloonAlign.START -> bodyWindow.showAsDropDown(
                    mainAnchor,
                    -(getMeasuredWidth()) + xOff,
                    -(getMeasuredHeight() / 2) - (mainAnchor.measuredHeight / 2) + yOff
                )
            }
        }
    }

    @JvmOverloads
    public fun relayShowAlign(
        align: BalloonAlign,
        balloon: Balloon,
        anchor: View,
        xOff: Int = 0,
        yOff: Int = 0
    ): Balloon {
        return relay(balloon) {
            when (align) {
                BalloonAlign.TOP -> it.showAlignTop(anchor, xOff, yOff)
                BalloonAlign.BOTTOM -> it.showAlignBottom(anchor, xOff, yOff)
                BalloonAlign.END -> it.showAlignRight(anchor, xOff, yOff)
                BalloonAlign.START -> it.showAlignLeft(anchor, xOff, yOff)
            }
        }
    }

    @JvmOverloads
    public fun update(anchor: View, xOff: Int = 0, yOff: Int = 0) {
        update(anchor = anchor) {
            this.bodyWindow.update(anchor, xOff, yOff, getMeasuredWidth(), getMeasuredHeight())
            if (builder.isVisibleOverlay) {
                overlayBinding.balloonOverlayView.forceInvalidate()
            }
        }
    }

    @MainThread
    private inline fun update(anchor: View, crossinline block: () -> Unit) {
        if (isShowing) {
            initializeArrow(anchor)
            block()
        }
    }

    public fun dismiss() {
        if (this.isShowing) {
            val dismissWindow: () -> Unit = {
                this.isShowing = false
                this.bodyWindow.dismiss()
                this.overlayWindow.dismiss()
                this.handler.removeCallbacks(autoDismissRunnable)
            }
            if (this.builder.balloonAnimation == BalloonAnimation.CIRCULAR) {
                this.bodyWindow.contentView.circularUnRevealed(builder.circularDuration) {
                    dismissWindow()
                }
            } else {
                dismissWindow()
            }
        }
    }

    public fun dismissWithDelay(delay: Long): Boolean =
        handler.postDelayed(autoDismissRunnable, delay)

    public fun setOnBalloonClickListener(onBalloonClickListener: OnBalloonClickListener?) {
        this.binding.balloonWrapper.setOnClickListener {
            onBalloonClickListener?.onBalloonClick(it)
            if (builder.dismissWhenClicked) dismiss()
        }
    }

    public fun setOnBalloonClickListener(block: (View) -> Unit) {
        setOnBalloonClickListener(OnBalloonClickListener(block))
    }

    public fun setOnBalloonInitializedListener(onBalloonInitializedListener: OnBalloonInitializedListener?) {
        this.onBalloonInitializedListener = onBalloonInitializedListener
    }

    public fun setOnBalloonInitializedListener(block: (View) -> Unit) {
        setOnBalloonInitializedListener(OnBalloonInitializedListener(block))
    }

    public fun setOnBalloonDismissListener(onBalloonDismissListener: OnBalloonDismissListener?) {
        this.bodyWindow.setOnDismissListener {
            stopBalloonHighlightAnimation()
            this@Balloon.dismiss()
            onBalloonDismissListener?.onBalloonDismiss()
        }
    }

    public fun setOnBalloonDismissListener(block: () -> Unit) {
        setOnBalloonDismissListener(OnBalloonDismissListener(block))
    }

    public fun setOnBalloonOutsideTouchListener(onBalloonOutsideTouchListener: OnBalloonOutsideTouchListener?) {
        this.bodyWindow.setTouchInterceptor(
            object : View.OnTouchListener {
                @SuppressLint("ClickableViewAccessibility")
                override fun onTouch(view: View, event: MotionEvent): Boolean {
                    if (event.action == MotionEvent.ACTION_OUTSIDE) {
                        if (builder.dismissWhenTouchOutside) {
                            this@Balloon.dismiss()
                        }
                        onBalloonOutsideTouchListener?.onBalloonOutsideTouch(view, event)
                        return true
                    }
                    return false
                }
            }
        )
    }

    public fun setOnBalloonOutsideTouchListener(block: (View, MotionEvent) -> Unit) {
        setOnBalloonOutsideTouchListener(
            OnBalloonOutsideTouchListener(block)
        )
    }

    public fun setOnBalloonTouchListener(onTouchListener: View.OnTouchListener?) {
        if (onTouchListener != null) {
            this.bodyWindow.setTouchInterceptor(onTouchListener)
        }
    }

    public fun setOnBalloonOverlayTouchListener(onTouchListener: View.OnTouchListener?) {
        if (onTouchListener != null) {
            this.overlayWindow.setTouchInterceptor(onTouchListener)
        }
    }

    public fun setOnBalloonOverlayTouchListener(block: (View, MotionEvent) -> Boolean) {
        setOnBalloonOverlayTouchListener(
            View.OnTouchListener(block)
        )
    }

    private fun passTouchEventToAnchor(anchor: View) {
        if (!this.builder.passTouchEventToAnchor) return
        setOnBalloonOverlayTouchListener { view, event ->
            view.performClick()
            val rect = Rect()
            anchor.getGlobalVisibleRect(rect)
            if (rect.contains(event.rawX.toInt(), event.rawY.toInt())) {
                anchor.rootView.dispatchTouchEvent(event)
                true
            } else false
        }
    }

    public fun setOnBalloonOverlayClickListener(onBalloonOverlayClickListener: OnBalloonOverlayClickListener?) {
        this.overlayBinding.root.setOnClickListener {
            onBalloonOverlayClickListener?.onBalloonOverlayClick()
            if (builder.dismissWhenOverlayClicked) dismiss()
        }
    }

    public fun setOnBalloonOverlayClickListener(block: () -> Unit) {
        setOnBalloonOverlayClickListener(
            OnBalloonOverlayClickListener(block)
        )
    }

    public fun setIsAttachedInDecor(value: Boolean): Balloon = apply {
        runOnAfterSDK22 {
            this.bodyWindow.isAttachedInDecor = value
        }
    }

    public fun getMeasuredWidth(): Int {
        val displayWidth = displaySize.x
        return when {
            builder.widthRatio != NO_Float_VALUE ->
                (displayWidth * builder.widthRatio).toInt()

            builder.minWidthRatio != NO_Float_VALUE || builder.maxWidthRatio != NO_Float_VALUE -> {
                val maxWidthRatio =
                    if (builder.maxWidthRatio != NO_Float_VALUE) builder.maxWidthRatio else 1f
                binding.root.measuredWidth.coerceIn(
                    (displayWidth * builder.minWidthRatio).toInt(),
                    (displayWidth * maxWidthRatio).toInt()
                )
            }

            builder.width != BalloonSizeSpec.WRAP -> builder.width.coerceAtMost(displayWidth)
            else -> binding.root.measuredWidth.coerceIn(builder.minWidth, builder.maxWidth)
        }
    }

    private fun measureTextWidth(textView: TextView, rootView: View) {
        with(textView) {
            var measuredTextWidth = textView.paint.measureText(textView.text.toString()).toInt()
            if (compoundDrawablesRelative.isExistHorizontalDrawable()) {
                minHeight = compoundDrawablesRelative.getIntrinsicHeight()
                measuredTextWidth += compoundDrawablesRelative.getSumOfIntrinsicWidth() + sumOfCompoundPadding
            } else if (compoundDrawables.isExistHorizontalDrawable()) {
                minHeight = compoundDrawables.getIntrinsicHeight()
                measuredTextWidth += compoundDrawables.getSumOfIntrinsicWidth() + sumOfCompoundPadding
            }
            maxWidth = getMeasuredTextWidth(measuredTextWidth, rootView)
        }
    }

    private fun traverseAndMeasureTextWidth(parent: ViewGroup) {
        parent.forEach { child ->
            if (child is TextView) {
                measureTextWidth(child, parent)
            } else if (child is ViewGroup) {
                traverseAndMeasureTextWidth(child)
            }
        }
    }

    private fun getMeasuredTextWidth(measuredWidth: Int, rootView: View): Int {
        val displayWidth = displaySize.x
        val spaces =
            rootView.paddingLeft + rootView.paddingRight + 0 + builder.marginRight + builder.marginLeft + (builder.arrowSize * 2)
        val maxTextWidth = builder.maxWidth - spaces

        return when {
            builder.widthRatio != NO_Float_VALUE ->
                (displayWidth * builder.widthRatio).toInt() - spaces

            builder.minWidthRatio != NO_Float_VALUE || builder.maxWidthRatio != NO_Float_VALUE -> {
                val maxWidthRatio =
                    if (builder.maxWidthRatio != NO_Float_VALUE) builder.maxWidthRatio else 1f
                measuredWidth.coerceAtMost((displayWidth * maxWidthRatio).toInt() - spaces)
            }

            builder.width != BalloonSizeSpec.WRAP && builder.width <= displayWidth ->
                builder.width - spaces

            else -> measuredWidth.coerceAtMost(maxTextWidth)
        }
    }

    public fun getMeasuredHeight(): Int {
        if (builder.height != BalloonSizeSpec.WRAP) {
            return builder.height
        }
        return this.binding.root.measuredHeight
    }

    public fun getContentView(): ViewGroup {
        return binding.balloonCard
    }

    public fun getBalloonArrowView(): View {
        return binding.balloonArrow
    }

    override fun onPause(owner: LifecycleOwner) {
        super.onPause(owner)
        if (builder.dismissWhenLifecycleOnPause) {
            dismiss()
        }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)
        this.destroyed = true
        this.overlayWindow.dismiss()
        this.bodyWindow.dismiss()
        this.builder.lifecycleOwner?.lifecycle?.removeObserver(this)
    }

    @BalloonInlineDsl
    public class Builder(private val context: Context) {
        public var width: Int = BalloonSizeSpec.WRAP

        public var minWidth: Int = 0

        public var maxWidth: Int = displaySize.x

        @FloatRange(from = 0.0, to = 1.0)
        public var widthRatio: Float = NO_Float_VALUE

        @FloatRange(from = 0.0, to = 1.0)
        public var minWidthRatio: Float = NO_Float_VALUE

        @FloatRange(from = 0.0, to = 1.0)
        public var maxWidthRatio: Float = NO_Float_VALUE

        @Px
        @set:JvmSynthetic
        public var height: Int = BalloonSizeSpec.WRAP

        @Px
        @set:JvmSynthetic
        public var paddingLeft: Int = 0

        @Px
        @set:JvmSynthetic
        public var paddingTop: Int = 0

        @Px
        @set:JvmSynthetic
        public var paddingRight: Int = 0

        @Px
        @set:JvmSynthetic
        public var paddingBottom: Int = 0

        @Px
        @set:JvmSynthetic
        public var marginRight: Int = 0

        @Px
        @set:JvmSynthetic
        public var marginLeft: Int = 0

        @Px
        @set:JvmSynthetic
        public var marginTop: Int = 0

        @Px
        @set:JvmSynthetic
        public var marginBottom: Int = 0

        @set:JvmSynthetic
        public var isVisibleArrow: Boolean = true

        @ColorInt
        @set:JvmSynthetic
        public var arrowColor: Int = NO_INT_VALUE

        @set:JvmSynthetic
        public var arrowColorMatchBalloon: Boolean = false

        @Px
        @set:JvmSynthetic
        public var arrowSize: Int = 12.dp

        @Px
        @set:JvmSynthetic
        public var arrowWidthSize: Int = 0.dp

        @Px
        @set:JvmSynthetic
        public var arrowHeightSize: Int = 0.dp

        public val arrowHalfSize: Float
            @JvmSynthetic @Px
            inline get() = arrowSize * 0.5f

        @FloatRange(from = 0.0, to = 1.0)
        @set:JvmSynthetic
        public var arrowPosition: Float = 0.5f

        @set:JvmSynthetic
        public var arrowPositionRules: ArrowPositionRules = ArrowPositionRules.ALIGN_BALLOON

        @set:JvmSynthetic
        public var arrowOrientationRules: ArrowOrientationRules =
            ArrowOrientationRules.ALIGN_ANCHOR

        @set:JvmSynthetic
        public var arrowOrientation: ArrowOrientation = ArrowOrientation.BOTTOM

        @set:JvmSynthetic
        public var arrowDrawable: Drawable? = null

        @set:JvmSynthetic
        public var arrowDrawableDirection: ArrowOrientation = ArrowOrientation.TOP

        @set:JvmSynthetic
        public var arrowLeftPadding: Int = 0

        @set:JvmSynthetic
        public var arrowRightPadding: Int = 0

        @set:JvmSynthetic
        public var arrowTopPadding: Int = 0

        @set:JvmSynthetic
        public var arrowBottomPadding: Int = 0

        @set:JvmSynthetic
        public var arrowAlignAnchorPadding: Int = 0

        @set:JvmSynthetic
        public var arrowAlignAnchorPaddingRatio: Float = 2.5f

        @set:JvmSynthetic
        public var arrowElevation: Float = 0f

        @ColorInt
        @set:JvmSynthetic
        public var backgroundColor: Int = Color.BLACK

        @set:JvmSynthetic
        public var backgroundDrawable: Drawable? = null

        @Px
        @set:JvmSynthetic
        public var cornerRadius: Float = 5f.dp


        @FloatRange(from = 0.0, to = 1.0)
        @set:JvmSynthetic
        public var alpha: Float = 1f

        @set:JvmSynthetic
        public var elevation: Float = 2f.dp

        @set:JvmSynthetic
        public var layout: View? = null

        @LayoutRes
        @set:JvmSynthetic
        public var layoutRes: Int? = null

        @set:JvmSynthetic
        public var isVisibleOverlay: Boolean = false

        @ColorInt
        @set:JvmSynthetic
        public var overlayColor: Int = Color.TRANSPARENT

        @Px
        @set:JvmSynthetic
        public var overlayPadding: Float = 0f

        @ColorInt
        @set:JvmSynthetic
        public var overlayPaddingColor: Int = Color.TRANSPARENT

        @set:JvmSynthetic
        public var overlayPosition: Point? = null

        @set:JvmSynthetic
        public var overlayShape: BalloonOverlayShape = BalloonOverlayOval

        @set:JvmSynthetic
        public var onBalloonClickListener: OnBalloonClickListener? = null

        @set:JvmSynthetic
        public var onBalloonDismissListener: OnBalloonDismissListener? = null

        @set:JvmSynthetic
        public var onBalloonInitializedListener: OnBalloonInitializedListener? = null

        @set:JvmSynthetic
        public var onBalloonOutsideTouchListener: OnBalloonOutsideTouchListener? = null

        @set:JvmSynthetic
        public var onBalloonTouchListener: View.OnTouchListener? = null

        @set:JvmSynthetic
        public var onBalloonOverlayTouchListener: View.OnTouchListener? = null

        @set:JvmSynthetic
        public var onBalloonOverlayClickListener: OnBalloonOverlayClickListener? = null

        @set:JvmSynthetic
        public var dismissWhenTouchOutside: Boolean = true

        @set:JvmSynthetic
        public var dismissWhenShowAgain: Boolean = false

        @set:JvmSynthetic
        public var dismissWhenClicked: Boolean = false

        @set:JvmSynthetic
        public var dismissWhenOverlayClicked: Boolean = true

        @set:JvmSynthetic
        public var dismissWhenLifecycleOnPause: Boolean = false

        @set:JvmSynthetic
        public var passTouchEventToAnchor: Boolean = false

        @set:JvmSynthetic
        public var autoDismissDuration: Long = NO_LONG_VALUE

        @set:JvmSynthetic
        public var lifecycleOwner: LifecycleOwner? = null

        @set:JvmSynthetic
        public var lifecycleObserver: LifecycleObserver? = null

        @StyleRes
        @set:JvmSynthetic
        public var balloonAnimationStyle: Int = NO_INT_VALUE

        @StyleRes
        @set:JvmSynthetic
        public var balloonOverlayAnimationStyle: Int = NO_INT_VALUE

        @set:JvmSynthetic
        public var balloonAnimation: BalloonAnimation = BalloonAnimation.FADE

        @set:JvmSynthetic
        public var balloonOverlayAnimation: BalloonOverlayAnimation = BalloonOverlayAnimation.FADE

        @set:JvmSynthetic
        public var circularDuration: Long = 500L

        @set:JvmSynthetic
        public var balloonHighlightAnimation: BalloonHighlightAnimation =
            BalloonHighlightAnimation.NONE

        @StyleRes
        @set:JvmSynthetic
        public var balloonHighlightAnimationStyle: Int = NO_INT_VALUE

        @set:JvmSynthetic
        public var balloonHighlightAnimationStartDelay: Long = 0L


        @set:JvmSynthetic
        public var preferenceName: String? = null

        @set:JvmSynthetic
        public var showTimes: Int = 1

        @set:JvmSynthetic
        public var runIfReachedShowCounts: (() -> Unit)? = null

        @set:JvmSynthetic
        public var isFocusable: Boolean = true

        @set:JvmSynthetic
        public var isStatusBarVisible: Boolean = true

        @set:JvmSynthetic
        public var isAttachedInDecor: Boolean = true

        public fun setWidth(value: Int): Builder = apply {
            require(
                value > 0 || value == BalloonSizeSpec.WRAP
            ) { "The width of the balloon must bigger than zero." }
            this.width = value.dp
        }

        public fun setWidthResource(@DimenRes value: Int): Builder = apply {
            this.width = context.dimenPixel(value)
        }

        public fun setMinWidth(value: Int): Builder = apply {
            this.minWidth = value.dp
        }

        public fun setMinWidthResource(@DimenRes value: Int): Builder = apply {
            this.minWidth = context.dimenPixel(value)
        }

        public fun setMaxWidth(value: Int): Builder = apply {
            this.maxWidth = value.dp
        }

        public fun setMaxWidthResource(@DimenRes value: Int): Builder = apply {
            this.maxWidth = context.dimenPixel(value)
        }

        public fun setWidthRatio(
            @FloatRange(from = 0.0, to = 1.0) value: Float
        ): Builder = apply { this.widthRatio = value }

        public fun setMinWidthRatio(
            @FloatRange(from = 0.0, to = 1.0) value: Float
        ): Builder = apply { this.minWidthRatio = value }

        public fun setMaxWidthRatio(
            @FloatRange(from = 0.0, to = 1.0) value: Float
        ): Builder = apply { this.maxWidthRatio = value }

        public fun setHeight(value: Int): Builder = apply {
            require(
                value > 0 || value == BalloonSizeSpec.WRAP
            ) { "The height of the balloon must bigger than zero." }
            this.height = value.dp
        }


        public fun setPadding(value: Int): Builder = apply {
            setPaddingLeft(value)
            setPaddingTop(value)
            setPaddingRight(value)
            setPaddingBottom(value)
        }

        public fun setPaddingResource(@DimenRes value: Int): Builder = apply {
            val padding = context.dimenPixel(value)
            this.paddingLeft = padding
            this.paddingTop = padding
            this.paddingRight = padding
            this.paddingBottom = padding
        }

        public fun setPaddingHorizontal(value: Int): Builder = apply {
            setPaddingLeft(value)
            setPaddingRight(value)
        }

        public fun setPaddingHorizontalResource(@DimenRes value: Int): Builder = apply {
            setPaddingLeftResource(value)
            setPaddingRightResource(value)
        }

        public fun setPaddingVertical(value: Int): Builder = apply {
            setPaddingTop(value)
            setPaddingBottom(value)
        }

        public fun setPaddingVerticalResource(@DimenRes value: Int): Builder = apply {
            setPaddingTopResource(value)
            setPaddingBottomResource(value)
        }

        public fun setPaddingLeft(value: Int): Builder = apply { this.paddingLeft = value.dp }

        public fun setPaddingLeftResource(@DimenRes value: Int): Builder = apply {
            this.paddingLeft = context.dimenPixel(value)
        }

        public fun setPaddingTop(value: Int): Builder = apply { this.paddingTop = value.dp }

        public fun setPaddingTopResource(@DimenRes value: Int): Builder = apply {
            this.paddingTop = context.dimenPixel(value)
        }

        public fun setPaddingRight(value: Int): Builder = apply {
            this.paddingRight = value.dp
        }

        public fun setPaddingRightResource(@DimenRes value: Int): Builder = apply {
            this.paddingRight = context.dimenPixel(value)
        }

        public fun setPaddingBottom(value: Int): Builder = apply {
            this.paddingBottom = value.dp
        }

        public fun setPaddingBottomResource(@DimenRes value: Int): Builder = apply {
            this.paddingBottom = context.dimenPixel(value)
        }

        public fun setMargin(value: Int): Builder = apply {
            setMarginLeft(value)
            setMarginTop(value)
            setMarginRight(value)
            setMarginBottom(value)
        }

        public fun setMarginResource(@DimenRes value: Int): Builder = apply {
            val margin = context.dimenPixel(value)
            this.marginLeft = margin
            this.marginTop = margin
            this.marginRight = margin
            this.marginBottom = margin
        }

        public fun setMarginHorizontal(value: Int): Builder = apply {
            setMarginLeft(value)
            setMarginRight(value)
        }

        public fun setMarginHorizontalResource(@DimenRes value: Int): Builder = apply {
            setMarginLeftResource(value)
            setMarginRightResource(value)
        }

        public fun setMarginVertical(value: Int): Builder = apply {
            setMarginTop(value)
            setMarginBottom(value)
        }

        public fun setMarginVerticalResource(@DimenRes value: Int): Builder = apply {
            setMarginTopResource(value)
            setMarginBottomResource(value)
        }

        public fun setMarginLeft(value: Int): Builder = apply {
            this.marginLeft = value.dp
        }

        public fun setMarginLeftResource(@DimenRes value: Int): Builder = apply {
            this.marginLeft = context.dimenPixel(value)
        }

        public fun setMarginTop(value: Int): Builder = apply {
            this.marginTop = value.dp
        }

        public fun setMarginTopResource(@DimenRes value: Int): Builder = apply {
            this.marginTop = context.dimenPixel(value)
        }

        public fun setMarginRight(value: Int): Builder = apply {
            this.marginRight = value.dp
        }

        public fun setMarginRightResource(@DimenRes value: Int): Builder = apply {
            this.marginRight = context.dimenPixel(value)
        }

        public fun setMarginBottom(value: Int): Builder = apply {
            this.marginBottom = value.dp
        }

        public fun setMarginBottomResource(@DimenRes value: Int): Builder = apply {
            this.marginBottom = context.dimenPixel(value)
        }

        public fun setIsVisibleArrow(value: Boolean): Builder =
            apply { this.isVisibleArrow = value }

        public fun setArrowColor(@ColorInt value: Int): Builder = apply { this.arrowColor = value }

        public fun setArrowColorMatchBalloon(value: Boolean): Builder = apply {
            this.arrowColorMatchBalloon = value
        }

        public fun setArrowColorResource(@ColorRes value: Int): Builder = apply {
            this.arrowColor = context.contextColor(value)
        }

        public fun setArrowSize(value: Int): Builder = apply {
            this.arrowSize =
                if (value == BalloonSizeSpec.WRAP) {
                    BalloonSizeSpec.WRAP
                } else {
                    value.dp
                }
        }

        public fun setArrowWidthSize(value: Int): Builder = apply {
            this.arrowWidthSize =
                if (value == BalloonSizeSpec.WRAP) {
                    BalloonSizeSpec.WRAP
                } else {
                    value.dp
                }
        }

        public fun setArrowHeightSize(value: Int): Builder = apply {
            this.arrowHeightSize =
                if (value == BalloonSizeSpec.WRAP) {
                    BalloonSizeSpec.WRAP
                } else {
                    value.dp
                }
        }

        public fun setArrowSizeResource(@DimenRes value: Int): Builder = apply {
            this.arrowSize = context.dimenPixel(value)
        }

        public fun setArrowPosition(
            @FloatRange(from = 0.0, to = 1.0) value: Float
        ): Builder = apply { this.arrowPosition = value }

        public fun setArrowPositionRules(value: ArrowPositionRules): Builder =
            apply { this.arrowPositionRules = value }

        public fun setArrowOrientation(value: ArrowOrientation): Builder = apply {
            this.arrowOrientation = value
        }

        public fun setArrowOrientationRules(value: ArrowOrientationRules): Builder = apply {
            this.arrowOrientationRules = value
        }

        public fun setArrowDrawable(value: Drawable?): Builder = apply {
            this.arrowDrawable = value?.mutate()
            if (value != null && arrowSize == BalloonSizeSpec.WRAP) {
                arrowSize = max(value.intrinsicWidth, value.intrinsicHeight)
            }
        }

        public fun setArrowDrawableDirection(value: ArrowOrientation): Builder = apply {
            this.arrowDrawableDirection = value
        }

        public fun setArrowDrawableResource(@DrawableRes value: Int): Builder = apply {
            setArrowDrawable(context.contextDrawable(value))
        }

        public fun setArrowLeftPadding(value: Int): Builder = apply {
            this.arrowLeftPadding = value.dp
        }

        public fun setArrowLeftPaddingResource(@DimenRes value: Int): Builder = apply {
            this.arrowLeftPadding = context.dimenPixel(value)
        }

        public fun setArrowRightPadding(value: Int): Builder = apply {
            this.arrowRightPadding = value.dp
        }

        public fun setArrowRightPaddingResource(@DimenRes value: Int): Builder = apply {
            this.arrowRightPadding = context.dimenPixel(value)
        }

        public fun setArrowTopPadding(value: Int): Builder = apply {
            this.arrowTopPadding = value.dp
        }

        public fun setArrowTopPaddingResource(@DimenRes value: Int): Builder = apply {
            this.arrowTopPadding = context.dimenPixel(value)
        }

        public fun setArrowBottomPadding(value: Int): Builder = apply {
            this.arrowBottomPadding = value.dp
        }

        public fun setArrowBottomPaddingResource(@DimenRes value: Int): Builder = apply {
            this.arrowBottomPadding = context.dimenPixel(value)
        }

        public fun setArrowAlignAnchorPadding(value: Int): Builder = apply {
            this.arrowAlignAnchorPadding = value.dp
        }

        public fun setArrowAlignAnchorPaddingResource(@DimenRes value: Int): Builder = apply {
            this.arrowAlignAnchorPadding = context.dimenPixel(value)
        }

        public fun setArrowAlignAnchorPaddingRatio(value: Float): Builder = apply {
            this.arrowAlignAnchorPaddingRatio = value
        }

        public fun setArrowElevation(value: Int): Builder = apply {
            this.arrowElevation = value.dp.toFloat()
        }

        public fun setArrowElevationResource(@DimenRes value: Int): Builder = apply {
            this.arrowElevation = context.dimen(value)
        }

        public fun setBackgroundColor(@ColorInt value: Int): Builder =
            apply { this.backgroundColor = value }

        public fun setBackgroundColorResource(@ColorRes value: Int): Builder = apply {
            this.backgroundColor = context.contextColor(value)
        }

        public fun setBackgroundDrawable(value: Drawable?): Builder = apply {
            this.backgroundDrawable = value?.mutate()
        }

        public fun setBackgroundDrawableResource(@DrawableRes value: Int): Builder = apply {
            this.backgroundDrawable = context.contextDrawable(value)?.mutate()
        }

        public fun setCornerRadius(value: Float): Builder = apply {
            this.cornerRadius = value.dp
        }


        public fun setAlpha(@FloatRange(from = 0.0, to = 1.0) value: Float): Builder = apply {
            this.alpha = value
        }

        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        public fun setElevation(value: Int): Builder = apply {
            this.elevation = value.dp.toFloat()
        }

        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        public fun setElevationResource(@DimenRes value: Int): Builder = apply {
            this.elevation = context.dimen(value)
        }

        public fun setLayout(@LayoutRes layoutRes: Int): Builder =
            apply { this.layoutRes = layoutRes }

        public fun setLayout(layout: View): Builder = apply { this.layout = layout }


        public fun setIsVisibleOverlay(value: Boolean): Builder =
            apply { this.isVisibleOverlay = value }

        public fun setOverlayColor(@ColorInt value: Int): Builder =
            apply { this.overlayColor = value }

        public fun setOverlayColorResource(@ColorRes value: Int): Builder = apply {
            this.overlayColor = context.contextColor(value)
        }

        public fun setOverlayPadding(value: Float): Builder =
            apply { this.overlayPadding = value.dp }

        public fun setOverlayPaddingResource(@DimenRes value: Int): Builder = apply {
            this.overlayPadding = context.dimen(value)
        }

        public fun setOverlayPaddingColor(@ColorInt value: Int): Builder =
            apply { this.overlayPaddingColor = value }

        public fun setOverlayPaddingColorResource(@ColorRes value: Int): Builder = apply {
            this.overlayPaddingColor = context.contextColor(value)
        }

        public fun setOverlayPosition(value: Point): Builder =
            apply { this.overlayPosition = value }

        public fun setOverlayShape(value: BalloonOverlayShape): Builder =
            apply { this.overlayShape = value }

        public fun setIsStatusBarVisible(value: Boolean): Builder = apply {
            this.isStatusBarVisible = value
        }

        public fun setIsAttachedInDecor(value: Boolean): Builder = apply {
            this.isAttachedInDecor = value
        }

        public fun setLifecycleOwner(value: LifecycleOwner?): Builder =
            apply { this.lifecycleOwner = value }

        public fun setLifecycleObserver(value: LifecycleObserver): Builder =
            apply { this.lifecycleObserver = value }

        public fun setBalloonAnimation(value: BalloonAnimation): Builder = apply {
            this.balloonAnimation = value
            if (value == BalloonAnimation.CIRCULAR) {
                setFocusable(false)
            }
        }

        public fun setBalloonAnimationStyle(@StyleRes value: Int): Builder = apply {
            this.balloonAnimationStyle = value
        }

        public fun setBalloonOverlayAnimation(value: BalloonOverlayAnimation): Builder = apply {
            this.balloonOverlayAnimation = value
        }

        public fun setBalloonOverlayAnimationStyle(@StyleRes value: Int): Builder = apply {
            this.balloonOverlayAnimationStyle = value
        }

        public fun setCircularDuration(value: Long): Builder = apply {
            this.circularDuration = value
        }

        @JvmOverloads
        public fun setBalloonHighlightAnimation(
            value: BalloonHighlightAnimation,
            startDelay: Long = 0L
        ): Builder = apply {
            this.balloonHighlightAnimation = value
            this.balloonHighlightAnimationStartDelay = startDelay
        }

        @SuppressLint("ResourceType")
        @JvmOverloads
        public fun setBalloonHighlightAnimationResource(
            @AnimRes value: Int,
            startDelay: Long = 0L
        ): Builder = apply {
            this.balloonHighlightAnimationStyle = value
            this.balloonHighlightAnimationStartDelay = startDelay
        }

        public fun setOnBalloonClickListener(value: OnBalloonClickListener): Builder = apply {
            this.onBalloonClickListener = value
        }

        public fun setOnBalloonDismissListener(value: OnBalloonDismissListener): Builder = apply {
            this.onBalloonDismissListener = value
        }

        public fun setOnBalloonInitializedListener(value: OnBalloonInitializedListener): Builder =
            apply {
                this.onBalloonInitializedListener = value
            }

        public fun setOnBalloonOutsideTouchListener(value: OnBalloonOutsideTouchListener): Builder =
            apply {
                this.onBalloonOutsideTouchListener = value
            }

        public fun setOnBalloonTouchListener(value: View.OnTouchListener): Builder = apply {
            this.onBalloonTouchListener = value
        }

        public fun setOnBalloonOverlayClickListener(value: OnBalloonOverlayClickListener): Builder =
            apply {
                this.onBalloonOverlayClickListener = value
            }

        @JvmSynthetic
        public fun setOnBalloonClickListener(block: (View) -> Unit): Builder = apply {
            this.onBalloonClickListener = OnBalloonClickListener(block)
        }

        @JvmSynthetic
        public fun setOnBalloonDismissListener(block: () -> Unit): Builder = apply {
            this.onBalloonDismissListener = OnBalloonDismissListener(block)
        }

        @JvmSynthetic
        public fun setOnBalloonInitializedListener(block: (View) -> Unit): Builder = apply {
            this.onBalloonInitializedListener = OnBalloonInitializedListener(block)
        }

        @JvmSynthetic
        public fun setOnBalloonOutsideTouchListener(block: (View, MotionEvent) -> Unit): Builder =
            apply {
                this.onBalloonOutsideTouchListener = OnBalloonOutsideTouchListener(block)
                setDismissWhenTouchOutside(false)
            }

        public fun setOnBalloonOverlayClickListener(block: () -> Unit): Builder = apply {
            this.onBalloonOverlayClickListener = OnBalloonOverlayClickListener(block)
        }

        public fun setDismissWhenTouchOutside(value: Boolean): Builder = apply {
            this.dismissWhenTouchOutside = value
            if (!value) {
                setFocusable(value)
            }
        }

        public fun setOnBalloonOverlayTouchListener(value: View.OnTouchListener): Builder = apply {
            this.onBalloonOverlayTouchListener = value
            setDismissWhenOverlayClicked(false)
        }

        public fun setDismissWhenShowAgain(value: Boolean): Builder = apply {
            this.dismissWhenShowAgain = value
        }

        public fun setDismissWhenClicked(value: Boolean): Builder =
            apply { this.dismissWhenClicked = value }

        public fun setDismissWhenLifecycleOnPause(value: Boolean): Builder = apply {
            this.dismissWhenLifecycleOnPause = value
        }

        public fun setDismissWhenOverlayClicked(value: Boolean): Builder = apply {
            this.dismissWhenOverlayClicked = value
        }

        public fun setShouldPassTouchEventToAnchor(value: Boolean): Builder = apply {
            this.passTouchEventToAnchor = value
        }

        public fun setAutoDismissDuration(value: Long): Builder =
            apply { this.autoDismissDuration = value }

        public fun setPreferenceName(value: String): Builder = apply { this.preferenceName = value }

        public fun setShowCounts(value: Int): Builder = apply { this.showTimes = value }

        public fun runIfReachedShowCounts(block: () -> Unit): Builder = apply {
            runIfReachedShowCounts = block
        }

        public fun runIfReachedShowCounts(runnable: Runnable): Builder = apply {
            runIfReachedShowCounts { runnable.run() }
        }

        public fun setFocusable(value: Boolean): Builder = apply { this.isFocusable = value }

        public fun build(): Balloon = Balloon(
            context = context,
            builder = this@Builder
        )
    }

    inner class AutoDismissRunnable(val balloon: Balloon) : Runnable {
        override fun run() {
            balloon.dismiss()
        }
    }

    public abstract class Factory {

        public abstract fun create(context: Context, lifecycle: LifecycleOwner?): Balloon
    }
}
