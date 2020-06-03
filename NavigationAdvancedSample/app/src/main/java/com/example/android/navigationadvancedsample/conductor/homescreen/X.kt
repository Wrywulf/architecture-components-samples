package com.example.android.navigationadvancedsample.conductor.homescreen

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.navigation.findNavController
import com.bluelinelabs.conductor.Controller
import com.example.android.navigationadvancedsample.R

/**
 * Shows "About"
 */
class X(arguments: Bundle? = null) : Controller(arguments) {


    @SuppressLint("SetTextI18n")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup,
        savedViewState: Bundle?
    ): View {
        return inflater.inflate(R.layout.dest_x, container, false)
                .also {
                    it.findViewById<Toolbar>(R.id.toolbar)
                            .setTitle(R.string.title_x) //TODO use title from Destination

                    val index = args.getInt(KEY_INDEX, -1)
                    it.findViewById<TextView>(R.id.lbl_x).text = "X $index"


                    it.findViewById<Button>(R.id.btn_pushX)
                            .setOnClickListener {
                                it.findNavController()
                                        .navigate(R.id.action_x_to_x, Bundle().apply {
                                            putInt(
                                                KEY_INDEX, index + 1
                                            )
                                        })
                            }
                }
    }

    companion object {
        const val KEY_INDEX = "index"
    }
}
