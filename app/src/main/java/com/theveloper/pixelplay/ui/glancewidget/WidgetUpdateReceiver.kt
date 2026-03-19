package com.theveloper.pixelplay.ui.glancewidget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidgetManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber

class WidgetUpdateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "com.theveloper.pixelplay.ACTION_WIDGET_UPDATE_PLAYBACK_STATE") return

        val pendingResult = goAsync()
        val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        scope.launch {
            try {
                val glanceAppWidgetManager = GlanceAppWidgetManager(context)

                val glanceIds = glanceAppWidgetManager.getGlanceIds(PixelPlayGlanceWidget::class.java)
                glanceIds.forEach { glanceId ->
                    PixelPlayGlanceWidget().update(context, glanceId)
                }

                val barGlanceIds = glanceAppWidgetManager.getGlanceIds(BarWidget4x1::class.java)
                barGlanceIds.forEach { glanceId ->
                    BarWidget4x1().update(context, glanceId)
                }

                val controlGlanceIds = glanceAppWidgetManager.getGlanceIds(ControlWidget4x2::class.java)
                controlGlanceIds.forEach { glanceId ->
                    ControlWidget4x2().update(context, glanceId)
                }

                val gridGlanceIds = glanceAppWidgetManager.getGlanceIds(GridWidget2x2::class.java)
                gridGlanceIds.forEach { glanceId ->
                    GridWidget2x2().update(context, glanceId)
                }
            } catch (e: Exception) {
                Timber.tag("WidgetUpdateReceiver").e(e, "Error updating widgets")
            } finally {
                pendingResult.finish()
                scope.cancel()
            }
        }
    }
}
