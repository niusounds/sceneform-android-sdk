package com.niusounds.scrrcv.ar

import android.graphics.Bitmap
import android.renderscript.Allocation
import android.renderscript.Float3
import android.renderscript.RenderScript

class BitmapChromaKey(private val renderScript: RenderScript) {
  private val script = ScriptC_chroma_key(renderScript)

  fun chromaKey(bitmap: Bitmap): Bitmap {
    val inAlloc = Allocation.createFromBitmap(renderScript, bitmap)
    val outAlloc = Allocation.createTyped(renderScript, inAlloc.type)

    script.apply {
      _keyColor = Float3(0.0f, 1.0f, 0.0f)
      _threshold = 0.3f
      _blendThreshold = 0.8f
    }
    script.forEach_chromaKey(inAlloc, outAlloc)

    val generatedBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config)
    outAlloc.copyTo(generatedBitmap)
    return generatedBitmap
  }
}