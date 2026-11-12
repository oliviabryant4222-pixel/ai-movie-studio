package studio.realframe;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.widget.LinearLayout;
import android.widget.Button;
import android.widget.TextView;
import org.json.JSONObject;

/**
 * Browser-style client for the same on-device native inference API.
 * The local access token is filled in-memory after the trusted page loads.
 * Never inject it into an external hosted page.
 */
public final class LocalWebClientActivity extends Activity {
    private WebView view;
    private static boolean trusted(Uri uri){
        return uri!=null && "http".equals(uri.getScheme()) &&
            "127.0.0.1".equals(uri.getHost()) && uri.getPort()==LocalApiService.PORT;
    }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(0xFF0C1425);
        getWindow().setNavigationBarColor(0xFF0C1425);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF0C1425);
        setContentView(root);
        Button back=new Button(this);
        back.setText("←  RealFrame • Local Server");
        back.setAllCaps(false);
        back.setTextColor(Color.WHITE);
        back.setBackgroundColor(0xFF1E3048);
        back.setOnClickListener(v->finish());
        root.addView(back,new LinearLayout.LayoutParams(-1,50*(int)getResources().getDisplayMetrics().density));
        view=new WebView(this);
        view.setBackgroundColor(0xFF0C1425);
        WebSettings settings=view.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        view.setWebViewClient(new WebViewClient(){
            @Override public void onPageFinished(WebView web,String url){
                Uri uri=Uri.parse(url);
                if(!trusted(uri))return;
                String token=JSONObject.quote(LocalApiService.getOrCreateToken(LocalWebClientActivity.this));
                // The token remains inside the trusted 127.0.0.1 page's memory.
                web.evaluateJavascript("document.getElementById('token').value="+token+";health();",null);
            }
            @Override public boolean shouldOverrideUrlLoading(WebView web,WebResourceRequest req){
                Uri target=req.getUrl();
                if(trusted(target))return false;
                if(!"https".equals(target.getScheme()))return true;
                new AlertDialog.Builder(LocalWebClientActivity.this)
                    .setTitle("Leave local-only mode?")
                    .setMessage("This link opens an external provider. Hosted AI receives prompts or media you choose to upload. It does not use your RealFrame local token. Continue?")
                    .setPositiveButton("Open online",(d,w)->{
                        Intent browser=new Intent(Intent.ACTION_VIEW,target);
                        try{startActivity(browser);}catch(Exception ignored){}
                    })
                    .setNegativeButton("Stay local",null)
                    .show();
                return true;
            }
        });
        root.addView(view,new LinearLayout.LayoutParams(-1,0,1));
        if(LocalApiService.isRunning())
            view.loadUrl("http://127.0.0.1:"+LocalApiService.PORT+"/");
        else view.loadData("<html><body style='background:#0c1425;color:white'>Start the local API server first in the app.</body></html>",
                          "text/html","UTF-8");
    }
    @Override public void onBackPressed(){
        if(view!=null&&view.canGoBack())view.goBack();
        else super.onBackPressed();
    }
    @Override protected void onDestroy(){
        if(view!=null){view.destroy();view=null;}
        super.onDestroy();
    }
}
