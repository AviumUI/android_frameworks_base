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

package org.avium.systemui.lockscreen.util;

import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.transition.Fade;
import androidx.transition.Transition;
import androidx.transition.TransitionManager;
import android.view.View;

public class LockscreenLayoutManager {

    private ConstraintLayout mContainer;
    private ConstraintSet mDefaultState;
    private Transition mTransition;

    public LockscreenLayoutManager(ConstraintLayout container) {
        this.mContainer = container;
        this.mDefaultState = new ConstraintSet();
        this.mTransition = new Fade();
        this.mTransition.setDuration(300);
        initializeConstraints();
    }

    private void initializeConstraints() {
        if (mContainer != null) {
            mDefaultState.clone(mContainer);
        }
    }

    public void setupHorizontalChain(int[] viewIds, int topMargin) {
        if (viewIds.length >= 2) {
            mDefaultState.createHorizontalChain(
                ConstraintSet.PARENT_ID, ConstraintSet.LEFT,
                ConstraintSet.PARENT_ID, ConstraintSet.RIGHT,
                viewIds, null, ConstraintSet.CHAIN_PACKED);

            for (int viewId : viewIds) {
                mDefaultState.connect(viewId, ConstraintSet.TOP, 
                    ConstraintSet.PARENT_ID, ConstraintSet.TOP, topMargin);
            }
        }
    }

    public void setupBottomChain(int[] viewIds, int bottomMargin) {
        if (viewIds.length >= 2) {
            mDefaultState.createHorizontalChain(
                ConstraintSet.PARENT_ID, ConstraintSet.LEFT,
                ConstraintSet.PARENT_ID, ConstraintSet.RIGHT,
                viewIds, null, ConstraintSet.CHAIN_PACKED);

            for (int viewId : viewIds) {
                mDefaultState.connect(viewId, ConstraintSet.BOTTOM, 
                    ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM, bottomMargin);
            }
        }
    }

    public void applyLayoutChanges() {
        if (mContainer != null) {
            TransitionManager.beginDelayedTransition(mContainer, mTransition);
            mDefaultState.applyTo(mContainer);
        }
    }

    public void setViewVisibility(View view, int visibility) {
        if (view != null) {
            view.setVisibility(visibility);
        }
    }

    public ConstraintSet getConstraintSet() {
        return mDefaultState;
    }
}