package com.poxi.mobile

import android.os.Bundle
import android.widget.TextView
import androidx.activity.ComponentActivity

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val textView = TextView(this).apply {
            text = """
                POXI

                Project initialized successfully.

                Voice system: Preparing...
                Accessibility: Preparing...
                Phone control: Preparing...
            """.trimIndent()

            textSize = 20f
            setPadding(40, 60, 40, 40)
        }

        setContentView(textView)
    }
}
