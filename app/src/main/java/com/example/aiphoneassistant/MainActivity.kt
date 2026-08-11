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
    private val skills get() = store.load()
    private val nlp = NaturalLanguageEngine()
    private var voice: VoiceController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); store = SkillStore(this)
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 20)
        home()
    }
    override fun onDestroy() { voice?.destroy(); super.onDestroy() }
    private fun root() = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(28,38,28,24); setBackgroundColor(Color.WHITE) }
    private fun title(t:String)=TextView(this).apply { text=t; textSize=27f; setTextColor(Color.rgb(25,25,25)); setPadding(0,0,0,18) }
    private fun info(t:String)=TextView(this).apply { text=t; textSize=15f; setPadding(0,6,0,12) }
    private fun button(t:String, a:()->Unit)=Button(this).apply { text=t; textSize=16f; setOnClickListener{a()}; layoutParams=LinearLayout.LayoutParams(-1,62).apply{setMargins(0,7,0,7)} }
    private fun home(){ val r=root(); r.addView(title("AI Phone Assistant")); r.addView(info("V3 • Voice + AI commands + app discovery + training by demonstration")); r.addView(button("🎙 VOICE ASSISTANT"){assistant()}); r.addView(button("🧠 TRAINING MODE"){training()}); r.addView(button("🧪 TEST MODE"){testing()}); r.addView(button("📱 INSTALLED APPS"){apps()}); r.addView(button("⚙ ACCESSIBILITY"){startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))}); setContentView(r) }
    private fun assistant(){ val r=root(); r.addView(title("🎙 Voice Assistant")); r.addView(info("Try: Open YouTube, or say the exact name of a trained skill.")); val input=EditText(this).apply{hint="Type a command"}; r.addView(input); r.addView(button("🎙 LISTEN"){startVoice{input.setText(it);handleCommand(it)}}); r.addView(button("▶ RUN COMMAND"){handleCommand(input.text.toString())}); r.addView(button("← Back"){home()}); setContentView(r) }
    private fun startVoice(result:(String)->Unit){ voice?.destroy(); voice=VoiceController(this,{runOnUiThread{result(it)}},{runOnUiThread{toast(it)}}); voice?.start() }
    private fun handleCommand(text:String){ when(val c=nlp.parse(text,skills)){ is ParsedCommand -> when(c.type){ CommandType.OPEN_APP->{val app=AppDiscovery.findApp(this,c.target); if(app==null)toast("App not found: ${c.target}") else packageManager.getLaunchIntentForPackage(app.packageName)?.let{startActivity(it)}}; CommandType.RUN_SKILL->{val s=c.skill!!; if(!s.approved)toast("Skill is not approved. Review it in Test Mode.") else execute(s)}; CommandType.UNKNOWN->toast("I don't understand that command yet.") } } }
    private fun training(){ val r=root(); r.addView(title("🧠 Training Mode")); r.addView(info("Explicitly start a demonstration, perform actions, then stop and review.")); r.addView(button("▶ START DEMONSTRATION"){val s=AssistantAccessibilityService.instance;if(s==null){toast("Enable Accessibility first.");startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))}else{s.startTraining();toast("Training started. Perform your actions, then return here.");stopTrainingScreen()}}); skills.forEach{s->r.addView(info("• ${s.name} — ${s.steps.size} step(s) — approved=${s.approved}"));r.addView(button("Delete ${s.name}"){store.save(skills.filter{it.id!=s.id});training()})};r.addView(button("← Back"){home()});setContentView(r) }
    private fun stopTrainingScreen(){val r=root();r.addView(title("🧠 Recording"));r.addView(info("Perform your actions outside this screen. Return here when finished."));r.addView(button("⏹ STOP & REVIEW"){reviewRecording(AssistantAccessibilityService.instance?.stopTraining().orEmpty())});r.addView(button("✖ CANCEL"){AssistantAccessibilityService.training=false;home()});setContentView(r)}
    private fun reviewRecording(steps:List<ActionStep>){val r=root();r.addView(title("Review Training"));r.addView(info("Recorded ${steps.size} supported click action(s)."));steps.forEachIndexed{i,s->r.addView(info("${i+1}. ${describe(s)}"))};val name=EditText(this).apply{hint="Skill name"};r.addView(name);r.addView(button("SAVE SKILL"){if(name.text.toString().trim().isEmpty()||steps.isEmpty()){toast("Name the skill and record at least one action.")}else{val l=skills;l.add(Skill(System.currentTimeMillis(),name.text.toString().trim(),steps.toMutableList(),false));store.save(l);toast("Saved. Review and approve it in Test Mode.");home()}});r.addView(button("RECORD AGAIN"){training()});r.addView(button("CANCEL"){home()});setContentView(r)}
    private fun testing(){val r=root();r.addView(title("🧪 Test Mode"));r.addView(info("Review, preview, test and explicitly approve skills."));if(skills.isEmpty())r.addView(info("No trained skills."));skills.forEach{s->r.addView(button("Review: ${s.name}"){reviewSkill(s)})};r.addView(button("← Back"){home()});setContentView(r)}
    private fun reviewSkill(s:Skill){val r=root();r.addView(title("Review: ${s.name}"));r.addView(info("Approved: ${s.approved}"));s.steps.forEachIndexed{i,a->r.addView(info("${i+1}. ${describe(a)}"))};r.addView(button("👁 PREVIEW ONLY"){toast("Preview complete. Nothing executed.")});r.addView(button("▶ RUN TEST"){execute(s)});r.addView(button(if(s.approved)"✓ APPROVED" else "✓ APPROVE SKILL"){val l=skills;val i=l.indexOfFirst{it.id==s.id};if(i>=0){l[i].approved=true;store.save(l);toast("Skill approved.");reviewSkill(l[i])}});r.addView(button("← Back"){testing()});setContentView(r)}
    private fun execute(s:Skill){val service=AssistantAccessibilityService.instance;if(service==null){toast("Enable Accessibility first.");startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));return};service.execute(s){m->if(m=="DONE")toast("Completed.") else if(m.startsWith("ERROR"))toast(m)}}
    private fun apps(){val r=root();r.addView(title("📱 Installed Apps"));val list=AppDiscovery.installedLaunchableApps(this);r.addView(info("${list.size} launchable apps found."));list.forEach{a->r.addView(info("${a.label}\n${a.packageName}"));r.addView(button("OPEN"){packageManager.getLaunchIntentForPackage(a.packageName)?.let{startActivity(it)}})};r.addView(button("← Back"){home()});setContentView(r)}
    private fun describe(s:ActionStep)="${s.type}${if(s.value.isNotEmpty())": ${s.value}" else ""}"
    private fun toast(m:String)=runOnUiThread{Toast.makeText(this,m,Toast.LENGTH_SHORT).show()}
}
