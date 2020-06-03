package com.example.android.navigationadvancedsample.conductor

import android.content.Context
import android.os.Bundle
import android.util.AttributeSet
import android.view.ViewGroup
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavOptions
import androidx.navigation.Navigator
import androidx.navigation.NavigatorProvider
import com.bluelinelabs.conductor.Controller
import com.bluelinelabs.conductor.ControllerChangeHandler
import com.bluelinelabs.conductor.Router
import com.bluelinelabs.conductor.RouterTransaction
import com.example.android.navigationadvancedsample.R
import java.lang.reflect.Constructor
import java.util.HashMap
import kotlin.collections.set

@Navigator.Name("controller")
class ControllerNavigator(private val router: Router) :
    Navigator<ControllerNavigator.Destination>() {

    private val lastTransaction: RouterTransaction?
        get() = if (router.backstackSize == 0) {
            null
        } else router.backstack[router.backstackSize - 1]

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

              // TODO is this still needed? Cannot find the equivalent in FragmentNavigator
//                val backStackEffect = if (isPush) {
//                    BACK_STACK_DESTINATION_ADDED
//                } else {
//                    BACK_STACK_DESTINATION_POPPED
//                }
//
//                dispatchOnNavigatorNavigated(lastTag, backStackEffect)
            }
        })
    }

    override fun popBackStack(): Boolean = if (router.backstackSize > 1) {
        lastPoppedTag = lastTransaction?.tag()
                .orEmpty()
        router.popCurrentController()
    } else {
        false
    }

    override fun createDestination(): Destination {
        return Destination(this)
    }

    override fun navigate(
        destination: Destination,
        args: Bundle?,
        navOptions: NavOptions?,
        navigatorExtras: Extras?
    ): NavDestination? {
        val controller = destination.createController(args)

        val transaction = RouterTransaction
                .with(controller)
                .tag(destination.id.toString())

        if (!router.hasRootController()) {
            router.setRoot(transaction)
        } else {
            router.pushController(transaction)
        }
        return destination
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

        private lateinit var controllerClass: Class<out Controller>

        override fun onInflate(context: Context, attrs: AttributeSet) {
            super.onInflate(context, attrs)
            val a = context.resources.obtainAttributes(
                attrs,
                R.styleable.ControllerNavigator
            )
            controllerClass = getControllerClassByName(
                context, requireNotNull(
                    a.getString(
                        R.styleable.ControllerNavigator_android_name
                    )
                )
            )
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
        fun createController(args: Bundle?): Controller = newInstance(controllerClass, args)

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
}