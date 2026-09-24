package com.sandevsystems.omarchyremote

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * After the phone starts or the app is updated, Android starts the app's process for this receiver;
 * [KeypadApp.onCreate] then resumes following the agents if it was on. Without it, an update (or a
 * reboot) left the phone disconnected until the app was opened.
 */
class StartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        (context.applicationContext as KeypadApp).following  // the process is up; onCreate did the rest
    }
}
