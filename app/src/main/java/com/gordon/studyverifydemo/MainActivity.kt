package com.gordon.studyverifydemo

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding =
            com.gordon.studyverifydemo.databinding.ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }
}