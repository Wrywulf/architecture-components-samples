package com.example.android.navigationadvancedsample.conductor

import android.content.Context
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.*
import com.bluelinelabs.conductor.Conductor
import com.bluelinelabs.conductor.Router
import com.example.android.navigationadvancedsample.R

/**
 * NavHostController provides an area within your layout for self-contained navigation to occur.
 *
 * NavHostController is intended to be used as the content area within a layout resource
 * defining your app's chrome around it, e.g.:
 *
 * Each NavHostController has a [NavController] that defines valid navigation within
 * the navigation host. This includes the [navigation graph][NavGraph] as well as navigation
 * state such as current location and back stack that will be saved and restored along with the
 * NavHostController itself.
 *
 * NavHostControllers register their navigation controller at the root of their view subtree
 * such that any descendant can obtain the controller instance through the [Navigation]
 * helper class's methods such as [Navigation.findNavController]. View event listener
 * implementations such as [View.OnClickListener] within navigation destination
 * controllers can use these helpers to navigate based on user interaction without creating a tight
 * coupling to the navigation host.
 */
class NavHostLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr), NavHost, LifecycleObserver {

    private var graphId: Int = 0
    private var defaultHost: Boolean = false
    private var viewModel: StateViewModel
    private var navigationController: NavHostController
    private var router: Router

    init {
        val a = context.obtainStyledAttributes(attrs, R.styleable.NavHostLayout)
        graphId = a.getResourceId(R.styleable.NavHostLayout_navGraph, 0)
        a.recycle()
//
//        /* 1 */ navigationController = NavController(context)
//        /* 2 */ Navigation.setViewNavController(this, navigationController)
//        /* 3 */
//        val navigator = createControllerNavigator()
//        /* 4 */ navigationController.navigatorProvider += navigator
//        /* 5 */ navigationController.setGraph(graphId)
//    }

        val activity = context as AppCompatActivity

        viewModel = ViewModelProvider(context)
                .get(
                    StateViewModel::
                    class.java
                )

        val savedInstanceState = viewModel.state

        router = Conductor.attachRouter(activity, this, savedInstanceState)
        navigationController = NavHostController(activity).apply {
            setLifecycleOwner(activity)
            setOnBackPressedDispatcher(activity.onBackPressedDispatcher)
            navigatorProvider.addNavigator(createControllerNavigator())
        }
        Navigation.setViewNavController(this, navigationController)

        var navState: Bundle? = null
        if (!savedInstanceState.isEmpty) {
            navState = savedInstanceState.getBundle(KEY_NAV_CONTROLLER_STATE)

            if (savedInstanceState.getBoolean(KEY_DEFAULT_NAV_HOST, false)) {
                defaultHost = true
            }
        }

        if (navState != null) {
            navigationController.restoreState(navState)
        } else {
//            if (defaultHost) {
            if (graphId != 0) {
                navigationController.setGraph(graphId)
            } else {
                //TODO a graphId was not set in xml. Should be provided as runtime args
            }
//            }
        }
    }

    override fun getNavController(): NavController = navigationController

    override fun onSaveInstanceState(): Parcelable? {
        val navState = navigationController.saveState()
        if (navState != null) {
            viewModel.state.putBundle(KEY_NAV_CONTROLLER_STATE, navState)
        }
        if (defaultHost) {
            viewModel.state.putBoolean(KEY_DEFAULT_NAV_HOST, true)
        }
        return super.onSaveInstanceState()
    }

    fun onBackPressed(): Boolean {
        return router.handleBack()
    }

    private fun createControllerNavigator(): Navigator<ControllerNavigator.Destination> {
        return ControllerNavigator(router)
    }

    class StateViewModel : ViewModel() {
        internal val state: Bundle = Bundle()
    }

    companion object {
        private const val KEY_NAV_CONTROLLER_STATE = "navControllerState"
        private const val KEY_DEFAULT_NAV_HOST = "defaultHost"
    }
}