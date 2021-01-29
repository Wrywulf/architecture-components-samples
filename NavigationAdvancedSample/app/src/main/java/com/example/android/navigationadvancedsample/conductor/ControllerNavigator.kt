package com.example.android.navigationadvancedsample.conductor

import android.content.Context
import android.os.Bundle
import android.util.AttributeSet
import android.util.Log
import androidx.annotation.AnimRes
import androidx.annotation.AnimatorRes
import androidx.navigation.*
import com.bluelinelabs.conductor.Controller
import com.bluelinelabs.conductor.Router
import com.bluelinelabs.conductor.RouterTransaction
import com.bluelinelabs.conductor.changehandler.SimpleSwapChangeHandler
import com.example.android.navigationadvancedsample.R
import com.example.android.navigationadvancedsample.conductor.ControllerNavigator.Destination
import com.example.android.navigationadvancedsample.conductor.changehandler.AnimatorChangeHandler
import java.lang.reflect.Constructor
import java.util.*
import kotlin.collections.set

/**
 * Navigator that navigates through [RouterTransaction]s via the provided [Router].
 * This navigator does not manage its own backstack,
 * but manages the [Router] accordingly.
 *
 * Every destination using this Navigator must set a valid [Controller] class name with
 * <code>android:name</code> or [Destination.controllerClass].
 *
 */
