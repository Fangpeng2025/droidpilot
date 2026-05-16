package com.mobilemcp.pro.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.util.Base64
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.mobilemcp.pro.model.CommandRequest
import com.mobilemcp.pro.model.CommandResponse
import com.mobilemcp.pro.model.UINode
import com.mobilemcp.pro.server.WebSocketCommandServer
import io.github.hzkitty.RapidOCR
import io.github.hzkitty.entity.OcrResult
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class MobileAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "MobileAccessibility"
        var instance: MobileAccessibilityService? = null
            private set
        var isRunning = false
            private set
    }

    private val gson = Gson()
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        isRunning = true
        Log.i(TAG, "Accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Events are not actively processed; UI tree is fetched on demand
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        isRunning = false
        serviceScope.cancel()
        Log.i(TAG, "Accessibility service destroyed")
    }

    fun handleCommand(request: CommandRequest): CommandResponse {
        return try {
            when (request.command) {
                "tap" -> executeTap(request.params)
                "long_press" -> executeLongPress(request.params)
                "swipe" -> executeSwipe(request.params)
                "type_text" -> executeTypeText(request.params)
                "set_text" -> executeSetText(request.params)
                "press_key" -> executePressKey(request.params)
                "get_ui_tree" -> executeGetUITree(request.params)
                "find_element" -> executeFindElement(request.params)
                "screenshot" -> executeScreenshot(request.params)
                "wait_for_element" -> executeWaitForElement(request.params)
                "open_app" -> executeOpenApp(request.params)
                "get_device_info" -> executeGetDeviceInfo()
                "scroll" -> executeScroll(request.params)
                "pinch" -> executePinch(request.params)
                "click_element" -> executeClickElement(request.params)
                "get_focused" -> executeGetFocused()
                "notifications" -> executeGetNotifications()
                "current_app" -> executeCurrentApp()
                "list_apps" -> executeListApps(request.params)
                "clear_text" -> executeClearText()
                "close_app" -> executeCloseApp(request.params)
                "double_tap" -> executeDoubleTap(request.params)
                "drag" -> executeDrag(request.params)
                "clipboard" -> executeClipboard(request.params)
                "media_control" -> executeMediaControl(request.params)
                "volume" -> executeVolume(request.params)
                "ocr" -> executeOCR(request.params)
                "ping" -> CommandResponse.success(request.id, JsonObject().apply { addProperty("pong", true) })
                else -> CommandResponse.error(request.id, "Unknown command: ${request.command}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling command ${request.command}", e)
            CommandResponse.error(request.id, "Error: ${e.message}")
        }
    }

    // --- Gesture Commands ---

    private fun executeTap(params: JsonObject?): CommandResponse {
        val x = params?.get("x")?.asFloat ?: return CommandResponse.error(null, "Missing x")
        val y = params.get("y")?.asFloat ?: return CommandResponse.error(null, "Missing y")
        val duration = params.get("duration")?.asLong ?: 100L

        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, duration)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        return dispatchGestureSync(gesture, "tap($x, $y)")
    }

    private fun executeLongPress(params: JsonObject?): CommandResponse {
        val x = params?.get("x")?.asFloat ?: return CommandResponse.error(null, "Missing x")
        val y = params.get("y")?.asFloat ?: return CommandResponse.error(null, "Missing y")
        val duration = params.get("duration")?.asLong ?: 1000L

        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, duration)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        return dispatchGestureSync(gesture, "longPress($x, $y)")
    }

    private fun executeSwipe(params: JsonObject?): CommandResponse {
        val startX = params?.get("startX")?.asFloat ?: return CommandResponse.error(null, "Missing startX")
        val startY = params.get("startY")?.asFloat ?: return CommandResponse.error(null, "Missing startY")
        val endX = params.get("endX")?.asFloat ?: return CommandResponse.error(null, "Missing endX")
        val endY = params.get("endY")?.asFloat ?: return CommandResponse.error(null, "Missing endY")
        val duration = params.get("duration")?.asLong ?: 300L

        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, duration)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        return dispatchGestureSync(gesture, "swipe($startX,$startY -> $endX,$endY)")
    }

    private fun executeScroll(params: JsonObject?): CommandResponse {
        val direction = params?.get("direction")?.asString ?: "down"
        val amount = params?.get("amount")?.asFloat ?: 500f

        val displayMetrics = resources.displayMetrics
        val centerX = displayMetrics.widthPixels / 2f
        val centerY = displayMetrics.heightPixels / 2f

        val (startX, startY, endX, endY) = when (direction) {
            "up" -> listOf(centerX, centerY - amount / 2, centerX, centerY + amount / 2)
            "down" -> listOf(centerX, centerY + amount / 2, centerX, centerY - amount / 2)
            "left" -> listOf(centerX - amount / 2, centerY, centerX + amount / 2, centerY)
            "right" -> listOf(centerX + amount / 2, centerY, centerX - amount / 2, centerY)
            else -> return CommandResponse.error(null, "Invalid direction: $direction")
        }

        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, 400)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        return dispatchGestureSync(gesture, "scroll($direction)")
    }

    private fun executePinch(params: JsonObject?): CommandResponse {
        val centerX = params?.get("x")?.asFloat ?: (resources.displayMetrics.widthPixels / 2f)
        val centerY = params?.get("y")?.asFloat ?: (resources.displayMetrics.heightPixels / 2f)
        val scale = params?.get("scale")?.asFloat ?: 1.5f // >1 = zoom in, <1 = zoom out
        val duration = params?.get("duration")?.asLong ?: 400L

        val startDist = 100f
        val endDist = startDist * scale

        val path1 = Path().apply {
            moveTo(centerX - startDist, centerY)
            lineTo(centerX - endDist, centerY)
        }
        val path2 = Path().apply {
            moveTo(centerX + startDist, centerY)
            lineTo(centerX + endDist, centerY)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path1, 0, duration))
            .addStroke(GestureDescription.StrokeDescription(path2, 0, duration))
            .build()

        return dispatchGestureSync(gesture, "pinch(scale=$scale)")
    }

    private fun dispatchGestureSync(gesture: GestureDescription, label: String): CommandResponse {
        val latch = CountDownLatch(1)
        var success = false

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                success = true
                latch.countDown()
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                success = false
                latch.countDown()
            }
        }, null)

        latch.await(5, TimeUnit.SECONDS)
        return if (success) {
            CommandResponse.success(null, JsonObject().apply { addProperty("action", label) })
        } else {
            CommandResponse.error(null, "Gesture $label failed or timed out")
        }
    }

    // --- Text Commands ---

    private fun executeTypeText(params: JsonObject?): CommandResponse {
        val text = params?.get("text")?.asString ?: return CommandResponse.error(null, "Missing text")
        val focused = findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: return CommandResponse.error(null, "No focused input field")

        val args = android.os.Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                (focused.text?.toString() ?: "") + text)
        }
        val result = focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        focused.recycle()

        return if (result) {
            CommandResponse.success(null, JsonObject().apply { addProperty("typed", text) })
        } else {
            CommandResponse.error(null, "Failed to type text")
        }
    }

    private fun executeSetText(params: JsonObject?): CommandResponse {
        val text = params?.get("text")?.asString ?: return CommandResponse.error(null, "Missing text")
        val focused = findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: return CommandResponse.error(null, "No focused input field")

        val args = android.os.Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        val result = focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        focused.recycle()

        return if (result) {
            CommandResponse.success(null, JsonObject().apply { addProperty("text_set", text) })
        } else {
            CommandResponse.error(null, "Failed to set text")
        }
    }

    // --- Key Commands ---

    private fun executePressKey(params: JsonObject?): CommandResponse {
        val key = params?.get("key")?.asString ?: return CommandResponse.error(null, "Missing key")
        val action = when (key.lowercase()) {
            "back" -> GLOBAL_ACTION_BACK
            "home" -> GLOBAL_ACTION_HOME
            "recents", "recent" -> GLOBAL_ACTION_RECENTS
            "notifications" -> GLOBAL_ACTION_NOTIFICATIONS
            "quick_settings" -> GLOBAL_ACTION_QUICK_SETTINGS
            "power_dialog" -> GLOBAL_ACTION_POWER_DIALOG
            "split_screen" -> GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN
            "lock_screen" -> if (Build.VERSION.SDK_INT >= 28) GLOBAL_ACTION_LOCK_SCREEN else
                return CommandResponse.error(null, "lock_screen requires API 28+")
            "take_screenshot" -> if (Build.VERSION.SDK_INT >= 28) GLOBAL_ACTION_TAKE_SCREENSHOT else
                return CommandResponse.error(null, "take_screenshot requires API 28+")
            else -> return CommandResponse.error(null, "Unknown key: $key")
        }

        val result = performGlobalAction(action)
        return if (result) {
            CommandResponse.success(null, JsonObject().apply { addProperty("key", key) })
        } else {
            CommandResponse.error(null, "Failed to press key: $key")
        }
    }

    // --- UI Tree Commands ---

    private fun executeGetUITree(params: JsonObject?): CommandResponse {
        val maxDepth = params?.get("maxDepth")?.asInt ?: 15
        val root = rootInActiveWindow ?: return CommandResponse.error(null, "No active window")

        val tree = buildUITree(root, 0, maxDepth)
        root.recycle()

        val json = gson.toJsonTree(tree)
        return CommandResponse.success(null, JsonObject().apply { add("tree", json) })
    }

    private fun executeFindElement(params: JsonObject?): CommandResponse {
        val root = rootInActiveWindow ?: return CommandResponse.error(null, "No active window")

        val results = mutableListOf<UINode>()
        val text = params?.get("text")?.asString
        val id = params?.get("id")?.asString
        val className = params?.get("className")?.asString
        val contentDesc = params?.get("contentDescription")?.asString
        val maxResults = params?.get("maxResults")?.asInt ?: 10

        findElements(root, text, id, className, contentDesc, results, maxResults)
        root.recycle()

        val json = gson.toJsonTree(results)
        return CommandResponse.success(null, JsonObject().apply {
            add("elements", json)
            addProperty("count", results.size)
        })
    }

    private fun executeClickElement(params: JsonObject?): CommandResponse {
        val root = rootInActiveWindow ?: return CommandResponse.error(null, "No active window")

        val text = params?.get("text")?.asString
        val id = params?.get("id")?.asString
        val contentDesc = params?.get("contentDescription")?.asString

        val node = findFirstElement(root, text, id, null, contentDesc)
        root.recycle()

        if (node == null) return CommandResponse.error(null, "Element not found")

        // Walk up to find clickable ancestor if this node isn't clickable
        var target = node
        while (target != null && !target.isClickable) {
            val parent = target.parent
            if (target != node) target.recycle()
            target = parent
        }

        if (target == null) {
            node.recycle()
            return CommandResponse.error(null, "No clickable element found")
        }

        val result = target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        val desc = node.text?.toString() ?: node.contentDescription?.toString() ?: "element"
        if (target != node) target.recycle()
        node.recycle()

        return if (result) {
            CommandResponse.success(null, JsonObject().apply { addProperty("clicked", desc) })
        } else {
            CommandResponse.error(null, "Click action failed")
        }
    }

    private fun executeWaitForElement(params: JsonObject?): CommandResponse {
        val text = params?.get("text")?.asString
        val id = params?.get("id")?.asString
        val className = params?.get("className")?.asString
        val contentDesc = params?.get("contentDescription")?.asString
        val timeoutMs = params?.get("timeout")?.asLong ?: 10000L
        val pollIntervalMs = 500L

        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            val root = rootInActiveWindow
            if (root != null) {
                val node = findFirstElement(root, text, id, className, contentDesc)
                if (node != null) {
                    val uiNode = nodeToUINode(node)
                    node.recycle()
                    root.recycle()
                    return CommandResponse.success(null, JsonObject().apply {
                        add("element", gson.toJsonTree(uiNode))
                        addProperty("found", true)
                        addProperty("elapsed_ms", System.currentTimeMillis() - startTime)
                    })
                }
                root.recycle()
            }
            Thread.sleep(pollIntervalMs)
        }

        return CommandResponse.success(null, JsonObject().apply {
            addProperty("found", false)
            addProperty("elapsed_ms", timeoutMs)
        })
    }

    private fun executeGetFocused(): CommandResponse {
        val focused = findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focused == null) {
            return CommandResponse.success(null, JsonObject().apply { addProperty("focused", false) })
        }
        val uiNode = nodeToUINode(focused)
        focused.recycle()
        return CommandResponse.success(null, JsonObject().apply {
            addProperty("focused", true)
            add("element", gson.toJsonTree(uiNode))
        })
    }

    private fun executeGetNotifications(): CommandResponse {
        // Use the accessibility events - we have access through service info
        val root = rootInActiveWindow
        // Trigger notifications panel
        performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
        Thread.sleep(500)

        val notifRoot = rootInActiveWindow
        val results = mutableListOf<UINode>()
        if (notifRoot != null) {
            findElements(notifRoot, null, null, "android.widget.FrameLayout", null, results, 50)
            notifRoot.recycle()
        }

        performGlobalAction(GLOBAL_ACTION_BACK)

        val json = gson.toJsonTree(results)
        return CommandResponse.success(null, JsonObject().apply {
            add("notifications", json)
        })
    }

    // --- Screenshot ---

    private fun executeScreenshot(params: JsonObject?): CommandResponse {
        if (Build.VERSION.SDK_INT < 30) {
            return CommandResponse.error(null, "Screenshot requires API 30+")
        }

        val quality = params?.get("quality")?.asInt ?: 80
        val latch = CountDownLatch(1)
        var resultBitmap: Bitmap? = null

        takeScreenshot(android.view.Display.DEFAULT_DISPLAY,
            mainExecutor,
            object : TakeScreenshotCallback {
                override fun onSuccess(screenshot: ScreenshotResult) {
                    val hwBitmap = Bitmap.wrapHardwareBuffer(
                        screenshot.hardwareBuffer, screenshot.colorSpace
                    )
                    resultBitmap = hwBitmap?.copy(Bitmap.Config.ARGB_8888, false)
                    hwBitmap?.recycle()
                    screenshot.hardwareBuffer.close()
                    latch.countDown()
                }

                override fun onFailure(errorCode: Int) {
                    Log.e(TAG, "Screenshot failed with code: $errorCode")
                    latch.countDown()
                }
            })

        latch.await(10, TimeUnit.SECONDS)

        val bitmap = resultBitmap ?: return CommandResponse.error(null, "Screenshot failed")
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)
        val base64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
        val width = bitmap.width
        val height = bitmap.height
        bitmap.recycle()

        return CommandResponse.success(null, JsonObject().apply {
            addProperty("image", base64)
            addProperty("format", "jpeg")
            addProperty("width", width)
            addProperty("height", height)
        })
    }

    // --- App Commands ---

    private fun executeOpenApp(params: JsonObject?): CommandResponse {
        val packageName = params?.get("package")?.asString
            ?: return CommandResponse.error(null, "Missing package name")

        val intent = packageManager.getLaunchIntentForPackage(packageName)
            ?: return CommandResponse.error(null, "App not found: $packageName")

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)

        return CommandResponse.success(null, JsonObject().apply {
            addProperty("opened", packageName)
        })
    }

    private fun executeGetDeviceInfo(): CommandResponse {
        val displayMetrics = resources.displayMetrics
        return CommandResponse.success(null, JsonObject().apply {
            addProperty("manufacturer", Build.MANUFACTURER)
            addProperty("model", Build.MODEL)
            addProperty("sdk", Build.VERSION.SDK_INT)
            addProperty("release", Build.VERSION.RELEASE)
            addProperty("screenWidth", displayMetrics.widthPixels)
            addProperty("screenHeight", displayMetrics.heightPixels)
            addProperty("density", displayMetrics.density)
            addProperty("densityDpi", displayMetrics.densityDpi)
        })
    }

    // --- UI Tree Helpers ---

    private fun buildUITree(node: AccessibilityNodeInfo, depth: Int, maxDepth: Int): UINode {
        val uiNode = nodeToUINode(node)

        if (depth < maxDepth) {
            val children = mutableListOf<UINode>()
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                children.add(buildUITree(child, depth + 1, maxDepth))
                child.recycle()
            }
            if (children.isNotEmpty()) {
                uiNode.children = children
            }
        }
        return uiNode
    }

    private fun nodeToUINode(node: AccessibilityNodeInfo): UINode {
        val rect = android.graphics.Rect()
        node.getBoundsInScreen(rect)

        return UINode(
            className = node.className?.toString(),
            text = node.text?.toString(),
            contentDescription = node.contentDescription?.toString(),
            viewId = node.viewIdResourceName,
            isClickable = node.isClickable,
            isScrollable = node.isScrollable,
            isEditable = node.isEditable,
            isEnabled = node.isEnabled,
            isChecked = node.isChecked,
            isFocused = node.isFocused,
            isSelected = node.isSelected,
            bounds = mapOf(
                "left" to rect.left,
                "top" to rect.top,
                "right" to rect.right,
                "bottom" to rect.bottom
            ),
            packageName = node.packageName?.toString()
        )
    }

    private fun findElements(
        node: AccessibilityNodeInfo,
        text: String?,
        id: String?,
        className: String?,
        contentDesc: String?,
        results: MutableList<UINode>,
        maxResults: Int
    ) {
        if (results.size >= maxResults) return

        if (matchesFilter(node, text, id, className, contentDesc)) {
            results.add(nodeToUINode(node))
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findElements(child, text, id, className, contentDesc, results, maxResults)
            child.recycle()
        }
    }

    private fun findFirstElement(
        node: AccessibilityNodeInfo,
        text: String?,
        id: String?,
        className: String?,
        contentDesc: String?
    ): AccessibilityNodeInfo? {
        if (matchesFilter(node, text, id, className, contentDesc)) {
            return AccessibilityNodeInfo.obtain(node)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findFirstElement(child, text, id, className, contentDesc)
            if (found != null) {
                child.recycle()
                return found
            }
            child.recycle()
        }
        return null
    }

    private fun matchesFilter(
        node: AccessibilityNodeInfo,
        text: String?,
        id: String?,
        className: String?,
        contentDesc: String?
    ): Boolean {
        if (text != null) {
            val nodeText = node.text?.toString() ?: ""
            val nodeDesc = node.contentDescription?.toString() ?: ""
            if (!nodeText.contains(text, ignoreCase = true) &&
                !nodeDesc.contains(text, ignoreCase = true)) return false
        }
        if (id != null && node.viewIdResourceName?.contains(id, ignoreCase = true) != true) return false
        if (className != null && node.className?.toString()?.contains(className, ignoreCase = true) != true) return false
        if (contentDesc != null && node.contentDescription?.toString()?.contains(contentDesc, ignoreCase = true) != true) return false
        return true
    }

    // --- App Info Commands ---

    private fun executeCurrentApp(): CommandResponse {
        val root = rootInActiveWindow ?: return CommandResponse.error(null, "No active window")
        val pkg = root.packageName?.toString()
        root.recycle()
        return if (pkg != null) {
            val appInfo = packageManager.getApplicationInfo(pkg, 0)
            val label = packageManager.getApplicationLabel(appInfo).toString()
            CommandResponse.success(null, JsonObject().apply {
                addProperty("package", pkg)
                addProperty("name", label)
            })
        } else {
            CommandResponse.error(null, "Could not determine current app")
        }
    }

    private fun executeListApps(params: JsonObject?): CommandResponse {
        val filter = params?.get("filter")?.asString
        val maxResults = params?.get("maxResults")?.asInt ?: 50
        val apps = packageManager.getInstalledApplications(0)
        val result = com.google.gson.JsonArray()

        var count = 0
        for (app in apps) {
            if (count >= maxResults) break
            val pkg = app.packageName
            val label = try {
                packageManager.getApplicationLabel(app).toString()
            } catch (e: Exception) {
                continue
            }

            // If filter provided, match against package name or label
            if (filter != null) {
                if (!pkg.contains(filter, ignoreCase = true) &&
                    !label.contains(filter, ignoreCase = true)) continue
            }

            // Only include apps with launch intent (skip system services)
            val launchIntent = packageManager.getLaunchIntentForPackage(pkg)
            val isLaunchable = launchIntent != null

            result.add(JsonObject().apply {
                addProperty("package", pkg)
                addProperty("name", label)
                addProperty("launchable", isLaunchable)
            })
            count++
        }

        return CommandResponse.success(null, JsonObject().apply {
            add("apps", result)
            addProperty("count", count)
        })
    }

    // --- Clear Text Command ---

    private fun executeClearText(): CommandResponse {
        val focused = findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: return CommandResponse.error(null, "No focused input field")

        val args = android.os.Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "")
        }
        val result = focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        focused.recycle()

        return if (result) {
            CommandResponse.success(null, JsonObject().apply { addProperty("cleared", true) })
        } else {
            CommandResponse.error(null, "Failed to clear text")
        }
    }

    // --- Close App Command ---

    private fun executeCloseApp(params: JsonObject?): CommandResponse {
        val packageName = params?.get("package")?.asString
        if (packageName != null) {
            // Force stop the specified app
            val intent = Intent(Intent.ACTION_MAIN)
            intent.addCategory(Intent.CATEGORY_HOME)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
            Thread.sleep(300)
            // Use activity manager to force stop
            try {
                val am = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
                am.killBackgroundProcesses(packageName)
                return CommandResponse.success(null, JsonObject().apply {
                    addProperty("closed", packageName)
                })
            } catch (e: Exception) {
                return CommandResponse.error(null, "Failed to close app: ${e.message}")
            }
        } else {
            // Close current foreground app
            val root = rootInActiveWindow ?: return CommandResponse.error(null, "No active window")
            val pkg = root.packageName?.toString()
            root.recycle()
            if (pkg == null) return CommandResponse.error(null, "Could not determine current app")

            // Go home first
            performGlobalAction(GLOBAL_ACTION_HOME)
            Thread.sleep(300)

            try {
                val am = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
                am.killBackgroundProcesses(pkg)
                return CommandResponse.success(null, JsonObject().apply {
                    addProperty("closed", pkg)
                })
            } catch (e: Exception) {
                return CommandResponse.error(null, "Failed to close app: ${e.message}")
            }
        }
    }

    // --- Double Tap Command ---

    private fun executeDoubleTap(params: JsonObject?): CommandResponse {
        val x = params?.get("x")?.asFloat ?: return CommandResponse.error(null, "Missing x")
        val y = params.get("y")?.asFloat ?: return CommandResponse.error(null, "Missing y")
        val interval = params.get("interval")?.asLong ?: 100L // ms between taps

        // First tap
        val path1 = Path().apply { moveTo(x, y) }
        val stroke1 = GestureDescription.StrokeDescription(path1, 0, 50)
        val gesture1 = GestureDescription.Builder().addStroke(stroke1).build()
        dispatchGestureSync(gesture1, "tap1($x, $y)")

        Thread.sleep(interval)

        // Second tap
        val path2 = Path().apply { moveTo(x, y) }
        val stroke2 = GestureDescription.StrokeDescription(path2, 0, 50)
        val gesture2 = GestureDescription.Builder().addStroke(stroke2).build()
        return dispatchGestureSync(gesture2, "doubleTap($x, $y)")
    }

    // --- Drag Command ---

    private fun executeDrag(params: JsonObject?): CommandResponse {
        val startX = params?.get("startX")?.asFloat ?: return CommandResponse.error(null, "Missing startX")
        val startY = params.get("startY")?.asFloat ?: return CommandResponse.error(null, "Missing startY")
        val endX = params.get("endX")?.asFloat ?: return CommandResponse.error(null, "Missing endX")
        val endY = params.get("endY")?.asFloat ?: return CommandResponse.error(null, "Missing endY")
        val duration = params.get("duration")?.asLong ?: 800L

        // Drag = long press then swipe
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, duration)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        return dispatchGestureSync(gesture, "drag($startX,$startY -> $endX,$endY)")
    }

    // --- Clipboard Command ---

    private fun executeClipboard(params: JsonObject?): CommandResponse {
        val action = params?.get("action")?.asString ?: "get"

        return when (action) {
            "get" -> {
                val clip = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                if (!clip.hasPrimaryClip()) {
                    return CommandResponse.success(null, JsonObject().apply {
                        addProperty("text", "")
                        addProperty("hasContent", false)
                    })
                }
                val item = clip.primaryClip?.getItemAt(0)
                val text = item?.text?.toString() ?: ""
                CommandResponse.success(null, JsonObject().apply {
                    addProperty("text", text)
                    addProperty("hasContent", true)
                })
            }
            "set" -> {
                val text = params?.get("text")?.asString
                    ?: return CommandResponse.error(null, "Missing text for clipboard set")
                val clip = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clip.setPrimaryClip(android.content.ClipData.newPlainText("text", text))
                CommandResponse.success(null, JsonObject().apply {
                    addProperty("set", true)
                    addProperty("text", text)
                })
            }
            else -> CommandResponse.error(null, "Unknown clipboard action: $action. Use get or set")
        }
    }

    // --- Media Control Command ---

    private fun executeMediaControl(params: JsonObject?): CommandResponse {
        val action = params?.get("action")?.asString?.lowercase()
            ?: return CommandResponse.error(null, "Missing action. Use: play, pause, play_pause, next, previous, stop")

        val keyCode = when (action) {
            "play" -> android.view.KeyEvent.KEYCODE_MEDIA_PLAY
            "pause" -> android.view.KeyEvent.KEYCODE_MEDIA_PAUSE
            "play_pause" -> android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            "next" -> android.view.KeyEvent.KEYCODE_MEDIA_NEXT
            "previous" -> android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS
            "stop" -> android.view.KeyEvent.KEYCODE_MEDIA_STOP
            "fast_forward" -> android.view.KeyEvent.KEYCODE_MEDIA_FAST_FORWARD
            "rewind" -> android.view.KeyEvent.KEYCODE_MEDIA_REWIND
            else -> return CommandResponse.error(null, "Unknown media action: $action")
        }

        // Inject key event via accessibility service dispatchGesture
        // Use performGlobalAction is not available for media keys
        // Instead use Instrumentation or dispatchKeyEvent through the window
        try {
            // Use Android's media session manager approach
            val downEvent = android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, keyCode)
            val upEvent = android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, keyCode)

        // AccessibilityService can inject key events on API 24+
        if (Build.VERSION.SDK_INT >= 24) {
            val dispatchResult = dispatchGesture(
                GestureDescription.Builder().build(), null, null
            )
            // For media keys, we need a different approach
            // Use the service's onKeyEvent or send ordered broadcast
        }

            // Alternative: use AudioManager dispatch
            val am = getSystemService(AUDIO_SERVICE) as android.media.AudioManager
            when (action) {
                "play_pause" -> am.dispatchMediaKeyEvent(
                    android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, keyCode)
                )
                "next" -> am.dispatchMediaKeyEvent(
                    android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, keyCode)
                )
                "previous" -> am.dispatchMediaKeyEvent(
                    android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, keyCode)
                )
                else -> am.dispatchMediaKeyEvent(
                    android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, keyCode)
                )
            }
            am.dispatchMediaKeyEvent(
                android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, keyCode)
            )

            return CommandResponse.success(null, JsonObject().apply {
                addProperty("action", action)
            })
        } catch (e: Exception) {
            return CommandResponse.error(null, "Media control failed: ${e.message}")
        }
    }

    // --- Volume Command ---

    private fun executeVolume(params: JsonObject?): CommandResponse {
        val action = params?.get("action")?.asString?.lowercase()
            ?: return CommandResponse.error(null, "Missing action. Use: up, down, mute, get, set")
        val am = getSystemService(AUDIO_SERVICE) as android.media.AudioManager

        when (action) {
            "up" -> {
                am.adjustVolume(android.media.AudioManager.ADJUST_RAISE, android.media.AudioManager.FLAG_SHOW_UI)
                return CommandResponse.success(null, JsonObject().apply { addProperty("action", "volume_up") })
            }
            "down" -> {
                am.adjustVolume(android.media.AudioManager.ADJUST_LOWER, android.media.AudioManager.FLAG_SHOW_UI)
                return CommandResponse.success(null, JsonObject().apply { addProperty("action", "volume_down") })
            }
            "mute" -> {
                am.adjustVolume(android.media.AudioManager.ADJUST_MUTE, android.media.AudioManager.FLAG_SHOW_UI)
                return CommandResponse.success(null, JsonObject().apply { addProperty("action", "muted") })
            }
            "unmute" -> {
                am.adjustVolume(android.media.AudioManager.ADJUST_UNMUTE, android.media.AudioManager.FLAG_SHOW_UI)
                return CommandResponse.success(null, JsonObject().apply { addProperty("action", "unmuted") })
            }
            "get" -> {
                val stream = params.get("stream")?.asString ?: "music"
                val streamType = when (stream) {
                    "music" -> android.media.AudioManager.STREAM_MUSIC
                    "ring" -> android.media.AudioManager.STREAM_RING
                    "notification" -> android.media.AudioManager.STREAM_NOTIFICATION
                    "alarm" -> android.media.AudioManager.STREAM_ALARM
                    "call" -> android.media.AudioManager.STREAM_VOICE_CALL
                    "system" -> android.media.AudioManager.STREAM_SYSTEM
                    else -> android.media.AudioManager.STREAM_MUSIC
                }
                val current = am.getStreamVolume(streamType)
                val max = am.getStreamMaxVolume(streamType)
                return CommandResponse.success(null, JsonObject().apply {
                    addProperty("current", current)
                    addProperty("max", max)
                    addProperty("stream", stream)
                })
            }
            "set" -> {
                val level = params.get("level")?.asInt
                    ?: return CommandResponse.error(null, "Missing level for volume set")
                val stream = params.get("stream")?.asString ?: "music"
                val streamType = when (stream) {
                    "music" -> android.media.AudioManager.STREAM_MUSIC
                    "ring" -> android.media.AudioManager.STREAM_RING
                    "notification" -> android.media.AudioManager.STREAM_NOTIFICATION
                    "alarm" -> android.media.AudioManager.STREAM_ALARM
                    "call" -> android.media.AudioManager.STREAM_VOICE_CALL
                    "system" -> android.media.AudioManager.STREAM_SYSTEM
                    else -> android.media.AudioManager.STREAM_MUSIC
                }
                am.setStreamVolume(streamType, level, android.media.AudioManager.FLAG_SHOW_UI)
                return CommandResponse.success(null, JsonObject().apply {
                    addProperty("set", level)
                    addProperty("stream", stream)
                })
            }
            else -> return CommandResponse.error(null, "Unknown volume action: $action")
        }
    }

    // --- OCR Command (RapidOCR4j) ---
    
    private fun executeOCR(params: JsonObject?): CommandResponse {
        try {
            // 先截图
            val screenshotResult = executeScreenshot(params)
            if (!screenshotResult.success) {
                return CommandResponse.error(null, "Screenshot failed for OCR")
            }
            
            val imageData = screenshotResult.data?.get("image")?.asString
            if (imageData.isNullOrEmpty()) {
                return CommandResponse.error(null, "No image data for OCR")
            }
            
            // Base64 解码为 Bitmap
            val bytes = Base64.decode(imageData, Base64.DEFAULT)
            val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: return CommandResponse.error(null, "Failed to decode image")
            
            // 使用 RapidOCR 识别
            val rapidOCR = RapidOCR.create(this)
            val ocrResult: OcrResult = rapidOCR.run(bitmap)
            
            // 构建返回结果
            val wordsArray = JsonArray()
            val textBuilder = StringBuilder()
            
            ocrResult.recRes?.forEach { rec ->
                val wordObj = JsonObject().apply {
                    addProperty("text", rec.text)
                    addProperty("confidence", rec.confidence)
                    
                    // 边界框 (四个角坐标)
                    val boxes = rec.dtBoxes
                    if (boxes != null && boxes.size >= 4) {
                        val boundsObj = JsonObject().apply {
                            addProperty("left", boxes[0].x.toInt())
                            addProperty("top", boxes[0].y.toInt())
                            addProperty("right", boxes[2].x.toInt())
                            addProperty("bottom", boxes[2].y.toInt())
                        }
                        add("bounds", boundsObj)
                    }
                }
                wordsArray.add(wordObj)
                textBuilder.append(rec.text).append(" ")
            }
            
            val resultObj = JsonObject().apply {
                addProperty("text", textBuilder.toString().trim())
                add("words", wordsArray)
                addProperty("wordCount", wordsArray.size())
                addProperty("elapseTime", ocrResult.elapseTime)
            }
            
            return CommandResponse.success(null, resultObj)
            
        } catch (e: Exception) {
            Log.e(TAG, "OCR error", e)
            return CommandResponse.error(null, "OCR error: ${e.message}")
        }
    }
}
