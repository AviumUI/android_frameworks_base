/*
 * Copyright (C) 2025-2026 The AviumUI Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.avium.systemui.lockscreen

import android.content.Context
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.core.view.isVisible
import java.util.IdentityHashMap
import com.android.systemui.dagger.SysUISingleton
import org.avium.systemui.lockscreen.util.CustomLockscreenSettings
import javax.inject.Inject

private const val TAG = "AVIUM_LOCKSCREEN"

@SysUISingleton
class NativeLockscreenViewHider @Inject constructor(private val context: Context) {

    private data class HiddenState(
        val visibility: Int,
        val width: Int,
        val height: Int,
        val layoutListener: View.OnLayoutChangeListener,
        val drawListener: ViewTreeObserver.OnDrawListener
    )

    private val hiddenViews = IdentityHashMap<View, HiddenState>()

    fun hideNativeViews(entryView: View) {
        if (!CustomLockscreenSettings.isEnabled()) {
            Log.d(TAG, "Custom lockscreen is disabled, native views will remain visible.")
            return
        }
        entryView.postDelayed({
            val rootView = entryView.rootView as? ViewGroup ?: return@postDelayed
            val rootId = context.resources.getIdentifier("keyguard_root_view", "id", "com.android.systemui")
            if (rootView.id != rootId) {
                 Log.w(TAG, "Could not find keyguard_root_view.")
            }

            Log.d(TAG, "Hiding native lockscreen clock views...")
            listOf(
                "bc_smartspace_view",
                "date_smartspace_view",
                "lockscreen_clock_view",
                "lockscreen_clock_view_large",
                "keyguard_slice_view"
            ).map { resourceName ->
                val resourceId = context.resources.getIdentifier(resourceName, "id", "com.android.systemui")
                if (resourceId != 0) { 
                    rootView.findViewById<View?>(resourceId)
                } else {
                    Log.w(TAG, "Resource not found for name: $resourceName")
                    null
                }
            }.forEach { view ->
                view?.hideView()
            }
        }, 100) 
    }

    fun restoreNativeViews(entryView: View) {
        val rootView = entryView.rootView as? ViewGroup ?: return
        val resourceNames = listOf(
            "bc_smartspace_view",
            "date_smartspace_view",
            "lockscreen_clock_view",
            "lockscreen_clock_view_large",
            "keyguard_slice_view"
        )
        resourceNames.forEach { resourceName ->
            val id = context.resources.getIdentifier(resourceName, "id", "com.android.systemui")
            if (id != 0) rootView.findViewById<View?>(id)?.restoreView()
        }
    }

    private fun View?.hideView() {
        if (this == null) return

        if (hiddenViews.containsKey(this)) return

        val originalParams = layoutParams
        val originalVisibility = visibility
        val originalWidth = originalParams?.width ?: ViewGroup.LayoutParams.WRAP_CONTENT
        val originalHeight = originalParams?.height ?: ViewGroup.LayoutParams.WRAP_CONTENT
        lateinit var layoutListener: View.OnLayoutChangeListener
        lateinit var drawListener: ViewTreeObserver.OnDrawListener

        fun makeInvisible() {
            if (isVisible) {
                visibility = View.INVISIBLE
            }
        }

        fun makeSizeZero() {
            val params = layoutParams
            if (params != null) {
                if (params.height != 0) params.height = 0
                if (params.width != 0) params.width = 0
                layoutParams = params
            }
        }

        makeSizeZero()
        makeInvisible()

        layoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            makeSizeZero()
            makeInvisible()
        }
        drawListener = ViewTreeObserver.OnDrawListener {
            makeInvisible()
        }
        addOnLayoutChangeListener(layoutListener)
        viewTreeObserver?.takeIf { it.isAlive }?.addOnDrawListener(drawListener)
        hiddenViews[this] = HiddenState(
            originalVisibility,
            originalWidth,
            originalHeight,
            layoutListener,
            drawListener
        )
        Log.d(TAG, "View ${this.id} hidden.")
    }

    private fun View?.restoreView() {
        if (this == null) return
        val state = hiddenViews.remove(this) ?: return
        removeOnLayoutChangeListener(state.layoutListener)
        viewTreeObserver?.takeIf { it.isAlive }?.removeOnDrawListener(state.drawListener)
        layoutParams = layoutParams?.also {
            it.width = state.width
            it.height = state.height
        }
        visibility = state.visibility
    }
}
