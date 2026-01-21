package com.aboshekh.stretchbar

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.AttributeSet
import android.view.*
import android.view.animation.OvershootInterpolator
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.animation.doOnEnd
import androidx.core.view.children
import androidx.transition.TransitionManager
import com.aboshekh.androidutils.SwipeDirection
import com.aboshekh.androidutils.detectSwipeDirection
import com.aboshekh.androidutils.getAvailableScreenHeight
import com.aboshekh.androidutils.getAvailableScreenWidth
import com.aboshekh.androidutils.pxFromDp
import com.aboshekh.androidutils.setCornerRadius
import kotlin.math.abs


/**
 * StretchBarContainer is a custom [ConstraintLayout] that manages multiple
 * [StretchBar] instances in a stacked layout.
 *
 * It supports:
 * - Collapsed and expanded states
 * - Swipe gestures to move or expand/ collapse bars
 * - Overlap margins between stacked cards
 * - Vibrations on interaction
 * - Smooth animations
 *
 */
@SuppressLint("ClickableViewAccessibility")
class StretchBarContainer @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : ConstraintLayout(context, attrs) {


    val dialogCollapsedHeight by lazy {
        context.pxFromDp(64F)
    }

    val dialogCornerRadius by lazy {
        context.pxFromDp(24F).toFloat()
    }

    /**
     * overlap margin between cards
     * */
    var overlapMargin = context.pxFromDp(6F)
        set(value) {
            field = value
            updateStackAppearance(true)
        }

    var maxVisibleCards = 5
        set(value) {
            field = value
            updateStackAppearance(true)
        }

    /**
     * Get collapsed height of the stack based on number of visible cards.
     *
     * The extra 3dp (converted to px) is added to ensure a small padding space
     * at the bottom of the stack for better visual spacing and to avoid clipping.
     */
    val collapsedStackHeight: Int
        get() {
            val visibleCount = childCount.coerceAtLeast(1)
            val overlapCount = (visibleCount - 1).coerceAtMost(maxVisibleCards - 1)
            val value = dialogCollapsedHeight + (overlapCount * overlapMargin)
            return value.plus(context.pxFromDp(3F))
        }


    private val dialogSideMargin by lazy {
        context.pxFromDp(24F)
    }

    var isVibrate = true

    private val gestureListener =
        object : GestureDetector.SimpleOnGestureListener() {

            val SWIPE_THRESHOLD = 150f
            var initialY = 0f
            private var initialScale = 1f
            private var currentScale = 1f

            /**
             * Flag to prevent triggering multiple swipe actions during the same gesture.
             */
            private var hasSwiped = false

