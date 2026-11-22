package com.example.financehome.domain

object VersionComparator {

    /**
     * Compares remote version string against current version string.
     * Returns true if remote is strictly newer than current.
     */
    fun isRemoteNewer(remote: String, current: String): Boolean {
        val cleanRemote = cleanVersion(remote)
        val cleanCurrent = cleanVersion(current)

        if (cleanRemote.isEmpty() || cleanCurrent.isEmpty()) return false

        val remoteParts = cleanRemote.map { it.toIntOrNull() ?: 0 }
        val currentParts = cleanCurrent.map { it.toIntOrNull() ?: 0 }

        val maxLen = maxOf(remoteParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }

    private fun cleanVersion(version: String): List<String> {
        val trimmed = version.trim()
            .removePrefix("v")
            .removePrefix("V")
            .split("-")[0]
            .split("+")[0]
        return trimmed.split(".").filter { it.isNotEmpty() }
    }
}
