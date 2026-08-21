package com.miolauncher.app.ui.components

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast

/** 在任意线程安全的 Toast 调用（自动切主线程） */
fun toastOnMain(context: Context, message: String) {
    if (Looper.myLooper() == Looper.getMainLooper()) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    } else {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
}
