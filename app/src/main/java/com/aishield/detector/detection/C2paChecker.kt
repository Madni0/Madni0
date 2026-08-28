package com.aishield.detector.detection

/**
 * Content Credentials (C2PA) status.
 *
 * IMPORTANT LIMITATION (documented for the client): C2PA manifests live in
 * the ORIGINAL media file's metadata. Screen capture re-renders pixels and
 * strips all file metadata, so credentials can never be verified from the
 * overlay pipeline. A future "share-to-verify" flow can check the original
 * file; until then the app honestly reports "Not detected".
 */
enum class C2paStatus {
    NOT_DETECTED,
    PRESENT_VALID,
    PRESENT_INVALID,
    UNKNOWN;

    fun display(): String = when (this) {
        NOT_DETECTED -> "Not detected"
        PRESENT_VALID -> "Present (valid)"
        PRESENT_INVALID -> "Present (invalid)"
        UNKNOWN -> "Unknown"
    }
}

object C2paChecker {
    const val NOTE =
        "Screen capture cannot retain embedded Content Credentials (C2PA). " +
            "Verify the original file via the upcoming share-to-verify flow."

    fun checkFromScreenCapture(): C2paStatus = C2paStatus.NOT_DETECTED
}
