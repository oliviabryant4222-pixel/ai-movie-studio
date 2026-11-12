package studio.realframe;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** RealFrame dashboard v1.3. Offline tools are clearly distinct from cloud AI inference. */
public final class StudioDashboardActivity extends Activity {
    private static final int BG=0xFF090F1C, CARD=0xFF151F30, WHITE=0xFFF4F6FC;
    private static final int MUTED=0xFFA2B0C7, ACCENT=0xFFB39CFF, CYAN=0xFF5CE1D3;
    private LinearLayout shell, content, tabs;
    private String section="Home";
    private int dp(float v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private GradientDrawable shape(int color,int radius){
        GradientDrawable d=new GradientDrawable();
        d.setColor(color); d.setCornerRadius(dp(radius)); return d;
    }
    private GradientDrawable gradient(int a,int b,int radius){
        GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{a,b});
        d.setCornerRadius(dp(radius)); return d;
    }
    private TextView label(String s,int sp,int color,boolean strong){
        TextView t=new TextView(this);
        t.setText(s);t.setTextSize(sp);t.setTextColor(color);
        if(strong)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setIncludeFontPadding(true);
        return t;
    }
    private LinearLayout vertical(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout horizontal(){LinearLayout l=new LinearLayout(this);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private void pad(View v,int x,int y){v.setPadding(dp(x),dp(y),dp(x),dp(y));}
    private void gap(LinearLayout parent,int px){
        View v=new View(this);parent.addView(v,new LinearLayout.LayoutParams(1,dp(px)));
    }
    private void put(LinearLayout dest,View v){dest.addView(v,new LinearLayout.LayoutParams(-1,-2));}
    private LinearLayout card(LinearLayout parent,int bg){
        LinearLayout card=vertical();
        card.setPadding(dp(19),dp(18),dp(19),dp(18));
        card.setBackground(shape(bg,21));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.bottomMargin=dp(13);parent.addView(card,lp);
        return card;
    }
    private void heading(String title,String subtitle){
        gap(content,18);
        put(content,label(title,23,WHITE,true));
        if(subtitle!=null){gap(content,6);put(content,label(subtitle,13,MUTED,false));}
        gap(content,13);
    }
    private View actionTile(String glyph,String title,String subtitle,int accent,Runnable action){
        LinearLayout row=horizontal();row.setPadding(dp(11),dp(13),dp(10),dp(13));
        row.setBackground(shape(0xFF202C41,15));
        TextView icon=label(glyph,23,accent,true);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(shape(0xFF2B3A52,13));
        row.addView(icon,new LinearLayout.LayoutParams(dp(46),dp(46)));
        LinearLayout texts=vertical();
        texts.setPadding(dp(12),0,dp(5),0);
        put(texts,label(title,16,WHITE,true));
        gap(texts,5);
        put(texts,label(subtitle,12,MUTED,false));
        row.addView(texts,new LinearLayout.LayoutParams(0,-2,1));
        TextView arrow=label("›",29,accent,true);
        row.addView(arrow,new LinearLayout.LayoutParams(dp(22),dp(46)));
        row.setOnClickListener(v->action.run());
        return row;
    }
    private void tile(LinearLayout parent,String glyph,String title,String subtitle,int accent,Runnable action){
        View tile=actionTile(glyph,title,subtitle,accent,action);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.bottomMargin=dp(10);parent.addView(tile,lp);
    }
    private void open(Class<? extends Activity> cls){startActivity(new Intent(this,cls));}
    private void online(String kind){
        Intent i=new Intent(this,WebStudioActivity.class);
        i.putExtra("kind",kind);startActivity(i);
    }
    private void advanced(String page){
        Intent i=new Intent(this,MainActivity.class);
        i.putExtra("open_tab",page);startActivity(i);
    }
    private void engineInfo(){
        new AlertDialog.Builder(this)
            .setTitle("Offline image engine — experimental")
            .setMessage("This build includes a CPU-only stable-diffusion.cpp image inference library. Import a compatible SD checkpoint first; CPU diffusion can be extremely slow or exhaust phone RAM. Qualcomm QNN/NPU acceleration and native offline video inference remain unavailable.")
            .setPositiveButton("Got it",null).show();
    }
    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(BG);
        if(Build.VERSION.SDK_INT>=26) getWindow().getDecorView().setSystemUiVisibility(0);
        render("Home");
    }
    private void render(String mode){
        section=mode;
        shell=vertical();shell.setBackgroundColor(BG);
        shell.setPadding(0,dp(12),0,0);
        setContentView(shell);
        LinearLayout top=horizontal();top.setPadding(dp(19),dp(8),dp(19),dp(14));
        TextView logo=label("✦",25,WHITE,true);logo.setGravity(Gravity.CENTER);
        logo.setBackground(gradient(0xFF7655E9,0xFF319FA5,13));
        top.addView(logo,new LinearLayout.LayoutParams(dp(45),dp(45)));
        LinearLayout name=vertical();name.setPadding(dp(11),0,0,0);
        put(name,label("REALFRAME",21,WHITE,true));
        put(name,label("AI  /  MOVIE STUDIO",11,MUTED,true));
        top.addView(name,new LinearLayout.LayoutParams(0,-2,1));
        TextView badge=label("BETA 1.7",11,CYAN,true);
        badge.setGravity(Gravity.CENTER);badge.setBackground(shape(0xFF172C37,12));
        top.addView(badge,new LinearLayout.LayoutParams(dp(88),dp(35)));
        shell.addView(top);

        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(false);
        content=vertical(); content.setPadding(dp(18),0,dp(18),dp(25));
        scroll.addView(content);
        shell.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));

