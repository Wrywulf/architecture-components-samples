package com.example.android.navigationadvancedsample.conductor.homescreen

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
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
                    val index = args.getInt(KEY_INDEX, -1)
                    Log.d("X", "onCreateView. index $index")
                    it.findViewById<Toolbar>(R.id.toolbar)
                            .apply {
                                setTitle(R.string.title_x) //TODO use title from Destination
                                setNavigationIcon(R.drawable.ic_baseline_arrow_back_24)
                                setNavigationOnClickListener {
                                    findNavController().navigate(R.id.action_upToHome)
                                }
                            }

                    it.findViewById<TextView>(R.id.lbl_x).text = "X $index"


                    it.findViewById<Button>(R.id.btn_pushX)
                            .setOnClickListener {
                                Log.d("X", "navigate from $index -> ${index+1}")
                                it.findNavController()
                                        .navigate(R.id.action_x_to_x, Bundle().apply {
                                            putInt(
                                                KEY_INDEX, index + 1
                                            )
                                        })
                            }
                }
    }

    override fun onDestroyView(view: View) {
        Log.d("X", "onDestroyView. index ${args.getInt(KEY_INDEX, -1)}")
    }

    companion object {
        const val KEY_INDEX = "index"
    }
}
