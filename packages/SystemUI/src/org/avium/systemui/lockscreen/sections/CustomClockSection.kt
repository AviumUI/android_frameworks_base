package org.avium.systemui.lockscreen.sections

import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.keyguard.shared.model.KeyguardSection
import org.avium.systemui.lockscreen.CustomLockscreenClockManager
import javax.inject.Inject

private const val TAG = "AVIUM_LOCKSCREEN" 

@SysUISingleton
class CustomClockSection @Inject constructor(
    private val customLockscreenClockManager: CustomLockscreenClockManager
) : KeyguardSection() {

    private val customView: View?
        get() = customLockscreenClockManager.view

    override fun addViews(parent: ConstraintLayout) {
        val view = customView ?: run {
            return
        }

        if (view.id == View.NO_ID) {
            view.id = View.generateViewId()
        }
        
        val oldParent = view.parent
        if (oldParent is ViewGroup) {
            oldParent.removeView(view)
        }
        
        parent.addView(view, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    override fun removeViews(parent: ConstraintLayout) {
        customView?.let { parent.removeView(it) }
    }

    override fun bindData(parent: ConstraintLayout) {
        Log.d(TAG, "do nothing")
    }

    override fun applyConstraints(constraintSet: ConstraintSet) {
        val view = customView ?: return

        constraintSet.constrainWidth(view.id, ConstraintSet.MATCH_CONSTRAINT)
        constraintSet.constrainHeight(view.id, ConstraintSet.MATCH_CONSTRAINT)

        constraintSet.connect(view.id, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
        constraintSet.connect(view.id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
        constraintSet.connect(view.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
        constraintSet.connect(view.id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
    }
}