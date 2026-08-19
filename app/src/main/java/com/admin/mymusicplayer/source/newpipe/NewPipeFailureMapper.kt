package com.admin.mymusicplayer.source.newpipe

import com.admin.mymusicplayer.source.SourceFailure
import com.admin.mymusicplayer.source.SourceFailureKind
import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.GeographicRestrictionException
import org.schabi.newpipe.extractor.exceptions.PaidContentException
import org.schabi.newpipe.extractor.exceptions.PrivateContentException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.exceptions.SignInConfirmNotBotException
import java.io.IOException

internal fun Throwable.asSourceFailure(action: String): SourceFailure = when (this) {
    is SourceFailure -> this
    is ReCaptchaException, is SignInConfirmNotBotException -> SourceFailure(
        SourceFailureKind.ANTI_BOT_CHALLENGE,
        "YouTube requested an anti-bot or sign-in challenge. This app will not bypass it.",
        this,
    )
    is AgeRestrictedContentException,
    is GeographicRestrictionException,
    is PaidContentException,
    is PrivateContentException,
    -> SourceFailure(
        SourceFailureKind.ACCESS_RESTRICTED,
        "This item has an age, region, payment, sign-in, or privacy restriction. It cannot be accessed here.",
        this,
    )
    is ContentNotAvailableException -> SourceFailure(
        SourceFailureKind.UNAVAILABLE,
        "This YouTube item is unavailable.",
        this,
    )
    is IOException -> SourceFailure(
        SourceFailureKind.NETWORK,
        "$action failed because the network did not respond.",
        this,
    )
    else -> SourceFailure(
        SourceFailureKind.EXTRACTOR_COMPATIBILITY,
        "$action failed. YouTube may have changed; export diagnostics before attempting an extractor update.",
        this,
    )
}
