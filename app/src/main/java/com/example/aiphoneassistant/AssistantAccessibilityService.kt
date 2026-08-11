package com.example.aiphoneassistant

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.ArrayDeque

class AssistantAccessibilityService : AccessibilityService() {
    companion object {
        @Volatile var instance: AssistantAccessibilityService? = null
        @Volatile var training = false
        @Volatile var recordedSteps: MutableList<ActionStep> = mutableListOf()
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var store: SkillStore

    override fun onServiceConnected() { super.onServiceConnected(); store=SkillStore(this); instance=this }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if(!training||event?.eventType!=AccessibilityEvent.TYPE_VIEW_CLICKED)return
        val source=event.source?:return
        val text=source.text?.toString()?.trim().orEmpty()
        val viewId=source.viewIdResourceName?.trim().orEmpty()
        val packageName=source.packageName?.toString().orEmpty()
        val step=when{
            packageName=="com.android.systemui"&&viewId.endsWith("/home")->ActionStep(ActionType.HOME,"",500)
            packageName=="com.android.systemui"&&viewId.endsWith("/recent_apps")->ActionStep(ActionType.RECENT_APPS,"",500)
            viewId.isNotEmpty()->ActionStep(ActionType.CLICK_ID,viewId,500)
            text.isNotEmpty()->ActionStep(ActionType.CLICK_TEXT,text,500)
            else->null
        }?:return
        recordedSteps.add(step)
        store.saveTrainingDraft(recordedSteps)
    }

    override fun onInterrupt() {}
    override fun onDestroy(){instance=null;super.onDestroy()}

    fun startTraining(){recordedSteps=mutableListOf();store.saveTrainingDraft(recordedSteps);training=true}
    fun stopTraining():List<ActionStep>{training=false;recordedSteps=store.loadTrainingDraft();return recordedSteps.toList()}
    fun clearTrainingDraft(){training=false;recordedSteps=mutableListOf();store.clearTrainingDraft()}

    fun execute(skill:Skill,callback:(String)->Unit){
        Thread{
            try{
                skill.steps.forEachIndexed{index,step->executeStep(step);handler.post{callback("Completed ${index+1}/${skill.steps.size}: ${step.type}${if(step.value.isNotEmpty())": ${step.value}" else ""}")};if(step.delayMs>0)Thread.sleep(step.delayMs)}
                handler.post{callback("DONE")}
            }catch(e:Exception){handler.post{callback("ERROR: ${e.message?:"Unknown error"}")}}
        }.start()
    }

    private fun executeStep(step:ActionStep){
        when(step.type){
            ActionType.LAUNCH_APP->{val intent=packageManager.getLaunchIntentForPackage(step.value)?:throw IllegalArgumentException("App not found: ${step.value}");intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(intent)}
            ActionType.BACK->performGlobalAction(GLOBAL_ACTION_BACK)
            ActionType.HOME->performGlobalAction(GLOBAL_ACTION_HOME)
            ActionType.RECENT_APPS->performGlobalAction(GLOBAL_ACTION_RECENTS)
            ActionType.WAIT->Thread.sleep(step.value.toLongOrNull()?:1000)
            ActionType.CLICK_TEXT->{val root=rootInActiveWindow?:throw IllegalStateException("No active window");val node=root.findAccessibilityNodeInfosByText(step.value).firstOrNull{it.isVisibleToUser}?:throw IllegalArgumentException("Text not found: ${step.value}");if(!clickNodeOrParent(node))throw IllegalStateException("Click failed")}
            ActionType.CLICK_ID->{
                if(step.value.endsWith("/home")&&step.value.startsWith("com.android.systemui:")){if(!performGlobalAction(GLOBAL_ACTION_HOME))throw IllegalStateException("Home action failed");return}
                if(step.value.endsWith("/recent_apps")&&step.value.startsWith("com.android.systemui:")){if(!performGlobalAction(GLOBAL_ACTION_RECENTS))throw IllegalStateException("Recent apps action failed");return}
                val root=rootInActiveWindow?:throw IllegalStateException("No active window")
                val node=try{root.findAccessibilityNodeInfosByViewId(step.value).firstOrNull{it.isVisibleToUser}}catch(_:Exception){null}
                if(node!=null&&clickNodeOrParent(node))return
                val suffix=step.value.substringAfterLast(":id/").substringAfterLast("/")
                val fallback=findClickableByResourceSuffix(root,suffix)
                if(fallback!=null&&clickNodeOrParent(fallback))return
                throw IllegalArgumentException("View ID not found or not clickable: ${step.value}")
            }
        }
    }

    private fun findClickableByResourceSuffix(root:AccessibilityNodeInfo,suffix:String):AccessibilityNodeInfo?{
        if(suffix.isBlank())return null
        val queue=ArrayDeque<AccessibilityNodeInfo>();queue.add(root)
        while(queue.isNotEmpty()){
            val node=queue.removeFirst();val id=node.viewIdResourceName.orEmpty()
            if(node.isVisibleToUser&&id.substringAfterLast("/")==suffix)return node
            for(i in 0 until node.childCount)node.getChild(i)?.let{queue.addLast(it)}
        }
        return null
    }

    private fun clickNodeOrParent(node:AccessibilityNodeInfo):Boolean{
        var current:AccessibilityNodeInfo?=node
        repeat(7){if(current?.isVisibleToUser==true&&current.isClickable)return current.performAction(AccessibilityNodeInfo.ACTION_CLICK);current=current?.parent}
        return false
    }
}