        LinearLayout bottom=horizontal();
        bottom.setPadding(dp(8),dp(9),dp(8),dp(9));
        bottom.setBackgroundColor(0xFF11192A);
        String[] items={"Home","Online","Offline","Hybrid","Vault","Editor"};
        String[] icons={"⌂","✧","◈","⇄","◇","▤"};
        for(int i=0;i<items.length;i++){
            final String next=items[i];
            LinearLayout tab=vertical();tab.setGravity(Gravity.CENTER);
            TextView ico=label(icons[i],23,next.equals(mode)?ACCENT:MUTED,true);
            ico.setGravity(Gravity.CENTER);
            put(tab,ico);
            TextView caption=label(next,10,next.equals(mode)?WHITE:MUTED,next.equals(mode));
            caption.setGravity(Gravity.CENTER);
            put(tab,caption);
            tab.setOnClickListener(v->{
                if("Vault".equals(next))advanced("Characters");
                else if("Editor".equals(next))advanced("Clips");
                else render(next);
            });
            bottom.addView(tab,new LinearLayout.LayoutParams(0,dp(53),1));
        }
        shell.addView(bottom);

        if("Home".equals(mode))drawHome();
        else if("Online".equals(mode))drawOnline();
        else if("Offline".equals(mode))drawOffline();
        else if("Hybrid".equals(mode))drawHybrid();
    }
    private void hero(){
        LinearLayout hero=vertical();
        hero.setPadding(dp(22),dp(22),dp(22),dp(23));
        hero.setBackground(gradient(0xFF493484,0xFF203F65,24));
        LinearLayout.LayoutParams heroLp=new LinearLayout.LayoutParams(-1,-2);
        heroLp.topMargin=dp(14);content.addView(hero,heroLp);
        put(hero,label("YOUR CREATIVE WORKSPACE",11,0xFFC8E8FF,true));
        gap(hero,11);
        put(hero,label("One studio.\nThree creative modes.",30,WHITE,true));
        gap(hero,9);
        put(hero,label("Generate online with available free quotas.\nEdit, manage and combine clips offline.",13,0xFFE0E5F4,false));
        gap(hero,15);
        TextView chip=label("ONLINE  •  OFFLINE  •  HYBRID",12,0xFF101B2D,true);
        chip.setGravity(Gravity.CENTER);
        chip.setBackground(shape(CYAN,10));
        hero.addView(chip,new LinearLayout.LayoutParams(dp(185),dp(37)));
    }
    private void drawHome(){
        hero();
        heading("Create a new project","One prompt, local/online/hybrid modes and character presets");
        LinearLayout creator=card(content,CARD);
        tile(creator,"✦","UNIFIED CREATOR","New: intelligent routing + privacy consent",ACCENT,()->open(UnifiedCreateActivity.class));
        heading("Create with AI","Hosted image and video models • no Nightmare or manual token");
        LinearLayout online=card(content,CARD);
        tile(online,"✦","AI Image Studio","Realistic portraits · prompts · downloads",ACCENT,()->online("image"));
        tile(online,"▶","AI Video Studio","Image-to-video · motion clips · online",CYAN,()->online("video"));
        heading("Choose your processing mode","Online, offline editing, and hybrid project management");
        LinearLayout modes=card(content,CARD);
        tile(modes,"⇄","Hybrid Studio","Switch between online generation and offline post-production",CYAN,()->render("Hybrid"));
        tile(modes,"☷","Model & Mode Manager","Register downloaded models; inspect local engine status",ACCENT,()->open(ModelManagerActivity.class));
        heading("On-device generation & editing","Experimental local AI, no cloud upload");
        LinearLayout localAi=card(content,CARD);
        tile(localAi,"◉","LOCAL AI Image Batch (CPU beta)","Generate 1–4 images; reuse one loaded model",CYAN,()->open(LocalImageActivity.class));
        heading("Offline workspace","Available without internet or a cloud account");
        LinearLayout offline=card(content,CARD);
        tile(offline,"◈","Photo Lab","Enhance a photo and export JPG offline",CYAN,()->open(OfflinePhotoLabActivity.class));
        tile(offline,"▧","Still Image → MP4","Make a silent video clip offline, no AI motion",CYAN,()->open(OfflineImageVideoActivity.class));
        tile(offline,"▤","Clip Timeline","Import, arrange and join matching MP4s",ACCENT,()->advanced("Clips"));
        tile(offline,"◷","Long-video planner","Plan 5-second clips + character continuity",0xFFFFC582,()->open(ProductionPlannerActivity.class));
        tile(offline,"◉","Advanced batch queue","Monitor and retry configured render jobs",ACCENT,()->advanced("Jobs"));
        LinearLayout banner=card(content,0xFF223047);
        put(banner,label("LOCAL AI MODEL STATUS",12,0xFFF5CE8E,true));
        gap(banner,7);
        put(banner,label("CPU text-to-image available with a supported imported model. No bundled model weights. Offline video AI and Snapdragon NPU acceleration are not included.",13,WHITE,false));
        gap(banner,10);
        TextView detail=label("View engine requirements  →",13,CYAN,true);
        detail.setOnClickListener(v->engineInfo());put(banner,detail);
    }
    private void drawOnline(){
        heading("Generate online","Access hosted models in-app • provider availability and limits apply");
        LinearLayout creator=card(content,CARD);
        tile(creator,"✦","Unified Creator","Write a prompt once and select Online mode",ACCENT,()->open(UnifiedCreateActivity.class));
        LinearLayout mode=card(content,CARD);
        tile(mode,"✦","Generate an AI image","Open image model demo",ACCENT,()->online("image"));
        tile(mode,"▣","Generate AI video","Open video model demo",CYAN,()->online("video"));
        LinearLayout note=card(content,0xFF1A2A35);
        put(note,label("ABOUT FREE GPU",13,CYAN,true));
        gap(note,9);
        put(note,label("Free hosted GPU is quota-limited. Some demos require login, may be unavailable, or restrict certain content. Uploaded images and prompts are processed on the provider's servers.",13,WHITE,false));
        tile(note,"↗","Open advanced connectors","Use optional configured APIs & job queue",CYAN,()->advanced("Settings"));
    }
    private void drawOffline(){
        heading("Create offline","No cloud required for image inference if a model is imported");
        LinearLayout creator=card(content,CARD);
        tile(creator,"✦","Unified Creator","Choose Local mode and use a character preset",ACCENT,()->open(UnifiedCreateActivity.class));
        LinearLayout mode=card(content,CARD);
        tile(mode,"◉","Local AI Image Batch (CPU beta)","1–4 images, automatically saved to gallery",CYAN,()->open(LocalImageActivity.class));
        tile(mode,"◈","Photo Lab","Adjust brightness, contrast & saturation; save JPG",CYAN,()->open(OfflinePhotoLabActivity.class));
        tile(mode,"▧","Still Image → MP4","Export a silent video from a photo",CYAN,()->open(OfflineImageVideoActivity.class));
        tile(mode,"▤","Clip Timeline","Import video clips and merge into MP4",ACCENT,()->advanced("Clips"));
        tile(mode,"◇","Characters & references","Store character recipes and appearance notes",0xFFFFC582,()->advanced("Characters"));
        tile(mode,"◷","Long-video planner","Export scene prompts and continuity graph",ACCENT,()->open(ProductionPlannerActivity.class));
        tile(mode,"▣","Storyboard & jobs","Manage project jobs and retry failed renders",ACCENT,()->advanced("Storyboard"));
        LinearLayout status=card(content,0xFF30263A);
        put(status,label("LOCAL TEXT-TO-IMAGE: EXPERIMENTAL",13,0xFFFFC582,true));
        gap(status,9);
        put(status,label("Native CPU stable-diffusion.cpp is included, but images need a compatible downloaded model and may be slow or OOM. This CPU pipeline has not been benchmarked on your Samsung. Offline AI video is not available.",13,WHITE,false));
        gap(status,11);
        TextView info=label("Why? See hardware + model requirements  →",13,CYAN,true);
        info.setOnClickListener(v->engineInfo());put(status,info);
    }
    private void chooseHybrid(){
        new AlertDialog.Builder(this)
            .setTitle("Choose your AI image engine")
            .setItems(new String[]{
                "LOCAL • This phone CPU, downloaded model required",
                "ONLINE • Cloud AI, uploads/prompt leave this phone"
            },(dialog,choice)->{
                if(choice==0)open(LocalImageActivity.class);
                else online("image");
            })
            .setNegativeButton("Cancel",null)
            .show();
    }
    private void drawHybrid(){
        heading("Hybrid Studio","Manual model choice + privacy-first routing");
        LinearLayout creator=card(content,CARD);
        tile(creator,"✦","Hybrid Create","Prompt, character, output, local/cloud selection",ACCENT,()->open(UnifiedCreateActivity.class));
        LinearLayout mode=card(content,CARD);
        tile(mode,"⇄","Choose local or online image","One prompt + smart routing; cloud consent required",CYAN,()->open(UnifiedCreateActivity.class));
        tile(mode,"◉","Local AI Image (CPU beta)","Runs on-device after model import",CYAN,()->open(LocalImageActivity.class));
        tile(mode,"✦","Generate online image","Remote image model; free provider quota",ACCENT,()->online("image"));
        tile(mode,"▶","Generate online video","Remote model; free provider quota",CYAN,()->online("video"));
        tile(mode,"▧","Offline image → MP4","Turn a saved still photo into an MP4 without AI",CYAN,()->open(OfflineImageVideoActivity.class));
        tile(mode,"▤","Offline video assembler","Use downloaded/generated clips to create longer edits",ACCENT,()->advanced("Clips"));
        tile(mode,"◷","Long-video production","Plan scenes, characters, continuity, export manifest",0xFFFFC582,()->open(ProductionPlannerActivity.class));
        tile(mode,"☷","Model manager & preferences","Store model path and pick online, local or hybrid",0xFFFFC582,()->open(ModelManagerActivity.class));
        LinearLayout status=card(content,0xFF30263A);
        put(status,label("WHAT HYBRID DOES TODAY",13,0xFFFFC582,true));
        gap(status,9);
        put(status,label("Manual hybrid: choose where an image is generated. Local uses the phone CPU with a downloaded model; online uses a provider. Automatic routing and automatic online fallback are not supported. Offline AI video remains unavailable.",13,WHITE,false));
    }

}
