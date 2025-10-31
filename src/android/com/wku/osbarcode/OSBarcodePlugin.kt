
package com.wku.osbarcode

import android.content.Intent
import org.apache.cordova.CallbackContext
import org.apache.cordova.CordovaPlugin
import org.json.JSONArray
import org.json.JSONObject
import java.lang.ref.WeakReference

class OSBarcodePlugin : CordovaPlugin() {
    companion object {
        private var callbackRef: WeakReference<CallbackContext>? = null

        fun completeWithSuccess(result: IntentResult) {
            val cb = callbackRef?.get() ?: return
            val json = JSONObject().apply {
                put("scanText", result.scanText)
                put("format", result.format)
                put("imageContentBase64", result.imageBase64)
            }
            cb.success(json)
            callbackRef?.clear()
        }
        fun completeWithError(message: String) {
            val cb = callbackRef?.get() ?: return
            cb.error(message)
            callbackRef?.clear()
        }
    }

    override fun execute(action: String, args: JSONArray, callbackContext: CallbackContext): Boolean {
        if (action != "scanAndGrabFrame") return false
        callbackRef = WeakReference(callbackContext)
        val opts = if (args.length() > 0) args.optJSONObject(0) ?: JSONObject() else JSONObject()
        val jpegQuality = opts.optInt("jpegQuality", 80)
        val facingBack  = opts.optBoolean("facingBack", true)
        val intent = Intent(cordova.activity, ScanAndGrabActivity::class.java).apply {
            putExtra("jpegQuality", jpegQuality)
            putExtra("facingBack", facingBack)
        }
        cordova.activity.startActivity(intent)
        return true
    }

    override fun onReset() {
        callbackRef?.clear()
        super.onReset()
    }
}

data class IntentResult(
    val scanText: String,
    val format: String,
    val imageBase64: String
)
