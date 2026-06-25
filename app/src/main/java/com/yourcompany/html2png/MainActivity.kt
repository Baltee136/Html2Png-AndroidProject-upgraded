package com.yourcompany.html2png

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    // Registers the system file picker. ActivityResultContracts handles all the
    // request-code plumbing for us so we don't need onActivityResult boilerplate.
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            handlePickedFiles(result.data)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        setContentView(webView)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        // Allows html2canvas inside the page to load local asset files (fonts, css)
        // referenced via relative file:// paths when needed.
        webView.settings.allowFileAccess = true

        webView.webViewClient = WebViewClient()
        webView.addJavascriptInterface(AndroidBridge(), "AndroidBridge")

        webView.loadUrl("file:///android_asset/www/index.html")
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    /**
     * Launches the system "open document" picker, scoped to .html files,
     * supporting multi-select. We use OpenDocument over GetContent because
     * it gives more consistent multi-select behavior across OEM file picker apps.
     */
    private fun launchPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "*/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            // Some pickers respect MIME type filtering for text/html, others don't;
            // we filter again by filename extension on the Kotlin side as a safety net.
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/html"))
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        filePickerLauncher.launch(intent)
    }

    private fun handlePickedFiles(data: Intent?) {
        if (data == null) return

        val uris = mutableListOf<Uri>()
        data.clipData?.let { clipData ->
            for (i in 0 until clipData.itemCount) {
                uris.add(clipData.getItemAt(i).uri)
            }
        }
        data.data?.let { uris.add(it) }

        if (uris.isEmpty()) return

        val filesArray = JSONArray()
        for (uri in uris) {
            try {
                val name = queryDisplayName(uri) ?: "file.html"
                if (!name.lowercase().endsWith(".html") && !name.lowercase().endsWith(".htm")) {
                    continue
                }
                val content = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: continue
                val obj = JSONObject()
                obj.put("name", name)
                obj.put("content", content)
                filesArray.put(obj)
            } catch (e: Exception) {
                // Skip files that fail to read; the page-level error UI will simply
                // show fewer queued files than selected, which is an acceptable
                // degradation rather than crashing the whole batch.
            }
        }

        if (filesArray.length() == 0) {
            runOnUiThread {
                Toast.makeText(this, "No valid .html files were selected.", Toast.LENGTH_SHORT).show()
            }
            return
        }

        val payload = filesArray.toString()
        runOnUiThread {
            // Calling back into the page's JS, defined in index.html as
            // window.receiveFilesFromAndroid(jsonString).
            webView.evaluateJavascript(
                "window.receiveFilesFromAndroid(${JSONObject.quote(payload)});",
                null
            )
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        val cursor = contentResolver.query(uri, null, null, null, null) ?: return null
        cursor.use {
            val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (it.moveToFirst() && nameIndex >= 0) {
                return it.getString(nameIndex)
            }
        }
        return null
    }

    /**
     * Decodes a base64 PNG string from the page and writes it into the public
     * Downloads folder using MediaStore, which is the only reliable cross-version
     * way to write to Downloads without needing broad storage permissions on
     * Android 10+.
     */
    private fun savePngToDownloads(filename: String, base64Data: String) {
        try {
            val bytes = Base64.decode(base64Data, Base64.DEFAULT)

            val resolver = contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/")
                }
            }

            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Downloads.EXTERNAL_CONTENT_URI
            } else {
                MediaStore.Files.getContentUri("external")
            }

            val uri = resolver.insert(collection, contentValues)
            if (uri == null) {
                showToast("Could not save $filename.")
                return
            }

            val outputStream: OutputStream? = resolver.openOutputStream(uri)
            outputStream?.use { it.write(bytes) }

            showToast("Saved $filename to Downloads")
        } catch (e: Exception) {
            showToast("Failed to save $filename: ${e.message}")
        }
    }

    private fun showToast(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * The JS bridge object exposed to the page as window.AndroidBridge.
     * Every method here is callable directly from JavaScript inside index.html.
     */
    inner class AndroidBridge {
        @JavascriptInterface
        fun pickFiles() {
            launchPicker()
        }

        @JavascriptInterface
        fun savePng(filename: String, base64Data: String) {
            savePngToDownloads(filename, base64Data)
        }
    }
}
