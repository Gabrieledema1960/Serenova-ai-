package com.example.aiphoneassistant

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityEvent
import java.util.ArrayDeque

class AssistantAccessibilityService : AccessibilityService() {
    companion object {
        @Volatile var instance: AssistantAccessibilityService? = null
        @Volatile var training = false
        @Volatile var recordedSteps: MutableList<ActionStep> = mutableListOf()
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var store: SkillStore
    @Volatile private var lastWindowPackage: String = ""

    override fun onServiceConnected() {
        super.onServiceConnected()
        store = SkillStore(this)
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.packageName != null) lastWindowPackage = event.packageName.toString()
        if (!training || event?.eventType != AccessibilityEvent.TYPE_VIEW_CLICKED) return
        val source = event.source ?: return
        val text = source.text?.toString()?.trim().orEmpty()
        val description = source.contentDescription?.toString()?.trim().orEmpty()
        val viewId = source.viewIdResourceName?.trim().orEmpty()
        val packageName = source.packageName?.toString().orEmpty()

        val step = when {
            packageName == "com.android.systemui" && viewId.endsWith("/home") ->
                ActionStep(ActionType.HOME, "", 700)
            packageName == "com.android.systemui" && viewId.endsWith("/recent_apps") ->
                ActionStep(ActionType.RECENT_APPS, "", 700)
            viewId.isNotEmpty() && packageName != "com.android.systemui" ->
                ActionStep(ActionType.CLICK_ID, viewId, 650)
            text.isNotEmpty() -> ActionStep(ActionType.CLICK_TEXT, text, 650)
            description.isNotEmpty() -> ActionStep(ActionType.CLICK_TEXT, description, 650)
            else -> null
        } ?: return

        recordedSteps.add(step)
        store.saveTrainingDraft(recordedSteps)
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    fun startTraining() {
        recordedSteps = mutableListOf()
        store.saveTrainingDraft(recordedSteps)
        training = true
    }

    fun stopTraining(): List<ActionStep> {
        training = false
        recordedSteps = store.loadTrainingDraft().toMutableList()
        return recordedSteps.toList()
    }

    fun clearTrainingDraft() {
        training = false
        recordedSteps = mutableListOf()
        store.clearTrainingDraft()
    }

    fun execute(skill: Skill, callback: (String) -> Unit) {
        Thread {
            try {
                skill.steps.forEachIndexed { index, step ->
                    executeStepWithRecovery(step)
                    handler.post {
                        callback("Completed ${index + 1}/${skill.steps.size}: ${step.type}${if (step.value.isNotEmpty()) ": ${step.value}" else ""}")
                    }
                    if (step.delayMs > 0) Thread.sleep(step.delayMs.toLong())
                }
                handler.post { callback("DONE") }
            } catch (e: Exception) {
                handler.post {
                    callback("ERROR: ${e.message ?: "Unknown error while replaying step"}")
                }
            }
        }.start()
    }

    /** Retry transient UI timing failures instead of immediately declaring the skill stuck. */
    private fun executeStepWithRecovery(step: ActionStep) {
        var last: Exception? = null
        repeat(4) { attempt ->
            try {
                if (attempt > 0) Thread.sleep(350L * attempt)
                executeStep(step)
                return
            } catch (e: Exception) {
                last = e
                // Give Android time to publish the new accessibility tree after
                // an app launch, navigation action, keyboard animation, etc.
                Thread.sleep(300L)
            }
        }
        throw last ?: IllegalStateException("Action failed")
    }

    private fun executeStep(step: ActionStep) {
        when (step.type) {
            ActionType.LAUNCH_APP -> {
                val intent = packageManager.getLaunchIntentForPackage(step.value)
                    ?: throw IllegalArgumentException("App not found: ${step.value}")
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                Thread.sleep(1000)
            }
            ActionType.BACK -> {
                if (!performGlobalAction(GLOBAL_ACTION_BACK)) throw IllegalStateException("Back action failed")
            }
            ActionType.HOME -> {
                if (!performGlobalAction(GLOBAL_ACTION_HOME)) throw IllegalStateException("Home action failed")
                Thread.sleep(800)
            }
            ActionType.RECENT_APPS -> {
                if (!performGlobalAction(GLOBAL_ACTION_RECENTS)) throw IllegalStateException("Recent Apps action failed")
                Thread.sleep(800)
            }
            ActionType.WAIT -> Thread.sleep(step.value.toLongOrNull() ?: 1000L)
            ActionType.CLICK_TEXT -> clickByTextOrDescription(step.value)
            ActionType.CLICK_ID -> clickByIdWithFallback(step.value)
        }
    }

    private fun clickByTextOrDescription(value: String) {
        val root = rootInActiveWindow ?: throw IllegalStateException("No active window yet")
        val exact = root.findAccessibilityNodeInfosByText(value)
            .firstOrNull { it.isVisibleToUser }
        if (exact != null && clickNodeOrParent(exact)) return

        val lower = value.lowercase()
        val candidate = findNode(root) { node ->
            val text = node.text?.toString()?.trim().orEmpty().lowercase()
            val desc = node.contentDescription?.toString()?.trim().orEmpty().lowercase()
            node.isVisibleToUser && (text == lower || desc == lower || text.contains(lower) || desc.contains(lower))
        }
        if (candidate != null && clickNodeOrParent(candidate)) return
        throw IllegalArgumentException("Text/button not found: $value")
    }

    private fun clickByIdWithFallback(id: String) {
        if (id.endsWith("/home") && id.startsWith("com.android.systemui:")) {
            if (!performGlobalAction(GLOBAL_ACTION_HOME)) throw IllegalStateException("Home action failed")
            Thread.sleep(700)
            return
        }
        if (id.endsWith("/recent_apps") && id.startsWith("com.android.systemui:")) {
            if (!performGlobalAction(GLOBAL_ACTION_RECENTS)) throw IllegalStateException("Recent Apps action failed")
            Thread.sleep(700)
            return
        }

        val root = rootInActiveWindow ?: throw IllegalStateException("No active window")
        val node = try {
            root.findAccessibilityNodeInfosByViewId(id).firstOrNull { it.isVisibleToUser }
        } catch (_: Exception) { null }
        if (node != null && clickNodeOrParent(node)) return

        // OEMs sometimes expose a different package prefix while retaining the
        // resource suffix. Use it as a fallback, but never use the old SystemUI
        // IDs as normal view IDs.
        val suffix = id.substringAfterLast(":id/").substringAfterLast("/")
        val fallback = findClickableByResourceSuffix(root, suffix)
        if (fallback != null && clickNodeOrParent(fallback)) return

        throw IllegalArgumentException("View/button not found or not clickable: $id")
    }

    private fun findClickableByResourceSuffix(root: AccessibilityNodeInfo, suffix: String): AccessibilityNodeInfo? {
        if (suffix.isBlank()) return null
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val id = node.viewIdResourceName.orEmpty()
            if (node.isVisibleToUser && id.substringAfterLast("/") == suffix) return node
            for (i in 0 until node.childCount) node.getChild(i)?.let(queue::addLast)
        }
        return null
    }

    private fun findNode(root: AccessibilityNodeInfo, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (predicate(node)) return node
            for (i in 0 until node.childCount) node.getChild(i)?.let(queue::addLast)
        }
        return null
    }

    private fun clickNodeOrParent(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        repeat(10) {
            if (current?.isVisibleToUser == true && current.isClickable) {
                return current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            current = current?.parent
        }
        return false
    }
}
