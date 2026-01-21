package com.aboshekh.stretchbar

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import androidx.annotation.IntDef
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.transition.ChangeBounds
import androidx.transition.Transition
import com.aboshekh.androidutils.SwipeDirection
import com.aboshekh.androidutils.pxFromDp


/**
 * [StretchBar] is an abstract custom view representing a single interactive card
 * inside a [StretchBarContainer]. It supports collapsed and expanded states, swipe gestures,
 * and expansion/collapse animations with optional callbacks.
 *
 * Extend this class to implement your own custom layout and behavior for each StretchBar.
 */
@SuppressLint("ClickableViewAccessibility")
abstract class StretchBar(
    context: Context,
    attributeSet: AttributeSet? = null,
) : ConstraintLayout(context, attributeSet) {

    companion object {
        const val DURATION = 200L
        const val COLLAPSED_STATE = 0
        const val EXPANDED_STATE = 1
    }

    /**
     * ConstraintSet to be applied when the StretchBar is expanded.
     */
    abstract val expandedConstraintSet: ConstraintSet

    /**
     * ConstraintSet to be applied when the StretchBar is collapsed.
     */
    abstract val collapsedConstraintSet: ConstraintSet

    /**
     * Returns the current state of this StretchBar: [COLLAPSED_STATE] or [EXPANDED_STATE].
     */
    @DialogState
    val currentState: Int
        get() = if (this.height == dialogCollapsedHeight) COLLAPSED_STATE else EXPANDED_STATE

    /** Default height in pixels when collapsed. */
    private val dialogCollapsedHeight by lazy { context.pxFromDp(64F) }

    /**
     * Transition used for expansion/collapse animations.
     * Listeners automatically trigger callbacks for start/end of states.
     */
    var transition: Transition = ChangeBounds().apply {
        duration = DURATION
        addListener(object : Transition.TransitionListener {
            override fun onTransitionStart(transition: Transition) {
                if (currentState == EXPANDED_STATE) onCollapsedStart() else onExpandedStart()
            }

            override fun onTransitionEnd(transition: Transition) {
                if (currentState == COLLAPSED_STATE) onCollapsedEnd() else onExpandedEnd()
            }

            override fun onTransitionCancel(transition: Transition) {}
            override fun onTransitionPause(transition: Transition) {}
            override fun onTransitionResume(transition: Transition) {}
        })
    }

    init {
        clipChildren = true
    }

    // ----------------------
    // Public API
    // ----------------------

    /**
     * Handles pull gestures from [StretchBarContainer] and activates the appropriate callback functions.
     *
     * @param swipeDirection The detected [SwipeDirection] for the gesture.
     */
    fun passSwipe(swipeDirection: SwipeDirection) {
        when (swipeDirection) {
            SwipeDirection.LEFT -> onSwipeLeft()
            SwipeDirection.RIGHT -> onSwipeRight()
            SwipeDirection.UP -> onSwipeUp()
            SwipeDirection.DOWN -> onSwipeDown()
            SwipeDirection.NO_SWIPE -> {}
        }
    }

    // ----------------------
    // Callbacks (can be overridden)
    // ----------------------

    open fun onCollapsedStart() {}

    open fun onExpandedStart() {}

    open fun onCollapsedEnd() {}

    open fun onExpandedEnd() {}

    open fun onSwipeLeft() {}

    open fun onSwipeRight() {}

    /**
     * Called on upward swipe gesture.
     * Default behavior expands the StretchBar if it is collapsed.
     */
    open fun onSwipeUp() {
        if (currentState == COLLAPSED_STATE)
            (parent as StretchBarContainer).expandUnderBar(this)
    }

    /**
     * Called on downward swipe gesture.
     * Default behavior collapses the StretchBar if it is expanded.
     */
    open fun onSwipeDown() {
        if (currentState == EXPANDED_STATE)
            (parent as StretchBarContainer).collapsedUnderBar(this)
    }

    /**
     * Called when the user taps on the root view of this StretchBar.
     * Default behavior expands the StretchBar if it is collapsed.
     */
    open fun onRootSingleTap() {
        if (currentState == COLLAPSED_STATE)
            (parent as StretchBarContainer).expandUnderBar(this)
    }


    // ----------------------
    // Annotation for state
    // ----------------------

    @IntDef(COLLAPSED_STATE, EXPANDED_STATE)
    @Retention(AnnotationRetention.SOURCE)
    annotation class DialogState
}
