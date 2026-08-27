package com.medbot.app.data.online

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.medbot.app.domain.clinical.WebQuerySanitizer
import com.medbot.app.domain.clinical.WebQueryDecision
import com.medbot.app.domain.model.OnlineEvidence
import com.medbot.app.domain.model.OnlineEvidenceBundle
import com.medbot.app.domain.model.OnlineEvidenceFailure
import com.medbot.app.domain.model.OnlineEvidenceResult
import com.medbot.app.domain.model.OnlineEvidenceSourceRole
import com.medbot.app.domain.repository.OnlineEvidenceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okio.BufferedSource
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * User-triggered online evidence retrieval with a fixed source allowlist.
 *
 * The gateway never receives conversation history, images, persona profiles, or
 * patient records. It only returns bounded public excerpts. PubMed is accessed
 * through NCBI E-utilities; WHO pages are fetched only for a small set of
 * known health-topic paths. The local LiteRT-LM model remains the sole answer
 * generator.
 */
class AndroidOnlineEvidenceRepository(context: Context) : OnlineEvidenceRepository {
    private val appContext = context.applicationContext
    private val connectivityManager = appContext.getSystemService(ConnectivityManager::class.java)
    private val client = OkHttpClient.Builder()
        .callTimeout(18, TimeUnit.SECONDS)
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .writeTimeout(6, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .build()
    private val responseCache = ConcurrentHashMap<String, CacheEntry>()
    private val robotsCache = ConcurrentHashMap<String, RobotsEntry>()

    override suspend fun search(query: String): OnlineEvidenceResult {
        val decision = WebQuerySanitizer.sanitize(query)
        val allowed = decision as? WebQueryDecision.Allowed
            ?: return OnlineEvidenceResult.Unavailable(
                OnlineEvidenceFailure.UNSAFE_QUERY,
                (decision as WebQueryDecision.Blocked).reason
            )
        val cacheKey = allowed.query.lowercase()
        responseCache[cacheKey]?.takeIf { it.expiresAt > System.currentTimeMillis() }?.let {
            return OnlineEvidenceResult.Success(it.bundle)
        }
        if (!hasValidatedInternet()) {
            return OnlineEvidenceResult.Unavailable(
                OnlineEvidenceFailure.OFFLINE,
                "The active network is unavailable or not validated"
            )
        }

        return withContext(Dispatchers.IO) {
            val sources = mutableListOf<OnlineEvidence>()
            var robotsDenied = false
            var sourceFailure = false

            knownWhoSources(allowed.query).forEach { spec ->
                when (val fetched = fetch(spec.url)) {
                    is FetchResult.Success -> parseWhoSource(spec, fetched)?.let(sources::add)
                    FetchResult.RobotsDenied -> robotsDenied = true
                    FetchResult.Failed -> sourceFailure = true
                }
            }

            when (val pubMed = fetchPubMed(allowed.query)) {
                is PubMedFetchResult.Success -> sources += pubMed.source
                PubMedFetchResult.RobotsDenied -> robotsDenied = true
                PubMedFetchResult.Failed -> sourceFailure = true
            }

            val selected = sources.distinctBy { it.url }.take(MAX_SOURCES)
            if (selected.isEmpty()) {
                val failure = when {
                    robotsDenied -> OnlineEvidenceFailure.ROBOTS_DISALLOWED
                    sourceFailure -> OnlineEvidenceFailure.SOURCE_UNAVAILABLE
                    else -> OnlineEvidenceFailure.NO_RESULTS
                }
                return@withContext OnlineEvidenceResult.Unavailable(
                    failure,
                    "No bounded source excerpt was available"
                )
            }
            val bundle = OnlineEvidenceBundle(allowed.query, selected)
            responseCache[cacheKey] = CacheEntry(
                bundle = bundle,
                expiresAt = System.currentTimeMillis() + CACHE_TTL_MS
            )
            trimCache()
            OnlineEvidenceResult.Success(bundle)
        }
    }

    private fun hasValidatedInternet(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun knownWhoSources(query: String): List<SourceSpec> {
        val normalized = query.lowercase()
        val paths = buildList {
            if (DIARRHEA_TERMS.any(normalized::contains)) {
                add("/health-topics/diarrhoea" to "WHO diarrhoea health topic")
                add("/tools/elena/interventions/zinc-diarrhoea" to "WHO zinc and diarrhoea intervention")
            }
            if (normalized.contains("pneumonia") || normalized.contains("pneumoni")) {
                add("/health-topics/pneumonia" to "WHO pneumonia health topic")
            }
            if (normalized.contains("diabetes") || normalized.contains("diabet")) {
                add("/health-topics/diabetes" to "WHO diabetes health topic")
            }
            if (normalized.contains("hipertensi") || normalized.contains("hypertension")) {
                add("/health-topics/hypertension" to "WHO hypertension health topic")
            }
            if (normalized.contains("asma") || normalized.contains("asthma")) {
                add("/health-topics/asthma" to "WHO asthma health topic")
            }
        }
        return paths.mapNotNull { (path, name) ->
            val url = "https://www.who.int$path".toHttpUrlOrNull() ?: return@mapNotNull null
            SourceSpec(url, name, OnlineEvidenceSourceRole.AUTHORITATIVE_GUIDANCE)
        }
    }

    private fun fetchPubMed(query: String): PubMedFetchResult {
        val searchUrl = "https://eutils.ncbi.nlm.nih.gov/entrez/eutils/esearch.fcgi".toHttpUrl()
            .newBuilder()
            .addQueryParameter("db", "pubmed")
            .addQueryParameter("term", "$query (treatment OR diagnosis OR guideline)")
            .addQueryParameter("retmode", "json")
            .addQueryParameter("retmax", "3")
            .addQueryParameter("sort", "relevance")
            .addQueryParameter("tool", "medbot_android")
            .build()
        return when (val search = fetch(searchUrl)) {
            FetchResult.RobotsDenied -> PubMedFetchResult.RobotsDenied
            FetchResult.Failed -> PubMedFetchResult.Failed
            is FetchResult.Success -> {
                val ids = parsePubMedIds(search.body)
                if (ids.isEmpty()) return PubMedFetchResult.Failed
                val summaryUrl = "https://eutils.ncbi.nlm.nih.gov/entrez/eutils/esummary.fcgi".toHttpUrl()
                    .newBuilder()
                    .addQueryParameter("db", "pubmed")
                    .addQueryParameter("id", ids.joinToString(","))
                    .addQueryParameter("retmode", "json")
                    .addQueryParameter("tool", "medbot_android")
                    .build()
                val summary = fetch(summaryUrl)
                if (summary !is FetchResult.Success) {
                    return if (summary == FetchResult.RobotsDenied) PubMedFetchResult.RobotsDenied else PubMedFetchResult.Failed
                }
                val first = parsePubMedSummary(summary.body, ids.first()) ?: return PubMedFetchResult.Failed
                val abstractUrl = "https://eutils.ncbi.nlm.nih.gov/entrez/eutils/efetch.fcgi".toHttpUrl()
                    .newBuilder()
                    .addQueryParameter("db", "pubmed")
                    .addQueryParameter("id", first.id)
                    .addQueryParameter("retmode", "text")
                    .addQueryParameter("rettype", "abstract")
                    .addQueryParameter("tool", "medbot_android")
                    .build()
                val abstractResult = fetch(abstractUrl) as? FetchResult.Success
                    ?: return PubMedFetchResult.Failed
                val abstractText = abstractResult.body
                val content = listOf(first.title, first.source, abstractText)
                    .filter { it.isNotBlank() }
                    .joinToString("\n")
                    .replace(Regex("\\s+"), " ")
                    .trim()
                    .take(MAX_SOURCE_CHARS)
                if (content.length < MIN_SOURCE_CHARS) PubMedFetchResult.Failed else {
                    val retrievedAt = System.currentTimeMillis()
                    PubMedFetchResult.Success(
                        OnlineEvidence(
                        sourceName = "PubMed / NCBI",
                        title = first.title,
                        url = "https://pubmed.ncbi.nlm.nih.gov/${first.id}/",
                        excerpt = content,
                        sourceRole = OnlineEvidenceSourceRole.PRIMARY_RESEARCH,
                        retrievedAt = retrievedAt,
                        responseSha256 = sha256(abstractResult.body),
                        etag = abstractResult.etag,
                        lastModified = abstractResult.lastModified,
                        freshnessUntil = retrievedAt + CACHE_TTL_MS
                        )
                    )
                }
            }
        }
    }

    private fun parsePubMedIds(body: String): List<String> = runCatching {
        val ids = JSONObject(body).optJSONObject("esearchresult")?.optJSONArray("idlist")
            ?: return@runCatching emptyList()
        buildList { for (index in 0 until ids.length()) ids.optString(index).takeIf(String::isNotBlank)?.let(::add) }
    }.getOrDefault(emptyList())

    private fun parsePubMedSummary(body: String, id: String): PubMedSummary? = runCatching {
        val result = JSONObject(body).optJSONObject("result") ?: return@runCatching null
        val item = result.optJSONObject(id) ?: return@runCatching null
        PubMedSummary(
            id = id,
            title = item.optString("title").trim(),
            source = item.optString("fulljournalname").ifBlank { item.optString("source") }.trim()
        )
    }.getOrNull()

    private fun parseWhoSource(spec: SourceSpec, fetched: FetchResult.Success): OnlineEvidence? {
        val html = fetched.body
        val title = Regex("(?is)<title[^>]*>(.*?)</title>")
            .find(html)?.groupValues?.getOrNull(1)
            ?.let(::htmlToText)
            ?.ifBlank { spec.name }
            ?: spec.name
        val text = htmlToText(html)
        val excerpt = relevantExcerpt(text, spec.url.encodedPath)
        if (excerpt.length < MIN_SOURCE_CHARS) return null
        val retrievedAt = System.currentTimeMillis()
        return OnlineEvidence(
            sourceName = spec.name,
            title = title.take(180),
            url = spec.url.toString(),
            excerpt = excerpt,
            sourceRole = spec.role,
            retrievedAt = retrievedAt,
            responseSha256 = sha256(html),
            etag = fetched.etag,
            lastModified = fetched.lastModified,
            freshnessUntil = retrievedAt + CACHE_TTL_MS
        )
    }

    private fun relevantExcerpt(text: String, path: String): String {
        val normalized = text.replace(Regex("\\s+"), " ").trim()
        val keywords = when {
            path.contains("diarrhoea") -> listOf("diarrhoea", "diarrhea", "dehydration", "rehydration")
            path.contains("pneumonia") -> listOf("pneumonia", "treatment", "symptoms")
            else -> listOf("treatment", "symptoms", "prevention")
        }
        val index = keywords.map { normalized.indexOf(it, ignoreCase = true) }
            .filter { it >= 0 }
            .minOrNull()
            ?: 0
        return normalized
            .substring(index.coerceAtLeast(0).coerceAtMost(normalized.length))
            .take(MAX_SOURCE_CHARS)
    }

    private fun htmlToText(html: String): String = html
        .replace(Regex("(?is)<script[^>]*>.*?</script>"), " ")
        .replace(Regex("(?is)<style[^>]*>.*?</style>"), " ")
        .replace(Regex("(?is)<[^>]+>"), " ")
        .replace("&nbsp;", " ", ignoreCase = true)
        .replace("&amp;", "&", ignoreCase = true)
        .replace("&lt;", "<", ignoreCase = true)
        .replace("&gt;", ">", ignoreCase = true)
        .replace(Regex("&#(\\d+);")) { match ->
            match.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: " "
        }
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun fetch(url: HttpUrl): FetchResult {
        if (!isAllowedHost(url)) return FetchResult.Failed
        val robots = robotsCache[url.host]
        if (robots != null && robots.expiresAt > System.currentTimeMillis() && !robots.allowed(url.encodedPath)) {
            return FetchResult.RobotsDenied
        }
        if (robots == null || robots.expiresAt <= System.currentTimeMillis()) {
            val robotsResult = fetchRobots(url.host, url.scheme)
            if (robotsResult == false) return FetchResult.RobotsDenied
            val refreshedRobots = robotsCache[url.host]
            if (refreshedRobots != null && !refreshedRobots.allowed(url.encodedPath)) {
                return FetchResult.RobotsDenied
            }
        }
        val request = Request.Builder()
            .url(url)
            .header("Accept", "text/html, application/json, text/plain;q=0.9")
            .header("User-Agent", USER_AGENT)
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return FetchResult.Failed
                val body = response.body?.source()?.readBounded(MAX_RESPONSE_BYTES)
                    ?: return FetchResult.Failed
                FetchResult.Success(
                    body = body,
                    etag = response.header("ETag"),
                    lastModified = response.header("Last-Modified")
                )
            }
        } catch (_: IOException) {
            FetchResult.Failed
        }
    }

