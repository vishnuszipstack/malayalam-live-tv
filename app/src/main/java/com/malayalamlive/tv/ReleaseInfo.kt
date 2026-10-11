package com.malayalamlive.tv

/** A published GitHub release of this app. */
data class ReleaseInfo(val versionCode: Int, val versionName: String, val apkUrl: String) {
    companion object {
        /**
         * Release tags are `v<major>.<minor>.<build>` where build is the CI run number, which is also
         * the APK's versionCode. Returns null for tags in any other shape.
         */
        fun versionCodeFromTag(tag: String): Int? =
            tag.removePrefix("v").split('.').takeIf { it.size >= 2 }?.last()?.toIntOrNull()
    }
}
