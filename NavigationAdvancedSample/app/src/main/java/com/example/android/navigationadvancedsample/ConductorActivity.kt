package com.example.android.navigationadvancedsample

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.example.android.navigationadvancedsample.conductor.ConductorNavHost
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

        val navGraphIds = mapOf(
            R.id.home to R.navigation.home,
            R.id.list to R.navigation.list,
            R.id.form to R.navigation.form
        )

        conductorNavHost = ConductorNavHost(
            activity = this,
            container = findViewById(R.id.nav_host_container),
            graphId = R.navigation.home,
            savedInstanceState = savedInstanceState
        )
        // Setup the bottom navigation view with a list of navigation graphs
        bottomNavigationView.setupWithConductorNavController(
            navGraphIds = navGraphIds,
            intent = intent,
            conductorNavHost = conductorNavHost
        )
        /*
         * Hide/Show bottomNavigationView for pre-defined destinations
         */
        val visibleBottomNavDestinations = setOf(R.id.titleScreen, R.id.leaderboard, R.id.register)
        conductorNavHost.navController.addOnDestinationChangedListener { controller, destination, arguments ->
            Log.i(
                "ConductorActivity",
                "OnDestinationChangedListener destination=$destination, controller=$controller, arguments=$arguments"
            )
            bottomNavigationView.isVisible = visibleBottomNavDestinations.contains(destination.id)
        }
    }

}
