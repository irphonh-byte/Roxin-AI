package com.ai.roxin.assistant.agent

import android.content.Context
import android.util.Log
import com.ai.roxin.assistant.camera.RoxinCameraVision
import com.ai.roxin.assistant.services.RoxinAccessibilityService
import com.ai.roxin.assistant.services.RoxinNotificationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Roxin AI Autonomous Agent Engine.
 * Built exclusively for Irfan (Owner & Boss).
 * Implements a true multi-step ReAct loop: whatever goal Irfan gives,
 * Roxin executes actions step-by-step until the task is fully achieved!
 */
class RoxinAgentEngine(private val context: Context, private val apiKey: String) {

    private val conversationHistory = mutableListOf<JSONObject>()
    private val memoryStore = mutableMapOf<String, String>()
    private val cameraVision = RoxinCameraVision(context)

    companion object {
        const val OWNER_NAME = "Irfan"
        const val ASSISTANT_NAME = "Roxin"
    }

    init {
        memoryStore["owner_name"] = OWNER_NAME
        memoryStore["owner_role"] = "Boss and Creator"
    }

    /**
     * Autonomous processing loop: runs continuously until the agent completes Irfan's task.
     */
    suspend fun processUserSpeech(userQuery: String): String = withContext(Dispatchers.IO) {
        try {
            // Add initial user request to history
            val userTurn = JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().put(JSONObject().put("text", userQuery)))
            }
            conversationHistory.add(userTurn)

            var stepCount = 0
            val maxSteps = 8
            var finalResponse = ""

            while (stepCount < maxSteps) {
                stepCount++
                val responseJson = callGeminiApi() ?: break

                val candidate = responseJson.optJSONArray("candidates")?.optJSONObject(0) ?: break
                val content = candidate.optJSONObject("content") ?: break
                val parts = content.optJSONArray("parts") ?: break

                var hasFunctionCall = false
                val modelParts = JSONArray()
                val functionResponses = JSONArray()

                for (i in 0 until parts.length()) {
                    val part = parts.getJSONObject(i)

                    if (part.has("text")) {
                        val txt = part.getString("text")
                        if (txt.isNotBlank()) {
                            finalResponse += txt + " "
                            modelParts.put(JSONObject().put("text", txt))
                        }
                    }

                    if (part.has("functionCall")) {
                        hasFunctionCall = true
                        val call = part.getJSONObject("functionCall")
                        val toolName = call.getString("name")
                        val args = call.optJSONObject("args") ?: JSONObject()

                        modelParts.put(part)

                        // Execute tool action on phone
                        val executionResult = executeTool(toolName, args)

                        val funcResp = JSONObject().apply {
                            put("functionResponse", JSONObject().apply {
                                put("name", toolName)
                                put("response", JSONObject().put("result", executionResult))
                            })
                        }
                        functionResponses.put(funcResp)
                    }
                }

                // Record model response in conversation history
                val modelTurn = JSONObject().apply {
                    put("role", "model")
                    put("parts", modelParts)
                }
                conversationHistory.add(modelTurn)

                // If tool was called, send result back to Gemini so it can continue the task
                if (hasFunctionCall && functionResponses.length() > 0) {
                    val toolResultTurn = JSONObject().apply {
                        put("role", "user")
                        put("parts", functionResponses)
                    }
                    conversationHistory.add(toolResultTurn)
                } else {
                    // Task is completed
                    break
                }
            }

            if (finalResponse.isBlank()) {
                finalResponse = "عرفان بھائی، آپ کا ٹاسک مکمل کر دیا گیا ہے۔"
            }

