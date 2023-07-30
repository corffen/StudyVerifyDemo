package com.gordon.studyverifydemo

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import com.gordon.common_module.balloon.BalloonAlign
import com.gordon.studyverifydemo.databinding.ActivityGuideBinding

class GuideActivity : AppCompatActivity() {
    private lateinit var binding: ActivityGuideBinding

    private val wordBalloon by lazy { BalloonUtils.getWordBalloon(this, this) }
    private val first = true
    private var step = 1
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGuideBinding.inflate(layoutInflater)
        setContentView(binding.root)
        wordBalloon.setOnBalloonDismissListener {
            binding.tvProgress.isInvisible = true
        }
        wordBalloon.setOnBalloonOverlayClickListener {
            step++
            binding.tvProgress.postDelayed({
                showBalloon(step)
            }, 16)
        }

        if (first) {
            binding.tvMosheng.post {
                showBalloon(step)
            }
        }
    }

    private fun showBalloon(step: Int) {
        binding.tvProgress.isVisible = true
        binding.tvProgress.text = "知道了$step/5"
        val content = wordBalloon.getContentView().findViewById<TextView>(R.id.tv_content)
        when (step) {
            1 -> {
                content.text = "陌生\n如果你不认识这个单词,就选择它"
                wordBalloon.showAlign(
                    BalloonAlign.TOP,
                    binding.tvMosheng,
                    subAnchorList = listOf(binding.tvProgress)
                )
            }

            2 -> {
                content.text = "模糊\n如果你不认识这个单词,就选择它"
                wordBalloon.showAlign(
                    BalloonAlign.TOP,
                    binding.tvMohu,
                    subAnchorList = listOf(binding.tvProgress)
                )
            }

            3 -> {
                content.text = "熟悉\n如果你不认识这个单词,就选择它"
                wordBalloon.showAlign(
                    BalloonAlign.TOP,
                    binding.tvShuxi,
                    subAnchorList = listOf(binding.tvProgress)
                )
            }

            else -> {
                wordBalloon.dismiss()
                binding.tvProgress.isInvisible = true
            }
        }

    }
}