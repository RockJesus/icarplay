package com.shilapi.xcertplay.carlife

import android.content.Context

/**
 * Single-APK integration does not publish the optional home-screen widget;
 * these no-ops keep the receiver engine independent from launcher resources.
 */
object CarLifeNaviWidget {
    fun clear(context: Context) = Unit
    fun updateMedia(context: Context, song: String, artist: String) = Unit
    fun updateNavi(context: Context, road: String, dist: String) = Unit
}
