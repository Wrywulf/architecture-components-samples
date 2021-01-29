package com.example.android.navigationadvancedsample

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.android.navigationadvancedsample.conductor.ConductorNavHost
import com.example.android.navigationadvancedsample.conductor.listscreen.MyAdapter
import com.google.android.material.bottomnavigation.BottomNavigationView

/**
 * An activity that inflates a layout that has a [BottomNavigationView].
 */
class ConductorActivity : AppCompatActivity() {

    private lateinit var conductorNavHost: ConductorNavHost

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_conductor)

        if (savedInstanceState == null) {
            setupBottomNavigationBar(savedInstanceState)
        } // Else, need to wait for onRestoreInstanceState
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        conductorNavHost.onSaveInstanceState(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        // Now that BottomNavigationBar has restored its instance state
        // and its selectedItemId, we can proceed with setting up the
        // BottomNavigationBar with Navigation
        setupBottomNavigationBar(savedInstanceState)
    }

    /**
     * Called on first creation and when restoring state.
     */
    private fun setupBottomNavigationBar(savedInstanceState: Bundle?) {
        val bottomNavigationView = findViewById<BottomNavigationView>(R.id.bottom_nav)

        /*
         * FIXME could resolve root graph first node start destinations,
         * but leaving it as a map here for now since it may provide more flexibility to not make graph design assumptions
         */
        val navGraphIds = mapOf(
            R.id.home to R.id.titleScreen,
            R.id.list to R.id.leaderboard,
            R.id.form to R.id.register
        )

        conductorNavHost = ConductorNavHost(
            activity = this,
            container = findViewById(R.id.nav_host_container),
            graphId = R.navigation.root,
            backstackRootDestinations = setOf(R.id.home,
                                                      R.id.list,
                                                      R.id.form),
            savedInstanceState = savedInstanceState
        )

        // Setup the bottom navigation view with a list of navigation graphs
        bottomNavigationView.setupWithConductorNavController(
            navGraphIds = navGraphIds,
            conductorNavHost = conductorNavHost
        )
        /*
         * Hide/Show bottomNavigationView for pre-defined destinations
         */
        val visibleBottomNavDestinations = setOf(
            R.id.home,
            R.id.list,
            R.id.form
        )
        conductorNavHost.navController.addOnDestinationChangedListener { controller, destination, arguments ->
            destination.parent?.id?.apply {
                val isSupportedDestination = visibleBottomNavDestinations.contains(this)

                if (isSupportedDestination && bottomNavigationView.selectedItemId != this) {
                    /* set checked, without triggering the click action */
                    bottomNavigationView.menu.findItem(this).isChecked = true
                }
//            bottomNavigationView.isVisible = visibleBottomNavDestinations.contains(destination.id)
            }

        }
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        /*
         * Apparently navController will crash if we attempt to do deepLink navigation,
         * while LifecycleOwner reports Lifecycle.State.INITIALIZED.
         * When we get the onPostCreateCallback we are guaranteed to have moved to Lifecycle.State.CREATED
         */
//        val deepLinkIntent = conductorNavHost.navController
//                .createDeepLink()
//                .setDestination(R.id.userProfile)
//                .setArguments(Bundle().apply { putString(MyAdapter.USERNAME_KEY, "Deep Linked") })
//                .createTaskStackBuilder().intents[0]
//        conductorNavHost.navController.handleDeepLink(deepLinkIntent)
    }

}
