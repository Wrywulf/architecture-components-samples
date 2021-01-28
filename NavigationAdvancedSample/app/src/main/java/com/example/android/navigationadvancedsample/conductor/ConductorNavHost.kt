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
    private val backstackRootDestinations: Set<Int>,
    private var startDestinationArgs: Bundle? = null
) : NavHost {
    private var navHostController: ConductorNavHostController
    private val savedStateBundles: SparseArray<SavedStates>
    private val router: Router = Conductor.attachRouter(activity, container, savedInstanceState)
    private val navigator = ControllerNavigator(router)
    private val destroyRouterMethod =
        Router::class.java.getDeclaredMethod("destroy", Boolean::class.java)
                .apply {
                    isAccessible = true
                }
    private var previousBackstackRootDestination: Int? = null

    init {
        Log.i("ConductorNavigation", "init")
        if (backstackRootDestinations.isEmpty()) {
            throw IllegalArgumentException("backstackRootDestinations must have at least 1 element, to support restoration of Navigation.")
        }
        navHostController = ConductorNavHostController(activity).apply {
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
                savedInstanceState.getSparseParcelableArray<SavedStates>(KEY_GRAPH_SAVED_STATES)
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
     * Lets callers navigate to a [destinationId],
     * while caching the backstack up until then.
     * This means when someone calls this method again,
     * with a previously used [destinationId],
     * we will restore the backstack present at that time,
     * thus letting callers switch between [destinationId] and their backstacks,
     * thus essentially allowing multiple backstacks.
     *
     * @param destinationId of the new graph we wish to use
     */
    private fun cacheOrRestoreDestinationBasedBackstack(destinationId: Int) {
        Log.i(
            "ConductorNavHost",
            "cacheOrRestoreDestinationBasedBackstack destinationId=$destinationId, previousBackstackRootDestination=${previousBackstackRootDestination}," +
                    " routerBackStack size=${router.backstackSize}, navSize=${navHostController.backStack.size}"
        )
        val previousDestinationId = previousBackstackRootDestination
        if (previousDestinationId == null || previousDestinationId == destinationId) {
            /*
             * Everytime we arrive at a destination where root id was found matching,
             * we cache the state, except when we're changing the backstack ourselves,
             * or restoring via PREVIOUS_DESTINATION_RESTORATION
             */
            cacheGraphState(destinationId)
        } else {
            /*
             * If the previousDestinationId is not the same as current and we have a cache version, then restore
             */
            /* Configure the Router and NavHost with the right cached state, if present */
            val savedStates = savedStateBundles[destinationId]
            if (savedStates != null) {
                Log.i(
                    "ConductorNavHost",
                    "cacheOrRestoreDestinationBasedBackstack RESTORING destinationId=$destinationId")
                /*
                 * Restore navController the previous state,
                 * then call setGraph to trigger a complete wipe of the current backstack,
                 * and restoration of the previous state we just set
                 */
                navController.restoreState(savedStates.navControllerBundle)
                navHostController.setGraph(graphId, startDestinationArgs)
                /*
             * In full activity restoration cases, it seems the router backstack,
             * navigation backstack and our cached router backstack gets out of sync,
             * destroying the router here, just prior to our manual restoration,
             * fixes that issue.
             */
//                destroyRouterMethod.invoke(router, true)

                /*
                 * Then restore the router state with the old backstack
                 */
                router.restoreInstanceState(savedStates.routerBundle)
                router.rebindIfNeeded()
//                savedStateBundles.remove(destinationId)
            } else {
                /*
                 * FIXME new destination so we need to clear backstack to previous destination,
                 *  then push new destination again, then save
                 */
                /*
                 * New destination, so save state
                 */
                cacheGraphState(destinationId)
            }
        }
        previousBackstackRootDestination = destinationId
    }

    /**
     * Allow navigation state to be saved in the provided [Bundle]
     */
    fun onSaveInstanceState(outState: Bundle) {
        Log.i("ConductorNavHost", "onSaveInstanceState outState=$outState")
        previousBackstackRootDestination?.let {
            cacheGraphState(it)
            outState.putInt(KEY_DESTINATION_ID, it)
        }
        startDestinationArgs?.let { outState.putBundle(KEY_START_DEST_ARGS, it) }
        outState.putInt(KEY_GRAPH_ID, graphId)
        outState.putSparseParcelableArray(KEY_GRAPH_SAVED_STATES, savedStateBundles)
    }

    private fun cacheGraphState(id: Int) {
        Log.i(
            "ConductorNavHost",
            "cacheGraphState CACHING id=$id")
        /*
         * Save state of current Router and NavHost,
         * since setting a new graph will pop any backstack associated with the previous graph,
         * so we can restore it to this Router backstack and NavHost backstack,
         * upon setting this graph, back again.
         */
        val routerBundle = Bundle()
        router.saveInstanceState(routerBundle)
        val navigationBundle = navController.saveState() ?: Bundle.EMPTY
        savedStateBundles.put(
            id,
            SavedStates(
                routerBundle,
                navigationBundle
            )
        )
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

    @Parcelize
    private data class SavedStates(val routerBundle: Bundle, val navControllerBundle: Bundle) :
        Parcelable
}