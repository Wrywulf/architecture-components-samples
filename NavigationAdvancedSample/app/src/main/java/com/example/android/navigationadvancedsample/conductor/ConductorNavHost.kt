package com.example.android.navigationadvancedsample.conductor

import android.app.Activity
import android.os.Bundle
import android.os.Parcelable
import android.util.Log
import android.util.SparseArray
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.*
import com.bluelinelabs.conductor.Conductor
import com.bluelinelabs.conductor.Router
import kotlinx.android.parcel.Parcelize

/**
 * A root [NavHost] that is to be instantiated in [Activity.onCreate]
 *
 * Supports multiple backstacks via [setGraph]
 *
 * It is important to call into [onSaveInstanceState] from [Activity.onSaveInstanceState] to be able to save navigation state.
 *
 * @param activity The hosting [AppCompatActivity]
 * @param savedInstanceState
 * @param graphId the resource ID for the root navigation graph.
 * @param container the [ViewGroup] navigation container in which the navigation graph will be inflated
 * @param startDestinationArgs the optional arguments for the startDestination in the root graph. Default is null
 */
class ConductorNavHost(
    activity: AppCompatActivity,
    savedInstanceState: Bundle?,
    private var graphId: Int,
    container: ViewGroup,
    private var startDestinationArgs: Bundle? = null
) : NavHost {
    private var navHostController: NavHostController
    private val savedStateBundles: SparseArray<SavedStates>
    private val router: Router = Conductor.attachRouter(activity, container, savedInstanceState)
    private val navigator = ControllerNavigator(router)
    private val destroyRouterMethod =
        Router::class.java.getDeclaredMethod("destroy", Boolean::class.java)
                .apply {
                    isAccessible = true
                }

    init {
        Log.i("ConductorNavigation", "init")
        navHostController = NavHostController(activity).apply {
            setLifecycleOwner(activity)
            setOnBackPressedDispatcher(activity.onBackPressedDispatcher)
            navigatorProvider.addNavigator(navigator)
        }
        Navigation.setViewNavController(container, navHostController)

        // Restore any state if needed
        if (savedInstanceState != null) {
            Log.i("ConductorNavigation", "Restoring savedInstanceState: $savedInstanceState")
            startDestinationArgs = savedInstanceState.getBundle(KEY_START_DEST_ARGS)

            // override using saved state
            graphId = savedInstanceState.getInt(KEY_GRAPH_ID)
            Log.i("ConductorNavigation", "Restoring savedInstanceState graphId: $graphId")

            savedStateBundles =
                savedInstanceState.getSparseParcelableArray<SavedStates>(KEY_GRAPH_SAVED_STATES)
                    ?: SparseArray()
            /*
             * If we are restoring,
             * we want to control the state of router ourselves,
             * and not have it re-inflate any potentially restored views,
             * so destroy the router all together first.
             */
            destroyRouterMethod.invoke(router, true)
        } else {
            savedStateBundles = SparseArray()
        }
        /*
         * Don't do caching - shouldCacheCurrentGraph = false,
         * as we are either initializing from noting, or restoring state,
         * and in neither case we need/should cache the current backstack.
         */
        setGraph(
            graphId = graphId,
            startDestinationArgs = startDestinationArgs,
            shouldCacheCurrentGraph = false
        )
    }

    private fun cacheGraphState() {
        /*
         * Save state of current Router and NavHost,
         * since setting a new graph will pop any backstack associated with the previous graph,
         * so we can restore it to this Router backstack and NavHost backstack,
         * upon setting this graph, back again.
         */
        val currentGraphId: Int = this.graphId
        val routerBundle = Bundle()
        router.saveInstanceState(routerBundle)
        val navigationBundle = navController.saveState() ?: Bundle.EMPTY
        savedStateBundles.put(
            currentGraphId,
            SavedStates(
                routerBundle,
                navigationBundle
            )
        )
    }

    /**
     * Expands upon [NavController.setGraph] to include,
     * caching and restoration of the internal graph, backstack and router based on [graphId],
     * essentially allowing multiple backstacks.
     * @param graphId of the new graph we wish to use
     * @param startDestinationArgs the optional arguments for the startDestination in the root graph. Default is null
     * @param destroyCachedGraph set to true will destroy the internal cache for the given [graphId], prior to restoration, leading to a "clean" navigation to the supplied graph. Default is false
     * @param shouldCacheCurrentGraph set to false will prevent caching of the current [graphId], which is useful in cases such as restoration, where we don't want to override our restored state with the empty newly initialized state. Default is true
     */
    fun setGraph(
        graphId: Int,
        startDestinationArgs: Bundle? = null,
        destroyCachedGraph: Boolean = false,
        shouldCacheCurrentGraph: Boolean = true
    ) {
        val newGraphId: Int = graphId
        Log.i(
            "ConductorNavHost",
            "setGraph newGraphId=$newGraphId, currentGraphId=${this.graphId}, routerBackStack size=${router.backstackSize}, navSize=${navHostController.backStack.size}"
        )

        if (shouldCacheCurrentGraph) {
            cacheGraphState()
        }

        if (destroyCachedGraph) {
            savedStateBundles.remove(newGraphId)
        }
        /* Configure the Router and NavHost with the right cached state, if present */
        val savedStates = savedStateBundles[newGraphId]
        Log.i("ConductorNavigation", "setGraph savedStates=$savedStates")
        if (savedStates != null) {
            /*
             * Restore navController first so it has the chance to clean up the old graph backstack,
             * and thus the content of the old router first.
             */
            navController.restoreState(savedStates.navControllerBundle)
            navHostController.setGraph(newGraphId, startDestinationArgs)

            /*
             * Then restore the router state with the old backstack
             */
            router.restoreInstanceState(savedStates.routerBundle)
            router.rebindIfNeeded()
            savedStateBundles.remove(newGraphId)
        } else {
            /*
             * If nothing was cached, clear the NavHost state before setting new graph.
             */
            navController.restoreState(Bundle.EMPTY)
            navHostController.setGraph(newGraphId, startDestinationArgs)
        }
        this.graphId = graphId
        this.startDestinationArgs = startDestinationArgs
    }

    /**
     * Allow navigation state to be saved in the provided [Bundle]
     */
    fun onSaveInstanceState(outState: Bundle) {
        cacheGraphState()
        startDestinationArgs?.let { outState.putBundle(KEY_START_DEST_ARGS, it) }
        outState.putInt(KEY_GRAPH_ID, graphId)
        outState.putSparseParcelableArray(KEY_GRAPH_SAVED_STATES, savedStateBundles)
    }

    companion object {
        private const val KEY_GRAPH_ID = "android-support-nav:conductor:graphId"
        private const val KEY_START_DEST_ARGS =
            "android-support-nav:conductor:startDestinationArgs"
        private const val KEY_GRAPH_SAVED_STATES = "android-support-nav:conductor:graphSavedStates"
    }

    override fun getNavController(): NavController = navHostController

    @Parcelize
    private data class SavedStates(val routerBundle: Bundle, val navControllerBundle: Bundle) :
        Parcelable
}