package com.admin.mymusicplayer.source

enum class SourceFailureKind {
    NETWORK,
    UNAVAILABLE,
    ACCESS_RESTRICTED,
    ANTI_BOT_CHALLENGE,
    EXTRACTOR_COMPATIBILITY,
}

class SourceFailure(
    val kind: SourceFailureKind,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
