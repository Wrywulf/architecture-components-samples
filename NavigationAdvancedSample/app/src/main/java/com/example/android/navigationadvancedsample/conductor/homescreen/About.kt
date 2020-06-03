package com.example.android.navigationadvancedsample.conductor.homescreen

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import com.bluelinelabs.conductor.Controller
import com.example.android.navigationadvancedsample.R

/**
 * Shows "About"
 */
class About() : Controller() {

    protected constructor(bundle: Bundle?) : this()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup,
        savedViewState: Bundle?
    ): View {
        return inflater.inflate(R.layout.dest_about, container, false).also {
            it.findViewById<Toolbar>(R.id.toolbar).setTitle(R.string.title_home)
        }
    }
}
