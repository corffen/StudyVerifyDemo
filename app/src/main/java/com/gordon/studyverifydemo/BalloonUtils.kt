/*
 * Copyright (C) 2019 skydoves
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.gordon.studyverifydemo

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import com.gordon.common_module.balloon.arrow.ArrowPositionRules
import com.gordon.common_module.balloon.Balloon
import com.gordon.common_module.balloon.BalloonSizeSpec

object BalloonUtils {

    fun getWordBalloon(context: Context, lifecycleOwner: LifecycleOwner): Balloon {
        return Balloon.Builder(context)
            .setLayout(R.layout.layout_mark_word) //设置布局
            .setArrowSize(10) //设置箭头的大小
            .setHeight(BalloonSizeSpec.WRAP) //设置高度为wrapContent
            .setArrowPositionRules(ArrowPositionRules.ALIGN_ANCHOR)//设置箭头位置的规则,anchor是锚点View
            .setArrowPosition(0.5f) //设置箭头所在的比例
            .setIsVisibleOverlay(true)
            .setOverlayColorResource(R.color.overlay)
            .setOverlayPadding(10f)
            .setDismissWhenShowAgain(true)
            .setDismissWhenTouchOutside(false)
            .setDismissWhenOverlayClicked(true)
            .setLifecycleOwner(lifecycleOwner) //绑定生命周期组件
            .build()
    }
}
