package com.wormhole.browser.core.downloads

enum class DownloadCategory(val label: String) {
    IMAGES("Images"),
    VIDEOS("Videos"),
    AUDIO("Audio"),
    DOCUMENTS("Documents"),
    ARCHIVES("Archives"),
    APPS("Apps"),
    OTHER("Other");

    companion object {
        private val documentExtensions = setOf(
            "pdf", "doc", "docx", "txt", "rtf", "odt", "xls", "xlsx", "ppt", "pptx", "csv",
        )
        private val archiveExtensions = setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz")
        private val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "svg", "heic", "heif")
        private val videoExtensions = setOf("mp4", "mkv", "webm", "mov", "avi", "3gp", "m4v")
        private val audioExtensions = setOf("mp3", "wav", "ogg", "m4a", "flac", "aac", "opus")
        private val appExtensions = setOf("apk", "aab")

        fun from(mimeType: String?, fileName: String): DownloadCategory {
            val mime = mimeType.orEmpty().lowercase()
            val extension = fileName.substringAfterLast('.', missingDelimiterValue = "").lowercase()

            return when {
                mime.startsWith("image/") || extension in imageExtensions -> IMAGES
                mime.startsWith("video/") || extension in videoExtensions -> VIDEOS
                mime.startsWith("audio/") || extension in audioExtensions -> AUDIO
                mime == "application/vnd.android.package-archive" || extension in appExtensions -> APPS
                mime.startsWith("application/pdf") || extension in documentExtensions -> DOCUMENTS
                mime.contains("zip") || mime.contains("compressed") || mime.contains("archive") ||
                    extension in archiveExtensions -> ARCHIVES
                else -> OTHER
            }
        }
    }
}
