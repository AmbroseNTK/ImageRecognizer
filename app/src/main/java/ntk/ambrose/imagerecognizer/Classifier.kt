package ntk.ambrose.imagerecognizer

import android.graphics.Bitmap

interface Classifier {
    fun recognizeImage(bitmap: Bitmap): Result
}