@Navigator.Name("controller")
class ControllerNavigator(private val router: Router) :
    Navigator<ControllerNavigator.Destination>() {
    private val destroyRouterMethod =
        Router::class.java.getDeclaredMethod("destroy", Boolean::class.java)
                .apply {
                    isAccessible = true
                }

    init {
        router.setPopsLastView(true)
    }

    override fun onSaveState(): Bundle {
        val routerBundle = Bundle()
        router.saveInstanceState(routerBundle)
        val backstackNames = router.backstack.map { it.controller.toString() }

        Log.i(
            "ControllerNavigator",
            "onSaveState CACHING backstackNames=$backstackNames"
        )
        return routerBundle
    }

    /**
     * Toggle all pop animations for all controllers currently in backstack,
     * to either [enabled] == true, which means they all use whatever was given with their [RouterTransaction],
     * or [enabled] == false, which then utilizes [SimpleSwapChangeHandler] to show no animation.
     */
    fun setAllPopAnimations(enabled: Boolean) {
        router.backstack.map {
            val popHandler = if (enabled) {
                null
            } else {
                SimpleSwapChangeHandler()
            }
            it.controller.overridePopHandler(popHandler)
        }
    }

    override fun onRestoreState(savedState: Bundle) {
        super.onRestoreState(savedState)
        Log.i(
            "ControllerNavigator",
            "onRestoreState RESTORING pre destroy size={${router.backstack.size}} names=${router.backstack.map { it.controller.toString() }}"
        )
        /*
         * When we actually restore,
         * we want to do it on a clean slate as we sometimes
         * (i.e. during activity restoration, or device orientation change)
         * wont have torn down completely and the Navigation and Router backstack will be out of sync,
         * so destroy whatever is in the router now, if anything, then do the restore.
         * Manifestations of issues without this include a router backstack which grows with each restore,
         * and views being visible behind each other.
         */
        destroyRouterMethod.invoke(router, true)

        Log.i(
            "ControllerNavigator",
            "onRestoreState DESTROYED size={${router.backstack.size}} names=${router.backstack.map { it.controller.toString() }}"
        )
        router.restoreInstanceState(savedState)

        /*
         * Because the restored state thinks everything is attached,
         * we need to tell the entire restored backstack that it needs to be reAttached,
         * then we call rebind to properly attach them all.
         * Manifestations of issues without this is views being visible,
         * but you cannot interact with them i.e. via touch.
         */
        router.prepareForHostDetach()
        router.rebindIfNeeded()
        Log.i(
            "ControllerNavigator",
            "onRestoreState RESTORING backstack size={${router.backstack.size}} names=${router.backstack.map { it.controller.toString() }}"
        )
    }

    @ExperimentalStdlibApi
    override fun popBackStack(): Boolean {
        val prePopSize = router.backstackSize
        router.popCurrentController()
        val postPopSize = router.backstackSize
        return (postPopSize == (prePopSize - 1))
    }

    override fun createDestination(): Destination {
        return Destination(this)
    }

    override fun navigate(
        destination: Destination,
        args: Bundle?,
        navOptions: NavOptions?,
        navigatorExtras: Navigator.Extras?
    ): NavDestination? {
        Log.d(
            "ControllerNavigator",
            "navigate to destination=$destination, PRE backstack names=${router.backstack.map { it.controller.toString() }}"
        )
        val initialNavigation: Boolean = router.backstack.isEmpty()

        val isSingleTopReplacement = (navOptions != null && !initialNavigation
                && navOptions.shouldLaunchSingleTop()
                && router.backstack.last()
                .tag() == createTag(destination, args))

        val transaction =
            createTransaction(destination = destination, args = args, navOptions = navOptions)

        val isAdded = when {
            initialNavigation -> {
                router.setRoot(transaction)
                true
            }
            isSingleTopReplacement -> {
                // Single Top means we only want one instance on the back stack
                // replace the current tip with a new instance
                router.replaceTopController(transaction)
                false
            }
            else -> {
                router.pushController(transaction)
                true
            }
        }
        if (navigatorExtras is Extras) {
            //TODO shared element?
        }

        Log.d(
            "ControllerNavigator",
            "navigate to destination=$destination, POST backstack names=${router.backstack.map { it.controller.toString() }}"
        )
        return if (isAdded) {
            destination
        } else {
            null
        }
    }

    private fun createTransaction(
        destination: Destination,
        args: Bundle?,
        navOptions: NavOptions?
    ): RouterTransaction {
        // TODO add transitions etc.
        val controller = destination.createController(args)

        @AnimRes
        @AnimatorRes
        val enterAnim: Int? = navOptions?.let {
            if (isAnimValid(it.enterAnim)) {
                it.enterAnim
            } else null
        }

        @AnimRes
        @AnimatorRes
        val exitAnim: Int? = navOptions?.let {
            if (isAnimValid(it.exitAnim)) {
                it.exitAnim
            } else null
        }

        @AnimRes
        @AnimatorRes
        val popEnterAnim: Int? = navOptions?.let {
            if (isAnimValid(it.popEnterAnim)) {
                it.popEnterAnim
            } else null
        }

        @AnimRes
        @AnimatorRes
        val popExitAnim: Int? = navOptions?.let {
            if (isAnimValid(it.popExitAnim)) {
                it.popExitAnim
            } else null
        }

        val pushChangeHandler = if (isAnimValid(enterAnim) || isAnimValid(exitAnim)) {
            AnimatorChangeHandler(fromAnimResId = exitAnim, toAnimResId = enterAnim)
        } else {
            null
        }

        val popChangeHandler = if (isAnimValid(popEnterAnim) || isAnimValid(popExitAnim)) {
            AnimatorChangeHandler(fromAnimResId = popExitAnim, toAnimResId = popEnterAnim)
        } else {
            null
        }


        val tag = createTag(destination, args)


        Log.d("ControllerNavigator", "Navigating to tag: $tag")
        return RouterTransaction
                .with(controller)
                .popChangeHandler(popChangeHandler)
                .pushChangeHandler(pushChangeHandler)
                .tag(tag)
    }

    private fun isAnimValid(animResId: Int?): Boolean {
        /*
         * null is obviously invalid
         * -1 is default value if nothing was provided
         * 0 is default value in Navigation when doing deepLinking
         */
        return (animResId != null && animResId != -1 && animResId != 0)
    }

    private fun createTag(destination: NavDestination, args: Bundle?): String {
        return "${destination.id}-${args.hashCode()}"
    }

    /**
     * NavDestination specific to [ControllerNavigator]
     *
     * Construct a new controller destination. This destination is not valid until you set the
     * Controller via [.setControllerClass].
     *
     * @param controllerNavigator The [ControllerNavigator] which this destination
     * will be associated with. Generally retrieved via a
     * [NavController]'s
     * [NavigatorProvider.getNavigator] method.
     */
    class Destination(controllerNavigator: Navigator<Destination>) :
        NavDestination(controllerNavigator) {

        var controllerClass: Class<out Controller>? = null

        override fun onInflate(context: Context, attrs: AttributeSet) {
            super.onInflate(context, attrs)
            val a = context.resources.obtainAttributes(
                attrs,
                R.styleable.ControllerNavigator
            )
            if (controllerClass == null) {
                controllerClass = getControllerClassByName(
                    context, requireNotNull(
                        a.getString(
                            R.styleable.ControllerNavigator_android_name
                        )
                    )
                )
            }
            a.recycle()
        }

        private fun getControllerClassByName(
            context: Context,
            origName: String
        ): Class<out Controller> {
            var name = origName
            if (name.isNotEmpty() && name[0] == '.') {
                name = context.packageName + name
            }

            var clazz: Class<out Controller>? = controllerClasses[name]
            if (clazz == null) {
                try {
                    clazz = Class.forName(name, true, context.classLoader) as Class<out Controller>
                    controllerClasses[name] = clazz
                } catch (e: ClassNotFoundException) {
                    throw RuntimeException(e)
                }

            }
            return clazz
        }

        /**
         * Create a new instance of the [Controller] associated with this destination.
         *
         * @param args optional args to set on the new Controller
         * @return an instance of the [Controller class][.getControllerClass] associated
         * with this destination
         */
        fun createController(args: Bundle?): Controller =
            newInstance(requireNotNull(controllerClass), args)

        /**
         * Instantiates a [Controller] using reflection. If the [Controller] has [Bundle] constructor
         * [args] will be passed to it. Otherwise, the default (empty) constructor will be used for
         * instantiation
         *
         * @return the instantiated [Controller] of class [clazz]
         */
        private fun newInstance(clazz: Class<out Controller>, args: Bundle?): Controller {
            val constructors = clazz.constructors
            val bundleConstructor = getBundleConstructor(constructors)

            if (args != null) {
                args.classLoader = clazz.classLoader
            }

            val controller: Controller
            controller = try {
                if (bundleConstructor != null) {
                    bundleConstructor.newInstance(args) as Controller
                } else {
                    getDefaultConstructor(constructors)!!.newInstance() as Controller
                }
            } catch (e: Exception) {
                throw RuntimeException(
                    "An exception occurred while creating a new instance of " + clazz + ". " + e.message,
                    e
                )
            }
            return controller
        }


        private fun getDefaultConstructor(constructors: Array<Constructor<*>>): Constructor<*>? {
            for (constructor in constructors) {
                if (constructor.parameterTypes.isEmpty()) {
                    return constructor
                }
            }
            return null
        }

        private fun getBundleConstructor(constructors: Array<Constructor<*>>): Constructor<*>? {
            for (constructor in constructors) {
                if (constructor.parameterTypes.size == 1 && constructor.parameterTypes[0] == Bundle::class.java) {
                    return constructor
                }
            }
            return null
        }

        companion object {
            private val controllerClasses = HashMap<String, Class<out Controller>>()

            private fun getBundleConstructor(constructors: Array<Constructor<*>>): Constructor<*> {
                for (constructor in constructors) {
                    if (constructor.parameterTypes.size == 1 && constructor.parameterTypes[0] == Bundle::class.java) {
                        return constructor
                    }
                }

                throw IllegalStateException("The controller does not have a bundle constructor.")
            }
        }
    }

    /**
     * Extras that can be passed to [ControllerNavigator] to enable [Controller] specific behavior
     */
    class Extras : Navigator.Extras {
        //TODO
    }
}