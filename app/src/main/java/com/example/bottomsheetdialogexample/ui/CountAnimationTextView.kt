package com.example.bottomsheetdialogexample.ui

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView


class CountAnimationTextView : AppCompatTextView {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )

    fun startCountAnimation() {
        val animator = ValueAnimator.ofInt(0, 10000)
        animator.duration = 30000
        animator.repeatCount = 5
        animator.repeatMode = ValueAnimator.REVERSE
        animator.addUpdateListener { animation -> text = animation.animatedValue.toString() }
        animator.start()
    }
}