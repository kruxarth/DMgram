package app.dmgram.web

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient.FileChooserParams
import androidx.activity.ComponentActivity
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.GetContent
import androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.core.content.ContextCompat
import app.dmgram.DMGramApp

class MediaRequests(private val activity: ComponentActivity) {
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var audioRequest: PermissionRequest? = null

    private val singleImage = activity.registerForActivityResult(PickVisualMedia()) { uri ->
        deliver(if (uri == null) null else arrayOf(uri))
    }
    private val multipleImages = activity.registerForActivityResult(PickMultipleVisualMedia(MAX_PICKS)) { uris ->
        deliver(if (uris.isEmpty()) null else uris.toTypedArray())
    }
    private val otherFile = activity.registerForActivityResult(GetContent()) { uri ->
        deliver(if (uri == null) null else arrayOf(uri))
    }
    private val recordAudio = activity.registerForActivityResult(RequestPermission()) { granted ->
        val request = audioRequest
        audioRequest = null
        if (request == null) return@registerForActivityResult
        if (granted) request.grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE))
        else request.deny()
    }

    fun showFileChooser(params: FileChooserParams?, callback: ValueCallback<Array<Uri>>?): Boolean {
        deliver(null)
        fileCallback = callback
        val accepts = params?.acceptTypes.orEmpty()
            .flatMap { it.split(',') }
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
        val multiple = params?.mode == FileChooserParams.MODE_OPEN_MULTIPLE
        try {
            if (isVisual(accepts)) {
                val request = PickVisualMediaRequest(visualMime(accepts))
                if (multiple) multipleImages.launch(request) else singleImage.launch(request)
            } else {
                otherFile.launch(contentMime(accepts))
            }
        } catch (error: ActivityNotFoundException) {
            Log.e(DMGramApp.TAG, "No picker for $accepts", error)
            try {
                otherFile.launch(contentMime(accepts))
            } catch (again: ActivityNotFoundException) {
                Log.e(DMGramApp.TAG, "No generic picker either", again)
                deliver(null)
            }
        }
        return true
    }

    fun onPermissionRequest(request: PermissionRequest) {
        val resources = request.resources ?: emptyArray()
        val audioOnly = resources.size == 1 && resources[0] == PermissionRequest.RESOURCE_AUDIO_CAPTURE
        if (!audioOnly) {
            request.deny()
            return
        }
        audioRequest?.deny()
        audioRequest = null
        val granted = ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            request.grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE))
            return
        }
        audioRequest = request
        recordAudio.launch(Manifest.permission.RECORD_AUDIO)
    }

    fun cancel() {
        deliver(null)
        audioRequest?.deny()
        audioRequest = null
    }

    private fun deliver(value: Array<Uri>?) {
        val pending = fileCallback ?: return
        fileCallback = null
        pending.onReceiveValue(value)
    }

    private fun isImage(type: String): Boolean =
        type.startsWith("image") ||
            type.endsWith("png") || type.endsWith("jpg") || type.endsWith("jpeg") ||
            type.endsWith("webp") || type.endsWith("gif") || type.endsWith("heic")

    private fun isVideo(type: String): Boolean =
        type.startsWith("video") || type.endsWith("mp4") || type.endsWith("webm") || type.endsWith("mov")

    private fun isVisual(accepts: List<String>): Boolean =
        accepts.isEmpty() || accepts.any { it == "*/*" || isImage(it) || isVideo(it) }

    private fun visualMime(accepts: List<String>): PickVisualMedia.VisualMediaType {
        val any = accepts.isEmpty() || accepts.any { it == "*/*" }
        val image = any || accepts.any { isImage(it) }
        val video = any || accepts.any { isVideo(it) }
        return when {
            image && !video -> PickVisualMedia.ImageOnly
            video && !image -> PickVisualMedia.VideoOnly
            else -> PickVisualMedia.ImageAndVideo
        }
    }

    private fun contentMime(accepts: List<String>): String = when {
        accepts.any { it.startsWith("audio") } -> "audio/*"
        accepts.any { isVideo(it) } && accepts.none { isImage(it) } -> "video/*"
        accepts.any { isImage(it) } -> "image/*"
        else -> "*/*"
    }

    companion object {
        private const val MAX_PICKS = 10
    }
}
