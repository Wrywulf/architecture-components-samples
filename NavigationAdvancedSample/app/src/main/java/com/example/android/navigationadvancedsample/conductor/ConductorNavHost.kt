package com.example.android.navigationadvancedsample.conductor

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.util.SparseArray
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.*
import com.bluelinelabs.conductor.Conductor
import com.bluelinelabs.conductor.Router

/**
 * A root [NavHost] that is to be instantiated in [Activity.onCreate]
 *
 * Supports multiple backstacks defined as root destinationIds in [backstackRootDestinations].
 * We listen to navigations via an [OnDestinationChangedListener] and traverse each destination.parent hierarchy,
 * until we find an ID match in [backstackRootDestinations], we then save the state of our backstack,
 * which will be restored if navigation occurs to a destination with a different root destinationId.
 * If only one backstack is required, simply add only that destinationId as entry in [backstackRootDestinations].
 *
 * It is important to call into [onSaveInstanceState] from [Activity.onSaveInstanceState] to be able to save navigation state.
 *
 * @param activity The hosting [AppCompatActivity]
 * @param savedInstanceState
 * @param graphId the resource ID for the root navigation graph.
 * @param container the [ViewGroup] navigation container in which the navigation graph will be inflated
 * @param router Optionally pass in a [Router] if you wish to use i.e. a [ControllerHostedRouter] rather than the default [ActivityHostedRoter]
 * @param backstackRootDestinations destinationIds who will be treated as a separate backstack, when navigating between them or a destination further down hierarchy
 * @param startDestinationArgs the optional arguments for the startDestination in the root graph. Default is null
 */
