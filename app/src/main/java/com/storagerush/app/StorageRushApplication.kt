package com.storagerush.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder

/**
 * Registers Coil's VideoFrameDecoder app-wide (Phase A). Without this, Coil
 * has no way to decode a preview frame from a video URI — AsyncImage
 * silently fails to load anything for videos, which is why video cards in
 * the deck showed as blank black cards with just the duration/play-icon
 * overlay on top, instead of an actual thumbnail frame.
 *
 * Registering it here (once, app-wide) means every AsyncImage in the app
 * that's given a video URI benefits automatically — no per-call
 * boilerplate needed at each call site.
 */
class StorageRushApplication : Application(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(VideoFrameDecoder.Factory())
            }
            .build()
    }
}