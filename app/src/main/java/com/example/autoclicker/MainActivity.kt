package com.example.autoclicker

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }

        val title = TextView(this).apply {
            text = getString(R.string.app_name)
            textSize = 22f
        }

        val info = TextView(this).apply {
            text = getString(R.string.usage_instructions)
            textSize = 15f
            setPadding(0, dp(16), 0, dp(24))
        }

        val enableButton = Button(this).apply {
            text = getString(R.string.open_accessibility_settings)
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }

        val hint = TextView(this).apply {
            text = getString(R.string.enable_hint)
            textSize = 13f
            gravity = Gravity.START
            setPadding(0, dp(16), 0, 0)
        }

        root.addView(title)
        root.addView(info)
        root.addView(enableButton)
        root.addView(hint)

        setContentView(ScrollView(this).apply { addView(root) })
    }
}
