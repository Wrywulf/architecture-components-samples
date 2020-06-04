package com.example.android.navigationadvancedsample.conductor.homescreen

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.appcompat.widget.Toolbar
import androidx.navigation.findNavController
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
            Log.d("About", "onCreateView.")
        return inflater.inflate(R.layout.dest_about, container, false).also {
            it.findViewById<Toolbar>(R.id.toolbar).setTitle(R.string.title_about) //TODO use title from Destination

            it.findViewById<Button>(R.id.btn_pushX).setOnClickListener {
                it.findNavController().navigate(R.id.action_about_to_x)
            }
        }
    }

    override fun onDestroyView(view: View) {
            Log.d("About", "onDestroyView.")
    }
}
