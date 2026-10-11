package com.malayalamlive.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReleaseInfoTest {
    @Test
    fun readsBuildNumberFromTag() {
        assertEquals(7, ReleaseInfo.versionCodeFromTag("v1.2.7"))
        assertEquals(12, ReleaseInfo.versionCodeFromTag("1.1.12"))
    }

    @Test
    fun rejectsOtherTags() {
        assertNull(ReleaseInfo.versionCodeFromTag("latest"))
        assertNull(ReleaseInfo.versionCodeFromTag("v1.2.beta"))
    }
}
