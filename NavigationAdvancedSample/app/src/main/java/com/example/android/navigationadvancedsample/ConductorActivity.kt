package com.example.android.navigationadvancedsample

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.android.navigationadvancedsample.conductor.ConductorNavigation
import com.google.android.material.bottomnavigation.BottomNavigationView

/**
 * An activity that inflates a layout that has a [BottomNavigationView].
 */
class ConductorActivity : AppCompatActivity() {

    private lateinit var conductorNavigation : ConductorNavigation

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_conductor)

        conductorNavigation = ConductorNavigation(
            activity = this,
            container = findViewById(R.id.nav_host_container),
            graphId = R.navigation.single_stack,
            savedInstanceState = savedInstanceState
        )
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        conductorNavigation.onSaveInstanceState(outState)
    }
}
