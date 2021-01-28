package com.example.android.navigationadvancedsample.conductor

import android.app.Activity
import android.os.Bundle
import android.os.Parcelable
import android.util.Log
import android.util.SparseArray
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.doOnLayout
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
    onNavigationReadyListener:OnNavigationReadyListener,
    private val backstackRootDestinations: Set<Int> = setOf(),
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
    private val routerContainerFullyAttachedField =
        Router::class.java.getDeclaredField("containerFullyAttached")
                .apply {
                    isAccessible = true
                }
    private var previousBackstackRootDestination: Int? = null

    init {
        Log.i("ConductorNavigation", "init")
//        if(container.parent != null){
//            routerContainerFullyAttachedField.setBoolean(router, true)
//        } else {
//            container.doOnLayout {
//                routerContainerFullyAttachedField.setBoolean(router, true)
//            }
//        }
        navHostController = ConductorNavHostController(activity).apply {
            setLifecycleOwner(activity)
            setOnBackPressedDispatcher(activity.onBackPressedDispatcher)
            navigatorProvider.addNavigator(navigator)
//            if (backstackRootDestinations.isNotEmpty()) {
//                addOnDestinationChangedListener { controller, destination, arguments ->
//                    /*
//                     * FIXME if destination exists in a subgraph where rootNode Id matches our set, then save/restore them
//                     */
////                    val destinationHierarchy = mutableListOf<NavGraph>()
//                    var shouldSaveRestoreBackstackId: Int? = null
//                    while (destination.parent != null) {
//                        destination.parent?.let {
//                            if (backstackRootDestinations.contains(it.id)) {
//                                shouldSaveRestoreBackstackId = it.id
//
//                            }
////                            destinationHierarchy.add(it)
//                        }
//                        if (shouldSaveRestoreBackstackId != null) {
//                            break
//                        }
//                    }
//                    if (shouldSaveRestoreBackstackId != null) {
//                        shouldSaveRestoreBackstackId?.let { navigateBackstacks(it) }
//                    }
//                }
//            }
        }
        Navigation.setViewNavController(container, navHostController)

        // Restore any state if needed
        if (savedInstanceState != null) {
            Log.i("ConductorNavigation", "Restoring savedInstanceState: $savedInstanceState")
            startDestinationArgs = savedInstanceState.getBundle(KEY_START_DEST_ARGS)

            // override using saved state
            graphId = savedInstanceState.getInt(KEY_GRAPH_ID)
            previousBackstackRootDestination = savedInstanceState.getInt(KEY_DESTINATION_ID)
            Log.i("ConductorNavigation", "Restoring savedInstanceState graphId: $graphId")
            Log.i(
                "ConductorNavigation",
                "Restoring savedInstanceState previousBackstackRootDestination: $previousBackstackRootDestination"
            )

            savedStateBundles =
                savedInstanceState.getSparseParcelableArray<SavedStates>(KEY_GRAPH_SAVED_STATES)
                    ?: SparseArray()
        } else {
            savedStateBundles = SparseArray()
        }
        /*
         * Don't do caching - shouldCacheCurrentGraph = false,
         * as we are either initializing from noting, or restoring state,
         * and in neither case we need/should cache the current backstack.
         */
//            navHostController.setGraph(graphId, startDestinationArgs)

//        router.rebindIfNeeded()
//        if (savedInstanceState != null) {
//            previousBackstackRootDestination?.let {
//                navigateBackstacks(it, startDestinationArgs, false)
//            }
//        }
        if(container.windowToken != null){
            navHostController.setGraph(graphId, startDestinationArgs)
            container.post{onNavigationReadyListener.onNavigationReady(navController)}
        } else {
            /*
             FIXME investigate if Router looses container at any point? postpone any navigation until container is ready and attached to window.
             see if first swap handler needs the listener still after this listener here.
             try to remove the destroy router calls
             */
            container.addOnAttachStateChangeListener(object :

                                                         View.OnAttachStateChangeListener{
                override fun onViewAttachedToWindow(v: View?) {
                    container.removeOnAttachStateChangeListener(this)

                    /*
                     * Don't do caching - shouldCacheCurrentGraph = false,
                     * as we are either initializing from noting, or restoring state,
                     * and in neither case we need/should cache the current backstack.
                     */
                    navHostController.setGraph(graphId, startDestinationArgs)
                    container.post{onNavigationReadyListener.onNavigationReady(navController)}

                }

                override fun onViewDetachedFromWindow(v: View?) {
                    container.removeOnAttachStateChangeListener(this)
                }

            })
        }
    }

    /*
     * Due to the initial graph setting, causing navigate to startDestination,
     * but the container not having been attached to the window yet,
     * which Conductor will then wait for on i.e. SimpleSwapChangeHandler,
     * any Controller we try to navigate to will have its view inflated and attached,
     * but the Controller wont be attached till the onViewAttachedToWindow,
     * as well as whenever the view.post() handler decides to run performPendingControllerChanges
     * in Router#832.
     * It seem that doing further navigation, i.e. attempting to deeplink,
     * while this is being set up, causes Controller views to be attached,
     * but not respond to any clicks or interaction - back press works though.
     */
    /*
     * Regarding co-hosting non-Conductor inside the same container will probably be an issue,
     * since Router#removeAllExceptVisibleAndUnowned indiscrimenately removes all other views it doesnt know about.
     */
    private fun cacheGraphState(id: Int) {
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
     * @param startDestinationArgs the optional arguments for the startDestination in the root graph. Default is null
     */
    private fun navigateBackstacks(
        destinationId: Int
    ) {
        Log.i(
            "ConductorNavHost",
            "navigateBackstack destinationId=$destinationId, previousBackstackRootDestination=${previousBackstackRootDestination}, routerBackStack size=${router.backstackSize}, navSize=${navHostController.backStack.size}"
        )
//        if (shouldCacheCurrent) {
//            cacheGraphState(destinationId)
//        }
//
//        if (destroyCachedGraph) {
//            savedStateBundles.remove(destinationId)
//        }
        val previousDestinationId = previousBackstackRootDestination
        if (previousDestinationId == null || previousDestinationId == destinationId) {

            /*
             * Everytime we arrive at a destination where root id was found matching,
             * we cache the state, except when we're changing the backstack ourselves
             */
            cacheGraphState(destinationId)
        } else {
            /*
             * If the previousDestinationId is not the same as current and we have a cache version, then restore
             */
            /* Configure the Router and NavHost with the right cached state, if present */
            val savedStates = savedStateBundles[destinationId]
            Log.i("ConductorNavHost", "navigateBackstack savedStates=$savedStates")
            if (savedStates != null) {
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
                destroyRouterMethod.invoke(router, true)

                /*
             * Then restore the router state with the old backstack
             */
                router.restoreInstanceState(savedStates.routerBundle)
                router.rebindIfNeeded()
                savedStateBundles.remove(destinationId)
            } else {
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
        /*
         * Destroy the router after saving it, since we wish to control restoration ourselves,
         * and not have it re-inflate any potentially restored views,
         * so destroy the router all together first.
         */
        destroyRouterMethod.invoke(router, true)
        startDestinationArgs?.let { outState.putBundle(KEY_START_DEST_ARGS, it) }
        outState.putInt(KEY_GRAPH_ID, graphId)
        outState.putSparseParcelableArray(KEY_GRAPH_SAVED_STATES, savedStateBundles)
    }

    companion object {
        private const val KEY_GRAPH_ID = "android-support-nav:conductor:graphId"
        private const val KEY_DESTINATION_ID = "android-support-nav:conductor:destinationId"
        private const val KEY_START_DEST_ARGS =
            "android-support-nav:conductor:startDestinationArgs"
        private const val KEY_GRAPH_SAVED_STATES = "android-support-nav:conductor:graphSavedStates"
    }

    override fun getNavController(): NavController = navHostController

    @Parcelize
    private data class SavedStates(val routerBundle: Bundle, val navControllerBundle: Bundle) :
        Parcelable

    interface OnNavigationReadyListener{
        fun onNavigationReady(navController:NavController)
    }
}