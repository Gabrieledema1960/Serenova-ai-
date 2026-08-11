package com.example.aiphoneassistant

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.widget.*

class MainActivity : Activity() {
    private lateinit var store: SkillStore
    private lateinit var ownerGate: VoiceOwnerGate
    private val skills get() = store.load()
    private val nlp = NaturalLanguageEngine()
    private var voice: VoiceController? = null
    private var friendlyVoice: FriendlyVoice? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = SkillStore(this)
        ownerGate = VoiceOwnerGate(this)
        friendlyVoice = FriendlyVoice(this)
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 20)
        }
        home()
    }

    override fun onDestroy() {
        voice?.destroy()
        friendlyVoice?.destroy()
        super.onDestroy()
    }

    private fun root() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(28, 38, 28, 24)
        setBackgroundColor(Color.WHITE)
    }
    private fun title(t: String) = TextView(this).apply { text=t; textSize=27f; setTextColor(Color.rgb(25,25,25)); setPadding(0,0,0,18) }
    private fun info(t: String) = TextView(this).apply { text=t; textSize=15f; setPadding(0,6,0,12) }
    private fun button(t:String, a:()->Unit)=Button(this).apply { text=t; textSize=16f; setOnClickListener{a()}; layoutParams=LinearLayout.LayoutParams(-1,62).apply{setMargins(0,7,0,7)} }

    private fun home() {
        val r=root()
        r.addView(title("AI Phone Assistant"))
        r.addView(info("V5 • Voice conversation + friendly replies + persistent demonstration training"))
        r.addView(info(if (ownerGate.isEnrolled()) "🔐 Owner phrase: ENABLED" else "🔐 Owner phrase: NOT SET"))
        r.addView(button("🎙 TALK TO ME"){assistant()})
        r.addView(button("🔐 SET UP OWNER VOICE"){ownerVoiceSetup()})
        r.addView(button("🧠 TRAINING MODE"){training()})
        r.addView(button("🧪 TEST MODE"){testing()})
        r.addView(button("📱 INSTALLED APPS"){apps()})
        r.addView(button("⚙ ACCESSIBILITY"){startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))})
        setContentView(r)
    }

    private fun ownerVoiceSetup() {
        val r=root()
        r.addView(title("🔐 Owner Voice Setup"))
        r.addView(info("Speak a private phrase once. I will only process voice commands that contain your phrase. This is a phrase gate, not biometric speaker identification."))
        r.addView(button("🎙 SPEAK OWNER PHRASE") {
            friendlyVoice?.say("Okay, friend. Say your private phrase now.")
            startVoice { spoken ->
                try {
                    ownerGate.enroll(spoken)
                    say("Perfect. I saved your private voice phrase. From now on, start commands with it.")
                    home()
                } catch (e: IllegalArgumentException) { say(e.message ?: "That phrase is too short. Try another one.") }
            }
        })
        if (ownerGate.isEnrolled()) r.addView(button("REMOVE OWNER PHRASE"){ownerGate.clear();say("Done. I removed your owner phrase.");home()})
        r.addView(button("← Back"){home()})
        setContentView(r)
    }

    private fun assistant() {
        val r=root()
        r.addView(title("🎙 Talk To Me"))
        if (!ownerGate.isEnrolled()) {
            r.addView(info("I can talk back to you, but first set your private owner phrase. Typed commands are disabled."))
            r.addView(button("SET UP OWNER VOICE"){ownerVoiceSetup()})
        } else {
            r.addView(info("Talk naturally. Start with your owner phrase. I will answer out loud like a friendly assistant."))
            r.addView(button("🎙 LISTEN") { listenForConversation() })
            r.addView(button("🧠 VOICE TRAINING") { listenForTrainingControl() })
            r.addView(button("🔊 TEST MY VOICE") { say("Hey! I'm here. Tell me what you want me to do, and I'll do my best.") })
        }
        r.addView(button("← Back"){home()})
        setContentView(r)
    }

    private fun listenForConversation() {
        say("I'm listening. What's up?")
        startVoice { spoken ->
            if (!ownerGate.accepts(spoken)) {
                say("I didn't accept that voice command. Please start with your private phrase.")
                return@startVoice
            }
            val command = ownerGate.stripPhrase(spoken).trim()
            if (command.isBlank()) {
                say("I'm listening. Go ahead and tell me what you need.")
                return@startVoice
            }
            respondToFriend(command)
        }
    }

    private fun respondToFriend(command: String) {
        val lower = command.lowercase()
        when {
            lower.matches(Regex("(hi|hello|hey|good morning|good afternoon|good evening).*")) -> {
                say("Hey! Good to hear your voice. What should we do?")
            }
            lower.contains("how are you") -> say("I'm doing great and I'm ready to help. What are we working on?")
            lower.contains("thank") -> say("You're welcome, friend. I've got you.")
            lower.contains("who are you") -> say("I'm your phone assistant. You train me, test me, and then I can carry out approved actions for you.")
            lower.contains("what can you do") -> say("I can open installed apps, run skills you have approved, learn demonstrations, and talk with you by voice.")
            lower.contains("stop listening") || lower == "stop" -> say("Okay. I'll be quiet for now.")
            else -> handleCommand(command)
        }
    }

    private fun listenForTrainingControl() {
        say("Say start training, or say stop training if a recording is already running.")
        startVoice { spoken ->
            if (!ownerGate.accepts(spoken)) {
                say("I didn't accept that voice command. Start with your owner phrase.")
                return@startVoice
            }
            val command = ownerGate.stripPhrase(spoken).lowercase()
            when {
                command.contains("start training") || command.contains("begin training") -> {
                    val s=AssistantAccessibilityService.instance
                    if(s==null){say("I need Accessibility turned on before I can train. I'll open that setting for you.");startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))}
                    else {s.startTraining();say("Training is on. Go to the app you want to teach me and perform the clicks. Every supported click is saved immediately. Come back and press Stop and Review when you're finished.");stopTrainingScreen()}
                }
                command.contains("stop training") || command.contains("finish training") -> {
                    val steps=AssistantAccessibilityService.instance?.stopTraining() ?: store.loadTrainingDraft()
                    say("Okay, I stopped the recording. I saved ${steps.size} actions. Let's review them.")
                    reviewRecording(steps)
                }
                else -> say("I heard you, but I need you to say start training or stop training.")
            }
        }
    }

    private fun startVoice(result:(String)->Unit){
        voice?.destroy()
        voice=VoiceController(this,{runOnUiThread{result(it)}},{runOnUiThread{say(it)}})
        voice?.start()
    }

    private fun handleCommand(text:String){
        when(val c=nlp.parse(text,skills)){
            is ParsedCommand -> when(c.type){
                CommandType.OPEN_APP->{
                    val app=AppDiscovery.findApp(this,c.target)
                    if(app==null) say("I couldn't find ${c.target} on your phone.")
                    else packageManager.getLaunchIntentForPackage(app.packageName)?.let{
                        say("Sure. I'm opening ${app.label} for you.")
                        startActivity(it)
                    } ?: say("I found ${app.label}, but Android didn't give me a launch action.")
                }
                CommandType.RUN_SKILL->{
                    val s=c.skill!!
                    if(!s.approved) say("I found that skill, but you haven't approved it yet. Review it in Test Mode.")
                    else { say("Sure, friend. I'm running ${s.name} now."); execute(s) }
                }
                CommandType.UNKNOWN->say("I heard you, but I don't know how to do that yet. You can teach me in Training Mode.")
            }
        }
    }

    private fun training(){
        val r=root()
        r.addView(title("🧠 Training Mode"))
        r.addView(info("Teach me by demonstration. Every supported click is saved immediately, including Android Home and Recent Apps actions."))
        val draft=store.loadTrainingDraft()
        if(draft.isNotEmpty()) r.addView(button("♻ RECOVER ${draft.size} SAVED ACTIONS"){reviewRecording(draft)})
        r.addView(button("🎙 START BY VOICE"){assistant()})
        r.addView(button("▶ START DEMONSTRATION"){
            val s=AssistantAccessibilityService.instance
            if(s==null){say("Please turn on Accessibility first.");startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))}
            else{s.startTraining();say("Training started. Perform the actions you want me to learn. Every action is being saved.");stopTrainingScreen()}
        })
        skills.forEach{s->r.addView(info("• ${s.name} — ${s.steps.size} step(s) — approved=${s.approved}));r.addView(button("Delete ${s.name}"){store.save(skills.filter{it.id!=s.id});training()})}
        r.addView(button("← Back"){home()})
        setContentView(r)
    }

    private fun stopTrainingScreen(){
        val r=root()
        r.addView(title("🧠 Recording"))
        r.addView(info("Perform your actions outside this screen. Every supported click is saved immediately. Return here when finished."))
        r.addView(button("⏹ STOP & REVIEW"){reviewRecording(AssistantAccessibilityService.instance?.stopTraining() ?: store.loadTrainingDraft())})
        r.addView(button("✖ CANCEL"){AssistantAccessibilityService.instance?.clearTrainingDraft();home()})
        setContentView(r)
    }

    private fun reviewRecording(steps:List<ActionStep>){
        val r=root();r.addView(title("Review Training"));r.addView(info("Recorded ${steps.size} supported action(s). Home and Recent Apps are replayed as Android global actions, so the old SystemUI View ID error is fixed."))
        steps.forEachIndexed{i,s->r.addView(info("${i+1}. ${describe(s)}"))}
        val name=EditText(this).apply{hint="Skill name"};r.addView(name)
        r.addView(button("SAVE SKILL"){
            if(name.text.toString().trim().isEmpty()||steps.isEmpty()) say("Please give the skill a name and record at least one action.")
            else { val l=skills; l.add(Skill(System.currentTimeMillis(),name.text.toString().trim(),steps.toMutableList(),false)); store.save(l);store.clearTrainingDraft();say("Saved permanently. Review and approve it in Test Mode when you're ready.");home() }
        })
        r.addView(button("RECORD AGAIN"){training()})
        r.addView(button("CANCEL"){store.clearTrainingDraft();home()})
        setContentView(r)
    }

    private fun testing(){val r=root();r.addView(title("🧪 Test Mode"));r.addView(info("Review, preview, test and explicitly approve skills."));if(skills.isEmpty())r.addView(info("No trained skills."));skills.forEach{s->r.addView(button("Review: ${s.name}"){reviewSkill(s)})};r.addView(button("← Back"){home()});setContentView(r)}
    private fun reviewSkill(s:Skill){val r=root();r.addView(title("Review: ${s.name}"));r.addView(info("Approved: ${s.approved}"));s.steps.forEachIndexed{i,a->r.addView(info("${i+1}. ${describe(a)}"))};r.addView(button("👁 PREVIEW ONLY"){say("Preview complete. I didn't execute anything.")});r.addView(button("▶ RUN TEST"){say("Okay, I'm testing the skill now.");execute(s)});r.addView(button(if(s.approved)"✓ APPROVED" else "✓ APPROVE SKILL"){val l=skills;val i=l.indexOfFirst{it.id==s.id};if(i>=0){l[i].approved=true;store.save(l);say("Approved. I can run this skill by voice now.");reviewSkill(l[i])}});r.addView(button("← Back"){testing()});setContentView(r)}
    private fun execute(s:Skill){val service=AssistantAccessibilityService.instance;if(service==null){say("I need Accessibility turned on before I can control your phone.");startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));return};service.execute(s){m->if(m=="DONE")say("All done. That worked.") else if(m.startsWith("ERROR"))say("I got stuck: ${m.removePrefix("ERROR: ").replace("View ID not found or not clickable:","I couldn't find the saved button:")}")}}
    private fun apps(){val r=root();r.addView(title("📱 Installed Apps"));val list=AppDiscovery.installedLaunchableApps(this);r.addView(info("${list.size} launchable apps found."));list.forEach{a->r.addView(info("${a.label}\n${a.packageName}"));r.addView(button("OPEN"){say("Opening ${a.label}.");packageManager.getLaunchIntentForPackage(a.packageName)?.let{startActivity(it)}})};r.addView(button("← Back"){home()});setContentView(r)}
    private fun describe(s:ActionStep)="${s.type}${if(s.value.isNotEmpty())": ${s.value}" else ""}"
    private fun say(message:String){friendlyVoice?.say(message);Toast.makeText(this,message,Toast.LENGTH_SHORT).show()}
}
