package com.example.bottomsheetdialogexample

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import com.example.bottomsheetdialogexample.ui.CountAnimationTextView

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //숫자를 카운트하는 뷰를 표시합니다.
        val countTextView: CountAnimationTextView = findViewById(R.id.text_count_animation)
        countTextView.startCountAnimation()

        //하단 다이얼로그 뷰를 표시합니다.
        findViewById<Button>(R.id.button).setOnClickListener {
            val selectDialog: CarrierSelectDialog = CarrierSelectDialog()
            selectDialog.show(supportFragmentManager, selectDialog.tag)
        }

    }
}