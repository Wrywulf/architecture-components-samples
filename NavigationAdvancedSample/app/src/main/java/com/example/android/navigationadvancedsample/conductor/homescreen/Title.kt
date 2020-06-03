package com.example.android.navigationadvancedsample.conductor.homescreen

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.appcompat.widget.Toolbar
import androidx.navigation.findNavController
import com.bluelinelabs.conductor.Controller
import com.example.android.navigationadvancedsample.R

/**
 * Shows the main title screen with a button that navigates to [About].
 */
class Title() : Controller() {
    protected constructor(bundle: Bundle?) : this()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup,
        savedViewState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.dest_title, container, false)

        view.findViewById<Toolbar>(R.id.toolbar).setTitle(R.string.title_home)
        view.findViewById<Button>(R.id.about_btn)
                .setOnClickListener {
                    it.findNavController()
                            .navigate(R.id.action_title_to_about)
                }
        return view
    }
}
