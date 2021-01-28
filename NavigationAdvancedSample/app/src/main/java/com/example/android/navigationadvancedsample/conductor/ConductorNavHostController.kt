package com.example.android.navigationadvancedsample.conductor

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.navigation.NavDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavOptions
import androidx.navigation.Navigator

class ConductorNavHostController(context: Context) : NavHostController(context) {

//    override fun navigate(
//        node: NavDestination,
//        args: Bundle?,
//        navOptions: NavOptions?,
//        navigatorExtras: Navigator.Extras?
//    ) {
//        super.navigate(node, args, navOptions, navigatorExtras)
//        /*
//         FIXME when we intent to do any sort of navigation, determine if its a new "root"
//         */
//    }
//    override fun navigate(
//        deepLink: Uri,
//        navOptions: NavOptions?,
//        navigatorExtras: Navigator.Extras?
//    ) {
//        super.navigate(deepLink, navOptions, navigatorExtras)
//    }
//
//    override fun handleDeepLink(intent: Intent?): Boolean {
//        val wasHandled = super.handleDeepLink(intent)
//
//        return wasHandled
//    }
}