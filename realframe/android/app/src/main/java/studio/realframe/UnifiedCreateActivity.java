package studio.realframe;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import org.json.JSONObject;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Unified prompt-first Create page with explicit cloud privacy consent.
 * Local image batches run only in LocalImageActivity. Hosted demos retain
 * their own quotas/login/content terms. Local AI video is not implemented.
 */
public final class UnifiedCreateActivity extends Activity {
    private static final int BG=0xFF0B1425, PANEL=0xFF172740, WHITE=0xFFF2F6FF,
            DIM=0xFFACBCD2, TEAL=0xFF5CDECA, VIOLET=0xFFB9A6FF;
    private EditText prompt,negative,seed;
    private Spinner mode,output,character;
    private final List<JSONObject> presets=new ArrayList<>();
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private GradientDrawable box(int color,int radius){
        GradientDrawable x=new GradientDrawable();
        x.setColor(color);x.setCornerRadius(dp(radius));return x;
    }
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private TextView label(String text,int sp,int color,boolean bold){
        TextView t=new TextView(this);t.setText(text);t.setTextSize(sp);t.setTextColor(color);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;
    }
    private void put(LinearLayout p,View v){p.addView(v,new LinearLayout.LayoutParams(-1,-2));}
    private void gap(LinearLayout p,int n){p.addView(new View(this),new LinearLayout.LayoutParams(1,dp(n)));}
    private void block(LinearLayout p,String title,String desc){
        put(p,label(title,20,WHITE,true));gap(p,5);put(p,label(desc,12,DIM,false));gap(p,11);
    }
    private EditText input(LinearLayout p,String title,String hint,String text){
        TextView t=label(title,13,DIM,true);t.setPadding(0,dp(8),0,dp(5));put(p,t);
        EditText e=new EditText(this);e.setHint(hint);e.setText(text);
        e.setTextColor(WHITE);e.setHintTextColor(0xFF889BB8);e.setTextSize(14);
        e.setPadding(dp(14),dp(11),dp(14),dp(11));e.setBackground(box(0xFF283B56,14));
        put(p,e);return e;
    }
    private Spinner choose(LinearLayout p,String title,String[] options){
        TextView label=label(title,13,DIM,true);label.setPadding(0,dp(10),0,dp(6));put(p,label);
        Spinner sp=new Spinner(this);
        sp.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,options));
        sp.setBackground(box(0xFFE0E8F2,11));put(p,sp);
        return sp;
    }
    private Button button(LinearLayout p,String title,int color,Runnable onClick){
        Button btn=new Button(this);btn.setAllCaps(false);
        btn.setText(title);btn.setTextSize(16);btn.setTextColor(WHITE);
        btn.setBackground(box(color,16));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(52));
        lp.topMargin=dp(12);p.addView(btn,lp);
        btn.setOnClickListener(v->onClick.run());
        return btn;
    }

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        LinearLayout root=column();root.setPadding(dp(16),dp(16),dp(16),dp(8));
        root.setBackgroundColor(BG);setContentView(root);
        TextView title=label("←   REALFRAME CREATE",24,WHITE,true);
        title.setOnClickListener(v->finish());put(root,title);gap(root,5);
        put(root,label("Choose Local • Online • Hybrid, then create with consistent prompts.",12,TEAL,false));
        gap(root,14);
        ScrollView scroll=new ScrollView(this);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout p=column();p.setPadding(dp(17),dp(15),dp(17),dp(17));
        p.setBackground(box(PANEL,20));scroll.addView(p);
        block(p,"One prompt, three modes","Cloud generation needs your approval; local mode never uploads.");
        output=choose(p,"OUTPUT",new String[]{"Image","Video"});
        mode=choose(p,"PROCESSING MODE",new String[]{"Hybrid (prefer local if ready)","Local only","Online only"});
        String preferred=getSharedPreferences("realframe_model_manager",MODE_PRIVATE)
                              .getString("mode","hybrid");
        mode.setSelection("local".equals(preferred)?1:"online".equals(preferred)?2:0);
        prompt=input(p,"PROMPT","Describe your fictional adult subject and scene",
            getPreferences(MODE_PRIVATE).getString("draft","Portrait of a fictional adult, natural lighting, realistic skin texture"));
        negative=input(p,"NEGATIVE PROMPT","Unwanted visual elements",
            getPreferences(MODE_PRIVATE).getString("negative","watermark, text, artificial beauty glow"));
        seed=input(p,"SEED (LOCAL IMAGES)","Seed for reproducibility",
            getPreferences(MODE_PRIVATE).getString("seed","13901"));
        presets.clear();presets.add(new JSONObject());presets.addAll(new StudioStore(this).characters());
        String[] names=new String[presets.size()];
        names[0]="No character preset";
        for(int i=1;i<names.length;i++)names[i]=presets.get(i).optString("name","Character "+i);
        character=choose(p,"CHARACTER VAULT",names);
        gap(p,12);
        put(p,label("For local images, import a compatible SD1.5 checkpoint first. Image batches are CPU-only and can take considerable time. Local AI video is not supported.",13,DIM,false));
        button(p,"✦  CREATE",0xFF6051BB,this::create);
        button(p,"☷  MODEL MANAGER",0xFF25776E,()->startActivity(new Intent(this,ModelManagerActivity.class)));
        gap(p,10);
        put(p,label("PRIVACY: Cloud prompts and uploaded references leave your phone. Never automatically fall back from local mode to hosted providers.",12,0xFFF5C8A7,false));
    }

    private void saveDraft(String rawPrompt){
        getPreferences(MODE_PRIVATE).edit()
            .putString("draft",rawPrompt)
            .putString("negative",negative.getText().toString())
            .putString("seed",seed.getText().toString()).apply();
    }
    private String builtPrompt(){
        String raw=prompt.getText().toString().trim();
        if(raw.isEmpty())throw new IllegalArgumentException("Write an image or video prompt");
        JSONObject c=presets.get(character.getSelectedItemPosition());
        if(c.length()==0)return raw;
        return "Consistent fictional adult character: "+c.optString("appearance")+
            ". Outfit: "+c.optString("wardrobe")+". Scene: "+raw;
    }
    private void create(){
        final String completed;
        try{completed=builtPrompt();}catch(Exception e){error(e.getMessage());return;}
        saveDraft(prompt.getText().toString());
        boolean video=output.getSelectedItemPosition()==1;
        HybridRoutePolicy.Mode chosen=mode.getSelectedItemPosition()==1 ?
            HybridRoutePolicy.Mode.LOCAL :
            mode.getSelectedItemPosition()==2 ?
            HybridRoutePolicy.Mode.ONLINE : HybridRoutePolicy.Mode.HYBRID;
        String selectedMode=chosen.name().toLowerCase(java.util.Locale.US);
        getSharedPreferences("realframe_model_manager",MODE_PRIVATE)
            .edit().putString("mode",selectedMode).apply();
        File model=new File(getSharedPreferences("realframe_local_sd",MODE_PRIVATE)
                          .getString("modelPath",""));
        ActivityManager manager=(ActivityManager)getSystemService(ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memory=new ActivityManager.MemoryInfo();
        manager.getMemoryInfo(memory);
        HybridRoutePolicy.Decision decision=HybridRoutePolicy.decide(chosen,video,
            LocalImageEngine.isAvailable(),model.isFile(),memory.availMem);
        switch(decision){
            case LOCAL_IMAGE:
                local(completed);
                break;
            case ONLINE_CONFIRM:
                confirmCloud(completed,video);
                break;
            case UNSUPPORTED_LOCAL_VIDEO:
                new AlertDialog.Builder(this)
                    .setTitle("Offline AI video is not available")
                    .setMessage("Local mode cannot generate diffusion video yet. You can assemble existing clips without internet, or explicitly switch to Online for hosted AI video.")
                    .setPositiveButton("Clip Editor",(d,w)->{
                        Intent intent=new Intent(this,MainActivity.class);
                        intent.putExtra("open_tab","Clips");startActivity(intent);
                    })
                    .setNegativeButton("Cancel",null).show();
                break;
            default:
                new AlertDialog.Builder(this)
                    .setTitle("Choose generation engine")
                    .setMessage(video ?
                        "Local AI video is not installed. Open an online video model only with your consent, or continue with the offline clip editor." :
                        "A local checkpoint, native library, or enough free RAM is missing. You can open the local importer or explicitly choose a hosted model.")
                    .setPositiveButton("Use Online AI",(d,w)->confirmCloud(completed,video))
                    .setNeutralButton(video?"Open Clip Editor":"Use Local Image",(d,w)->{
                        if(video){
                            Intent edit=new Intent(this,MainActivity.class);edit.putExtra("open_tab","Clips");
                            startActivity(edit);
                        }else local(completed);
                    })
                    .setNegativeButton("Cancel",null).show();
        }
    }
    private void local(String fullPrompt){
        Intent i=new Intent(this,LocalImageActivity.class);
        i.putExtra("prompt",fullPrompt);
        i.putExtra("negative",negative.getText().toString());
        i.putExtra("seed",seed.getText().toString().trim());
        startActivity(i);
    }
    private void confirmCloud(String fullPrompt,boolean video){
        new AlertDialog.Builder(this)
            .setTitle("Cloud processing permission")
            .setMessage("An external Hugging Face model will process your prompt and any image you upload. Free quotas and provider rules apply. Continue?")
            .setPositiveButton("Continue Online",(d,w)->{
                Intent intent=new Intent(this,WebStudioActivity.class);
                intent.putExtra("kind",video?"video":"image");
                intent.putExtra("prompt",fullPrompt);
                startActivity(intent);
            })
            .setNegativeButton("Stay on Device",null).show();
    }
    private void error(String message){
        new AlertDialog.Builder(this).setMessage(message).setPositiveButton("OK",null).show();
    }
}
