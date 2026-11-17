package com.ankan.netfreeze

import android.view.View

/** One screen of the bottom navigation. */
interface Tab {
    val view: View
    fun onShow() {}
    fun onHide() {}
}

/** What the tabs can ask the activity to do. */
interface Host {
    fun toggleFreeze()
    fun exemptChanged()
}
