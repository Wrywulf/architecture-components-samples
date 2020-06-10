package com.example.android.navigationadvancedsample.conductor

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.*
import com.bluelinelabs.conductor.Conductor
import com.bluelinelabs.conductor.Router


/**
 * A root [NavHost] that is to be instantiated in [Activity.onCreate]
 *
 * It is important to call into [onSaveInstanceState] from [Activity.onSaveInstanceState] to be able to save navigation state.
 *
 * @param activity The hosting [AppCompatActivity]
 * @param savedInstanceState
 * @param graphId the resource ID for the root navigation graph.
 * @param container the [ViewGroup] navigation container in which the navigation graph will be inflated
 * @param startDestinationArgs the optional arguments for the startDestination in the root graph. Default is null
 */
class ConductorNavigation(
    activity: AppCompatActivity,
    savedInstanceState: Bundle?,
    private var graphId: Int,
    container: ViewGroup,
    private var startDestinationArgs: Bundle? = null
) : NavHost {
    private var navigationController: NavHostController
    private val router: Router = Conductor.attachRouter(activity, container, savedInstanceState)

    init {
        Log.i("ConductorNavigation", "init")
        navigationController = NavHostController(activity).apply {
            setLifecycleOwner(activity)
            setOnBackPressedDispatcher(activity.onBackPressedDispatcher)
            navigatorProvider.addNavigator(ControllerNavigator(router))
        }
        Navigation.setViewNavController(container, navigationController)

        // Restore any state if needed
        if (savedInstanceState != null) {
            Log.i("ConductorNavigation", "Restoring savedInstanceState: $savedInstanceState")
            startDestinationArgs = savedInstanceState.getBundle(KEY_START_DEST_ARGS)

            // override using saved state
            graphId = savedInstanceState.getInt(KEY_GRAPH_ID)

            val navState: Bundle? = savedInstanceState.getBundle(KEY_NAV_CONTROLLER_STATE)
            if (navState != null) {
                Log.i("ConductorNavigation", "Restoring navState: $navState")
                navigationController.restoreState(navState)
            }
        }
        navigationController.setGraph(graphId, startDestinationArgs)
    }


    /**
     * Allow navigation state to be saved in the provided [Bundle]
     */
    fun onSaveInstanceState(outState: Bundle) {
        val navState = navigationController.saveState()
        Log.i("ConductorNavigation", "onSaveInstanceState: $navState")
        navState?.let { outState.putBundle(KEY_NAV_CONTROLLER_STATE, it) }
        startDestinationArgs?.let { outState.putBundle(KEY_START_DEST_ARGS, it) }
        outState.putInt(KEY_GRAPH_ID, graphId)
    }

    companion object {
        private const val KEY_NAV_CONTROLLER_STATE =
            "android-support-nav:conductor:navControllerState"
        private const val KEY_GRAPH_ID = "android-support-nav:conductor:graphId"
        private const val KEY_START_DEST_ARGS =
            "android-support-nav:conductor:startDestinationArgs"
    }

    override fun getNavController(): NavController = navController

}