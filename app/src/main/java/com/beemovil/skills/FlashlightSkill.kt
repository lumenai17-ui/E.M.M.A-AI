package com.beemovil.skills

import android.content.Context
import android.hardware.camera2.CameraManager
import android.os.Build
import org.json.JSONObject

/**
 * FlashlightSkill — toggle the phone flashlight (torch mode).
 */
class FlashlightSkill(private val context: Context) : BeeSkill {
    override val name = "flashlight"
    override val description = "Control the phone flashlight. Actions: 'on', 'off', 'toggle'"
    override val parametersSchema = JSONObject("""
        {"type":"object","properties":{
            "action":{"type":"string","enum":["on","off","toggle"]}
        },"required":["action"]}
    """.trimIndent())

    private var isOn = false

    override fun execute(params: JSONObject): JSONObject {
        val action = params.optString("action", "toggle")

        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList.firstOrNull()
                ?: return JSONObject().put("error", "No camera found")

            val targetState = when (action) {
                "on" -> true
                "off" -> false
                "toggle" -> !isOn
                else -> !isOn
            }

            cameraManager.setTorchMode(cameraId, targetState)
            isOn = targetState
            JSONObject()
                .put("success", true)
                .put("state", if (isOn) "on" else "off")
                .put("message", if (isOn) "🔦 Linterna encendida" else "🔦 Linterna apagada")
        } catch (e: Exception) {
            JSONObject().put("error", "Flashlight error: ${e.message}")
        }
    }
}
