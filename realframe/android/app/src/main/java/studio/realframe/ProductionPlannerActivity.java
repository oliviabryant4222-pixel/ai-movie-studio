package studio.realframe;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Device-local deterministic production planner for multiple AI video clips.
 * This produces a portable storyboard, prompt packs and continuity graph.
 * It deliberately does not pretend to contain local AI model inference.
 */
public final class ProductionPlannerActivity extends Activity {
    private static final int SAVE_PROJECT = 7005;
    private static final int BG=0xFF0C1423, CARD=0xFF1A2940, TEXT=0xFFEFF4FF,
        DIM=0xFFA7B9D3, PURPLE=0xFFB4A2FF, CYAN=0xFF68E2D0;
    private LinearLayout main, panel;
    private EditText subject, scene, seconds, clipSeconds, seedText;
    private Spinner character;
    private TextView progress, preview;
    private final List<JSONObject> roster=new ArrayList<>();
    private JSONArray plan=new JSONArray();
    private int cursor=0;
    private String projectJson="";
    private SharedPreferences saved;
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private GradientDrawable shape(int color){
        GradientDrawable d=new GradientDrawable();
        d.setColor(color);d.setCornerRadius(dp(16));return d;
    }
    private TextView label(String text,int size,int color,boolean bold){
        TextView t=new TextView(this);
        t.setText(text);t.setTextSize(size);t.setTextColor(color);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return t;
    }
    private void put(LinearLayout parent,View item){parent.addView(item,new LinearLayout.LayoutParams(-1,-2));}
    private void gap(LinearLayout parent,int size){
        View space=new View(this);parent.addView(space,new LinearLayout.LayoutParams(1,dp(size)));
    }
    private EditText field(LinearLayout parent,String title,String hint,String value){
        TextView cap=label(title,13,DIM,true);
        cap.setPadding(0,dp(11),0,dp(5));put(parent,cap);
        EditText input=new EditText(this);
        input.setSingleLine(title.contains("seconds")||title.contains("Seed"));
        input.setText(value);input.setHint(hint);
        input.setTextColor(TEXT);input.setHintTextColor(DIM);
        input.setPadding(dp(12),dp(8),dp(12),dp(8));
        input.setBackground(shape(0xFF263954));
        put(parent,input);
        return input;
    }
    private Button action(LinearLayout parent,String title,Runnable fn){
        Button button=new Button(this);button.setText(title);button.setAllCaps(false);
        button.setTextSize(14);button.setTextColor(TEXT);
        button.setBackground(shape(0xFF443D73));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(46));
        lp.topMargin=dp(8);parent.addView(button,lp);
        button.setOnClickListener(v->{try{fn.run();}catch(Exception e){toast("Failed: "+e.getMessage());}});
        return button;
    }
    private void toast(String text){Toast.makeText(this,text,Toast.LENGTH_LONG).show();}
    private static int parse(String s,int min,int max,String label){
        int n;
        try{n=Integer.parseInt(s.trim());}catch(Exception e){throw new IllegalArgumentException(label+" must be a number");}
        if(n<min||n>max)throw new IllegalArgumentException(label+" must be "+min+" to "+max);
        return n;
    }
    private static long parseSeed(String s){
        try{
            long seed=Long.parseLong(s.trim());
            if(seed<1||seed>2147483647L)throw new Exception();
            return seed;
        }catch(Exception e){throw new IllegalArgumentException("Seed must be from 1 to 2,147,483,647");}
    }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        saved=getSharedPreferences("production_planner",MODE_PRIVATE);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        main=new LinearLayout(this);main.setOrientation(LinearLayout.VERTICAL);
        main.setBackgroundColor(BG);main.setPadding(dp(16),dp(15),dp(16),dp(12));
        setContentView(main);
        TextView title=label("←  Long-Video Production",23,TEXT,true);
        title.setOnClickListener(v->finish());put(main,title);gap(main,7);
        put(main,label("Consistency recipe  •  short clips  •  exportable scene plan",12,DIM,false));
        gap(main,14);
        ScrollView scroll=new ScrollView(this);main.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(14),dp(13),dp(14),dp(14));panel.setBackground(shape(CARD));
        scroll.addView(panel);

        TextView planHeader=label("1. Your fictional adult character",18,PURPLE,true);put(panel,planHeader);
        roster.clear();roster.add(new JSONObject());
        roster.addAll(new StudioStore(this).characters());
        String[] names=new String[roster.size()];
        names[0]="Custom character description";
        for(int i=1;i<names.length;i++)names[i]=roster.get(i).optString("name","Character "+i);
        character=new Spinner(this);
        ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names);
        character.setAdapter(adapter);character.setBackground(shape(0xFFCAD5E5));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(47));
        lp.topMargin=dp(9);panel.addView(character,lp);
        subject=field(panel,"Character appearance (adult)","Describe their stable facial and body features",
            saved.getString("subject","25-year-old fictional adult, natural skin texture, realistic proportions"));
        scene=field(panel,"Scene theme","A continuous story and location",
            saved.getString("scene","A relaxed natural-light portrait video in a realistic apartment"));
        gap(panel,15);
        put(panel,label("2. Scene duration",18,PURPLE,true));
        seconds=field(panel,"Total seconds (5–3600)","Example: 120",saved.getString("seconds","60"));
        clipSeconds=field(panel,"Clip seconds (2–10)","Example: 5",saved.getString("clipSeconds","5"));
        seedText=field(panel,"Seed (1–2147483647)","Example: 13901",saved.getString("seed","13901"));
        gap(panel,12);
        action(panel,"✦  Build storyboard (offline)",this::build);
        action(panel,"💾  Save manifest as JSON",this::saveProject);
        action(panel,"📋  Copy ALL scene prompts",this::copyAll);
        action(panel,"◈  Generate local character reference images",()->
            startActivity(new Intent(this,ReferenceStudioActivity.class)));
        action(panel,"🎬  Turn storyboard images into offline MP4",()->
            startActivity(new Intent(this,StoryMovieComposerActivity.class)));
        action(panel,"▶  Open AI video generator",()->{
            Intent intent=new Intent(this,WebStudioActivity.class);intent.putExtra("kind","video");
            startActivity(intent);
        });
        gap(panel,15);
        put(panel,label("3. Scene preview",18,PURPLE,true));
        progress=label("",13,CYAN,true);gap(panel,8);put(panel,progress);
        preview=label("Create a storyboard to see its continuity plan.",13,TEXT,false);
        preview.setPadding(dp(9),dp(10),dp(9),dp(10));
        preview.setBackground(shape(0xFF243550));put(panel,preview);
        LinearLayout nav=new LinearLayout(this);nav.setOrientation(LinearLayout.HORIZONTAL);
        gap(panel,7);put(panel,nav);
        Button previous=new Button(this);previous.setText("◀ Previous");previous.setAllCaps(false);
        Button next=new Button(this);next.setText("Next ▶");next.setAllCaps(false);
        previous.setOnClickListener(v->{if(cursor>0){cursor--;refresh();}});
        next.setOnClickListener(v->{if(cursor<plan.length()-1){cursor++;refresh();}});
        previous.setTextColor(TEXT);next.setTextColor(TEXT);
        previous.setBackground(shape(0xFF34425B));next.setBackground(shape(0xFF34425B));
        LinearLayout.LayoutParams half=new LinearLayout.LayoutParams(0,dp(44),1);
        half.rightMargin=dp(6);nav.addView(previous,half);
        nav.addView(next,new LinearLayout.LayoutParams(0,dp(44),1));
        action(panel,"📋  Copy CURRENT scene prompt",this::copyCurrent);
        gap(panel,7);
        put(panel,label("Planning is local and free. Actual AI clip generation still requires an available online model, or a separately integrated native model engine. Frame chaining is a reference instruction, not guaranteed face identity.",12,DIM,false));
        String previousPlan=saved.getString("plan","");
        if(!previousPlan.isEmpty()){
            try{JSONObject obj=new JSONObject(previousPlan);
                plan=obj.getJSONArray("scenes");projectJson=obj.toString(2);refresh();
            }catch(Exception ignored){saved.edit().remove("plan").apply();}
        }
    }
    private String selectedAppearance(){
        JSONObject chosen=roster.get(character.getSelectedItemPosition());
        String app=chosen.optString("appearance","");
        if(!app.isEmpty())return app;
        return subject.getText().toString().trim();
    }
    private void build(){
        try {
        String identity=selectedAppearance();
        if(identity.isEmpty())throw new IllegalArgumentException("Add a character description");
        String setting=scene.getText().toString().trim();
        if(setting.isEmpty())throw new IllegalArgumentException("Add a scene theme");
        int duration=parse(seconds.getText().toString(),5,3600,"Total seconds");
        int perClip=parse(clipSeconds.getText().toString(),2,10,"Clip seconds");
        long baseSeed=parseSeed(seedText.getText().toString());
        int number=(duration+perClip-1)/perClip;
        if(number>1000)throw new IllegalArgumentException("Use longer clips to keep scene count <= 1000");
        JSONArray scenes=new JSONArray();
        JSONObject chosen=roster.get(character.getSelectedItemPosition());
        String wardrobe=chosen.optString("wardrobe","");
        int time=0;
        for(int i=0;i<number;i++){
            int clipTime=Math.min(perClip,duration-time);
            String id=String.format(Locale.US,"clip_%04d",i+1);
            String prior=i==0?"":String.format(Locale.US,"clip_%04d",i);
            long seed=1+(baseSeed+i*104729L)%2147483647L;
            String instruction="Photorealistic video of one fictional adult character. " +
                "Identity reference: "+identity+". "+
                (wardrobe.isEmpty()?"":"Wardrobe reference: "+wardrobe+". ")+
                "Setting: "+setting+". Scene "+(i+1)+" of "+number+
                ". Keep consistent face, proportions, hair and outfit. " +
                (i==0?"Establishing shot; natural subtle motion.":
                "Continue from final frame of "+prior+" as the start reference; maintain position, lighting and costume.")+
                " Natural physics, steady camera, authentic skin detail, no artificial beauty glow.";
            JSONObject one=new JSONObject()
                .put("id",id).put("order",i+1)
                .put("durationSeconds",clipTime)
                .put("seed",seed).put("prompt",instruction)
                .put("dependsOn",prior)
                .put("startFrameReference",prior.isEmpty()?"master_character_reference":"last_frame_of_"+prior)
                .put("targetOutput",id+".mp4")
                .put("status","planned");
            scenes.put(one);time+=clipTime;
        }
        JSONObject project=new JSONObject()
            .put("format","realframe-scene-manifest-v2")
            .put("createdAt",System.currentTimeMillis())
            .put("characterId",chosen.optString("id"))
            .put("characterName",chosen.optString("name","Custom fictional adult"))
            .put("identityPrompt",identity).put("wardrobePrompt",wardrobe)
            .put("setting",setting).put("durationSeconds",duration)
            .put("clipSeconds",perClip).put("targetOutput","final_film.mp4")
            .put("generationStatus","plan_only_no_ai_backend")
            .put("scenes",scenes);
        plan=scenes;projectJson=project.toString(2);
        saved.edit().putString("subject",subject.getText().toString())
            .putString("scene",setting)
            .putString("seconds",seconds.getText().toString())
            .putString("clipSeconds",clipSeconds.getText().toString())
            .putString("seed",seedText.getText().toString())
            .putString("plan",projectJson).apply();
        cursor=0;refresh();toast(number+" scenes planned; prompts ready.");
        } catch (org.json.JSONException exception) {
            throw new IllegalStateException("Could not create storyboard JSON", exception);
        }
    }
    private JSONObject current(){
        JSONObject scene=plan.optJSONObject(cursor);
        if(scene==null)throw new IllegalArgumentException("Build a storyboard first");
        return scene;
    }
    private void refresh(){
        if(plan.length()==0){progress.setText("No scenes");return;}
        JSONObject scene=current();
        progress.setText("Scene "+(cursor+1)+" / "+plan.length()+
            "  •  "+scene.optInt("durationSeconds")+" seconds • Seed "+scene.optLong("seed"));
        preview.setText(scene.optString("prompt")+"\n\nDepends on: "+
            (scene.optString("dependsOn").isEmpty()?"Master character":scene.optString("dependsOn"))+
            "\nReference: "+scene.optString("startFrameReference"));
    }
    private void clipboard(String value){
        ClipboardManager clip=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
        clip.setPrimaryClip(ClipData.newPlainText("RealFrame storyboard prompt",value));
        toast("Copied to clipboard.");
    }
    private void copyCurrent(){clipboard(current().optString("prompt"));}
    private void copyAll(){
        if(plan.length()==0)throw new IllegalArgumentException("Build storyboard first");
        StringBuilder text=new StringBuilder();
        for(int i=0;i<plan.length();i++){
            JSONObject item=plan.optJSONObject(i);
            if(item==null)continue;
            text.append(item.optString("id")).append("  (").append(item.optInt("durationSeconds")).append("s)\n")
                .append(item.optString("prompt")).append("\n\n");
            if(text.length()>900000)throw new IllegalArgumentException("Prompt pack is too large for Android clipboard. Export JSON instead.");
        }
        clipboard(text.toString());
    }
    private void saveProject(){
        if(projectJson.isEmpty())throw new IllegalArgumentException("Build a storyboard first");
        Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.setType("application/json");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_TITLE,"realframe_storyboard_"+System.currentTimeMillis()+".json");
        startActivityForResult(intent,SAVE_PROJECT);
    }
    @Override protected void onActivityResult(int code,int resultCode,Intent data){
        super.onActivityResult(code,resultCode,data);
        if(code!=SAVE_PROJECT||resultCode!=RESULT_OK||data==null||data.getData()==null)return;
        try(OutputStream out=getContentResolver().openOutputStream(data.getData(),"w")){
            if(out==null)throw new IllegalStateException("File not writable");
            out.write(projectJson.getBytes(StandardCharsets.UTF_8));
            toast("Storyboard saved. Import/download finished clips to the Clip Timeline to join.");
        }catch(Exception e){toast("Could not save JSON: "+e.getMessage());}
    }
}
