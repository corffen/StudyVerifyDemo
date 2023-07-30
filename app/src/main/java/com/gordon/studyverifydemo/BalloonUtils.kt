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
import androidx.appcompat.widget.DrawableUtils
import androidx.lifecycle.LifecycleOwner
import com.blankj.utilcode.util.ColorUtils
import com.blankj.utilcode.util.ImageUtils
import com.gordon.common_module.balloon.Balloon
import com.gordon.common_module.balloon.BalloonSizeSpec
import com.gordon.common_module.balloon.arrow.ArrowOrientation
import com.gordon.common_module.balloon.arrow.ArrowOrientationRules
import com.gordon.common_module.balloon.arrow.ArrowPositionRules

object BalloonUtils {

    fun getWordBalloon(context: Context, lifecycleOwner: LifecycleOwner): Balloon {
        return Balloon.Builder(context)
            .setLayout(R.layout.layout_mark_word) //设置布局
            .setBackgroundColor(ColorUtils.getColor(android.R.color.transparent))
            .setHeight(BalloonSizeSpec.WRAP) //设置高度为wrapContent
            .setArrowSize(BalloonSizeSpec.WRAP) //设置箭头的大小
            .setArrowBottomPadding(4)
            .setArrowDrawableResource(R.drawable.ic_triangle_bottom)
            .setArrowPositionRules(ArrowPositionRules.ALIGN_ANCHOR)//设置箭头位置的规则,anchor是锚点View
            .setArrowOrientationRules(ArrowOrientationRules.ALIGN_FIXED)
            .setArrowOrientation(ArrowOrientation.BOTTOM)
            .setArrowDrawableDirection(ArrowOrientation.BOTTOM)
            .setArrowPosition(0.5f) //设置箭头所在的比例
            .setArrowColor(ColorUtils.string2Int("#FFB433"))
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
