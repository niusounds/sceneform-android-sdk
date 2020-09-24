package com.niusounds.scrrcv.ar

import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.renderscript.RenderScript
import android.util.Log
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.Renderable
import com.google.ar.sceneform.rendering.ViewRenderable
import com.google.ar.sceneform.ux.ArFragment
import com.google.ar.sceneform.ux.TransformableNode
import kotlinx.android.synthetic.main.activity_main.*
import java.io.IOException

class MainActivity : AppCompatActivity() {
    private val arFragment: ArFragment by lazy { ar_fragment as ArFragment }
    private var viewRenderable: Renderable? = null
    private val imageView: ImageView by lazy { ImageView(this) }

    private val renderScript by lazy { RenderScript.create(this) }

    private var udpReceiver: UdpReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ViewRenderable.builder()
            .setView(this, imageView)
            .setSizer { view ->
                val scale = 1.0 / view.height.toDouble() * 0.5
                Vector3((view.width * scale).toFloat(), (view.height * scale).toFloat(), 0.01f)
            }
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

            val anchor = hitResult.createAnchor()
            val anchorNode = AnchorNode(anchor).apply {
                setParent(arFragment.arSceneView.scene)
            }

            val model = TransformableNode(arFragment.transformationSystem).apply {
                setParent(anchorNode)
                renderable = viewRenderable
                select()
            }

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
        renderScript.finish()
        super.onDestroy()
    }
}