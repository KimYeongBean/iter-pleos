package io.github.kimyeongbean.iter.app

import android.content.Context

/**
 * The user's answer to the collection notice (GLOSSARY: 수집 동의, #127). Nothing is recorded
 * before [agreed]. Location is a separate, optional part: recorded only when [location] too.
 */
internal data class RecordingConsent(
    val answer: Answer = Answer.NOT_ASKED,
    val location: Boolean = false,
) {
    enum class Answer { NOT_ASKED, DECLINED, AGREED }

    val agreed get() = answer == Answer.AGREED

    /** True when recording may record location: the user agreed to both. */
    val recordsLocation get() = agreed && location
}

/** Keeps the collection consent on the device, so it holds for the recording service after a boot too. */
internal class RecordingConsentStore(
    context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): RecordingConsent {
        val answer =
            prefs.getString(KEY_ANSWER, null)?.let { saved ->
                RecordingConsent.Answer.entries.firstOrNull { it.name == saved }
            } ?: RecordingConsent.Answer.NOT_ASKED
        return RecordingConsent(answer, location = answer == RecordingConsent.Answer.AGREED && prefs.getBoolean(KEY_LOCATION, false))
    }

    fun save(consent: RecordingConsent) {
        // commit, not apply: the recording service may read it from another start right after.
        prefs
            .edit()
            .putString(KEY_ANSWER, consent.answer.name)
            .putBoolean(KEY_LOCATION, consent.location)
            .putLong(KEY_ANSWERED_AT, System.currentTimeMillis())
            .putInt(KEY_NOTICE_VERSION, NOTICE_VERSION)
            .commit()
    }

    private companion object {
        const val PREFS = "recording_consent"
        const val KEY_ANSWER = "answer"
        const val KEY_LOCATION = "location"
        const val KEY_ANSWERED_AT = "answered_at"
        const val KEY_NOTICE_VERSION = "notice_version"

        /** Raise when the notice's content changes, so a new version can ask again. */
        const val NOTICE_VERSION = 1
    }
}
