package com.example.aiphoneassistant

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.Settings
import android.view.Gravity
import android.view.animation.Animation
import android.view.animation.RotateAnimation
import android.widget.*

class MainActivity : Activity() {
    private lateinit var store: SkillStore
    private val skills get() = store.load()
    private val nlp = NaturalLanguageEngine()
    private var voice: VoiceController? = null
    private var friendlyVoice: FriendlyVoice? = null
    private var orb: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = SkillStore(this)
        friendlyVoice = FriendlyVoice(this)
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_CONTACTS)
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(permissions.toTypedArray(), 21)
        }
        home()
    }

    override fun onDestroy() { voice?.destroy(); friendlyVoice?.destroy(); super.onDestroy() }

    private fun root() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(24,24,24,20); setBackgroundColor(Color.rgb(5,8,18)) }
    private fun title(t:String)=TextView(this).apply{text=t;textSize=26f;gravity=Gravity.CENTER;setTextColor(Color.WHITE);setPadding(0,8,0,16)}
    private fun info(t:String)=TextView(this).apply{text=t;textSize=15f;gravity=Gravity.CENTER;setTextColor(Color.LTGRAY);setPadding(0,6,0,12)}
    private fun button(t:String,a:()->Unit)=Button(this).apply{text=t;textSize=15f;setOnClickListener{a()};layoutParams=LinearLayout.LayoutParams(-1,58).apply{setMargins(0,5,0,5)}}

    private fun home(){
        val r=root(); r.addView(title("JARVIS")); r.addView(info("Your voice-controlled phone assistant")); addAnimatedOrb(r)
        r.addView(info("Say: Jarvis, open WhatsApp • Jarvis, call Mom • Jarvis, open YouTube"))
        r.addView(button("🎙 TALK TO JARVIS"){assistant()}); r.addView(button("🧠 TRAINING MODE"){training()}); r.addView(button("🧪 TEST MODE"){testing()})
        r.addView(button("📱 INSTALLED APPS"){apps()}); r.addView(button("⚙ ACCESSIBILITY"){startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))})
        setContentView(r)
    }

    private fun addAnimatedOrb(parent:LinearLayout){
        orb=TextView(this).apply{text="◉";textSize=86f;gravity=Gravity.CENTER;setTextColor(Color.CYAN);layoutParams=LinearLayout.LayoutParams(-1,150)}
        parent.addView(orb)
    }
    private fun animateListening(on:Boolean){
        orb?.clearAnimation(); if(!on)return
        val rotate=RotateAnimation(0f,360f,Animation.RELATIVE_TO_SELF,.5f,Animation.RELATIVE_TO_SELF,.5f).apply{duration=1400;repeatCount=Animation.INFINITE}
        orb?.startAnimation(rotate)
    }

    private fun assistant(){
        val r=root();r.addView(title("JARVIS"));addAnimatedOrb(r);r.addView(info("Say 'Jarvis' followed by what you want. The interface moves while I listen."))
        r.addView(button("🎙 LISTEN FOR JARVIS"){listenForConversation()});r.addView(button("🧠 VOICE TRAINING"){listenForTrainingControl()});r.addView(button("🔊 TEST MY VOICE"){say("Hey! I'm Jarvis. I'm listening.")});r.addView(button("← Back"){home()});setContentView(r)
    }

    private fun listenForConversation(){
        animateListening(true);say("I'm listening.")
        startVoice { spoken ->
            animateListening(false)
            val command=stripJarvis(spoken)
            if(command==null){say("Please say Jarvis first.");return@startVoice}
            if(command.isBlank()) say("Yes? What can I do for you?") else respondToFriend(command)
        }
    }

    private fun stripJarvis(spoken:String):String? {
        val normalized=spoken.trim().replace(Regex("\\s+")," ")
        val match=Regex("(?i)\\bjarvis\\b(?:[,:.!-]\\s*)?(.*)").find(normalized) ?: return null
        return match.groupValues.getOrNull(1)?.trim() ?: ""
    }

    private fun respondToFriend(command:String){
        val lower=command.lowercase()
        when{
            lower.matches(Regex("(hi|hello|hey|good morning|good afternoon|good evening).*"))->say("Hey! Good to hear your voice. What should we do?")
            lower.contains("how are you")->say("I'm doing great. I'm right here with you. What do you need?")
            lower.contains("thank")->say("You're welcome, friend.")
            lower.contains("who are you")->say("I'm Jarvis, your phone assistant. I can open apps, call people, and run the skills you teach me.")
            lower.contains("what can you do")->say("I can open your installed apps, call saved contacts, learn demonstrations, run approved skills, and talk with you.")
            lower.contains("stop listening")||lower=="stop"->say("Okay. I'll be quiet.")
            else->handleCommand(command)
        }
    }

    private fun handleCommand(text:String){
        val lower=text.lowercase()
        if(lower.matches(Regex("(call|phone|ring)\\s+.+"))){callPerson(text);return}
        when(val c=nlp.parse(text,skills)){
            is ParsedCommand->when(c.type){
                CommandType.OPEN_APP->{val app=AppDiscovery.findApp(this,c.target);if(app==null)say("I couldn't find ${c.target} installed.") else packageManager.getLaunchIntentForPackage(app.packageName)?.let{say("Sure. I'm opening ${app.label}.");startActivity(it)}?:say("I found ${app.label}, but Android couldn't launch it.")}
                CommandType.RUN_SKILL->{val s=c.skill!!;if(!s.approved)say("That skill isn't approved yet.") else{say("Sure, friend. I'm running ${s.name}.");execute(s)}}
                CommandType.UNKNOWN->say("I heard you, but I don't know that action yet. You can teach it to me in Training Mode.")
            }
        }
    }

    private fun callPerson(text:String){
        if(checkSelfPermission(Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED){say("I need Contacts permission to find that person.");requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS),22);return}
        val raw=text.lowercase().replace(Regex("^(call|phone|ring)\\s+"),"").trim()
        val aliases=when(raw){
            "mom","my mom","mother","mummy","mama"->listOf("mom","mother","mummy","mama")
            "dad","my dad","father","daddy","papa"->listOf("dad","father","daddy","papa")
            else->listOf(raw)
        }
        val projection=arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,ContactsContract.CommonDataKinds.Phone.NUMBER)
        var name:String?=null;var number:String?=null
        contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,projection,null,null,ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME+" ASC")?.use{c->
            while(c.moveToNext()){
                val n=c.getString(0);val low=n.lowercase()
                if(aliases.any{a->low==a||low.contains(a)}){name=n;number=c.getString(1);break}
            }
        }
        if(number==null){say("I couldn't find $raw in your contacts.");return}
        say("Calling ${name}.")
        startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(number)}")))
    }

    private fun listenForTrainingControl(){
        say("Say Jarvis, start training, or Jarvis, stop training.")
        startVoice { spoken ->
            val c=stripJarvis(spoken)
            if(c==null){say("Please say Jarvis first.");return@startVoice}
            when{
                c.lowercase().contains("start training")->startTrainingFromVoice()
                c.lowercase().contains("stop training")||c.lowercase().contains("finish training")->stopTrainingFromVoice()
                else->say("Say Jarvis, start training, or Jarvis, stop training.")
            }
        }
    }

    private fun startTrainingFromVoice(){
        val s=AssistantAccessibilityService.instance
        if(s==null){say("Turn on Accessibility first. I'll open the setting.");startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));return}
        s.startTraining();say("Training is on. Demonstrate the actions you want me to learn. Every supported action is saved immediately.");stopTrainingScreen()
    }
    private fun stopTrainingFromVoice(){val steps=AssistantAccessibilityService.instance?.stopTraining()?:store.loadTrainingDraft();say("I stopped training and saved ${steps.size} actions.");reviewRecording(steps)}
    private fun startVoice(result:(String)->Unit){voice?.destroy();voice=VoiceController(this,{runOnUiThread{result(it)}},{runOnUiThread{animateListening(false);say(it)}});voice?.start()}

    private fun training(){
        val r=root();r.addView(title("🧠 Training Mode"));addAnimatedOrb(r);r.addView(info("Teach Jarvis by demonstration. Every supported action is saved immediately."));val draft=store.loadTrainingDraft()
        if(draft.isNotEmpty())r.addView(button("♻ RECOVER ${draft.size} SAVED ACTIONS"){reviewRecording(draft)})
        r.addView(button("🎙 START BY VOICE"){assistant()})
        r.addView(button("▶ START DEMONSTRATION"){val s=AssistantAccessibilityService.instance;if(s==null){say("Please turn on Accessibility first.");startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))}else{s.startTraining();say("Training started. Perform the actions you want Jarvis to learn.");stopTrainingScreen()}})
        skills.forEach{s->r.addView(info("• ${s.name} — ${s.steps.size} step(s) — approved=${s.approved}"));r.addView(button("Delete ${s.name}"){store.save(skills.filter{it.id!=s.id});training()})}
        r.addView(button("← Back"){home()});setContentView(r)
    }

    private fun stopTrainingScreen(){
        val r=root();r.addView(title("🧠 Recording"));addAnimatedOrb(r);r.addView(info("Perform actions outside this screen. Return when finished."));r.addView(button("⏹ STOP & REVIEW"){reviewRecording(AssistantAccessibilityService.instance?.stopTraining()?:store.loadTrainingDraft())});r.addView(button("✖ CANCEL"){AssistantAccessibilityService.instance?.clearTrainingDraft();home()});setContentView(r)
    }
    private fun reviewRecording(steps:List<ActionStep>){
        val r=root();r.addView(title("Review Training"));r.addView(info("Recorded ${steps.size} supported action(s)."));steps.forEachIndexed{i,s->r.addView(info("${i+1}. ${describe(s)}"))};val name=EditText(this).apply{hint="Skill name"};r.addView(name)
        r.addView(button("SAVE SKILL"){if(name.text.toString().trim().isEmpty()||steps.isEmpty())say("Give the skill a name and record at least one action.")else{val l=skills;l.add(Skill(System.currentTimeMillis(),name.text.toString().trim(),steps.toMutableList(),false));store.save(l);store.clearTrainingDraft();say("Saved permanently. Approve it in Test Mode.");home()}})
        r.addView(button("RECORD AGAIN"){training()});r.addView(button("CANCEL"){store.clearTrainingDraft();home()});setContentView(r)
    }
    private fun testing(){
        val r=root();r.addView(title("🧪 Test Mode"));r.addView(info("Review, test and approve trained skills."));if(skills.isEmpty())r.addView(info("No trained skills."));skills.forEach{s->r.addView(button("Review: ${s.name}"){reviewSkill(s)})};r.addView(button("← Back"){home()});setContentView(r)
    }
    private fun reviewSkill(s:Skill){
        val r=root();r.addView(title("Review: ${s.name}"));r.addView(info("Approved: ${s.approved}"));s.steps.forEachIndexed{i,a->r.addView(info("${i+1}. ${describe(a)}"))};r.addView(button("👁 PREVIEW ONLY"){say("Preview complete. I didn't execute anything.")});r.addView(button("▶ RUN TEST"){say("Testing it now.");execute(s)});r.addView(button(if(s.approved)"✓ APPROVED" else "✓ APPROVE SKILL"){val l=skills;val i=l.indexOfFirst{it.id==s.id};if(i>=0){l[i].approved=true;store.save(l);say("Approved. I can run it by voice now.");reviewSkill(l[i])}});r.addView(button("← Back"){testing()});setContentView(r)
    }
    private fun execute(s:Skill){val service=AssistantAccessibilityService.instance;if(service==null){say("I need Accessibility turned on before I can control your phone.");startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));return};service.execute(s){m->if(m=="DONE")say("All done. That worked.") else if(m.startsWith("ERROR"))say("I got stuck: ${m.removePrefix("ERROR: ")}")}}
    private fun apps(){val r=root();r.addView(title("📱 Installed Apps"));val list=AppDiscovery.installedLaunchableApps(this);r.addView(info("${list.size} launchable apps found."));list.forEach{a->r.addView(info("${a.label}\n${a.packageName}"));r.addView(button("OPEN"){say("Opening ${a.label}.");packageManager.getLaunchIntentForPackage(a.packageName)?.let{startActivity(it)}})};r.addView(button("← Back"){home()});setContentView(r)}
    private fun describe(s:ActionStep)="${s.type}${if(s.value.isNotEmpty())": ${s.value}" else ""}"
    private fun say(message:String){friendlyVoice?.say(message);Toast.makeText(this,message,Toast.LENGTH_SHORT).show()}
}
