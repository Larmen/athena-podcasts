package se.anders.athenapodcaster.data.licences

/** One piece of software or one font shipped inside the app (FR-066). */
data class Licence(
    val name: String,
    val copyright: String,
    val licenceName: String,
    /** File under `assets/licenses/` holding the full licence text. */
    val assetFile: String,
)

/**
 * Everything the APK contains that has its own licence. Anything added later must be listed here
 * with its licence text in `assets/licenses/` before it ships (FR-066); a test checks the files exist.
 */
object Licences {
    private const val OFL = "SIL Open Font License 1.1"

    val all: List<Licence> = listOf(
        Licence("Athena Podcasts", "Copyright (c) 2026 Anders", "MIT License", "athena-podcasts-MIT.txt"),
        Licence("VT323", "Copyright 2011, The VT323 Project Authors", OFL, "vt323-OFL.txt"),
        Licence("IBM Plex Mono", "Copyright © 2017 IBM Corp.", OFL, "ibm-plex-mono-OFL.txt"),
        Licence("Space Mono", "Copyright 2016 The Space Mono Project Authors", OFL, "space-mono-OFL.txt"),
        Licence("JetBrains Mono", "Copyright 2020 The JetBrains Mono Project Authors", OFL, "jetbrains-mono-OFL.txt"),
        Licence("Inter", "Copyright 2020 The Inter Project Authors", OFL, "inter-OFL.txt"),
        Licence("Atkinson Hyperlegible", "Copyright 2020 Braille Institute of America, Inc.", OFL, "atkinson-hyperlegible-OFL.txt"),
        Licence(
            "Android Jetpack (Compose, Room, WorkManager, DataStore, Media3), Kotlin and kotlinx.serialization",
            "Copyright The Android Open Source Project; JetBrains s.r.o. and Kotlin contributors",
            "Apache License 2.0",
            "apache-2.0.txt",
        ),
        Licence(
            "OkHttp and Okio",
            "Copyright Square, Inc.",
            "Apache License 2.0",
            "apache-2.0.txt",
        ),
    )
}

/**
 * Licence files are hard-wrapped at about 70 columns, which breaks badly on a phone. Joins the lines of each
 * paragraph (blank lines separate paragraphs) so the screen can wrap the text itself.
 */
fun reflow(text: String): String =
    text.replace("\r\n", "\n").trim().split(Regex("\n\\s*\n")).joinToString("\n\n") { paragraph ->
        paragraph.lines().joinToString(" ") { it.trim() }
    }
