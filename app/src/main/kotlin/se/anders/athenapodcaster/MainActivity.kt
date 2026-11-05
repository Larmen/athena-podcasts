package se.anders.athenapodcaster

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import se.anders.athenapodcaster.ui.components.LocalArtStyle
import se.anders.athenapodcaster.ui.components.LocalArtworkStore
import se.anders.athenapodcaster.ui.licences.LicencesScreen
import se.anders.athenapodcaster.playback.ACTION_OPEN_PLAYER
import se.anders.athenapodcaster.ui.FeedsViewModel
import se.anders.athenapodcaster.ui.QueueViewModel
import se.anders.athenapodcaster.ui.queue.QueueScreen
import se.anders.athenapodcaster.ui.MainViewModel
import se.anders.athenapodcaster.ui.PlayerViewModel
import se.anders.athenapodcaster.ui.player.NowPlayingBar
import se.anders.athenapodcaster.ui.player.PlayerScreen
import se.anders.athenapodcaster.ui.episodes.EpisodeListScreen
import se.anders.athenapodcaster.ui.feeds.AddFeedScreen
import se.anders.athenapodcaster.ui.feeds.FeedListScreen
import se.anders.athenapodcaster.ui.Screen
import se.anders.athenapodcaster.ui.settings.SettingsCallbacks
import se.anders.athenapodcaster.ui.settings.SettingsScreen
import se.anders.athenapodcaster.ui.theme.AthenaTheme
import se.anders.athenapodcaster.ui.theme.useDarkTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels { MainViewModel.Factory }
    private val feedsViewModel: FeedsViewModel by viewModels { FeedsViewModel.Factory }
    private val queueViewModel: QueueViewModel by viewModels { QueueViewModel.Factory }
    private val playerViewModel: PlayerViewModel by viewModels { PlayerViewModel.Factory }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openPlayerIfAsked(intent)
    }

    /** The notification and lock-screen widget send ACTION_OPEN_PLAYER (FR-021). */
    private fun openPlayerIfAsked(intent: Intent?) {
        if (intent?.action == ACTION_OPEN_PLAYER) viewModel.open(Screen.Player)
    }

    override fun onStart() {
        super.onStart()
        (application as AthenaPodcasterApplication).container.player.connect()
        viewModel.refreshLauncher() // the launcher may have been installed or updated since last time
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) openPlayerIfAsked(intent)
        // FR-030: refresh when the app is opened (once per launch, not on every rotation).
        if (savedInstanceState == null) feedsViewModel.refreshAll()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )

        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val appearance by viewModel.appearance.collectAsStateWithLifecycle()
            val launcherAvailable by viewModel.launcherAvailable.collectAsStateWithLifecycle()
            val screen by viewModel.screen.collectAsStateWithLifecycle()
            val backupMessage by viewModel.backupMessage.collectAsStateWithLifecycle()
            val pendingImport by viewModel.pendingImport.collectAsStateWithLifecycle()

            // The bar icons follow the theme the user chose, which may differ from the system's (FR-060).
            val dark = useDarkTheme(appearance.themeMode)
            DisposableEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }

            val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
                if (uri != null) viewModel.exportTo(contentResolver, uri)
            }
            val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if (uri != null) viewModel.readImport(contentResolver, uri)
            }

            val feeds by feedsViewModel.feeds.collectAsStateWithLifecycle()
            val queueRows by queueViewModel.rows.collectAsStateWithLifecycle()
            val queuedIds by queueViewModel.ids.collectAsStateWithLifecycle()
            val selectedFeed by feedsViewModel.selectedFeed.collectAsStateWithLifecycle()
            val episodes by feedsViewModel.selectedEpisodes.collectAsStateWithLifecycle()
            val refreshing by feedsViewModel.refreshing.collectAsStateWithLifecycle()
            val addState by feedsViewModel.addState.collectAsStateWithLifecycle()
            val opmlOutcome by feedsViewModel.opmlOutcome.collectAsStateWithLifecycle()
            val opmlProgress by feedsViewModel.opmlProgress.collectAsStateWithLifecycle()
            var includePrivate by rememberSaveable { mutableStateOf(false) }
            val episodeFilter by feedsViewModel.filter.collectAsStateWithLifecycle()
            val markAllInfo by feedsViewModel.markAllInfo.collectAsStateWithLifecycle()
            val askMobile by feedsViewModel.askMobile.collectAsStateWithLifecycle()
            val revealedAddress by feedsViewModel.revealedAddress.collectAsStateWithLifecycle()
            val playerState by playerViewModel.state.collectAsStateWithLifecycle()
            val playerEpisode by playerViewModel.current.collectAsStateWithLifecycle()
            val playerArt by playerViewModel.artUrls.collectAsStateWithLifecycle()

            // Notification permission is asked for just in time, the first time something is played (NFR-003).
            // Playback starts regardless; without the permission only the notification is hidden.
            var askedNotifications by rememberSaveable { mutableStateOf(false) }
            val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
            fun requestNotificationsOnce() {
                if (askedNotifications || Build.VERSION.SDK_INT < 33) return
                askedNotifications = true
                if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            val launcherImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if (uri != null) viewModel.importLauncherFile(contentResolver, uri)
            }
            val opmlImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if (uri != null) feedsViewModel.importOpml(contentResolver, uri)
            }
            val opmlExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/x-opml")) { uri ->
                if (uri != null) feedsViewModel.exportOpml(contentResolver, uri, includePrivate)
            }

            val container = (application as AthenaPodcasterApplication).container
            AthenaTheme(appearance.themeMode, appearance.typeface, appearance.textScalePercent) {
              CompositionLocalProvider(LocalArtworkStore provides container.artwork, LocalArtStyle provides settings.artStyle) {
                // The now-playing strip sits under the podcast and episode screens while an episode is loaded.
                val showBar = playerState.episodeId != null && !playerState.ended && (screen is Screen.Feeds || screen is Screen.Episodes || (screen is Screen.Queue && (screen as Screen.Queue).back != Screen.Player))
                Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Box(Modifier.weight(1f)) {
                when (val current = screen) {
                    Screen.Feeds -> FeedListScreen(
                        feeds = feeds,
                        refreshing = refreshing,
                        onRefresh = feedsViewModel::refreshAll,
                        onAdd = { feedsViewModel.resetAdd(); viewModel.open(Screen.AddFeed) },
                        onOpen = { id -> feedsViewModel.select(id); viewModel.open(Screen.Episodes(id)) },
                        queueCount = queueRows.size,
                        onQueue = { viewModel.open(Screen.Queue(Screen.Feeds)) },
                        onSettings = { viewModel.open(Screen.Settings) },
                    )
                    is Screen.Queue -> {
                        BackHandler { viewModel.open(current.back) }
                        QueueScreen(
                            rows = queueRows,
                            onBack = { viewModel.open(current.back) },
                            onPlayNow = { row ->
                                requestNotificationsOnce()
                                playerViewModel.play(row.episode, row.feedTitle, row.feedImageUrl) { viewModel.open(Screen.Player) }
                            },
                            onRemove = { queueViewModel.remove(it) },
                            onMoveUp = { queueViewModel.moveUp(it) },
                            onMoveDown = { queueViewModel.moveDown(it) },
                            onClear = { queueViewModel.clear() },
                        )
                    }
                    Screen.AddFeed -> {
                        BackHandler { viewModel.open(Screen.Feeds) }
                        AddFeedScreen(
                            state = addState,
                            onAdd = { url, isPrivate ->
                                requestNotificationsOnce()
                                feedsViewModel.add(url, isPrivate) { id -> feedsViewModel.select(id); viewModel.open(Screen.Episodes(id)) }
                            },
                            onBack = { viewModel.open(Screen.Feeds) },
                        )
                    }
                    is Screen.Episodes -> {
                        BackHandler { viewModel.open(Screen.Feeds) }
                        EpisodeListScreen(
                            feed = selectedFeed,
                            episodes = episodes,
                            filter = episodeFilter,
                            markAllInfo = markAllInfo,
                            refreshing = refreshing,
                            onNextFilter = feedsViewModel::nextFilter,
                            onRefresh = { feedsViewModel.refresh(current.feedId) },
                            onPlay = { episode ->
                                requestNotificationsOnce()
                                playerViewModel.play(episode, selectedFeed?.title.orEmpty(), selectedFeed?.imageUrl) { viewModel.open(Screen.Player) }
                            },
                            queuedIds = queuedIds,
                            onToggleQueue = { queueViewModel.toggle(it.id) },
                            onMarkUnplayed = feedsViewModel::setUnplayed,
                            onMarkPlayed = feedsViewModel::markPlayed,
                            onMarkAllPlayed = { feedsViewModel.markAllPlayed(current.feedId) },
                            onDownload = { episode -> requestNotificationsOnce(); feedsViewModel.download(episode) },
                            onCancelDownload = feedsViewModel::cancelDownload,
                            onDeleteDownload = feedsViewModel::deleteDownload,
                            askMobile = askMobile,
                            onConfirmMobile = feedsViewModel::confirmMobile,
                            onDismissMobile = feedsViewModel::dismissMobile,
                            revealedAddress = revealedAddress,
                            onToggleAddress = { selectedFeed?.let(feedsViewModel::toggleAddress) },
                            onUnsubscribe = { feedsViewModel.remove(current.feedId); viewModel.open(Screen.Feeds) },
                            onBack = { viewModel.open(Screen.Feeds) },
                        )
                    }
                    Screen.Player -> {
                        val backTarget = selectedFeed?.let { Screen.Episodes(it.id) } ?: Screen.Feeds
                        BackHandler { viewModel.open(backTarget) }
                        PlayerScreen(
                            state = playerState,
                            episode = playerEpisode,
                            artUrls = playerArt,
                            queueCount = queueRows.size,
                            onQueue = { viewModel.open(Screen.Queue(Screen.Player)) },
                            onBack = { viewModel.open(backTarget) },
                            onPlayPause = playerViewModel::togglePlayPause,
                            onSeekBack = playerViewModel::seekBack,
                            onSeekForward = playerViewModel::seekForward,
                            onMarkUnplayed = { playerEpisode?.let { playerViewModel.setUnplayed(it.id) } },
                            // The file is deleted, so playback stops and we leave the player.
                            onMarkPlayed = { playerEpisode?.let { playerViewModel.markPlayed(it.id); viewModel.open(backTarget) } },
                        )
                    }
                    Screen.Licences -> {
                        BackHandler { viewModel.open(Screen.Settings) }
                        LicencesScreen(onBack = { viewModel.open(Screen.Settings) })
                    }
                    Screen.Settings -> {
                        BackHandler { viewModel.open(Screen.Feeds) }
                        SettingsScreen(
                            settings = settings,
                            appearance = appearance,
                            launcherAvailable = launcherAvailable,
                            backupMessage = backupMessage,
                            importPending = pendingImport != null,
                            opmlOutcome = opmlOutcome,
                            opmlProgress = opmlProgress,
                            includePrivate = includePrivate,
                            callbacks = SettingsCallbacks(
                                onBack = { viewModel.open(Screen.Feeds) },
                                onThemeChange = viewModel::setThemeMode,
                                onTypefaceChange = viewModel::setTypeface,
                                onTextScaleChange = viewModel::setTextScale,
                                onExport = { exportLauncher.launch("athena-podcaster-settings.json") },
                                onImport = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                                onConfirmImport = viewModel::confirmImport,
                                onCancelImport = viewModel::cancelImport,
                                onImportOpml = { opmlImportLauncher.launch(arrayOf("*/*")) },
                                onExportOpml = { opmlExportLauncher.launch("athena-podcaster-subscriptions.opml") },
                                onIncludePrivateChange = { includePrivate = it },
                                onOpenLicences = { viewModel.open(Screen.Licences) },
                                onFollowLauncherChange = viewModel::setFollowLauncher,
                                onImportLauncherFile = { launcherImportLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                                onArtStyleChange = viewModel::setArtStyle,
                                onAutoDownloadChange = viewModel::setAutoDownload,
                                onRefreshHoursChange = viewModel::setRefreshHours,
                                onWifiOnlyChange = viewModel::setWifiOnly,
                                onChargingOnlyChange = viewModel::setChargingOnly,
                                onDeleteAfterPlayedChange = viewModel::setDeleteAfterPlayed,
                            ),
                        )
                    }
                }
                }
                if (showBar) {
                    NowPlayingBar(
                        state = playerState,
                        onOpen = { viewModel.open(Screen.Player) },
                        onPlayPause = playerViewModel::togglePlayPause,
                        onSeekBack = playerViewModel::seekBack,
                        onSeekForward = playerViewModel::seekForward,
                    )
                }
                }
              }
            }
        }
    }
}
