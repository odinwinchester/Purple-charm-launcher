package com.purplecharm.launcher

import android.content.Context
import android.graphics.Color
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView

/**
 * Four independently owned visual layers for the expanded weather screen.
 * Layer 0: backdrop; 1: celestial artwork; 2: optional effects;
 * 3: scrollable meteorological information and controls.
 *
 * Legacy combined artwork remains a single layer-1 image. Never draw
 * additional sun/moon/clouds from that same PNG in the effects layer.
 */
internal class WeatherLayerComposer(context: Context) : FrameLayout(context) {
    val backgroundLayer = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    val celestialLayer = FrameLayout(context).apply {
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
    }
    val effectsLayer = FrameLayout(context).apply {
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        isClickable = false
        isFocusable = false
    }
    val informationLayer = FrameLayout(context).apply {
        setBackgroundColor(Color.TRANSPARENT)
    }

    init {
        setBackgroundColor(Color.rgb(12, 6, 29))
        addView(backgroundLayer, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(celestialLayer, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(effectsLayer, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(informationLayer, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    fun setBackdrop(resourceId: Int) {
        backgroundLayer.setImageResource(resourceId)
    }

    fun setCelestialArtwork(resourceId: Int, label: String, topMarginPx: Int, heightPx: Int) {
        celestialLayer.removeAllViews()
        val image = ImageView(context).apply {
            setImageResource(resourceId)
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
            contentDescription = label
        }
        celestialLayer.addView(image, LayoutParams(
            LayoutParams.MATCH_PARENT, heightPx
        ).apply { topMargin = topMarginPx })
    }

    fun clearCelestialArtwork() = celestialLayer.removeAllViews()

    fun setEffectsVisible(visible: Boolean) {
        effectsLayer.visibility = if (visible) View.VISIBLE else View.GONE
    }

    fun setInformation(view: View) {
        informationLayer.removeAllViews()
        informationLayer.addView(view, LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT
        ))
    }
}
