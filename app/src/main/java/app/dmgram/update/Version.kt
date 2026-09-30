package app.dmgram.update

data class SemVer(
    val major: Int,
    val minor: Int,
    val patch: Int,
) : Comparable<SemVer> {
    override fun compareTo(other: SemVer): Int {
        if (major != other.major) return major.compareTo(other.major)
        if (minor != other.minor) return minor.compareTo(other.minor)
        return patch.compareTo(other.patch)
    }

    override fun toString(): String = "$major.$minor.$patch"

    companion object {
        private val pattern = Regex("""^v?(\d+)\.(\d+)\.(\d+)""")

        fun parse(raw: String): SemVer? {
            val match = pattern.find(raw.trim()) ?: return null
            return SemVer(
                match.groupValues[1].toInt(),
                match.groupValues[2].toInt(),
                match.groupValues[3].toInt(),
            )
        }
    }
}
