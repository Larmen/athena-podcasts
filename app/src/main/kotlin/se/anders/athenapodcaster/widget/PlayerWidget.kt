package se.anders.athenapodcaster.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import se.anders.athenapodcaster.MainActivity
import se.anders.athenapodcaster.R
import se.anders.athenapodcaster.playback.ACTION_OPEN_PLAYER
import se.anders.athenapodcaster.playback.PlaybackService

/** What the widget shows (FR-120). Pushed by [se.anders.athenapodcaster.playback.PlaybackService]; idle until then. */
data class WidgetState(val title: String? = null, val playing: Boolean = false) {
    val loaded: Boolean get() = !title.isNullOrBlank()
}

/** The 4 × 1 home-screen widget: the episode now playing and one large Play/Pause button (FR-120). */
class PlayerWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { manager.updateAppWidget(it, views(context, state)) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TOGGLE) return super.onReceive(context, intent)
        val pending = goAsync()
        // A receiver's own context may not bind to services; the application context may.
        val app = context.applicationContext
        val future = MediaController.Builder(app, SessionToken(app, ComponentName(app, PlaybackService::class.java))).buildAsync()
        future.addListener({
            try {
                val c = future.get()
                if (c.playWhenReady) c.pause() else c.play()
                c.release()
            } catch (_: Exception) {
                // The service could not be reached: the widget stays as it is.
            } finally {
                pending.finish()
            }
        }, ContextCompat.getMainExecutor(context))
    }

    companion object {
        const val ACTION_TOGGLE = "se.anders.athenapodcaster.WIDGET_TOGGLE"

        @Volatile var state = WidgetState()

        /** Redraws every placed widget from [state]. Cheap; called on each player change. */
        fun refresh(context: Context, new: WidgetState) {
            if (new == state) return
            state = new
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, PlayerWidget::class.java))
            if (ids.isEmpty()) return
            val v = views(context, new)
            ids.forEach { manager.updateAppWidget(it, v) }
        }

        private fun views(context: Context, s: WidgetState): RemoteViews {
            val v = RemoteViews(context.packageName, R.layout.widget_player)
            val open = PendingIntent.getActivity(
                context, 1,
                Intent(context, MainActivity::class.java).setAction(ACTION_OPEN_PLAYER)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val toggle = PendingIntent.getBroadcast(
                context, 2,
                Intent(context, PlayerWidget::class.java).setAction(ACTION_TOGGLE),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            v.setTextViewText(R.id.widget_title, if (s.loaded) s.title else context.getString(R.string.widget_nothing))
            v.setImageViewResource(R.id.widget_play, if (s.playing) R.drawable.ic_pause else R.drawable.ic_play)
            v.setContentDescription(R.id.widget_play, context.getString(if (s.playing) R.string.widget_pause else R.string.widget_play))
            v.setOnClickPendingIntent(R.id.widget_title, open)
            // Nothing loaded: Play cannot start anything from here, so the button opens the app instead.
            v.setOnClickPendingIntent(R.id.widget_play, if (s.loaded) toggle else open)
            return v
        }
    }
}
