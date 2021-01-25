package com.example.android.navigationadvancedsample.conductor.changehandler

import android.animation.Animator
import android.animation.AnimatorInflater
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.content.Context
import android.content.res.Resources.NotFoundException
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.animation.*
import androidx.annotation.AnimRes
import androidx.annotation.AnimatorRes
import com.bluelinelabs.conductor.Controller
import com.bluelinelabs.conductor.ControllerChangeHandler

/**
 * A base [ControllerChangeHandler] that facilitates using [Animator]s to replace Controller Views
 *
 * @param fromAnimResId Optional [Animator] or [Animation] resource ID for the exiting view
 * @param toAnimResId Optional [Animator] or [Animation] resource ID for the entering view
 */
class AnimatorChangeHandler @JvmOverloads constructor(
    @AnimatorRes @AnimRes private var fromAnimResId: Int? = null,
    @AnimatorRes @AnimRes private var toAnimResId: Int? = null,
    var removesFromViewOnPush: Boolean = true
) : ControllerChangeHandler() {
    var canceled = false
    var needsImmediateCompletion = false
    private var completed = false

    private var anim: Anim? = null
    private var onAnimationReadyOrAbortedListener: OnAnimationReadyOrAbortedListener? =
        null

    override fun saveToBundle(bundle: Bundle) {
        fromAnimResId?.let {
            bundle.putInt(
                KEY_ANIMATOR_FROM_RES,
                it
            )
        }
        toAnimResId?.let {
            bundle.putInt(
                KEY_ANIMATOR_TO_RES,
                it
            )
        }
        bundle.putBoolean(
            KEY_REMOVES_FROM_ON_PUSH,
            removesFromViewOnPush
        )
    }

    override fun restoreFromBundle(bundle: Bundle) {
        fromAnimResId = bundle.getInt(KEY_ANIMATOR_FROM_RES, -1)
                .let {
                    if (it != -1) {
                        it
                    } else {
                        null
                    }
                }
        toAnimResId = bundle.getInt(KEY_ANIMATOR_TO_RES, -1).let {
            if (it != -1) {
                it
            } else {
                null
            }
        }
        removesFromViewOnPush =
            bundle.getBoolean(KEY_REMOVES_FROM_ON_PUSH)
    }

    override fun onAbortPush(
        newHandler: ControllerChangeHandler,
        newTop: Controller?
    ) {
        super.onAbortPush(newHandler, newTop)
        canceled = true

        if (anim != null) {
            anim?.cancel()
        } else if (onAnimationReadyOrAbortedListener != null) {
            onAnimationReadyOrAbortedListener!!.onReadyOrAborted()
        }
    }

    override fun completeImmediately() {
        super.completeImmediately()
        needsImmediateCompletion = true
        if (anim != null) {
            anim?.end()
        } else if (onAnimationReadyOrAbortedListener != null) {
            onAnimationReadyOrAbortedListener!!.onReadyOrAborted()
        }
    }

    override fun removesFromViewOnPush(): Boolean {
        return removesFromViewOnPush
    }

    /**
     * Will be called after the animation is complete to reset the View that was removed to its pre-animation state.
     */
    private fun resetFromView(from: View) {
        anim?.resetFromAnimation()
    }

    override fun performChange(
        container: ViewGroup,
        from: View?,
        to: View?,
        isPush: Boolean,
        changeListener: ControllerChangeCompletedListener
    ) {
        var readyToAnimate = true
        val addingToView = to != null && to.parent == null
        if (addingToView) {
            if (isPush || from == null) {
                container.addView(to)
            } else if (to!!.parent == null) {
                container.addView(to, container.indexOfChild(from))
            }
            if (to!!.width <= 0 && to.height <= 0) {
                readyToAnimate = false
                onAnimationReadyOrAbortedListener =
                    OnAnimationReadyOrAbortedListener(
                        container,
                        from,
                        to,
                        isPush,
                        true,
                        changeListener
                    )
                to.viewTreeObserver
                        .addOnPreDrawListener(onAnimationReadyOrAbortedListener)
            }
        }
        if (readyToAnimate) {
            performAnimation(container, from, to, isPush, addingToView, changeListener)
        }
    }

    private fun complete(
        changeListener: ControllerChangeCompletedListener,
        animatorListener: Anim.Listener?
    ) {
        if (!completed) {
            completed = true
            changeListener.onChangeCompleted()
        }
        anim?.let {
            if (animatorListener != null) {
                it.removeListener(animatorListener)
            }
            it.cancel()
        }
        anim = null
        onAnimationReadyOrAbortedListener = null
    }

    fun performAnimation(
        container: ViewGroup,
        from: View?,
        to: View?,
        isPush: Boolean,
        toAddedToContainer: Boolean,
        changeListener: ControllerChangeCompletedListener
    ) {
        if (canceled) {
            complete(changeListener, null)
            return
        }
        if (needsImmediateCompletion) {
            if (from != null && (!isPush || removesFromViewOnPush)) {
                container.removeView(from)
            }
            complete(changeListener, null)
            if (isPush && from != null) {
                resetFromView(from)
            }
            return
        }

        anim = Anim.createFromResources(container.context, fromAnimResId, toAnimResId)
                ?.apply {
                    addListener(object : Anim.Listener {
                        override fun onAnimationCancel(animation: Anim) {
                            from?.let { resetFromView(it) }
                            if (to != null && to.parent === container) {
                                container.removeView(to)
                            }
                            complete(changeListener, this)
                        }

                        override fun onAnimationEnd(animation: Anim) {
                            if (!canceled && anim != null) {
                                if (from != null && (!isPush || removesFromViewOnPush)) {
                                    container.removeView(from)
                                }
                                complete(changeListener, this)
                                if (isPush && from != null) {
                                    resetFromView(from)
                                }
                            }
                        }

                    })

                    start(container = container, fromView = from, toView = to)
                }
    }

    private inner class OnAnimationReadyOrAbortedListener internal constructor(
        val container: ViewGroup,
        val from: View?,
        val to: View?,
        val isPush: Boolean,
        val addingToView: Boolean,
        val changeListener: ControllerChangeCompletedListener
    ) : ViewTreeObserver.OnPreDrawListener {
        private var hasRun = false
        override fun onPreDraw(): Boolean {
            onReadyOrAborted()
            return true
        }

        fun onReadyOrAborted() {
            if (!hasRun) {
                hasRun = true
                if (to != null) {
                    val observer = to.viewTreeObserver
                    if (observer.isAlive) {
                        observer.removeOnPreDrawListener(this)
                    }
                }
                performAnimation(container, from, to, isPush, addingToView, changeListener)
            }
        }

    }

    companion object {
        private const val KEY_ANIMATOR_FROM_RES = "AnimatorChangeHandler.animator.from"
        private const val KEY_ANIMATOR_TO_RES = "AnimatorChangeHandler.animator.to"
        private const val KEY_REMOVES_FROM_ON_PUSH =
            "AnimatorChangeHandler.removesFromViewOnPush"
    }

    private sealed class Anim {
        protected val listeners = mutableListOf<Listener>()

        /**
         * Encapsulates to/from [Animator]s
         */
        class Ator(val fromAnimator: Animator?, val toAnimator: Animator?) : Anim() {
            private val animator: AnimatorSet = AnimatorSet().apply {
                toAnimator?.let { play(it) }
                fromAnimator?.let { play(it) }
            }

            private val animatorListener = object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator?) {
                    listeners.forEach {
                        it.onAnimationEnd(this@Ator)
                    }
                }

                override fun onAnimationCancel(animation: Animator?) {
                    listeners.forEach {
                        it.onAnimationCancel(this@Ator)
                    }
                }
            }

            override fun start(container: ViewGroup, fromView: View?, toView: View?) {
                fromAnimator?.setTarget(fromView)
                toAnimator?.setTarget(toView)
                animator.start()
            }

            override fun end() {
                animator.end()
            }

            override fun cancel() {
                animator.cancel()
            }

            override fun resetFromAnimation() {
                /**
                 * Semi-hacky way to enable resetting the properties animated to their original state:
                 * simply reverse them instantly
                 */
                animator.clone()
                        .apply {
                            removeAllListeners()
                            duration = 0
                            interpolator = ReverseInterpolator()
                            start()
                        }

            }

            override fun onListenerAdded(listener: Listener) {
                if (animator.listeners?.contains(animatorListener) != true) {
                    animator.addListener(animatorListener)
                }
            }

            override fun onListenerRemoved(listener: Listener) {
                if (listeners.isEmpty()) {
                    animator.removeListener(animatorListener)
                }
            }

            /**
             * Plays an animation in reverse. The actual interpolator doesn't matter as we're
             * using a duration of 0.
             */
            private class ReverseInterpolator : Interpolator {
                private val interpolator: Interpolator = LinearInterpolator()

                override fun getInterpolation(input: Float): Float {
                    return 1 - interpolator.getInterpolation(input)
                }
            }

        }

        /**
         * Encapsulates to/from [Animation]s
         */
        data class Ation(val fromAnimation: Animation?, val toAnimation: Animation?) : Anim() {
            // Animation doesn't track cancellation, so we need to track it externally
            private var isCanceled = false

            private var container: ViewGroup? = null

            override fun start(container: ViewGroup, fromView: View?, toView: View?) {
                if (fromView != null || toView != null) {
                    this.container = container
                }
                toAnimation?.let { toView?.startAnimation(it) }
                fromAnimation?.let { fromView?.startAnimation(it) }
            }

            override fun end() {
                // cancel to end it - but don't mark it as cancelled
                toAnimation?.cancel()
                fromAnimation?.cancel()
            }

            override fun cancel() {
                isCanceled = true
                toAnimation?.cancel()
                fromAnimation?.cancel()
            }

            override fun resetFromAnimation() {
                // no-op since the animation does not actually change any view properties
            }

            private val animationListener = object : Animation.AnimationListener {
                override fun onAnimationRepeat(animation: Animation?) {
                    // no-op
                }

                override fun onAnimationEnd(animation: Animation?) {
                    // onAnimationEnd() comes during draw(), so there can still be some
                    // draw events happening after this call. We don't want to detach
                    // the view until after the onAnimationEnd()
                    container?.post {
                        listeners.forEach {
                            if (isCanceled) {
                                it.onAnimationCancel(this@Ation)
                            } else {
                                it.onAnimationEnd(this@Ation)
                            }
                        }
                    }
                    container = null
                }

                override fun onAnimationStart(animation: Animation?) {
                    // no-op
                }
            }

            override fun onListenerAdded(listener: Listener) {
                fromAnimation?.setAnimationListener(animationListener)
                    ?: toAnimation?.setAnimationListener(animationListener)
            }

            override fun onListenerRemoved(listener: Listener) {
                if (listeners.isEmpty()) {
                    fromAnimation?.setAnimationListener(null)
                    toAnimation?.setAnimationListener(null)
                }
            }
        }


        fun addListener(listener: Listener) {
            listeners.add(listener)
            onListenerAdded(listener)
        }

        fun removeListener(listener: Listener) {
            listeners.remove(listener)
            onListenerRemoved(listener)
        }

        abstract fun cancel()

        abstract fun start(container: ViewGroup, fromView: View?, toView: View?)

        abstract fun end()

        abstract fun resetFromAnimation()

        protected abstract fun onListenerAdded(listener: Listener)

        protected abstract fun onListenerRemoved(listener: Listener)

        interface Listener {
            fun onAnimationCancel(animation: Anim)

            fun onAnimationEnd(animation: Anim)
        }

        companion object {

            /**
             * Creates an [Anim] based on the provided resources. The resources must either both be
             * [Animation] resources or both be [Animator] resources.
             *
             * @return an [Anim] instance, or null if both anim resources are null
             * @throws IllegalArgumentException if [Anim] cannot be created.
             * @throws NotFoundException if one or both resources cannot be found.
             */
            fun createFromResources(
                context: Context,
                @AnimRes @AnimatorRes fromAnimResId: Int?,
                @AnimRes @AnimatorRes toAnimResId: Int?
            ): Anim? {

                if (fromAnimResId == null && toAnimResId == null) {
                    return null
                }

                val fromResIdDir = fromAnimResId?.let { context.resources.getResourceTypeName(it) }
                val toResIdDir = toAnimResId?.let { context.resources.getResourceTypeName(it) }

                if (fromResIdDir != null && toResIdDir != null && fromResIdDir != toResIdDir) {
                    throw IllegalArgumentException("Animation resources must either both be Animation or both be Animator. No mixing allowed.")
                }

                val isAnim =
                    if (fromResIdDir != null) {
                        "anim" == fromResIdDir
                    } else {
                        "anim" == toResIdDir
                    }

                fun Context.inflateAnimation(@AnimRes resId: Int?): Animation? {
                    return try {
                        resId?.let { AnimationUtils.loadAnimation(context, it) }
                        // A null animation may be returned and that is acceptable
                    } catch (e: NotFoundException) {
                        throw e // Rethrow it -- the resource should be found if it is provided.
                    } catch (e: RuntimeException) {
                        // Other exceptions can occur when loading an Animator from AnimationUtils.
                        null
                    }
                }

                fun Context.inflateAnimator(@AnimatorRes resId: Int?): Animator? {
                    return try {
                        resId?.let { AnimatorInflater.loadAnimator(context, it) }
                        // A null animation may be returned and that is acceptable
                    } catch (e: NotFoundException) {
                        throw e // Rethrow it -- the resource should be found if it is provided.
                    } catch (e: RuntimeException) {
                        // Other exceptions can occur when loading an Animator from AnimationUtils.
                        null
                    }
                }

                return if (isAnim) {
                    // try AnimationUtils first
                    try {
                        val inflatedFromAnimation = context.inflateAnimation(fromAnimResId)
                        val inflatedToAnimation = context.inflateAnimation(toAnimResId)
                        Anim.Ation(
                            fromAnimation = inflatedFromAnimation,
                            toAnimation = inflatedToAnimation
                        )
                    } catch (e: Exception) {
                        throw e
                    }
                } else {
                    // try Animator
                    try {
                        val inflatedFromAnimator = context.inflateAnimator(fromAnimResId)
                        val inflatedToAnimator = context.inflateAnimator(toAnimResId)
                        Anim.Ator(
                            fromAnimator = inflatedFromAnimator,
                            toAnimator = inflatedToAnimator
                        )

                    } catch (e: RuntimeException) {
                        if (isAnim) {
                            // Rethrow it -- we already tried AnimationUtils and it failed.
                            throw e
                        }
                        throw e //FIXME
//                        // Otherwise, it is probably an animation resource
//                        animation =
//                            AnimationUtils.loadAnimation(context, nextAnim)
//                        if (animation != null) {
//                            return AnimationOrAnimator(animation)
//                        }
                    }
                }
            }
        }
    }

}