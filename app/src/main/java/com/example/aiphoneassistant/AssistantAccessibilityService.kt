package com.example.aiphoneassistant

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class AssistantAccessibilityService : AccessibilityService() {
    companion object {
        @Volatile var instance: AssistantAccessibilityService? = null
        @Volatile var training = false
        @Volatile var recordedSteps: MutableList<ActionStep> = mutableListOf()
    }
    private val handler = Handler(Looper.getMainLooper())
    override fun onServiceConnected() { super.onServiceConnected(); instance = this }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!training || event?.eventType != AccessibilityEvent.TYPE_VIEW_CLICKED) return
        val source = event.source ?: return
        val text = source.text?.toString()?.trim().orEmpty()
        val viewId = source.viewIdResourceName?.trim().orEmpty()
        when {
            viewId.isNotEmpty() -> recordedSteps.add(ActionStep(ActionType.CLICK_ID, viewId, 350))
            text.isNotEmpty() -> recordedSteps.add(ActionStep(ActionType.CLICK_TEXT, text, 350))
        }
    }
    override fun onInterrupt() {}
    override fun onDestroy() { instance = null; super.onDestroy() }
    fun startTraining() { recordedSteps = mutableListOf(); training = true }
    fun stopTraining(): List<ActionStep> { training = false; return recordedSteps.toList() }
    fun execute(skill: Skill, callback: (String) -> Unit) {
        Thread {
            try {
                skill.steps.forEachIndexed { index, step ->
                    executeStep(step)
                    handler.post { callback("Completed ${index + 1}/${skill.steps.size}: ${step.type}${if (step.value.isNotEmpty()) ": ${step.value}" else ""}") }
                    if (step.delayMs > 0) Thread.sleep(step.delayMs)
                }
                handler.post { callback("DONE") }
            } catch (e: Exception) { handler.post { callback("ERROR: ${e.message ?: "Unknown error"}") } }
        }.start()
    }
    private fun executeStep(step: ActionStep) {
        when (step.type) {
            ActionType.LAUNCH_APP -> {
                val intent = packageManager.getLaunchIntentForPackage(step.value) ?: throw IllegalArgumentException("App not found: ${step.value}")
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(intent)
            }
            ActionType.BACK -> performGlobalAction(GLOBAL_ACTION_BACK)
            ActionType.HOME -> performGlobalAction(GLOBAL_ACTION_HOME)
            ActionType.WAIT -> Thread.sleep(step.value.toLongOrNull() ?: 1000)
            ActionType.CLICK_TEXT -> {
                val root = rootInActiveWindow ?: throw IllegalStateException("No active window")
                val node = root.findAccessibilityNodeInfosByText(step.value).firstOrNull { it.isVisibleToUser } ?: throw IllegalArgumentException("Text not found: ${step.value}")
                if (!clickNodeOrParent(node)) throw IllegalStateException("Click failed")
            }
            ActionType.CLICK_ID -> {
                val root = rootInActiveWindow ?: throw IllegalStateException("No active window")
                val node = root.findAccessibilityNodeInfosByViewId(step.value).firstOrNull { it.isVisibleToUser } ?: throw IllegalArgumentException("View ID not found: ${step.value}")
                if (!clickNodeOrParent(node)) throw IllegalStateException("Click failed")
            }
        }
    }
    private fun clickNodeOrParent(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        repeat(5) { if (current?.isClickable == true) return current.performAction(AccessibilityNodeInfo.ACTION_CLICK); current = current?.parent }
        return false
    }
}