class ConductorNavHost(
    activity: AppCompatActivity,
    savedInstanceState: Bundle?,
    private var graphId: Int,
    container: ViewGroup,
    /*
     * Always pass in null bundle as we control the router state ourselves completely.
     */
    router: Router = Conductor.attachRouter(activity, container, null),
    private val backstackRootDestinations: Set<Int>,
    private var startDestinationArgs: Bundle? = null
) : NavHost {
    private var navHostController: NavHostController
    private val savedStateBundles: SparseArray<Bundle>
    private val navigator = ControllerNavigator(router)
    private var previousBackstackRootDestination: Int? = null

    init {
        Log.i("ConductorNavigation", "init")
        if (backstackRootDestinations.isEmpty()) {
            throw IllegalArgumentException("backstackRootDestinations must have at least 1 element, to support restoration of Navigation.")
        }
        navHostController = NavHostController(activity).apply {
            setLifecycleOwner(activity)
            setOnBackPressedDispatcher(activity.onBackPressedDispatcher)
            navigatorProvider.addNavigator(navigator)

            if (backstackRootDestinations.isNotEmpty()) {
                addOnDestinationChangedListener { controller, destination, arguments ->
                    Log.i(
                        "ConductorActivity",
                        "ConductorNavHost destination=$destination, controller=$controller, arguments=$arguments"
                    )
                    /*
                     * If destination exists in a subgraph where a rootNode Id match is found in our set,
                     * then save/restore them
                     */
                    var parent = destination.parent
                    var shouldSaveRestoreBackstackId: Int? = null
                    if (parent == null) {
                        /* if no parent, then just check ourselves */
                        if (backstackRootDestinations.contains(destination.id)) {
                            shouldSaveRestoreBackstackId = destination.id
                        }
                    } else {
                        while (parent != null) {
                            parent.let {
                                if (backstackRootDestinations.contains(it.id)) {
                                    shouldSaveRestoreBackstackId = it.id
                                }
                            }
                            if (shouldSaveRestoreBackstackId != null) {
                                break
                            }
                            parent = parent.parent
                        }
                    }
                    shouldSaveRestoreBackstackId?.let { cacheOrRestoreDestinationBasedBackstack(it) }
                }
            }
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
                savedInstanceState.getSparseParcelableArray<Bundle>(KEY_GRAPH_SAVED_STATES)
                    ?: SparseArray()

            val previousBackstackRootDestination = savedInstanceState.getInt(KEY_DESTINATION_ID, -1)
            Log.i(
                "ConductorNavigation",
                "Restoring savedInstanceState previousBackstackRootDestination: $previousBackstackRootDestination"
            )

            if (previousBackstackRootDestination == -1) {
                null
            } else {
                previousBackstackRootDestination
            }?.let {
                this.previousBackstackRootDestination = PREVIOUS_DESTINATION_RESTORATION
                cacheOrRestoreDestinationBasedBackstack(it)
            }
        } else {
            savedStateBundles = SparseArray()
            /*
             * Don't do caching - shouldCacheCurrentGraph = false,
             * as we are either initializing from noting, or restoring state,
             * and in neither case we need/should cache the current backstack.
             */
            navHostController.setGraph(graphId, startDestinationArgs)
        }
    }

    /**
     * Caches the entire Navigation and backstack
     * each time we navigate on a graph which has the same [id] as previous navigation,
     * and also for the the first navigation.
     *
     * If navigation occurs from one [id] to another,
     * then we will restore the entire state and backstack,
     * essentially managing multiple backstacks,
     * or cache it if its the first time we navigate there.
     *
     * @param id defining a "backstack", usually a root graph id, which can be looked up from any destination further down the graph
     */
    private fun cacheOrRestoreDestinationBasedBackstack(id: Int) {
        val previousId = previousBackstackRootDestination
        if (previousId == null || previousId == id) {
            /*
             * Everytime we arrive at a destination where root id was found matching,
             * we cache the state, except when we're changing the backstack ourselves,
             * or restoring via PREVIOUS_DESTINATION_RESTORATION
             */
            cacheNavigationState(id)
        } else {
            /*
             * If the previousId is not the same as current and we have a cache version,
             * then configure the and NavHost with the right cached state.
             */
            val savedState = savedStateBundles[id]
            if (savedState != null) {
                /*
                 * Restore navController the previous state,
                 * then call setGraph to trigger a complete wipe of the current backstack,
                 * and restoration of the previous state we just set as well as triggering
                 * call to ControllerNavigator.onRestoreState
                 */
                navController.restoreState(savedState)
                navHostController.setGraph(graphId, startDestinationArgs)

                Log.i(
                    "ConductorNavHost",
                    "cacheOrRestoreDestinationBasedBackstack RESTORING destinationId=$id, backstackLabels=${navController.backStack.map { "${it.destination.label}," }}"
                )
                /*
                 * Cache newly restored state again, since re-restoring a previously restored state causes weird issues,
                 * where a controller wont be on the right "backstack" anymore but be on a different than original
                 */
                cacheNavigationState(id)
            } else {
                /*
                 * New destination, so save state
                 */
                cacheNavigationState(id)
            }
        }
        previousBackstackRootDestination = id
    }

    /**
     * Allow navigation state to be saved in the provided [Bundle]
     */
    fun onSaveInstanceState(outState: Bundle) {
        Log.i("ConductorNavHost", "onSaveInstanceState outState=$outState")
        previousBackstackRootDestination?.let {
            cacheNavigationState(it)
            outState.putInt(KEY_DESTINATION_ID, it)
        }
        startDestinationArgs?.let { outState.putBundle(KEY_START_DEST_ARGS, it) }
        outState.putInt(KEY_GRAPH_ID, graphId)
        outState.putSparseParcelableArray(KEY_GRAPH_SAVED_STATES, savedStateBundles)
    }

    private fun cacheNavigationState(id: Int) {
        val backstackDestinationLabels = navController.backStack.map { "${it.destination.label}," }
        Log.i(
            "ConductorNavHost",
            "cacheNavigationState CACHING id=$id, backstackLabels=$backstackDestinationLabels"
        )
        /*
         * Save state of current Router and NavHost,
         * since setting a new graph will pop any backstack associated with the previous graph,
         * so we can restore it to this Router backstack and NavHost backstack,
         * upon setting this graph, back again.
         */
        val navigationBundle = navController.saveState() ?: Bundle.EMPTY
        savedStateBundles.put(id, navigationBundle)
    }

    /**
     * @see [ControllerNavigator.setAllPopAnimations]
     */
    fun setAllPopAnimations(enabled: Boolean) {
        navigator.setAllPopAnimations(enabled)
    }

    companion object {
        private const val KEY_GRAPH_ID = "android-support-nav:conductor:graphId"
        private const val KEY_DESTINATION_ID = "android-support-nav:conductor:destinationId"
        private const val KEY_START_DEST_ARGS =
            "android-support-nav:conductor:startDestinationArgs"
        private const val KEY_GRAPH_SAVED_STATES = "android-support-nav:conductor:graphSavedStates"
        private const val PREVIOUS_DESTINATION_RESTORATION = -1337
    }

    override fun getNavController(): NavController = navHostController
}