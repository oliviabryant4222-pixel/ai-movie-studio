package studio.realframe;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** Browser-powered open-source-model studio. No Nightmare app or local API required.
 * Uses independent hosted third-party Hugging Face Spaces with quotas and content policies.
 */
public final class WebStudioActivity extends Activity {
    private static final int FILE_CHOOSER = 7007;
    private static final String IMAGE_SPACE = "https://huggingface.co/spaces/mrfakename/Z-Image-Turbo";
    private static final String VIDEO_SPACE = "https://huggingface.co/spaces/zerogpu-aoti/wan2-2-fp8da-aoti-faster";
    private static final String ALT_IMAGE_SPACE = "https://huggingface.co/spaces/Tongyi-MAI/Z-Image-Turbo";
    private WebView web;
    private EditText prompt;
    private ValueCallback<Uri[]> pendingFiles;
    private String currentSpace = IMAGE_SPACE;

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private android.graphics.drawable.GradientDrawable rounded(int c) {
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setColor(c);g.setCornerRadius(dp(12));return g;
    }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
    private Button action(String label, LinearLayout row, View.OnClickListener callback) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextColor(android.graphics.Color.WHITE);
        button.setTextSize(12);
        button.setBackground(rounded(0xFF403870));
        button.setPadding(dp(4),dp(7),dp(4),dp(7));
        button.setOnClickListener(callback);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0, dp(44), 1);
        lp.setMargins(dp(3),dp(4),dp(3),dp(5));
        row.addView(button, lp);
        return button;
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        getWindow().setStatusBarColor(0xFF0B1120); getWindow().setNavigationBarColor(0xFF0B1120);
        root.setPadding(dp(10),dp(10),dp(10),dp(4)); root.setBackgroundColor(0xFF0B1120);
        TextView title = new TextView(this);
        title.setText("✦  REALFRAME  ·  ONLINE AI");
        title.setTextColor(0xFFF6F7FD);
        title.setTextSize(19);
        title.setPadding(dp(8),dp(5),dp(4),dp(5));
        root.addView(title);
        TextView explainer = new TextView(this);
        explainer.setText("Image + video models inside the app. Cloud demo: internet required, free quotas apply. No Nightmare or API address/token. The model provider controls its own content rules.");
        explainer.setTextSize(12); explainer.setTextColor(0xFFADB9CB);
        explainer.setPadding(dp(8),dp(4),dp(8),dp(9));
        root.addView(explainer);
        prompt = new EditText(this);
        prompt.setHint("Write your AI prompt; tap Copy then paste in the generator below");
        prompt.setMinLines(2);
        prompt.setMaxLines(3);
        prompt.setTextColor(0xFFF4F6FC);
        prompt.setHintTextColor(0xFF9BA6BA);
        prompt.setBackground(rounded(0xFF1A2840));
        prompt.setPadding(dp(12),dp(9),dp(12),dp(9));
        root.addView(prompt,new LinearLayout.LayoutParams(-1,dp(100)));
        LinearLayout firstRow = new LinearLayout(this);
        action("Copy prompt", firstRow, v -> {
            String value = prompt.getText().toString();
            if (value.trim().isEmpty()) { toast("Type a prompt first."); return; }
            ClipboardManager clip = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            clip.setPrimaryClip(ClipData.newPlainText("RealFrame prompt", value));
            toast("Copied! Long-press the generator prompt box and paste.");
        });
        action("Image AI", firstRow, v -> openSpace(IMAGE_SPACE));
        action("Video AI", firstRow, v -> openSpace(VIDEO_SPACE));
        root.addView(firstRow);
        LinearLayout secondRow = new LinearLayout(this);
        action("Other image AI", secondRow, v -> openSpace(ALT_IMAGE_SPACE));
        action("Open browser", secondRow, v -> {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(currentSpace)));
        });
        action("Back to studio", secondRow, v -> finish());
        root.addView(secondRow);
        web = new WebView(this);
        web.setBackgroundColor(0xFF101B2E);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setAllowFileAccess(false);
        web.getSettings().setAllowContentAccess(true);
        web.getSettings().setSupportMultipleWindows(false);
        web.getSettings().setJavaScriptCanOpenWindowsAutomatically(false);
        web.getSettings().setMediaPlaybackRequiresUserGesture(true);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if ("https".equals(uri.getScheme()) && isTrustedHost(uri.getHost())) return false;
                if ("https".equals(uri.getScheme()) || "mailto".equals(uri.getScheme())) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); } catch (Exception ignored) { }
                }
                return true;
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                                        FileChooserParams params) {
                if (pendingFiles != null) pendingFiles.onReceiveValue(null);
                pendingFiles = callback;
                try {
                    Intent picker = params.createIntent();
                    startActivityForResult(picker, FILE_CHOOSER);
                    return true;
                } catch (Exception e) {
                    pendingFiles = null; toast("File picker not available.");
                    return false;
                }
            }
        });
        web.setDownloadListener((url, userAgent, contentDisposition, mimeType, length) -> {
            try {
                Uri uri = Uri.parse(url);
                if (!"https".equals(uri.getScheme())) {
                    toast("For this format, open in browser and use the site's Download button.");
                    return;
                }
                String filename = URLUtil.guessFileName(url, contentDisposition, mimeType);
                filename = filename.replaceAll("[^a-zA-Z0-9._-]", "_");
                DownloadManager.Request request = new DownloadManager.Request(uri);
                request.setTitle("RealFrame " + filename);
                request.setDescription("Saving generated media");
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename);
                request.addRequestHeader("User-Agent", userAgent == null ? "" : userAgent);
                String cookies = CookieManager.getInstance().getCookie(url);
                if (cookies != null && !cookies.isEmpty()) request.addRequestHeader("Cookie", cookies);
                ((DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE)).enqueue(request);
                toast("Saving to Downloads: " + filename);
            } catch (Exception e) {
                toast("Could not save here. Open in browser: " + e.getMessage());
            }
        });
        root.addView(web, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        String kind = getIntent().getStringExtra("kind");
        if ("video".equals(kind)) openSpace(VIDEO_SPACE); else openSpace(IMAGE_SPACE);
    }
    private static boolean isTrustedHost(String host) {
        return host != null && (host.equals("huggingface.co") || host.endsWith(".huggingface.co")
                || host.equals("hf.space") || host.endsWith(".hf.space"));
    }
    private void openSpace(String url) {
        currentSpace = url;
        if (web != null) web.loadUrl(url);
    }
    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_CHOOSER && pendingFiles != null) {
            pendingFiles.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
            pendingFiles = null;
        }
    }
    @Override public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }
    @Override protected void onDestroy() {
        if (pendingFiles != null) { pendingFiles.onReceiveValue(null); pendingFiles = null; }
        if (web != null) { web.destroy(); web = null; }
        super.onDestroy();
    }
}