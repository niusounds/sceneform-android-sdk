package com.niusounds.scrrcv.ar

import android.content.ContentValues
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.media.CamcorderProfile
import android.os.Bundle
import android.provider.MediaStore
import android.renderscript.RenderScript
import android.util.Log
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.rendering.FixedHeightViewSizer
import com.google.ar.sceneform.rendering.Renderable
import com.google.ar.sceneform.rendering.ViewRenderable
import com.google.ar.sceneform.samples.videorecording.VideoRecorder
import com.google.ar.sceneform.samples.videorecording.WritingArFragment
import com.google.ar.sceneform.ux.TransformableNode
import kotlinx.android.synthetic.main.activity_main.*
import java.io.IOException


class MainActivity : AppCompatActivity() {
    private val arFragment: WritingArFragment by lazy { ar_fragment as WritingArFragment }
    private var viewRenderable: Renderable? = null
    private var anchorNode: AnchorNode? = null
    private var transformableNode: TransformableNode? = null
    private val imageView: ImageView by lazy { ImageView(this) }

    private val renderScript by lazy { RenderScript.create(this) }

    private var udpReceiver: UdpReceiver? = null

    private val videoRecorder: VideoRecorder by lazy { VideoRecorder() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ViewRenderable.builder()
            .setView(this, imageView)
            .setSizer(FixedHeightViewSizer(0.5f))
            .build()
            .thenAccept {
                viewRenderable = it
            }
            .exceptionally {
                Toast.makeText(this, "Unable to load Tiger renderable", Toast.LENGTH_LONG).show()
                null
            }

        arFragment.setOnTapArPlaneListener { hitResult, plane, motionEvent ->
            val viewRenderable = this.viewRenderable ?: return@setOnTapArPlaneListener
            if (anchorNode != null) {
                anchorNode?.anchor = hitResult.createAnchor()
                transformableNode?.select()
                return@setOnTapArPlaneListener
            }

            val anchor = hitResult.createAnchor()
            anchorNode = AnchorNode(anchor).apply {
                setParent(arFragment.arSceneView.scene)
            }

            transformableNode = TransformableNode(arFragment.transformationSystem).apply {
                setParent(anchorNode)
                renderable = viewRenderable
                select()
            }
            arFragment.arSceneView.planeRenderer.isEnabled = false // hide floor dots
        }

        // Initialize the VideoRecorder.
        val orientation = resources.configuration.orientation
        videoRecorder.setVideoQuality(CamcorderProfile.QUALITY_2160P, orientation)
        videoRecorder.setSceneView(arFragment.arSceneView)

        recordButton.setOnClickListener { toggleRecording()/**/ }
    }

    private fun toggleRecording() {
        if (!arFragment.hasWritePermission()) {
//            Log.e(TAG, "Video recording requires the WRITE_EXTERNAL_STORAGE permission")
            Toast.makeText(
                this,
                "Video recording requires the WRITE_EXTERNAL_STORAGE permission",
                Toast.LENGTH_LONG
            )
                .show()
            arFragment.launchPermissionSettings()
            return
        }
        val recording = videoRecorder.onToggleRecord()
        if (recording) {
            recordButton.setImageResource(R.drawable.ic_stop)
        } else {
            recordButton.setImageResource(R.drawable.ic_videocam)
            val videoPath = videoRecorder.videoPath.absolutePath
            Toast.makeText(this, "Video saved: $videoPath", Toast.LENGTH_SHORT).show()
//            Log.d(TAG, "Video saved: $videoPath")

            // Send  notification of updated content.
            val values = ContentValues()
            values.put(MediaStore.Video.Media.TITLE, "Sceneform Video")
            values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            values.put(MediaStore.Video.Media.DATA, videoPath)
            contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
        }
    }

    override fun onResume() {
        super.onResume()

        // Receive screen data
        udpReceiver = UdpReceiver(port = 9999, onData = {
            try {
                // ImageDecoder decodes to Hardware Bitmap
                // But it is not supported.
                // So use Software Bitmap (BitmapFactory).
//                val source = ImageDecoder.createSource(it)
//                val drawable = ImageDecoder.decodeDrawable(source)
//                val bitmap = ImageDecoder.decodeBitmap(source)

                val data = ByteArray(it.remaining())
                it.get(data)
                val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size)

                val modifiedBitmap = BitmapChromaKey(renderScript).chromaKey(bitmap)

                runOnUiThread {
                    imageView.setImageDrawable(BitmapDrawable(resources, modifiedBitmap))
                }

            } catch (e: IOException) {
                Log.d("ReceiverView", "decode failed $e")
            }
        })
    }

    override fun onPause() {
        udpReceiver?.close()
        udpReceiver = null
        super.onPause()
    }

    override fun onDestroy() {
        renderScript.destroy()
        super.onDestroy()
    }
}