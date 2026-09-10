package com.wasimaster.wmkeyboard.core.endpoints

import java.net.URLEncoder

/**
 * A git host the keyboard can fetch files from, and how each one spells the
 * address of a raw file.
 *
 * Everything the keyboard downloads from a repository (word lists, n-gram packs,
 * emoji and CJK dictionaries, vocabulary packs, the add-on repository, Espanso
 * packages) is plain files in git. GitHub serves them from
 * raw.githubusercontent.com; every other forge serves the same bytes from its own
 * host with its own path shape. A mirror of the repository on any of them is a
 * drop-in replacement, as long as the app knows the shape. That is what lets the
 * F-Droid build point at a copy the user trusts instead of depending on GitHub.
 *
 * Every shape below was fetched from a live instance on 2026-09-10.
 */
enum class GitForge(
    /** Stable on disk. Never store [name]. */
    val id: String,
) {
    /** github.com, or a GitHub Enterprise host. */
    GITHUB("github"),

    /** Forgejo and Gitea, which share their URL scheme: Codeberg, gitea.com, any self-hosted one. */
    FORGEJO("forgejo"),

    /** gitlab.com or a self-managed GitLab. The owner may be a group path like `group/sub`. */
    GITLAB("gitlab"),

    /** git.sr.ht. Its blob addresses answer with the file itself, as `text/plain`. */
    SOURCEHUT("sourcehut"),

    /** bitbucket.org. */
    BITBUCKET("bitbucket"),

    /** Any HTTPS folder that holds the repository's files, with no forge around it. */
    FOLDER("folder"),
    ;

    /** The host a location on this forge starts with, or blank for [FOLDER]. */
    val defaultHost: String
        get() = when (this) {
            GITHUB -> "github.com"
            FORGEJO -> "codeberg.org"
            GITLAB -> "gitlab.com"
            SOURCEHUT -> "git.sr.ht"
            BITBUCKET -> "bitbucket.org"
            FOLDER -> ""
        }

    /**
     * The ref used when none is given. GitHub and GitLab resolve `HEAD` to the
     * default branch; the others need a branch named.
     */
    val defaultRef: String
        get() = when (this) {
            GITHUB, GITLAB -> "HEAD"
            FORGEJO, SOURCEHUT, BITBUCKET -> "main"
            FOLDER -> ""
        }

    companion object {
        fun of(id: String): GitForge? = entries.firstOrNull { it.id == id }
    }
}

/**
 * Where one repository lives: a forge, a host, an owner, a repository and a ref.
 *
 * For [GitForge.FOLDER] only [host] is used, and it holds the folder's whole
 * `https://` address.
 */