            return@withContext finalResponse.trim()

        } catch (e: Exception) {
            Log.e("RoxinAI", "Error in agent loop", e)
            return@withContext "عرفان بھائی، ٹاسک کے دوران خرابی پیش آئی: ${e.localizedMessage}"
        }
    }

    private fun callGeminiApi(): JSONObject? {
        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"
        val url = URL(endpoint)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true

        val root = JSONObject()

        // 1. System Instruction defining loyalty to Irfan and autonomous execution
        val systemInstruction = JSONObject().apply {
            put("parts", JSONArray().put(JSONObject().put("text", """
                You are Roxin AI, an ultra-smart autonomous AI agent created specifically for your owner and boss, Irfan.
                You are deeply loyal, polite, sharp, and address him respectfully as 'Irfan' or 'عرفان بھائی'.
                You speak fluent Urdu, Hindi, and English with a natural human personality.
                
                Whatever task Irfan gives you, your mission is to completely finish it on his phone using your tools:
                - If he says send WhatsApp to someone, locate contact, type message, and click send.
                - If he asks to see something in real life, call look_through_camera.
                - If he says check Gmail, open Gmail and read the screen.
                - If he asks to open an app or click something, perform the actions.
                - Do not stop halfway; execute all necessary tool calls until the goal is fully accomplished.
                When done, confirm cleanly to Irfan.
            """.trimIndent())))
        }
        root.put("systemInstruction", systemInstruction)

        // 2. Contents history
        val contents = JSONArray()
        for (item in conversationHistory) {
            contents.put(item)
        }
        root.put("contents", contents)

        // 3. Complete Tools Schema
        val tools = JSONArray()
        val funcs = JSONArray().apply {
            put(toolSchema("look_through_camera", "Capture picture from phone camera and describe what is in front of Irfan", listOf("prompt")))
            put(toolSchema("open_app", "Launch any app on Irfan's phone (WhatsApp, Gmail, Chrome, YouTube, Settings, Camera)", listOf("app_name")))
            put(toolSchema("send_whatsapp_message", "Open WhatsApp, find contact, type and send message", listOf("contact_name", "message_text")))
            put(toolSchema("read_screen", "Reads all visible text on the active phone screen", emptyList()))
            put(toolSchema("click_screen", "Clicks any button or item with matching text", listOf("text_to_click")))
            put(toolSchema("type_text", "Types text into the active field", listOf("text_to_type")))
            put(toolSchema("press_key", "Perform navigation like 'back', 'home', 'recents'", listOf("action")))
            put(toolSchema("wait_seconds", "Wait for screen to load", listOf("seconds")))
            put(toolSchema("check_notifications", "Checks incoming notification from WhatsApp or Gmail", emptyList()))
            put(toolSchema("remember_fact", "Saves a detail into permanent memory", listOf("key", "fact")))
            put(toolSchema("recall_fact", "Recalls a saved detail from permanent memory", listOf("key")))
        }
        tools.put(JSONObject().put("functionDeclarations", funcs))
        root.put("tools", tools)

        OutputStreamWriter(conn.outputStream).use { it.write(root.toString()) }

        val responseCode = conn.responseCode
        val stream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
        val text = BufferedReader(InputStreamReader(stream)).use { it.readText() }

        if (responseCode !in 200..299) {
            Log.e("RoxinAI", "Gemini API error: $text")
            return null
        }

        return JSONObject(text)
    }

    private fun toolSchema(name: String, desc: String, required: List<String>): JSONObject {
        val f = JSONObject().apply {
            put("name", name)
            put("description", desc)
            val p = JSONObject().put("type", "OBJECT")
            val props = JSONObject()
            for (r in required) {
                props.put(r, JSONObject().put("type", "STRING").put("description", r))
            }
            p.put("properties", props)
            if (required.isNotEmpty()) {
                p.put("required", JSONArray(required))
            }
            put("parameters", p)
        }
        return f
    }

    private suspend fun executeTool(toolName: String, args: JSONObject): String {
        val service = RoxinAccessibilityService.instance

        return when (toolName) {
            "look_through_camera" -> {
                val prompt = args.optString("prompt", "اس تصویر میں کیا نظر آ رہا ہے؟")
                val base64 = suspendCoroutine<String?> { cont ->
                    cameraVision.captureCurrentView { img: String -> cont.resume(img) }
                }
                if (base64 != null) {
                    analyzeWithVision(prompt, base64)
                } else {
                    "کیمرہ فریم حاصل نہیں ہو سکا۔"
                }
            }
            "open_app" -> {
                val app = args.optString("app_name")
                if (service?.launchApp(app) == true) "ایپ $app کامیابی سے کھول دی گئی ہے۔" else "$app کھل نہیں سکی۔"
            }
            "send_whatsapp_message" -> {
                val contact = args.optString("contact_name")
                val msg = args.optString("message_text")
                if (service != null) {
                    service.launchApp("whatsapp")
                    Thread.sleep(1200)
                    service.clickByText(contact)
                    Thread.sleep(800)
                    service.typeTextIntoField("Message", msg)
                    Thread.sleep(500)
                    service.clickByText("Send")
                    "عرفان بھائی، واٹس ایپ پر $contact کو میسج بھیج دیا گیا ہے: $msg"
                } else {
                    "Accessibility سروس فعال نہیں ہے۔"
                }
            }
            "read_screen" -> service?.dumpScreenText() ?: "اسکرین مواد دستیاب نہیں ہے۔"
            "click_screen" -> {
                val target = args.optString("text_to_click")
                if (service?.clickByText(target) == true) "کلک کر دیا: $target" else "'$target' نہیں مل سکا۔"
            }
            "type_text" -> {
                val txt = args.optString("text_to_type")
                if (service?.typeTextIntoField(null, txt) == true) "ٹائپ ہو گیا: $txt" else "ٹیکسٹ فیلڈ نہیں ملی۔"
            }
            "press_key" -> {
                val action = args.optString("action")
                service?.doGlobalAction(action)
                "ایکشن $action مکمل ہوا۔"
            }
            "wait_seconds" -> {
                val sec = args.optInt("seconds", 2)
                Thread.sleep((sec * 1000).toLong())
                "$sec سیکنڈ انتظار مکمل۔"
            }
            "check_notifications" -> {
                val s = RoxinNotificationService.lastNotificationSender
                val m = RoxinNotificationService.lastNotificationText
                if (s.isNotEmpty()) "تازہ ترین نوٹیفیکیشن: $s: $m" else "کوئی نیا نوٹیفیکیشن نہیں ہے۔"
            }
            "remember_fact" -> {
                val k = args.optString("key")
                val v = args.optString("fact")
                memoryStore[k] = v
                "روکسن نے یاد رکھ لیا ہے: $k = $v"
            }
            "recall_fact" -> {
                val k = args.optString("key")
                memoryStore[k]?.let { "محفوظ معلومات: $it" } ?: "کوئی ریکارڈ نہیں ملا۔"
            }
            else -> "ٹول مکمل ہوا۔"
        }
    }

    private suspend fun analyzeWithVision(prompt: String, base64Image: String): String = withContext(Dispatchers.IO) {
        try {
            val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"
            val conn = URL(endpoint).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true

            val root = JSONObject()
            val contents = JSONArray()
            val userTurn = JSONObject()
            val parts = JSONArray()

            parts.put(JSONObject().apply {
                put("inlineData", JSONObject().apply {
                    put("mimeType", "image/jpeg")
                    put("data", base64Image)
                })
            })
            parts.put(JSONObject().put("text", "You are Roxin AI assisting Irfan. Describe what you see in Urdu/Hindi respectfully: $prompt"))
            userTurn.put("role", "user")
            userTurn.put("parts", parts)
            contents.put(userTurn)
            root.put("contents", contents)

            OutputStreamWriter(conn.outputStream).use { it.write(root.toString()) }

            val response = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
            val cand = JSONObject(response).getJSONArray("candidates").getJSONObject(0)
            cand.getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
        } catch (e: Exception) {
            "کیمرہ اینالیسس میں خرابی: ${e.localizedMessage}"
        }
    }
}
