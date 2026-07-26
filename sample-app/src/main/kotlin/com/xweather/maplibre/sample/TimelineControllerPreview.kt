package com.xweather.maplibre.sample

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView

@Composable
@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
fun TimelineControllerPreview() {
    AndroidView(
        factory = { context ->
            TimelineController(context).apply {
                startHour = 8
                endHour = 11
                progress = 0.39f
                nowProgress = 0.39f
            }
        },
    )
}