            override fun onDown(event: MotionEvent): Boolean {
                hasSwiped = false
                if (childCount == 1) {
                    initialY = event.rawY - translationY
                } else {
                    initialY = event.rawY
                    initialScale = currentScale
                }
                return true
            }


            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float,
            ): Boolean {
                val topView = getViewOntTop() ?: return false

                if (childCount == 1) {

                    if (e1 == null || hasSwiped) return false

                    val swipeDirection = detectSwipeDirection(e1, e2, SWIPE_THRESHOLD)

                    if (swipeDirection != SwipeDirection.NO_SWIPE) {
                        hasSwiped = true
                        topView.passSwipe(swipeDirection)
                    }

                } else {

                    if (topView.currentState == StretchBar.EXPANDED_STATE) {

                        if (e1 == null || hasSwiped) return false

                        val swipeDirection =
                            detectSwipeDirection(e1, e2, SWIPE_THRESHOLD)

                        if (swipeDirection != SwipeDirection.NO_SWIPE) {
                            hasSwiped = true
                            topView.passSwipe(swipeDirection)
                        }

                    } else {

                        val deltaY = (e2.rawY - initialY).coerceAtMost(0f).toInt()

                        val layoutParams = topView.layoutParams as MarginLayoutParams


                        layoutParams.bottomMargin = -deltaY.coerceIn(-collapsedStackHeight, 0)

                        topView.layoutParams = layoutParams

                    }

                }

                return true
            }


            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float,
            ): Boolean = true

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                val topView = getViewOntTop() ?: return false
                topView.onRootSingleTap()
                return super.onSingleTapConfirmed(e)
            }

        }

    private val gestureDetector = GestureDetector(context, gestureListener)

    init {

        clipChildren = false
        clipToPadding = false

        post {
            (parent as? ViewGroup)?.clipChildren = false
            (parent as? ViewGroup)?.clipToPadding = false
        }

        setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)

            if (event.action != MotionEvent.ACTION_UP) return@setOnTouchListener true

            val topView = getViewOntTop() ?: return@setOnTouchListener true

            if (topView.currentState == StretchBar.EXPANDED_STATE) return@setOnTouchListener true


            val deltaY = event.rawY - gestureListener.initialY

            if (abs(deltaY) > gestureListener.SWIPE_THRESHOLD && deltaY < 0) {

                val percentage = (topView.layoutParams as MarginLayoutParams).bottomMargin.div(
                    collapsedStackHeight
                )
                val duration = (1 - percentage) * StretchBar.DURATION

                changeFocusToNext(duration)

            } else {
                animateBottomMargin(topView, 0, StretchBar.DURATION)
            }



            return@setOnTouchListener true
        }

    }


    fun addUnderBar(stretchBar: StretchBar, isAnimated: Boolean) {

        addToParentViewGroup(stretchBar)

        if (isAnimated) addUnderBarAnimation(stretchBar)

        updateStackAppearance(isAnimated)

        if (isVibrate) context.vibrate(70L)
    }

    private fun addUnderBarAnimation(stretchBar: StretchBar) {

        stretchBar.apply {
            alpha = 0f
            scaleX = 0.5f
            scaleY = 0.5f

            translationY = this@StretchBarContainer.height.toFloat()

            animate()
                .translationY(0f)
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(StretchBar.DURATION)
                .setInterpolator(OvershootInterpolator(0.6F))

        }

    }

    fun addUnderBars(vararg stretchBars: StretchBar, animated: Boolean) {
        stretchBars.forEach {
            addToParentViewGroup(it)
            if (animated) addUnderBarAnimation(it)
            if (isVibrate) context.vibrate(500L)
        }
        post { updateStackAppearance(animated) }
    }

    /**
     * handel dialog's progress and add it to parent ViewGroup
     * */
    private fun addToParentViewGroup(stretchBar: StretchBar) {

        if (stretchBar.id == NO_ID) {
            stretchBar.id = generateViewId()
        }

        stretchBar.setCornerRadius(dialogCornerRadius, StretchBar.DURATION, false)

        addView(stretchBar)

        collapsed(stretchBar)
        stretchBar.onCollapsedEnd() // Because in first call don't call transition listener

    }

    private fun updateStackAppearance(animated: Boolean = true) {

        val cards = children.toList().reversed()

        cards.forEachIndexed { index, view ->
            val targetMargin = if (index < maxVisibleCards)
                overlapMargin * index
            else
                overlapMargin * (maxVisibleCards - 1)

            if (animated) {
                animateBottomMargin(view, targetMargin, StretchBar.DURATION)
            } else {
                val params = view.layoutParams as MarginLayoutParams
                params.bottomMargin = targetMargin
                view.layoutParams = params
            }

            view.visibility = View.VISIBLE
            view.scaleX = 1f
            view.scaleY = 1f
            view.translationY = 0f
        }

    }

    private fun animateBottomMargin(
        view: View,
        targetMargin: Int,
        duration: Long,
        onEnd: (() -> Unit)? = null,
    ) {
        val layoutParams = view.layoutParams as MarginLayoutParams
        val startMargin = layoutParams.bottomMargin

        ValueAnimator.ofInt(startMargin, targetMargin).apply {
            this.duration = duration
            addUpdateListener {
                layoutParams.bottomMargin = it.animatedValue as Int
                view.layoutParams = layoutParams
            }
            doOnEnd {
                onEnd?.invoke()
            }
            start()
        }
    }


    fun changeFocusToNext(duration: Long) {
        val topView = getViewOntTop() ?: return
        animateBottomMargin(topView, collapsedStackHeight.toInt(), duration) {
            moveChildToBack(topView)
            post { updateStackAppearance() }
        }
    }

    private fun moveChildToBack(stretchBar: StretchBar) {
        if (stretchBar !in children.toList()) return
        removeView(stretchBar)
        addView(stretchBar, 0)
    }


    fun expandUnderBar(stretchBar: StretchBar) {
        if (stretchBar.currentState == StretchBar.COLLAPSED_STATE) expand(stretchBar)
    }

    private fun expand(stretchBar: StretchBar) {

        TransitionManager.beginDelayedTransition(this, stretchBar.transition)

        // apply underBar's root changes
        val layoutSet = ConstraintSet()
        layoutSet.clone(this)
        layoutSet.constrainWidth(stretchBar.id, context.getAvailableScreenWidth())
        layoutSet.constrainHeight(stretchBar.id, context.getAvailableScreenHeight())

        layoutSet.connect(
            stretchBar.id,
            LayoutParams.TOP,
            LayoutParams.PARENT_ID,
            LayoutParams.TOP,
            0
        )
        layoutSet.connect(
            stretchBar.id,
            LayoutParams.BOTTOM,
            LayoutParams.PARENT_ID,
            LayoutParams.BOTTOM,
            0
        )
        layoutSet.connect(
            stretchBar.id,
            LayoutParams.START,
            LayoutParams.PARENT_ID,
            LayoutParams.START,
            0
        )
        layoutSet.connect(
            stretchBar.id,
            LayoutParams.END,
            LayoutParams.PARENT_ID,
            LayoutParams.END,
            0
        )
        layoutSet.applyTo(this)

        // apply underBar's children changes
        stretchBar.expandedConstraintSet.applyTo(stretchBar)

        // change radius & state
        stretchBar.setCornerRadius(0f, StretchBar.DURATION, true)

    }

    fun collapsedUnderBar(stretchBar: StretchBar) {
        if (stretchBar.currentState == StretchBar.EXPANDED_STATE) collapsed(stretchBar)
    }

    private fun collapsed(stretchBar: StretchBar) {

        TransitionManager.beginDelayedTransition(this, stretchBar.transition)

        // apply underBar's root changes
        val layoutSet = ConstraintSet()
        layoutSet.clone(this)
        layoutSet.constrainWidth(stretchBar.id, LayoutParams.MATCH_PARENT)
        layoutSet.constrainHeight(stretchBar.id, dialogCollapsedHeight)

        layoutSet.clear(stretchBar.id, LayoutParams.TOP)

        layoutSet.connect(
            stretchBar.id,
            LayoutParams.BOTTOM,
            LayoutParams.PARENT_ID,
            LayoutParams.BOTTOM
        )
        layoutSet.connect(
            stretchBar.id, LayoutParams.START,
            LayoutParams.PARENT_ID, LayoutParams.START,
            dialogSideMargin
        )
        layoutSet.connect(
            stretchBar.id, LayoutParams.END,
            LayoutParams.PARENT_ID, LayoutParams.END,
            dialogSideMargin
        )
        layoutSet.applyTo(this)

        // apply underBar's children changes
        stretchBar.collapsedConstraintSet.applyTo(stretchBar)

        // change radius & state

        stretchBar.setCornerRadius(dialogCornerRadius, StretchBar.DURATION, true)

    }


    fun expandUnderBarInFocus() {
        expandUnderBar(getViewOntTop() ?: return)
    }

    fun collapsedUnderBarInFocus() {
        collapsedUnderBar(getViewOntTop() ?: return)
    }

    fun getViewOntTop(): StretchBar? = children.lastOrNull() as? StretchBar

    fun getViewBehindTop(): StretchBar? =
        if (childCount >= 2) children.toList()[childCount - 2] as? StretchBar else null

    fun getUnderBarByPosition(position: Int): StretchBar? =
        children.toList()[position] as? StretchBar

    fun getUnderBarById(id: Int): StretchBar? = children.find { it.id == id } as? StretchBar

    fun removeUnderBar(stretchBar: StretchBar) {
        removeView(stretchBar)
    }

    fun hideUnderBar(stretchBar: StretchBar) {
        stretchBar.visibility = View.GONE
    }

    fun showUnderBar(stretchBar: StretchBar) {
        stretchBar.visibility = View.VISIBLE
    }

    private fun ViewGroup.disableChildrenClickable() {
        for (child in children) {
            if (child.getTag(R.id.tag_original_clickable) == null) {
                child.setTag(R.id.tag_original_clickable, child.isClickable)
            }

            child.isClickable = false

            if (child is ViewGroup) {
                child.disableChildrenClickable()
            }
        }
    }

    private fun ViewGroup.restoreOriginalChildrenClickable() {
        for (child in children) {
            val original = child.getTag(R.id.tag_original_clickable) as? Boolean
            if (original != null) {
                child.isClickable = original
            }

            if (child is ViewGroup) {
                child.restoreOriginalChildrenClickable()
            }
        }
    }


    override fun addView(child: View?) {
        checkView(child)
        addView(child, childCount)
    }

    override fun addView(child: View?, index: Int) {
        checkView(child)
        if (index == childCount) {
            (child as StretchBar).restoreOriginalChildrenClickable()
            getViewOntTop()?.disableChildrenClickable()
        } else {
            (child as StretchBar).disableChildrenClickable()
        }
        super.addView(child, index)
    }

    override fun addView(child: View?, width: Int, height: Int) {
        checkView(child)
        super.addView(child, width, height)
    }

    override fun addView(child: View?, params: ViewGroup.LayoutParams?) {
        checkView(child)
        super.addView(child, params)
    }

    override fun addView(child: View?, index: Int, params: ViewGroup.LayoutParams?) {
        checkView(child)
        super.addView(child, index, params)
    }

    override fun addViewInLayout(
        child: View?,
        index: Int,
        params: ViewGroup.LayoutParams?,
    ): Boolean {
        checkView(child)
        return super.addViewInLayout(child, index, params)
    }

    override fun addViewInLayout(
        child: View?,
        index: Int,
        params: ViewGroup.LayoutParams?,
        preventRequestLayout: Boolean,
    ): Boolean {
        checkView(child)
        return super.addViewInLayout(child, index, params)
    }

    private fun checkView(child: View?) {
        if (child !is StretchBar) throw Exception("child must be UnderBar")
    }

    override fun removeView(view: View?) {
        if (view == getViewOntTop())
            getViewBehindTop()?.restoreOriginalChildrenClickable()
        super.removeView(view)
    }

    @Suppress("DEPRECATION")
    private fun Context.vibrate(milliseconds: Long) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        vibrator.vibrate(
            VibrationEffect.createOneShot(
                milliseconds,
                VibrationEffect.DEFAULT_AMPLITUDE
            )
        )
    }

}