data class RepoLocation(
    val forge: GitForge,
    /** `codeberg.org`, or a full `https://` folder address for [GitForge.FOLDER]. A scheme is tolerated either way. */
    val host: String,
    val owner: String = "",
    val repo: String = "",
    /** Branch, tag or commit; blank means the forge's [GitForge.defaultRef]. */
    val ref: String = "",
) {

    private val bareHost: String
        get() = host.trim().removePrefix("https://").removePrefix("http://").trim('/')

    private val cleanOwner: String get() = owner.trim().trim('/')
    private val cleanRepo: String get() = repo.trim().trim('/').removeSuffix(".git")
    private val effectiveRef: String get() = ref.trim().ifEmpty { forge.defaultRef }

    /**
     * Whether every field this forge needs is filled in. An incomplete location
     * is never fetched from: the caller falls back to the default instead.
     */
    val isComplete: Boolean
        get() = when (forge) {
            GitForge.FOLDER -> host.trim().startsWith("https://", ignoreCase = true) && bareHost.isNotEmpty()
            else -> bareHost.isNotEmpty() && !bareHost.contains('/') &&
                cleanOwner.isNotEmpty() && cleanRepo.isNotEmpty()
        }

    /** The address of [path], a file inside the repository, on this forge. */
    fun rawUrl(path: String): String {
        val file = path.trim().trimStart('/')
        val ref = effectiveRef
        return when (forge) {
            GitForge.GITHUB ->
                if (bareHost.equals("github.com", ignoreCase = true) ||
                    bareHost.equals("www.github.com", ignoreCase = true)
                ) {
                    "https://raw.githubusercontent.com/$cleanOwner/$cleanRepo/$ref/$file"
                } else {
                    // GitHub Enterprise keeps raw files on the instance itself.
                    "https://$bareHost/$cleanOwner/$cleanRepo/raw/$ref/$file"
                }
            // Forgejo spells the ref's kind out. A full commit id is a commit;
            // everything else is read as a branch, which is what a mirror follows.
            GitForge.FORGEJO -> {
                val kind = if (COMMIT_ID.matches(ref)) "commit" else "branch"
                "https://$bareHost/$cleanOwner/$cleanRepo/raw/$kind/$ref/$file"
            }
            GitForge.GITLAB -> "https://$bareHost/$cleanOwner/$cleanRepo/-/raw/$ref/$file"
            GitForge.SOURCEHUT -> "https://$bareHost/~${cleanOwner.removePrefix("~")}/$cleanRepo/blob/$ref/$file"
            GitForge.BITBUCKET -> "https://$bareHost/$cleanOwner/$cleanRepo/raw/$ref/$file"
            GitForge.FOLDER -> "https://$bareHost/$file"
        }
    }

    /**
     * An API address that lists the entries of [directory], or null when this
     * forge has no listing that needs no account.
     *
     * The answer is a JSON array of objects carrying `name` and `type` on every
     * forge that has one; [isDirectoryType] reads the `type`.
     */
    fun directoryListingUrl(directory: String): String? {
        val dir = directory.trim().trim('/')
        val ref = effectiveRef
        val refQuery = if (ref == "HEAD") "" else "?ref=${enc(ref)}"
        return when (forge) {
            GitForge.GITHUB ->
                if (bareHost.equals("github.com", ignoreCase = true) ||
                    bareHost.equals("www.github.com", ignoreCase = true)
                ) {
                    "https://api.github.com/repos/$cleanOwner/$cleanRepo/contents/$dir$refQuery"
                } else {
                    "https://$bareHost/api/v3/repos/$cleanOwner/$cleanRepo/contents/$dir$refQuery"
                }
            GitForge.FORGEJO -> "https://$bareHost/api/v1/repos/$cleanOwner/$cleanRepo/contents/$dir?ref=${enc(ref)}"
            GitForge.GITLAB -> {
                val refPart = if (ref == "HEAD") "" else "&ref=${enc(ref)}"
                "https://$bareHost/api/v4/projects/${enc("$cleanOwner/$cleanRepo")}/repository/tree" +
                    "?path=${enc(dir)}&per_page=100$refPart"
            }
            GitForge.SOURCEHUT, GitForge.BITBUCKET, GitForge.FOLDER -> null
        }
    }

    companion object {
        private val COMMIT_ID = Regex("[0-9a-fA-F]{40}")

        private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

        /** A listing entry's `type` that names a directory: `dir` on GitHub and Forgejo, `tree` on GitLab. */
        fun isDirectoryType(type: String?): Boolean = type == "dir" || type == "tree"

        /**
         * Reads a repository page address the way people copy it out of a
         * browser, on any forge, and returns the location plus the path inside
         * the repository it pointed at (blank for the repository root).
         *
         * Recognised by path shape, not by host, so a self-hosted Forgejo or
         * GitLab reads the same as Codeberg or gitlab.com:
         *
         * | Shape | Forge |
         * |---|---|
         * | `/-/tree/REF/…`, `/-/blob/REF/…` | GitLab |
         * | `/src/branch/REF/…`, `/src/commit/REF/…`, `/src/tag/REF/…` | Forgejo |
         * | `/~OWNER/REPO/tree/REF/…` | SourceHut |
         * | `/src/REF/…` on bitbucket.org | Bitbucket |
         * | `/tree/REF/…`, `/blob/REF/…` | GitHub |
         * | bare `/OWNER/REPO` | by host; unknown hosts are not guessed |
         *
         * HTTPS only. Returns null for anything it cannot place.
         */
        fun fromPageUrl(pasted: String): Pair<RepoLocation, String>? {
            val trimmed = pasted.trim().trimEnd('/')
            val schemeEnd = trimmed.indexOf("://")
            if (schemeEnd > 0 && !trimmed.substring(0, schemeEnd).equals("https", ignoreCase = true)) return null
            val rest = if (schemeEnd > 0) trimmed.substring(schemeEnd + 3) else trimmed
            val host = rest.substringBefore('/').lowercase()
            if (host.isEmpty() || host.contains(':') || !host.contains('.')) return null
            val parts = rest.substringAfter('/', "").split('/').filter { it.isNotEmpty() }
            if (parts.size < 2) return null

            fun tail(from: Int) = parts.drop(from).joinToString("/")

            // GitLab: everything before "/-/" is the project path, groups included.
            val dash = parts.indexOf("-")
            if (dash >= 2 && parts.getOrNull(dash + 1) in setOf("tree", "blob", "raw") && dash + 2 < parts.size) {
                val loc = RepoLocation(
                    GitForge.GITLAB, host,
                    owner = parts.subList(0, dash - 1).joinToString("/"),
                    repo = parts[dash - 1], ref = parts[dash + 2],
                )
                return loc to tail(dash + 3)
            }
            if (parts[0].startsWith("~")) {
                val ref = if (parts.getOrNull(2) in setOf("tree", "blob")) parts.getOrNull(3).orEmpty() else ""
                val loc = RepoLocation(GitForge.SOURCEHUT, host, parts[0].removePrefix("~"), parts[1], ref)
                return loc to if (ref.isEmpty()) "" else tail(4)
            }
            val owner = parts[0]
            val repo = parts[1].removeSuffix(".git")
            val kind = parts.getOrNull(2)
            if (kind == "src" && parts.getOrNull(3) in setOf("branch", "commit", "tag") && parts.size >= 5) {
                return RepoLocation(GitForge.FORGEJO, host, owner, repo, parts[4]) to tail(5)
            }
            if (host == "bitbucket.org") {
                val ref = if (kind == "src") parts.getOrNull(3).orEmpty() else ""
                return RepoLocation(GitForge.BITBUCKET, host, owner, repo, ref) to if (ref.isEmpty()) "" else tail(4)
            }
            if ((kind == "tree" || kind == "blob") && parts.size >= 4) {
                val forge = KNOWN_HOSTS[host] ?: GitForge.GITHUB
                return RepoLocation(forge, host, owner, repo, parts[3]) to tail(4)
            }
            if (parts.size == 2) {
                val forge = KNOWN_HOSTS[host] ?: return null
                return RepoLocation(forge, host, owner, repo) to ""
            }
            return null
        }

        /** Hosts whose forge is known without a path shape to go on. */
        private val KNOWN_HOSTS = mapOf(
            "github.com" to GitForge.GITHUB,
            "www.github.com" to GitForge.GITHUB,
            "codeberg.org" to GitForge.FORGEJO,
            "gitea.com" to GitForge.FORGEJO,
            "gitlab.com" to GitForge.GITLAB,
            "git.sr.ht" to GitForge.SOURCEHUT,
            "bitbucket.org" to GitForge.BITBUCKET,
        )
    }
}
