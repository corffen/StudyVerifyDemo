package com.gordon.studyverifydemo

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.blankj.utilcode.util.ToastUtils
import com.gordon.studyverifydemo.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        with(binding) {
            progressView.setOnProgressChangeListener {
                progressView.labelText = "heart ${it.toInt()}%"
            }
            progressView.setOnProgressClickListener {
                ToastUtils.showShort("$it")
            }
        }
    }
}