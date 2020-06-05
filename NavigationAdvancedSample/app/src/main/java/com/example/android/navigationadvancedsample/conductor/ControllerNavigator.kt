package com.example.android.navigationadvancedsample.conductor

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.util.AttributeSet
import android.util.Log
import android.view.ViewGroup
import androidx.annotation.AnimRes
import androidx.annotation.AnimatorRes
import androidx.navigation.*
import com.bluelinelabs.conductor.Controller
import com.bluelinelabs.conductor.ControllerChangeHandler
import com.bluelinelabs.conductor.Router
import com.bluelinelabs.conductor.RouterTransaction
import com.example.android.navigationadvancedsample.R
import com.example.android.navigationadvancedsample.conductor.ControllerNavigator.Destination
import com.example.android.navigationadvancedsample.conductor.changehandler.AnimatorChangeHandler
import java.lang.reflect.Constructor
import java.util.*
import kotlin.collections.set

/**
 * Navigator that navigates through [RouterTransaction]s. Every
 * destination using this Navigator must set a valid [Controller] class name with
 * <code>android:name</code> or [Destination.controllerClass].
 */
@Navigator.Name("controller")
class ControllerNavigator(private val router: Router) :
    Navigator<ControllerNavigator.Destination>() {

    private val handler = Handler()

    private val desiredBackstack = mutableListOf<RouterTransaction>()

    private val backStackProcessingRunnable = Runnable {
        // make the desired backstack come into effect by popping the delta from the router's backstack
        val delta: Set<RouterTransaction> = router.backstack.toSet()
                .minus(desiredBackstack.toSet())
        val newTopOfBackStack = desiredBackstack.lastOrNull()
        desiredBackstack.clear()
        Log.d(
            "ControllerNavigator",
            "Execute backstack change. Entries ${delta.size}"
        )
        if (delta.size == 1) {
            delta.firstOrNull()?.let {
                Log.d("ControllerNavigator", "Popping single with tag: ${it.tag()}")
                router.popController(it.controller)
            }
        } else if (delta.size > 1) {
            newTopOfBackStack?.let {
                Log.d(
                    "ControllerNavigator",
                    "Popping multiple (${delta.size}) up to tag: ${it.tag()}"
                )
                router.popToTag(
                    it.tag()!!,
                    delta.firstOrNull()
                            ?.popChangeHandler()
                )
            }
        }
    }

    /**
     * Schedules back stack popping - overwriting any previous value
     */
    private fun dispatchBackstackPopping() {
        handler.removeCallbacks(backStackProcessingRunnable)
        handler.post(backStackProcessingRunnable)
    }

    private val lastTransaction: RouterTransaction?
        get() = router.backstack.lastOrNull()

    private var lastPoppedTag: String = ""

    private val lastTag: String
        get() {
            lastTransaction ?: return lastPoppedTag
            return lastTransaction?.tag()
                    .orEmpty()
        }

    init {
        router.addChangeListener(object : ControllerChangeHandler.ControllerChangeListener {
            override fun onChangeStarted(
                to: Controller?,
                from: Controller?,
                isPush: Boolean,
                container: ViewGroup,
                handler: ControllerChangeHandler
            ) {
            }

            override fun onChangeCompleted(
                to: Controller?,
                from: Controller?,
                isPush: Boolean,
                container: ViewGroup,
                handler: ControllerChangeHandler
            ) {
                val lastTag = lastTag.toIntOrNull() ?: 0
                if (lastTag == 0) {
                    return
                }
            }
        })
    }

    @ExperimentalStdlibApi
    override fun popBackStack(): Boolean {
        return lastTransaction?.let {
            if (desiredBackstack.isEmpty()) {
                desiredBackstack.addAll(router.backstack)
            }
            desiredBackstack.removeLastOrNull()
                    ?.let {
                        dispatchBackstackPopping()
                        true
                    } ?: false
        } ?: false
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
        val initialNavigation: Boolean = router.backstack.isEmpty()

        val isSingleTopReplacement = (navOptions != null && !initialNavigation
                && navOptions.shouldLaunchSingleTop()
                && router.backstack.last()
                .tag() == destination.id.toString())

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
            if (it.enterAnim != -1) {
                it.enterAnim
            } else null
        }

        @AnimRes
        @AnimatorRes
        val exitAnim: Int? = navOptions?.let {
            if (it.exitAnim != -1) {
                it.exitAnim
            } else null
        }

        @AnimRes
        @AnimatorRes
        val popEnterAnim: Int? = navOptions?.let {
            if (it.popEnterAnim != -1) {
                it.popEnterAnim
            } else null
        }

        @AnimRes
        @AnimatorRes
        val popExitAnim: Int? = navOptions?.let {
            if (it.popExitAnim != -1) {
                it.popExitAnim
            } else null
        }

        val pushChangeHandler = if (enterAnim != null || exitAnim != null) {
            AnimatorChangeHandler(fromAnimResId = exitAnim, toAnimResId = enterAnim)
        } else {
            null
        }

        val popChangeHandler = if (popEnterAnim != null || popExitAnim != null) {
            AnimatorChangeHandler(fromAnimResId = popExitAnim, toAnimResId = popEnterAnim)
        } else {
            null
        }


        val tag = "${destination.id}-${args.hashCode()}"


        Log.d("ControllerNavigator", "Navigating to tag: $tag")
        return RouterTransaction
                .with(controller)
                .popChangeHandler(popChangeHandler)
                .pushChangeHandler(pushChangeHandler)
                .tag(tag)
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