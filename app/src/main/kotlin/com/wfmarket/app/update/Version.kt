package com.wfmarket.app.update

/**
 * A release's version, as semantic versioning orders them: `1.2.10` is after `1.2.9`, and a
 * pre-release such as `1.3.0-beta.2` comes before the `1.3.0` it leads up to. A tag's leading `v`
 * and any build metadata after a `+` are ignored.
 */
data class Version(val major: Int, val minor: Int, val patch: Int, val preRelease: List<String> = emptyList()) : Comparable<Version> {

    override fun compareTo(other: Version): Int {
        compareValuesBy(this, other, Version::major, Version::minor, Version::patch).let { if (it != 0) return it }
        // A version without a pre-release part is the release itself, after all of its pre-releases.
        if (preRelease.isEmpty() || other.preRelease.isEmpty()) return other.preRelease.size.coerceAtMost(1) - preRelease.size.coerceAtMost(1)
        for ((mine, theirs) in preRelease.zip(other.preRelease)) {
            val a = mine.toIntOrNull()
            val b = theirs.toIntOrNull()
            val order = when {
                a != null && b != null -> a.compareTo(b)
                // Numbers sort before words, so beta.2 is before beta.rc.
                a != null -> -1
                b != null -> 1
                else -> mine.compareTo(theirs)
            }
            if (order != 0) return order
        }
        return preRelease.size.compareTo(other.preRelease.size)
    }

    override fun toString() = "$major.$minor.$patch" + if (preRelease.isEmpty()) "" else "-" + preRelease.joinToString(".")

    companion object {
        private val Pattern = Regex("""^v?(\d+)\.(\d+)\.(\d+)(?:-([0-9A-Za-z.-]+))?(?:\+[0-9A-Za-z.-]+)?$""")

        /** The version in [text], such as a tag like `v1.2.0`, or null if it isn't one. */
        fun parse(text: String): Version? {
            val match = Pattern.matchEntire(text.trim()) ?: return null
            val (major, minor, patch, pre) = match.destructured
            return Version(major.toInt(), minor.toInt(), patch.toInt(), pre.split('.').filter { it.isNotEmpty() })
        }
    }
}
