/*
 * Copyright 2021 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.chrisbanes.insetter

import android.view.View
import android.view.ViewParent
import androidx.collection.ArraySet
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsAnimationCompat.Callback.DISPATCH_MODE_CONTINUE_ON_SUBTREE
import androidx.core.view.WindowInsetsCompat

class AnimatedInsetter internal constructor(
    private val views: Set<View>,
    private val parent: View,
    private val persistentInsetTypes: Int,
    private val animatingInsetTypes: Int,
    private val focusViews: Set<View>,
) {
    class Builder() {
        private var animatingViews = ArraySet<View>()
        private var focusViews = ArraySet<View>()
        private var parent: ViewParent? = null

        private var persistentInsetTypes: Int = 0
        private var animatingInsetTypes: Int = 0

        fun animateView(view: View) {
            animatingViews.add(view)
        }

        fun focusView(view: View) {
            focusViews.add(view)
        }

        fun setParent(parent: ViewParent) {
            this.parent = parent
        }

        fun setPersistentTypes(types: Int) {
            persistentInsetTypes = types
        }

        fun setAnimatingTypes(types: Int) {
            animatingInsetTypes = types
        }

        fun build(): AnimatedInsetter {
            require(animatingViews.isNotEmpty()) {
                "No views have been provided to addView()"
            }
            requireNotNull(parent) {
                "A common non-null parent to all views must be provided"
            }
            require(
                animatingInsetTypes and WindowInsetsCompat.Type.ime() == 0 ||
                    focusViews.isEmpty()
            ) {
                "A view to control focus has been provided but the types provided to" +
                    " setAnimatingTypes() do no include the IME"
            }

            val p = parent
            require(p is View) {
                // TODO improve error message
                "The provided parent does not extend View"
            }

            return AnimatedInsetter(
                views = animatingViews,
                parent = p,
                persistentInsetTypes = persistentInsetTypes,
                animatingInsetTypes = animatingInsetTypes,
                focusViews = focusViews,
            )
        }
    }

    fun set() {
        /**
         * 1) Since our Activity has declared `window.setDecorFitsSystemWindows(false)`, we need to
         * handle any [WindowInsetsCompat] as appropriate.
         *
         * Our [RootViewDeferringInsetsCallback] will update our attached view's padding to match
         * the combination of the [WindowInsetsCompat.Type.systemBars], and selectively apply the
         * [WindowInsetsCompat.Type.ime] insets, depending on any ongoing WindowInsetAnimations
         * (see that class for more information).
         */

        val deferringInsetsListener = RootViewDeferringInsetsCallback(
            persistentInsetTypes = persistentInsetTypes,
            deferredInsetTypes = animatingInsetTypes
        )
        // RootViewDeferringInsetsCallback is both an WindowInsetsAnimation.Callback and an
        // OnApplyWindowInsetsListener, so needs to be set as so.
        ViewCompat.setWindowInsetsAnimationCallback(parent, deferringInsetsListener)
        ViewCompat.setOnApplyWindowInsetsListener(parent, deferringInsetsListener)

        /**
         * 2) The second step is reacting to any animations which run. This can be system driven,
         * such as the user focusing on an EditText and on-screen keyboard (IME) coming on screen,
         * or app driven (more on that in step 3).
         *
         * To react to animations, we set an [android.view.WindowInsetsAnimation.Callback] on any
         * views which we wish to react to inset animations. In this example, we want our
         * EditText holder view, and the conversation RecyclerView to react.
         *
         * We use our [TranslateDeferringInsetsAnimationCallback] class, bundled in this sample,
         * which will automatically move each view as the IME animates.
         *
         * Note about [TranslateDeferringInsetsAnimationCallback], it relies on the behavior of
         * [RootViewDeferringInsetsCallback] on the layout's root view.
         */

        views.forEach { view ->
            ViewCompat.setWindowInsetsAnimationCallback(
                view,
                TranslateDeferringInsetsAnimationCallback(
                    view = view,
                    persistentInsetTypes = persistentInsetTypes,
                    deferredInsetTypes = animatingInsetTypes,
                    dispatchMode = DISPATCH_MODE_CONTINUE_ON_SUBTREE
                )
            )
        }

        /**
         * 2.5) We also want to make sure that our EditText is focused once the IME
         * is animated in, to enable it to accept input. Similarly, if the IME is animated
         * off screen and the EditText is focused, we should clear that focus.
         *
         * The bundled [ControlFocusInsetsAnimationCallback] callback will automatically request
         * and clear focus for us.
         *
         * Since `binding.messageEdittext` is a child of `binding.messageHolder`, this
         * [WindowInsetsAnimationCompat.Callback] will only work if the ancestor view's callback uses the
         * [WindowInsetsAnimationCompat.Callback.DISPATCH_MODE_CONTINUE_ON_SUBTREE] dispatch mode, which
         * we have done above.
         */

        focusViews.forEach { view ->
            ViewCompat.setWindowInsetsAnimationCallback(
                view,
                ControlFocusInsetsAnimationCallback(view, animatingInsetTypes)
            )
        }
    }
}