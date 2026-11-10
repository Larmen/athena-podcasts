package se.anders.athenapodcaster.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import se.anders.athenapodcaster.AthenaPodcasterApplication
import se.anders.athenapodcaster.MainActivity
import se.anders.athenapodcaster.data.settings.ThemeMode
import se.anders.athenapodcaster.domain.EffectiveAppearance
import se.anders.athenapodcaster.domain.effectiveAppearance
import se.anders.athenapodcaster.R
import se.anders.athenapodcaster.playback.ACTION_OPEN_PLAYER
import se.anders.athenapodcaster.playback.PlaybackService

/** What the widget shows (FR-120). Pushed by [se.anders.athenapodcaster.playback.PlaybackService]; idle until then. */
data class WidgetState(val title: String? = null, val playing: Boolean = false) {
    val loaded: Boolean get() = !title.isNullOrBlank()
}

/** The 4 × 1 home-screen widget: the episode now playing and one large Play/Pause button (FR-120). */
class PlayerWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) = redraw(context)

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, appWidgetId: Int, newOptions: android.os.Bundle) = redraw(context)

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
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        @Volatile var state = WidgetState()

        /** The player changed what is playing. */
        fun refresh(context: Context, new: WidgetState) {
            if (new == state) return
            state = new
            redraw(context)
        }

        /** Draws every placed widget from [state] and the app's current appearance (theme, typeface, size). */
        fun redraw(context: Context) {
            val app = context.applicationContext
            val manager = AppWidgetManager.getInstance(app)
            val ids = manager.getAppWidgetIds(ComponentName(app, PlayerWidget::class.java))
            if (ids.isEmpty()) return
            scope.launch {
                val container = (app as AthenaPodcasterApplication).container
                val appearance = effectiveAppearance(container.settings.settings.first(), container.launcherAppearance.appearance.value)
                val s = state
                ids.forEach { id ->
                    val o = manager.getAppWidgetOptions(id)
                    val wDp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 280).coerceAtLeast(120)
                    val hDp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 64).coerceAtLeast(40)
                    manager.updateAppWidget(id, views(app, s, appearance, wDp, hDp))
                }
            }
        }

        private fun views(context: Context, s: WidgetState, appearance: EffectiveAppearance, wDp: Int, hDp: Int): RemoteViews {
            val dark = when (appearance.themeMode) {
                ThemeMode.Dark -> true
                ThemeMode.Light -> false
                ThemeMode.System -> (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            }
            val bg = if (dark) Color.BLACK else Color.WHITE
            val fg = if (dark) Color.WHITE else Color.BLACK
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
            val text = if (s.loaded) s.title.orEmpty() else context.getString(R.string.widget_nothing)
            v.setImageViewBitmap(R.id.widget_title, WidgetPicture.draw(context, text, appearance, wDp, hDp, bg, fg))
            v.setImageViewResource(R.id.widget_play, if (s.playing) R.drawable.ic_pause else R.drawable.ic_play)
            v.setColorStateList(R.id.widget_play, "setImageTintList", ColorStateList.valueOf(fg))
            v.setContentDescription(R.id.widget_play, context.getString(if (s.playing) R.string.widget_pause else R.string.widget_play))
            v.setOnClickPendingIntent(R.id.widget_title, open)
            // Nothing loaded: Play cannot start anything from here, so the button opens the app instead.
            v.setOnClickPendingIntent(R.id.widget_play, if (s.loaded) toggle else open)
            return v
        }
    }
}
