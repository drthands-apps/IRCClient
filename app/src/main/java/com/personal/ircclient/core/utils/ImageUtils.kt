package com.personal.ircclient.core.utils

import android.graphics.*
import android.util.Base64
import java.io.ByteArrayOutputStream

object ImageUtils {
    fun addWatermark(bitmap: Bitmap, text: String): Bitmap {
        val config = bitmap.config ?: Bitmap.Config.ARGB_8888
        val result = bitmap.copy(config, true)
        val canvas = Canvas(result)
        val paint = Paint().apply {
            color = Color.WHITE
            alpha = 100
            textSize = bitmap.height / 10f
            isAntiAlias = true
            setShadowLayer(2f, 1f, 1f, Color.BLACK)
        }
        
        // Bottom right corner
        val bounds = Rect()
        paint.getTextBounds(text, 0, text.length, bounds)
        val x = bitmap.width - bounds.width() - 20f
        val y = bitmap.height - 20f
        
        canvas.drawText(text, x, y, paint)
        return result
    }

    fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 50, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }

    fun base64ToBitmap(base64: String): Bitmap? {
        return try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            null
        }
    }
}
