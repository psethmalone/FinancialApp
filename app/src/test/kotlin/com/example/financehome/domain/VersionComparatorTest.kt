package com.example.financehome.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionComparatorTest {

    @Test
    fun `remote major version greater is newer`() {
        assertTrue(VersionComparator.isRemoteNewer("2.0", "1.0"))
        assertTrue(VersionComparator.isRemoteNewer("v2.0.0", "1.9.9"))
    }

    @Test
    fun `remote minor version greater is newer`() {
        assertTrue(VersionComparator.isRemoteNewer("1.1", "1.0"))
        assertTrue(VersionComparator.isRemoteNewer("v1.2", "1.1.5"))
    }

    @Test
    fun `remote patch version greater is newer`() {
        assertTrue(VersionComparator.isRemoteNewer("1.0.1", "1.0.0"))
        assertTrue(VersionComparator.isRemoteNewer("v1.0.5", "1.0.4"))
    }

    @Test
    fun `equal versions are not newer`() {
        assertFalse(VersionComparator.isRemoteNewer("1.0", "1.0"))
        assertFalse(VersionComparator.isRemoteNewer("v1.0", "1.0"))
        assertFalse(VersionComparator.isRemoteNewer("1.0.0", "1.0"))
        assertFalse(VersionComparator.isRemoteNewer("v1.0.0", "1.0.0"))
    }

    @Test
    fun `older versions are not newer`() {
        assertFalse(VersionComparator.isRemoteNewer("0.9", "1.0"))
        assertFalse(VersionComparator.isRemoteNewer("1.0.1", "1.1.0"))
        assertFalse(VersionComparator.isRemoteNewer("1.0", "2.0"))
    }

    @Test
    fun `versions with suffixes are parsed cleanly`() {
        assertTrue(VersionComparator.isRemoteNewer("1.1-beta1", "1.0"))
        assertFalse(VersionComparator.isRemoteNewer("1.0-rc2", "1.0"))
    }
}
