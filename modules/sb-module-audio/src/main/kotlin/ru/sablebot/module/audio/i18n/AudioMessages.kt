package ru.sablebot.module.audio.i18n

object AudioMessages {
    // Playback
    const val NOW_PLAYING = "audio.play.now_playing"
    const val ADDED_TO_QUEUE = "audio.play.added_to_queue"
    const val PLAYLIST_ADDED = "audio.play.playlist_added"
    const val SEARCHING = "audio.play.searching"
    const val LOADING = "audio.play.loading"

    // Queue
    const val QUEUE_TITLE = "audio.queue.title"
    const val QUEUE_EMPTY = "audio.queue.empty"
    const val QUEUE_PAGE = "audio.queue.page"
    const val QUEUE_CLEARED = "audio.queue.cleared"
    const val QUEUE_SHUFFLED = "audio.queue.shuffled"

    // Track info
    const val TRACK_TITLE = "audio.track.title"
    const val TRACK_DURATION = "audio.track.duration"
    const val TRACK_REQUESTED_BY = "audio.track.requested_by"
    const val TRACK_LIVE = "audio.track.live"

    // Controls
    const val PAUSE_SUCCESS = "audio.pause.success"
    const val RESUME_SUCCESS = "audio.resume.success"
    const val SKIP_SUCCESS = "audio.skip.success"
    const val SKIP_TO_SUCCESS = "audio.skip.to_success"
    const val STOP_SUCCESS = "audio.stop.success"
    const val SEEK_SUCCESS = "audio.seek.success"
    const val REMOVE_SUCCESS = "audio.remove.success"
    const val MOVE_SUCCESS = "audio.move.success"
    const val CLEAR_SUCCESS = "audio.clear.success"

    // Volume
    const val VOLUME_SET = "audio.volume.set"
    const val VOLUME_CURRENT = "audio.volume.current"

    // Repeat
    const val REPEAT_SET = "audio.repeat.set"
    const val REPEAT_NONE = "audio.repeat.none"
    const val REPEAT_CURRENT = "audio.repeat.current"
    const val REPEAT_ALL = "audio.repeat.all"

    // Filter
    const val FILTER_APPLIED = "audio.filter.applied"
    const val FILTER_CLEARED = "audio.filter.cleared"
    const val FILTER_CURRENT = "audio.filter.current"

    // 24/7
    const val MODE_247_ENABLED = "audio.247.enabled"
    const val MODE_247_DISABLED = "audio.247.disabled"

    // Join/Disconnect
    const val JOIN_SUCCESS = "audio.join.success"
    const val DISCONNECT_SUCCESS = "audio.disconnect.success"

    // Playlist
    const val PLAYLIST_SAVED = "audio.playlist.saved"
    const val PLAYLIST_LOADED = "audio.playlist.loaded"
    const val PLAYLIST_DELETED = "audio.playlist.deleted"
    const val PLAYLIST_NOT_FOUND = "audio.playlist.not_found"
    const val PLAYLIST_LIST_TITLE = "audio.playlist.list_title"
    const val PLAYLIST_LIST_EMPTY = "audio.playlist.list_empty"
    const val PLAYLIST_SHOW_TITLE = "audio.playlist.show_title"

    // History
    const val HISTORY_TITLE = "audio.history.title"
    const val HISTORY_EMPTY = "audio.history.empty"

    // Lyrics
    const val LYRICS_TITLE = "audio.lyrics.title"
    const val LYRICS_NOT_FOUND = "audio.lyrics.not_found"
    const val LYRICS_COMING_SOON = "audio.lyrics.coming_soon"

    // Errors
    const val ERROR_NOT_IN_VOICE = "audio.error.not_in_voice"
    const val ERROR_NOT_SAME_CHANNEL = "audio.error.not_same_channel"
    const val ERROR_NO_ACTIVE_PLAYER = "audio.error.no_active_player"
    const val ERROR_NO_PERMISSION = "audio.error.no_permission"
    const val ERROR_NO_RESULTS = "audio.error.no_results"
    const val ERROR_LOAD_FAILED = "audio.error.load_failed"
    const val ERROR_INVALID_POSITION = "audio.error.invalid_position"
    const val ERROR_INVALID_VOLUME = "audio.error.invalid_volume"
    const val ERROR_INVALID_SEEK = "audio.error.invalid_seek"
    const val ERROR_SEEK_NOT_SUPPORTED = "audio.error.seek_not_supported"
    const val ERROR_CANNOT_REMOVE_CURRENT = "audio.error.cannot_remove_current"
    const val ERROR_QUEUE_FULL = "audio.error.queue_full"
    const val ERROR_GENERIC = "audio.error.generic"

    // Now Playing Panel
    const val PANEL_TITLE = "audio.panel.title"
    const val PANEL_QUEUE_ENDED = "audio.panel.queue_ended"
    const val PANEL_FOOTER_VOLUME = "audio.panel.footer_volume"
    const val PANEL_FOOTER_REPEAT = "audio.panel.footer_repeat"
    const val PANEL_FOOTER_QUEUE = "audio.panel.footer_queue"
    const val PANEL_FOOTER_FILTER = "audio.panel.footer_filter"
    const val PANEL_UP_NEXT = "audio.panel.up_next"
}
