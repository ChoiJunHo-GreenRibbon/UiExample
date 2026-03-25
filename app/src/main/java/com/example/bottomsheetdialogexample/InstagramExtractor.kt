package com.example.bottomsheetdialogexample

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object InstagramExtractor {

    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val URL_PATTERN = Pattern.compile(
        "(https?://(?:www\\.)?instagram\\.com/(?:reel|reels|p|tv)/[A-Za-z0-9_-]+/?[^\\s]*)"
    )

    /**
     * Extracts an Instagram URL from shared text.
     */
    fun extractInstagramUrl(text: String): String? {
        val matcher = URL_PATTERN.matcher(text)
        return if (matcher.find()) {
            matcher.group(1)?.split("?")?.get(0)?.trimEnd('/')
        } else {
            null
        }
    }

    /**
     * Fetches the video download URL from an Instagram post/reel page.
     * Tries multiple methods to extract the video URL.
     */
    suspend fun getVideoDownloadUrl(instagramUrl: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Method 1: Fetch page HTML and parse og:video meta tag
            val videoUrl = fetchOgVideoUrl(instagramUrl)
            if (videoUrl != null) {
                return@withContext Result.success(videoUrl)
            }

            // Method 2: Try with /embed/ suffix
            val embedUrl = "$instagramUrl/embed/"
            val embedVideoUrl = fetchVideoFromEmbed(embedUrl)
            if (embedVideoUrl != null) {
                return@withContext Result.success(embedVideoUrl)
            }

            // Method 3: Try extracting from page source JSON
            val jsonVideoUrl = fetchVideoFromPageSource(instagramUrl)
            if (jsonVideoUrl != null) {
                return@withContext Result.success(jsonVideoUrl)
            }

            Result.failure(Exception("동영상 URL을 찾을 수 없습니다. 링크를 확인해주세요."))
        } catch (e: Exception) {
            Result.failure(Exception("다운로드 실패: ${e.message}"))
        }
    }

    private fun fetchOgVideoUrl(url: String): String? {
        val request = buildRequest(url)
        val response = client.newCall(request).execute()
        val html = response.body?.string() ?: return null

        val doc = Jsoup.parse(html)

        // Try og:video meta tag
        val ogVideo = doc.select("meta[property=og:video]").attr("content")
        if (ogVideo.isNotBlank() && ogVideo.contains("http")) {
            return ogVideo
        }

        // Try og:video:secure_url
        val ogVideoSecure = doc.select("meta[property=og:video:secure_url]").attr("content")
        if (ogVideoSecure.isNotBlank() && ogVideoSecure.contains("http")) {
            return ogVideoSecure
        }

        // Try to find video URL in script tags
        return extractVideoUrlFromScripts(html)
    }

    private fun fetchVideoFromEmbed(embedUrl: String): String? {
        val request = buildRequest(embedUrl)
        val response = client.newCall(request).execute()
        val html = response.body?.string() ?: return null

        return extractVideoUrlFromScripts(html)
    }

    private fun fetchVideoFromPageSource(url: String): String? {
        val request = Request.Builder()
            .url("$url/?__a=1&__d=dis")
            .addHeader("User-Agent", "Instagram 275.0.0.27.98 Android")
            .addHeader("Accept", "*/*")
            .addHeader("X-IG-App-ID", "936619743392459")
            .build()

        return try {
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return null

            // Look for video_url in the JSON response
            val videoUrlPattern = Pattern.compile("\"video_url\"\\s*:\\s*\"([^\"]+)\"")
            val matcher = videoUrlPattern.matcher(body)
            if (matcher.find()) {
                matcher.group(1)?.replace("\\u0026", "&")?.replace("\\/", "/")
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun extractVideoUrlFromScripts(html: String): String? {
        // Pattern to find video URLs in various formats Instagram uses
        val patterns = listOf(
            Pattern.compile("\"video_url\"\\s*:\\s*\"([^\"]+)\""),
            Pattern.compile("\"contentUrl\"\\s*:\\s*\"([^\"]+)\""),
            Pattern.compile("\"src\"\\s*:\\s*\"(https://[^\"]*\\.mp4[^\"]*)\""),
            Pattern.compile("source\\s+src=\"(https://[^\"]*\\.mp4[^\"]*)\""),
            Pattern.compile("video_url&quot;:&quot;([^&]+)&quot;"),
        )

        for (pattern in patterns) {
            val matcher = pattern.matcher(html)
            if (matcher.find()) {
                val url = matcher.group(1)
                    ?.replace("\\u0026", "&")
                    ?.replace("\\/", "/")
                    ?.replace("&amp;", "&")
                if (url != null && url.startsWith("http")) {
                    return url
                }
            }
        }
        return null
    }

    private fun buildRequest(url: String): Request {
        return Request.Builder()
            .url(url)
            .addHeader("User-Agent",
                "Mozilla/5.0 (Linux; Android 12; SM-G991B) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/112.0.0.0 Mobile Safari/537.36")
            .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .addHeader("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7")
            .addHeader("Connection", "keep-alive")
            .build()
    }
}
