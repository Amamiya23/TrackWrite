package com.trackwrite.app

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(48, 48, 48, 48)
        }

        layout.addView(
            TextView(this).apply {
                text = "TrackWrite"
                textSize = 28f
                setTextColor(getColor(R.color.trackwrite_text))
            },
        )
        layout.addView(
            TextView(this).apply {
                text = "GPS track recording and photo geotagging core is ready for implementation."
                textSize = 16f
                setTextColor(getColor(R.color.trackwrite_text))
            },
        )

        setContentView(layout)
    }
}