    private fun fetchRobots(host: String, scheme: String): Boolean? {
        val url = "$scheme://$host/robots.txt".toHttpUrlOrNull() ?: return false
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        return try {
            client.newCall(request).execute().use { response ->
                if (response.code == 404 || response.code == 410) {
                    robotsCache[host] = RobotsEntry(allowAll = true, expiresAt = System.currentTimeMillis() + ROBOTS_TTL_MS)
                    return true
                }
                if (!response.isSuccessful) return false
                val body = response.body?.source()?.readBounded(MAX_ROBOTS_BYTES) ?: return false
                robotsCache[host] = RobotsEntry(
                    body = body,
                    allowAll = false,
                    expiresAt = System.currentTimeMillis() + ROBOTS_TTL_MS
                )
                true
            }
        } catch (_: IOException) {
            false
        }
    }

    private fun isAllowedHost(url: HttpUrl): Boolean =
        url.scheme == "https" && url.host in ALLOWED_HOSTS

    private fun trimCache() {
        while (responseCache.size > MAX_CACHE_ENTRIES) {
            responseCache.entries.minByOrNull { it.value.expiresAt }?.let { responseCache.remove(it.key) } ?: return
        }
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte) }

    private sealed interface FetchResult {
        data class Success(
            val body: String,
            val etag: String? = null,
            val lastModified: String? = null
        ) : FetchResult
        data object RobotsDenied : FetchResult
        data object Failed : FetchResult
    }

    private sealed interface PubMedFetchResult {
        data class Success(val source: OnlineEvidence) : PubMedFetchResult
        data object RobotsDenied : PubMedFetchResult
        data object Failed : PubMedFetchResult
    }

    private data class PubMedSummary(val id: String, val title: String, val source: String)
    private data class SourceSpec(val url: HttpUrl, val name: String, val role: OnlineEvidenceSourceRole)
    private data class CacheEntry(val bundle: OnlineEvidenceBundle, val expiresAt: Long)
    private data class RobotsEntry(
        val body: String = "",
        val allowAll: Boolean,
        val expiresAt: Long
    ) {
        fun allowed(path: String): Boolean = allowAll || RobotsTxtPolicy.isAllowed(body, path, USER_AGENT)
    }

    private companion object {
        const val USER_AGENT = "MedBot/1.0 (bounded clinical evidence retrieval)"
        const val MAX_RESPONSE_BYTES = 512 * 1024
        const val MAX_ROBOTS_BYTES = 64 * 1024
        const val MAX_SOURCE_CHARS = 3_800
        const val MIN_SOURCE_CHARS = 120
        const val CACHE_TTL_MS = 10 * 60 * 1_000L
        const val ROBOTS_TTL_MS = 60 * 60 * 1_000L
        const val MAX_CACHE_ENTRIES = 16
        const val MAX_SOURCES = 4
        val ALLOWED_HOSTS = setOf("www.who.int", "eutils.ncbi.nlm.nih.gov")
        val DIARRHEA_TERMS = listOf("diare", "diarrhea", "diarrhoea", "mencret")
    }
}

private fun BufferedSource.readBounded(maxBytes: Int): String? {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8 * 1024)
    var total = 0
    while (true) {
        val read = read(buffer)
        if (read < 0) break
        if (read == 0) continue
        total += read
        if (total > maxBytes) return null
        output.write(buffer, 0, read)
    }
    return String(output.toByteArray(), StandardCharsets.UTF_8)
}
