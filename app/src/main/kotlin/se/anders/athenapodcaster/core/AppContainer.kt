package se.anders.athenapodcaster.core

import android.content.Context
import se.anders.athenapodcaster.data.art.ArtworkStore
import se.anders.athenapodcaster.data.launcher.LauncherAppearanceSource
import se.anders.athenapodcaster.data.db.AppDatabase
import se.anders.athenapodcaster.data.download.Downloads
import se.anders.athenapodcaster.data.download.EpisodeDownloader
import se.anders.athenapodcaster.data.download.Notifier
import se.anders.athenapodcaster.data.download.NetworkPolicy
import se.anders.athenapodcaster.data.refresh.Sync
import se.anders.athenapodcaster.data.secrets.KeystoreSecretStore
import se.anders.athenapodcaster.data.secrets.SecretStore
import se.anders.athenapodcaster.data.episodes.EpisodeRepository
import se.anders.athenapodcaster.data.episodes.QueueRepository
import se.anders.athenapodcaster.data.feeds.FeedFetcher
import se.anders.athenapodcaster.data.feeds.FeedRepository
import se.anders.athenapodcaster.data.settings.SettingsRepository
import se.anders.athenapodcaster.playback.PlayerController

/** Manual dependency container (plan §1). Repositories are added as their tasks land. */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext

    val settings: SettingsRepository = SettingsRepository(appContext)
    val secrets: SecretStore by lazy { KeystoreSecretStore(appContext) }
    val database: AppDatabase by lazy { AppDatabase.create(appContext, secrets) }
    val feeds: FeedRepository by lazy { FeedRepository(database, FeedFetcher(), secrets) }
    val episodes: EpisodeRepository by lazy { EpisodeRepository(database) }
    val queue: QueueRepository by lazy { QueueRepository(database) }
    val player: PlayerController by lazy { PlayerController(appContext) }
    val notifier: Notifier by lazy { Notifier(appContext) }
    val launcherAppearance: LauncherAppearanceSource by lazy { LauncherAppearanceSource(appContext) }
    val artwork: ArtworkStore by lazy { ArtworkStore(appContext) }
    val downloader: EpisodeDownloader by lazy { EpisodeDownloader() }
    val networkPolicy: NetworkPolicy by lazy { NetworkPolicy(appContext) }
    val downloads: Downloads by lazy { Downloads(appContext, database, episodes, settings) }
    val sync: Sync by lazy { Sync(feeds, downloads, notifier, artwork) { player.state.value.episodeId } }
}
