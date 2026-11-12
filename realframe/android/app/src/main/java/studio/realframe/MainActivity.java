package studio.realframe;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Native Android prototype: local NPU companion + optional HF Gradio + clip editor. */
public final class MainActivity extends Activity {
    private static final int PICK_IMAGE = 1001, PICK_CLIPS = 1002, EXPORT_BACKUP = 1003, IMPORT_BACKUP = 1004;
    private StudioStore store;
    private SecretVault prefs;
    private Renderer renderer;
    private final ExecutorService utilityExecutor = Executors.newSingleThreadExecutor();
    private LinearLayout root, page;
    private String selectedImage = "";
    private String activeTab = "Home";
    private int jobsPage = 0, clipsPage = 0;
    private static final int PAGE_SIZE = 30;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        store = new StudioStore(this); prefs = new SecretVault(this);
        renderer = new Renderer(this, prefs);
        showHome();
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                        android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 229);
        }
        pump();
    }
    @Override protected void onResume() { super.onResume(); if (store != null) pump(); }
    @Override protected void onDestroy() {
        super.onDestroy(); utilityExecutor.shutdownNow();
    }

    private void notifyUser(String msg) { runOnUiThread(() -> Toast.makeText(this, msg, Toast.LENGTH_LONG).show()); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private TextView text(String value, int size) {
        TextView tv = new TextView(this); tv.setText(value); tv.setTextSize(size);
        tv.setTextColor(Color.rgb(30,36,47)); tv.setPadding(dp(2),dp(7),dp(2),dp(7));
        return tv;
    }
    private void title(String value) { TextView t = text(value, 22); t.setTypeface(null, 1); page.addView(t); }
    private void note(String value) { TextView t = text(value, 14); t.setTextColor(Color.rgb(85,93,105)); page.addView(t); }
    private EditText field(String title, String initial, boolean multi) {
        page.addView(text(title, 14));
        EditText e = new EditText(this); e.setText(initial);
        e.setTextSize(15); e.setSingleLine(!multi);
        if (multi) { e.setMinLines(3); e.setGravity(android.view.Gravity.TOP); }
        page.addView(e, new LinearLayout.LayoutParams(-1,-2));
        return e;
    }
    private Spinner spinner(String label, String... options) {
        page.addView(text(label, 14));
        Spinner s = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, options);
        s.setAdapter(adapter); page.addView(s); return s;
    }
    private Button button(String label, Runnable fn) {
        Button b = new Button(this); b.setText(label);
        b.setOnClickListener(v -> { try { fn.run(); } catch (Exception e) { notifyUser(e.getMessage()); } });
        page.addView(b, new LinearLayout.LayoutParams(-1,-2)); return b;
    }
    private void sep() {
        View line = new View(this); line.setBackgroundColor(Color.rgb(222,225,231));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(1));
        lp.setMargins(0,dp(8),0,dp(8)); page.addView(line, lp);
    }
    private void layout(String tab) {
        activeTab = tab;
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(16), dp(12), dp(6));
        root.setBackgroundColor(Color.rgb(249,249,251)); setContentView(root);
        TextView header = text("REALFRAME  ·  AI STUDIO  1.1", 17);
        header.setTypeface(null, 1); root.addView(header);
        HorizontalScrollView menuScroll = new HorizontalScrollView(this);
        LinearLayout menu = new LinearLayout(this); menu.setOrientation(LinearLayout.HORIZONTAL);
        String[] tabs = {"Home", "Connect AI", "Generate", "Characters", "Storyboard", "Jobs", "Clips", "Settings"};
        for (String name : tabs) {
            Button b = new Button(this); b.setText(name); b.setTextSize(12);
            b.setAllCaps(false); b.setOnClickListener(v -> navigate(name));
            menu.addView(b, new LinearLayout.LayoutParams(-2,dp(48)));
        }
        menuScroll.addView(menu); root.addView(menuScroll);
        ScrollView scroll = new ScrollView(this);
        page = new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(0,dp(10),0,dp(24)); scroll.addView(page);
        root.addView(scroll, new LinearLayout.LayoutParams(-1,0,1));
    }
    private void navigate(String tab) {
        switch (tab) {
            case "Connect AI": showConnect(); break;
            case "Generate": showGenerate(); break;
            case "Characters": showCharacters(); break;
            case "Storyboard": showStoryboard(); break;
            case "Jobs": showJobs(); break;
            case "Clips": showClips(); break;
            case "Settings": showSettings(); break;
            default: showHome();
        }
    }

    private void showHome() {
        layout("Home"); title("Your mobile AI production studio");
        note("Source-code prototype (requires separate working inference backend). Generate via Nightmare Mobile local API or optional public Hugging Face Gradio Spaces. Free cloud GPU quotas are not unlimited.");
        button("⚡ Connect and generate first REAL AI image", this::showConnect);
        button("📸 Image / 🎬 Video generation", this::showGenerate);
        button("🧬 Character Vault", this::showCharacters);
        button("📦 Queue / resume jobs", this::showJobs);
        button("🎞 Join short MP4 clips", this::showClips);
        button("⚙ Configure generation backends", this::showSettings);
        sep(); title("Test without setting up an AI model");
        note("Use the offline diagnostic to verify installation, job queue, gallery save and MP4 playback. Its outputs are test files, NOT AI-generated images or videos.");
        button("✓ Generate offline TEST image", () -> queueDiagnostic("image"));
        button("✓ Generate offline TEST video", () -> queueDiagnostic("video"));
        note("Device reported: " + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL +
                (android.os.Build.VERSION.SDK_INT >= 31 ? " · SoC " + android.os.Build.SOC_MODEL : ""));
        sep();
        note("PHONE-LOCAL: install Nightmare Mobile separately, download compatible models, enable Settings → Advanced → API. Your app does not bundle its proprietary/missing native inference binary.");
        note("SNAPDRAGON 8 GEN 3 / 12GB: Most Nightmare video flows list 8 Elite; Wan 2.2 is marked alpha on 8 Gen 3. Verify on-device model support first. Cloud video requires a working Space and free quota.");
        note("A foreground notification keeps your render queue running when leaving this screen, within Android foreground-service time limits. Avoid force-stopping the app. Saved uncertain jobs need manual retry. MP4 merging needs matching formats.");
    }

    /** One-screen setup for the user's first real on-phone image/video render.
     * Uses documented Nightmare API; unlike offline diagnostics, these jobs require model inference.
     */
    private void showConnect() {
        layout("Connect AI"); title("Connect real AI · Snapdragon");
        note("This is the next step after the offline TEST. For phone-local images, install Nightmare Mobile and a compatible checkpoint first. Enable Nightmare Settings → Advanced → API → Listen on local network and copy its token. Real AI rendering is performed by Nightmare, not the test-image generator.");
        EditText url = field("Nightmare API URL", prefs.get("localUrl", "http://127.0.0.1:8820"), false);
        EditText token = field("Nightmare token (blank keeps saved token)", "", false);
        token.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        button("Save local connection", () -> {
            try {
                String address = url.getText().toString().trim();
                Uri parsed = Uri.parse(address);
                if (!("http".equals(parsed.getScheme()) || "https".equals(parsed.getScheme())) ||
                        parsed.getHost() == null) throw new Exception("Enter full API URL beginning with http:// or https://");
                prefs.set("localUrl", address);
                if (!token.getText().toString().trim().isEmpty())
                    prefs.setToken("nightmare", token.getText().toString().trim());
                notifyUser("Connection saved. Now tap Check installed image models.");
            } catch (Exception e) { notifyUser(e.getMessage()); }
        });
        button("1 · Check installed image models", () -> {
            utilityExecutor.execute(() -> {
                try {
                    String models = renderer.localWebUiModels();
                    if (models.trim().equals("[]"))
                        throw new Exception("No compatible installed image model. Download/select one in Nightmare first.");
                    String shown = models.length() > 5000 ? models.substring(0, 5000) + "\n…" : models;
                    runOnUiThread(() -> new android.app.AlertDialog.Builder(this)
                            .setTitle("Connected · image models")
                            .setMessage(shown)
                            .setPositiveButton("Great", null).show());
                } catch (Exception e) { notifyUser("Image API not ready: " + e.getMessage()); }
            });
        });
        EditText firstPrompt = field("Your first REAL image prompt",
                "Realistic unretouched smartphone photograph of a fictional adult, natural skin texture, soft daylight, comfortable casual clothing, plausible anatomy", true);
        button("2 · Generate REAL local image (1 image)", () -> {
            try {
                String description = firstPrompt.getText().toString().trim();
                if (description.isEmpty()) throw new Exception("Type an image prompt");
                JSONObject job = makeJob("image", "Local", "a1111", description,
                        randomSeed(), "", "", "");
                job.put("negative", "artificial glow, plastic skin, overprocessing")
                        .put("width", 512).put("height", 768).put("steps", 20).put("cfg", 6);
                store.putJob(job);
                notifyUser("REAL local image queued. Open Jobs to view the result or error.");
                pump(); showJobs();
            } catch (Exception e) { notifyUser(e.getMessage()); }
        });
        sep(); title("Video — use a supported Nightmare flow");
        note("Video models are heavy on Snapdragon 8 Gen 3. Some flows are unsupported/experimental. Choose a working video flow inside Nightmare first, save it, then discover it below. If image-to-video, attach an image before queuing.");
        String savedFlow = prefs.get("videoFlow", "");
        note("Selected video flow: " + (savedFlow.isEmpty() ? "none" : savedFlow));
        button("3 · Discover local video flows", () -> {
            utilityExecutor.execute(() -> {
                try {
                    List<String> found = renderer.localFlowIds();
                    if (found.isEmpty()) throw new Exception("No flows found. Check Nightmare API.");
                    String[] choices = found.toArray(new String[0]);
                    runOnUiThread(() -> new android.app.AlertDialog.Builder(this)
                            .setTitle("Choose your video-capable flow")
                            .setItems(choices, (dialog, index) -> {
                                prefs.set("videoFlow", choices[index]);
                                notifyUser("Saved flow " + choices[index] + ". Verify it makes VIDEO in Nightmare.");
                                showConnect();
                            }).setNegativeButton("Cancel", null).show());
                } catch (Exception e) { notifyUser("Flows not available: " + e.getMessage()); }
            });
        });
        button("Attach starting image for image-to-video", this::pickImage);
        EditText videoPrompt = field("Short video motion prompt", "The adult person smiles naturally and gently turns toward the window. Stable camera, smooth natural motion.", true);
        button("4 · Queue REAL local video (1 clip)", () -> {
            try {
                String selected = prefs.get("videoFlow", "");
                if (selected.isEmpty() || "txt2img".equals(selected) || "a1111".equals(selected))
                    throw new Exception("Discover and select a real video flow, not a text-to-image flow.");
                JSONObject job = makeJob("video", "Local", selected,
                        videoPrompt.getText().toString().trim(), randomSeed(), selectedImage, "", "");
                job.put("duration", 2).put("flowParams", "{}");
                store.putJob(job);
                selectedImage = "";
                notifyUser("Real video job queued; model must support this flow. Check Jobs for result/error.");
                pump(); showJobs();
            } catch (Exception e) { notifyUser(e.getMessage()); }
        });
        sep(); note("CLOUD: go to Settings to configure optional Hugging Face Gradio Spaces. Hosted free GPUs are quota-limited and have provider rules. No external paid compute is required by this app.");
        button("Configure optional cloud image/video API", this::showSettings);
    }

    private void pickImage() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("image/*"); intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, PICK_IMAGE);
    }
    private void pickClips() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("video/mp4"); intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(intent, PICK_CLIPS);
    }
    @Override protected void onActivityResult(int req, int result, Intent data) {
        super.onActivityResult(req,result,data);
        if (result != RESULT_OK || data == null) return;
        try {
            if (req == PICK_IMAGE && data.getData() != null) {
                Uri uri = data.getData(); takeReadPermission(data, uri);
                selectedImage = uri.toString();
                notifyUser("Reference image attached. Return to form and save/queue.");
                // Don't navigate here: keep form controls/typed text intact.
            } else if (req == EXPORT_BACKUP && data.getData() != null) {
                Uri uri = data.getData();
                utilityExecutor.execute(() -> {
                    try {
                        byte[] raw = store.exportBackup().getBytes(java.nio.charset.StandardCharsets.UTF_8);
                        try (java.io.OutputStream out = getContentResolver().openOutputStream(uri)) {
                            if (out == null) throw new Exception("Cannot create backup file");
                            out.write(raw);
                        }
                        notifyUser("Project metadata backed up; media files and API tokens are NOT included.");
                    } catch (Exception e) { notifyUser("Backup failed: " + e.getMessage()); }
                });
            } else if (req == IMPORT_BACKUP && data.getData() != null) {
                Uri uri = data.getData();
                utilityExecutor.execute(() -> {
                    try {
                        byte[] raw;
                        try (java.io.InputStream in = getContentResolver().openInputStream(uri)) {
                            if (in == null) throw new Exception("Cannot open backup");
                            raw = in.readNBytes(5_000_001);
                        }
                        if (raw.length > 5_000_000) throw new Exception("Backup file exceeds 5 MB safety limit");
                        prefs.set("queuePaused", "1");
                        store.importBackup(new String(raw, java.nio.charset.StandardCharsets.UTF_8));
                        notifyUser("Project metadata restored. Queue paused: review jobs then Resume.");
                        runOnUiThread(this::showJobs);
                    } catch (Exception e) { notifyUser("Restore failed: " + e.getMessage()); }
                });
            } else if (req == PICK_CLIPS) {
                if (data.getClipData() != null) {
                    for (int i=0; i<data.getClipData().getItemCount(); i++) {
                        Uri uri = data.getClipData().getItemAt(i).getUri();
                        takeReadPermission(data, uri);
                        store.addClip(uri.toString(), "Imported MP4 " + (i+1));
                    }
                } else if (data.getData() != null) {
                    Uri uri = data.getData(); takeReadPermission(data, uri);
                    store.addClip(uri.toString(), "Imported MP4");
                }
                showClips();
            }
        } catch (Exception e) { notifyUser(e.getMessage()); }
    }
    private void takeReadPermission(Intent intent, Uri uri) {
        try { getContentResolver().takePersistableUriPermission(uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION); }
        catch (Exception ignored) { }
    }

    private void showCharacters() {
        layout("Characters"); title("Character Vault · fictional adults");
        note("Save appearance/wardrobe and a reference image. A fixed text profile helps consistency; a seed alone does not lock identity. Reference support depends on the selected flow.");
        EditText name = field("Character name", "Character 01", false);
        EditText appearance = field("Appearance (fictional adult 18+)",
                "25-year-old adult woman, natural skin texture, realistic proportions", true);
        EditText wardrobe = field("Wardrobe profile", "casual cotton T-shirt, comfortable shorts", true);
        button("Choose reference image", this::pickImage);
        button("Save Character", () -> {
            try {
                String id = UUID.randomUUID().toString();
                JSONObject character = new JSONObject().put("id", id)
                        .put("name", name.getText().toString().trim())
                        .put("appearance", appearance.getText().toString().trim())
                        .put("wardrobe", wardrobe.getText().toString().trim())
                        .put("reference", selectedImage).put("minAge", 18);
                if (character.optString("name").isEmpty()) throw new Exception("Please enter a name");
                store.putCharacter(character); selectedImage = "";
                notifyUser("Saved character"); showCharacters();
            } catch (Exception e) { notifyUser(e.getMessage()); }
        });
        sep(); title("Saved characters");
        for (JSONObject c : store.characters()) {
            note(c.optString("name") + " • " + c.optString("appearance") +
                    (c.optString("reference").isEmpty() ? "" : " • reference attached"));
            button("Delete " + c.optString("name"), () -> {
                try { store.removeCharacter(c.optString("id")); showCharacters(); }
                catch (Exception e) { notifyUser(e.getMessage()); }
            });
        }
    }

    private List<JSONObject> charactersWithNone() {
        List<JSONObject> all = new ArrayList<>();
        all.add(new JSONObject()); all.addAll(store.characters()); return all;
    }
    private Spinner characterSelector(List<JSONObject> chars) {
        String[] names = new String[chars.size()]; names[0] = "None (use prompt only)";
        for (int i=1; i<chars.size(); i++) names[i] = chars.get(i).optString("name");
        return spinner("Character preset", names);
    }
    private String applyCharacter(JSONObject c, String scenePrompt) {
        if (c.length()==0) return scenePrompt;
        return "Fictional adult character: " + c.optString("appearance") +
                ". Wardrobe: " + c.optString("wardrobe") + ". Scene: " + scenePrompt;
    }
    private int countValue(String s, int maximum) {
        try { return Math.max(1,Math.min(maximum,Integer.parseInt(s.trim()))); }
        catch (Exception e) { return 1; }
    }
    private int parseCount(String input, int maximum, String label) throws Exception {
        final int value;
        try { value = Integer.parseInt(input.trim()); }
        catch (NumberFormatException e) { throw new Exception(label + " must be a number"); }
        if (value < 1 || value > maximum)
            throw new Exception(label + " must be between 1 and " + maximum);
        return value;
    }
    private long seedValue(String s) {
        try { return Long.parseLong(s.trim()); } catch (Exception e) { return 0; }
    }
    private long randomSeed() { return java.util.concurrent.ThreadLocalRandom.current().nextLong(1L, Integer.MAX_VALUE); }

    private void queueDiagnostic(String kind) {
        try {
            JSONObject j = makeJob(kind, "Demo", "demo", "Built-in offline TEST (not AI)",
                    randomSeed(), "", "", "");
            store.putJob(j);
            notifyUser("Diagnostic queued: " + kind + ". Open Jobs to view output.");
            pump(); showJobs();
        } catch (Exception e) { notifyUser("Diagnostic failed: " + e.getMessage()); }
    }

    private JSONObject makeJob(String kind, String backend, String flow, String prompt,
                                long seed, String reference, String characterId, String dependsOn) throws Exception {
        return new JSONObject().put("id", UUID.randomUUID().toString())
                .put("kind", kind).put("backend", backend).put("flow", flow)
                .put("prompt", prompt).put("seed", seed).put("reference", reference)
                .put("characterId", characterId).put("dependsOn", dependsOn)
                .put("status", "queued").put("message", "Waiting")
                .put("created", System.currentTimeMillis());
    }

    private void showGenerate() {
        layout("Generate"); title("Image + Video Generator");
        note("Local images + model-supported local video: select installed Nightmare flow/model. On Snapdragon 8 Gen 3 / 12GB, upstream video flow support may be experimental on 8 Gen 3; only queue video after an actual test in Nightmare. Free cloud Space is optional, quota-limited.");
        List<JSONObject> all = charactersWithNone();
        Spinner characters = characterSelector(all);
        Spinner type = spinner("Media", "image", "video");
        Spinner backend = spinner("Backend", "Local", "Cloud", "Demo");
        EditText prompt = field("Scene / generation prompt", "Photorealistic adult woman standing in soft window light, natural skin texture, realistic camera photography", true);
        EditText flow = field("Local flow: a1111 = easy images; choose actual saved video flow", "a1111", false);
        type.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (position == 1 && "a1111".equals(flow.getText().toString().trim()))
                    flow.setText(prefs.get("videoFlow", "current"));
                if (position == 0 && (flow.getText().toString().trim().isEmpty() ||
                        "current".equals(flow.getText().toString().trim())))
                    flow.setText("a1111");
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
        EditText model = field("Nightmare installed model ID (optional; overrides flow model)", "", false);
        EditText nodeParams = field("Advanced local flow node params JSON (optional; see Settings → Inspect flow)", "{}", true);
        EditText negative = field("Negative prompt (when supported)", "AI glow, plastic skin, oversmoothing", true);
        EditText duration = field("Requested seconds: cloud {{duration}}; for local use video node params", "2", false);
        EditText steps = field("Image steps (A1111 / local image route)", "20", false);
        EditText cfg = field("Guidance CFG for A1111 images", "6.0", false);
        EditText width = field("Requested width (local flow-dependent)", "512", false);
        EditText height = field("Requested height (local flow-dependent)", "768", false);
        Spinner promptMode = spinner("Batch prompt mode", "Same prompt / seed variations", "One prompt per line × variations");
        EditText count = field("Variations per prompt (1–50; serial local GPU)", "1", false);
        EditText seed = field("Start seed (0 = random per job)", "0", false);
        button("Attach reference image (optional)", this::pickImage);
        button("Queue image / video jobs", () -> {
            try {
                JSONObject character = all.get(characters.getSelectedItemPosition());
                String reference = !selectedImage.isEmpty() ? selectedImage : character.optString("reference");
                String entered = prompt.getText().toString().trim();
                if (entered.isEmpty()) throw new Exception("Enter a scene prompt");
                List<String> prompts = new ArrayList<>();
                if (promptMode.getSelectedItemPosition() == 0) {
                    prompts.add(entered);
                } else {
                    for (String line : entered.split("\\r?\\n")) {
                        if (!line.trim().isEmpty()) prompts.add(line.trim());
                    }
                }
                int variations = parseCount(count.getText().toString(), 50, "Variations");
                int total = variations * prompts.size();
                if (total > 250) throw new Exception("Maximum 250 jobs per submitted batch. Reduce prompts or variations.");
                long initial = seedValue(seed.getText().toString());
                if ("video".equals(type.getSelectedItem().toString()) &&
                        "Local".equals(backend.getSelectedItem().toString()) &&
                        ("txt2img".equals(flow.getText().toString().trim()) ||
                         "a1111".equals(flow.getText().toString().trim())))
                    throw new Exception("Video needs an actual Nightmare video flow ID, not txt2img/a1111. Use Connect AI to select a saved video flow.");
                // Parse everything first: a malformed setting must never leave half a batch queued.
                String validatedParams = new JSONObject(nodeParams.getText().toString()).toString();
                int parsedDuration = parseCount(duration.getText().toString(), 20, "Seconds");
                int parsedWidth = parseCount(width.getText().toString(), 2048, "Width");
                int parsedHeight = parseCount(height.getText().toString(), 2048, "Height");
                int parsedSteps = parseCount(steps.getText().toString(), 100, "Steps");
                double parsedCfg = Double.parseDouble(cfg.getText().toString().trim());
                if (!Double.isFinite(parsedCfg) || parsedCfg < 1 || parsedCfg > 30)
                    throw new Exception("CFG must be between 1 and 30");
                List<JSONObject> newJobs = new ArrayList<>();
                for (String scene : prompts) for (int i=0; i<variations; i++) {
                    int index = newJobs.size();
                    String text = applyCharacter(character, scene);
                    JSONObject job = makeJob(type.getSelectedItem().toString(),
                            backend.getSelectedItem().toString(), flow.getText().toString().trim(), text,
                            initial == 0 ? randomSeed() : initial + index, reference,
                            character.optString("id"), "");
                    job.put("model", model.getText().toString().trim());
                    job.put("flowParams", validatedParams);
                    job.put("negative", negative.getText().toString());
                    job.put("duration", parsedDuration);
                    job.put("width", parsedWidth);
                    job.put("height", parsedHeight);
                    job.put("steps", parsedSteps).put("cfg", parsedCfg);
                    newJobs.add(job);
                }
                store.putJobs(newJobs); // One atomic write, not 250 repeated full-file rewrites.
                selectedImage = ""; notifyUser("Queued " + total + " job(s). Foreground worker requested.");
                pump(); showJobs();
            } catch (Exception e) { notifyUser(e.getMessage()); }
        });
        sep(); note("Tips: open Connect AI for one-tap REAL image test using Nightmare's documented Automatic1111 endpoint. Video needs a supported saved flow. Settings → Test local API lists installed models and available flows. Video requires a video-capable flow, e.g. one you saved from a supported Video flow; txt2img outputs an image, not an MP4. Image reference is optional; for continuity use Image-to-video flow when supported.");
    }

    private void showStoryboard() {
        layout("Storyboard"); title("Long-film storyboard");
        note("Storyboard supports up to 1,800 queued scenes per project. Local video renders serially, often taking minutes per clip. Scene references can reduce identity drift, not eliminate it. Export only after clips finish.");
        List<JSONObject> all = charactersWithNone();
        Spinner chars = characterSelector(all);
        Spinner backend = spinner("Backend", "Local", "Cloud", "Demo");
        EditText flow = field("Nightmare video flow ID", "current", false);
        EditText videoParams = field("Advanced local video node params JSON (e.g. seconds; depends on actual flow node ID)", "{}", true);
        EditText duration = field("Target assembled length in seconds (plan only; up to 1,800 clips)", "60", false);
        EditText perClip = field("Expected seconds per clip (model-specific; 2s suggested for 8 Gen 3)", "2", false);
        EditText scenes = field("One scene per line; repeat these scene prompts as needed", "Scene 1: adult woman looks toward camera and smiles naturally.\nScene 2: adult woman turns toward window, camera gently tracks left.\nScene 3: woman takes a step forward, soft natural movement.", true);
        Spinner chain = spinner("Clip continuity", "Independent scenes (faster to retry)", "Chain previous clip's last frame");
        button("Queue storyboard clips", () -> {
            try {
                JSONObject c = all.get(chars.getSelectedItemPosition());
                String[] lines = scenes.getText().toString().split("\\r?\\n");
                List<String> scenePrompts = new ArrayList<>();
                for (String line : lines) if (!line.trim().isEmpty()) scenePrompts.add(line.trim());
                if (scenePrompts.isEmpty()) throw new Exception("Enter at least one scene");
                int clipSeconds = parseCount(perClip.getText().toString(), 30, "Clip seconds");
                int targetSeconds = parseCount(duration.getText().toString(), 36000, "Total seconds");
                int count = (int)Math.ceil(targetSeconds / (double)clipSeconds);
                if (count > 1800) throw new Exception("Up to 1,800 clips in one project. Increase seconds per clip or split across projects.");
                if ("Local".equals(backend.getSelectedItem().toString()) &&
                        flow.getText().toString().trim().isEmpty())
                    throw new Exception("Local video requires a saved Nightmare video flow ID");
                String validatedParams = new JSONObject(videoParams.getText().toString()).toString();
                String projectId = UUID.randomUUID().toString();
                String preceding = "";
                List<JSONObject> planned = new ArrayList<>();
                for (int i=0; i<count; i++) {
                    String depends = chain.getSelectedItemPosition()==1 ? preceding : "";
                    String line = scenePrompts.get(i % scenePrompts.size());
                    JSONObject j = makeJob("video",backend.getSelectedItem().toString(),
                            flow.getText().toString().trim(),applyCharacter(c,line),randomSeed(),
                            c.optString("reference"), c.optString("id"),depends);
                    j.put("projectId", projectId).put("sceneIndex", i + 1)
                            .put("duration", Math.min(clipSeconds, targetSeconds - i*clipSeconds))
                            .put("flowParams", validatedParams);
                    planned.add(j); preceding = j.optString("id");
                }
                store.putJobs(planned); // Single atomic commit keeps scene dependencies intact.
                notifyUser("Queued " + count + " scenes. Estimated length only; model durations may differ.");
                pump(); showJobs();
            } catch (Exception e) { notifyUser(e.getMessage()); }
        });
        note("Long-film MP4 export: when short clips finish, open Clips → Export this project (in scene order). Actual length depends on model's node Seconds setting and may differ from target. Full transitions, identity verification and true parallel GPU inference remain future work.");
    }

    private void updateJob(JSONObject job, String status, String message, String result) throws Exception {
        job.put("status", status).put("message", message);
        if (result != null) job.put("resultUri", result);
        store.putJob(job);
    }
    private void pump() {
        if ("1".equals(prefs.get("queuePaused", "0"))) return;
        // Android foreground service owns the queue even if the Activity goes away.
        try { RenderService.requestRun(this); }
        catch (Exception e) { notifyUser("Cannot start render worker: " + e.getMessage()); }
    }

    private void showJobs() {
        layout("Jobs"); title("Batch / Render Queue");
        note("Saved, dependency-aware serial queue. Foreground notification runs after leaving this screen, subject to Android limits. Interrupted remote calls are not automatically retried to avoid duplicates.");
        boolean paused = "1".equals(prefs.get("queuePaused","0"));
        button(paused ? "Resume queue" : "Pause after current job", () -> {
            prefs.set("queuePaused",paused?"0":"1"); if (paused) pump(); showJobs();
        });
        button("Refresh jobs", this::showJobs);
        button("Cancel all waiting (not running) jobs", () -> {
            new android.app.AlertDialog.Builder(this)
                    .setTitle("Cancel waiting jobs?")
                    .setMessage("This will cancel queued jobs but will not interrupt a render already running, or delete finished results. Continue?")
                    .setNegativeButton("Keep jobs", null)
                    .setPositiveButton("Cancel queued", (dialog, which) -> {
                        try { int count = store.cancelWaitingJobs();
                            notifyUser("Cancelled " + count + " waiting jobs"); showJobs();
                        } catch (Exception e) { notifyUser(e.getMessage()); }
                    }).show();
        });
        if (!paused) button("Start / continue worker", () -> { pump(); notifyUser("Worker requested"); });
        List<JSONObject> allJobs = store.jobs();
        int queued = 0, done = 0, failed = 0, running = 0;
        for (JSONObject j : allJobs) {
            switch (j.optString("status")) {
                case "queued": queued++; break;
                case "done": done++; break;
                case "running": running++; break;
                default: failed++; break;
            }
        }
        note("Total: " + allJobs.size() + " | queued " + queued + " | running " + running +
                " | done " + done + " | needs attention " + failed);
        int jobPages = Math.max(1, (allJobs.size() + PAGE_SIZE - 1)/PAGE_SIZE);
        jobsPage = Math.min(jobsPage, jobPages - 1);
        note("Page " + (jobsPage + 1) + " / " + jobPages + " · showing up to " + PAGE_SIZE + " jobs");
        if (jobsPage > 0) button("◀ Previous jobs", () -> { jobsPage--; showJobs(); });
        if (jobsPage + 1 < jobPages) button("Next jobs ▶", () -> { jobsPage++; showJobs(); });
        int from = jobsPage * PAGE_SIZE;
        int to = Math.min(allJobs.size(), from + PAGE_SIZE);
        for (int idx = from; idx < to; idx++) {
            JSONObject j = allJobs.get(idx);
            sep();
            String p = j.optString("prompt");
            note(j.optString("kind").toUpperCase() + " · " + j.optString("backend") +
                    " · " + j.optString("status").toUpperCase() +
                    "\n" + (p.length()>100 ? p.substring(0,100)+"…" : p) +
                    "\n" + j.optString("message"));
            String state = j.optString("status");
            if ("queued".equals(state) || "blocked".equals(state) ||
                    "interrupted".equals(state) || "error".equals(state)) {
                button("Cancel this job", () -> {
                    try { if (store.cancelWaitingJob(j.optString("id"))) notifyUser("Job cancelled");
                          else notifyUser("Job is running or no longer cancellable");
                          showJobs();
                    } catch (Exception e) { notifyUser(e.getMessage()); }
                });
            }
            if ("done".equals(state)) button("Open result", () -> {
                try {
                    Uri uri = Uri.parse(j.optString("resultUri"));
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setDataAndType(uri, "video".equals(j.optString("kind")) ? "video/mp4" : "image/*");
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); startActivity(intent);
                } catch (Exception e) { notifyUser(e.getMessage()); }
            });
            if ("error".equals(state) || "cancelled".equals(state) || "blocked".equals(state) || "interrupted".equals(state)) button("Retry (possible duplicate if interrupted)", () -> {
                try { updateJob(j,"queued","Retry queued",null); pump(); showJobs(); }
                catch (Exception e) { notifyUser(e.getMessage()); }
            });
            if ("queued".equals(state)) button("Cancel queued job", () -> {
                try { updateJob(j,"cancelled","Cancelled before execution",null); showJobs(); }
                catch (Exception e) { notifyUser(e.getMessage()); }
            });
        }
    }

    private void showClips() {
        layout("Clips"); title("MP4 Clip Timeline");
        note("Imported and generated videos appear here in timeline order. Reorder and join matching MP4 clips into one longer video. Different codecs/resolutions require desktop/Termux FFmpeg normalization.");
        button("Import MP4 clips (multiple)", this::pickClips);
        // Each storyboard has its own project ID. Export only that project's clips,
        // in scene order, rather than accidentally merging clips from other projects.
        java.util.Map<String, List<JSONObject>> projects = new java.util.LinkedHashMap<>();
        for (JSONObject job : store.jobs()) {
            if (!"video".equals(job.optString("kind"))) continue;
            String id = job.optString("projectId");
            if (id.isEmpty()) continue;
            if (!projects.containsKey(id)) projects.put(id, new ArrayList<>());
            projects.get(id).add(job);
        }
        if (!projects.isEmpty()) {
            sep(); title("Storyboard projects · export in order");
            for (java.util.Map.Entry<String, List<JSONObject>> entry : projects.entrySet()) {
                List<JSONObject> scenes = entry.getValue();
                scenes.sort((x,y) -> Integer.compare(x.optInt("sceneIndex"),y.optInt("sceneIndex")));
                int complete = 0;
                for (JSONObject scene : scenes) if ("done".equals(scene.optString("status")) &&
                        !scene.optString("resultUri").isEmpty()) complete++;
                String id = entry.getKey();
                int issues = 0;
                for (JSONObject scene : scenes) {
                    String state = scene.optString("status");
                    if ("error".equals(state) || "blocked".equals(state) ||
                            "interrupted".equals(state) || "cancelled".equals(state)) issues++;
                }
                note("Project " + id.substring(0, Math.min(8,id.length())) + " · " + complete + "/" + scenes.size() + " clips ready · " + issues + " need attention");
                if (issues > 0) button("Retry failed/interrupted scenes in this project", () -> {
                    try {
                        int retried = store.retryProject(id);
                        notifyUser("Requeued " + retried + " scenes; interrupted jobs can duplicate provider output.");
                        pump(); showClips();
                    } catch (Exception e) { notifyUser("Retry failed: " + e.getMessage()); }
                });
                Button export = button("Export this project → MP4", () -> {
                    notifyUser("Joining project clips, keep app open…");
                    utilityExecutor.execute(() -> {
                        try {
                            List<String> ordered = new ArrayList<>();
                            for (JSONObject scene : scenes) {
                                if (!"done".equals(scene.optString("status")))
                                    throw new Exception("Project contains incomplete clips");
                                ordered.add(scene.optString("resultUri"));
                            }
                            String result = ClipStitcher.join(this, ordered);
                            store.addClip(result, "Project " + id.substring(0,Math.min(8,id.length())) + " movie");
                            notifyUser("Project exported to Movies/RealFrameStudio");
                            runOnUiThread(this::showClips);
                        } catch (Exception e) { notifyUser("Project export failed: " + e.getMessage()); }
                    });
                });
                export.setEnabled(complete == scenes.size() && complete >= 2);
            }
        }
        sep(); title("All clips · manual timeline");
        List<JSONObject> items = store.clips();
        int clipPages = Math.max(1, (items.size() + PAGE_SIZE - 1)/PAGE_SIZE);
        clipsPage = Math.min(clipsPage, clipPages - 1);
        note(items.size() + " timeline items · page " + (clipsPage+1) + "/" + clipPages);
        if (clipsPage > 0) button("◀ Previous clips", () -> { clipsPage--; showClips(); });
        if (clipsPage + 1 < clipPages) button("Next clips ▶", () -> { clipsPage++; showClips(); });
        int clipFrom = clipsPage * PAGE_SIZE;
        for (int i=clipFrom; i<Math.min(items.size(), clipFrom + PAGE_SIZE); i++) {
            final int pos = i;
            JSONObject item = items.get(i); sep();
            note((i+1) + ". " + item.optString("title"));
            button("▲ Move up", () -> {
                if (pos==0) return;
                java.util.Collections.swap(items,pos,pos-1);
                try { store.replaceClips(items); showClips(); } catch (Exception e) { notifyUser(e.getMessage()); }
            });
            button("✕ Remove", () -> {
                items.remove(pos);
                try { store.replaceClips(items); showClips(); } catch (Exception e) { notifyUser(e.getMessage()); }
            });
        }
        button("🎞 Merge clips → MP4", () -> {
            if (items.size()<2) { notifyUser("Select at least two clips"); return; }
            notifyUser("Starting join. Keep app active.");
            utilityExecutor.execute(() -> {
                try {
                    List<String> paths = new ArrayList<>();
                    for (JSONObject item : store.clips()) paths.add(item.optString("uri"));
                    String result = ClipStitcher.join(this, paths);
                    store.addClip(result,"Merged film");
                    notifyUser("MP4 film saved to Movies/RealFrameStudio");
                    runOnUiThread(this::showClips);
                } catch (Exception e) { notifyUser("Join failed: " + e.getMessage()); }
            });
        });
        note("Native join does not transcode: source clips must match codec, dimensions and audio format. For mismatched clips use the included tools/stitch.py (FFmpeg) on Termux/PC.");
    }

    private void exportBackupPicker() {
        Intent save = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        save.addCategory(Intent.CATEGORY_OPENABLE);
        save.setType("application/json");
        save.putExtra(Intent.EXTRA_TITLE, "RealFrame_Project_Backup.json");
        startActivityForResult(save, EXPORT_BACKUP);
    }

    private void restoreBackupPicker() {
        new android.app.AlertDialog.Builder(this)
            .setTitle("Replace saved project metadata?")
            .setMessage("Restore replaces characters, jobs and clip lists. Running jobs must finish first; waiting jobs will be paused. Files and API tokens are NOT in backups. Old media references may no longer work. Continue?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Choose backup", (dialog, which) -> {
                Intent select = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                select.addCategory(Intent.CATEGORY_OPENABLE);
                select.setType("application/json");
                startActivityForResult(select, IMPORT_BACKUP);
            }).show();
    }

    private void showSettings() {
        layout("Settings"); title("Renderers / Connections");
        note("PROJECT PROTECTION: export your queue, fictional character descriptions and clip metadata before resetting/reinstalling. No generated images, videos or API tokens are in this file. Existing photo/video content URIs may not survive a reinstall.");
        button("Export project metadata backup (.json)", this::exportBackupPicker);
        button("Restore project metadata backup (replaces current)", this::restoreBackupPicker);
        sep();
        note("LOCAL: install Nightmare Mobile and an image/video model supported by your Snapdragon; then Settings → Advanced → API → Listen on local network. Copy displayed URL and token. The upstream video guide lists some 8 Gen 3 flows as alpha/experimental; confirm in Nightmare's model list. 127.0.0.1 may work on the same phone; otherwise use its shown LAN URL.");
        EditText localUrl = field("Nightmare local API base URL", prefs.get("localUrl","http://127.0.0.1:8820"),false);
        EditText localToken = field("Nightmare API token (leave blank to keep stored)", "",false);
        sep(); title("Optional Hugging Face Gradio Spaces");
        note("Read the Space's 'Use via API' page and paste its URL, endpoint and exact JSON argument array. Placeholders: {{prompt}}, {{negative}}, {{seed}}, {{duration}}, {{width}}, {{height}}, {{image_path}}. Video seconds depend on the Space. No guaranteed free GPU; provider quotas and rules apply.");
        EditText imageSpace = field("Image Space URL (https://owner-space.hf.space)",prefs.get("imageSpace",""),false);
        EditText imageEndpoint = field("Image endpoint",prefs.get("imageEndpoint","/predict"),false);
        EditText imageArgs = field("Image API data array template",prefs.get("imageArgs","[\"{{prompt}}\"]"),true);
        EditText videoSpace = field("Video Space URL (https://owner-space.hf.space)",prefs.get("videoSpace",""),false);
        EditText videoEndpoint = field("Video endpoint",prefs.get("videoEndpoint","/predict"),false);
        EditText videoArgs = field("Video API data array template",prefs.get("videoArgs","[\"{{prompt}}\"]"),true);
        EditText hfToken = field("Hugging Face access token (optional; leave blank to keep)","",false);
        button("Save settings", () -> {
            try {
                new org.json.JSONArray(imageArgs.getText().toString());
                new org.json.JSONArray(videoArgs.getText().toString());
                prefs.set("localUrl",localUrl.getText().toString().trim());
                prefs.set("imageSpace",imageSpace.getText().toString().trim());
                prefs.set("imageEndpoint",imageEndpoint.getText().toString().trim());
                prefs.set("imageArgs",imageArgs.getText().toString());
                prefs.set("videoSpace",videoSpace.getText().toString().trim());
                prefs.set("videoEndpoint",videoEndpoint.getText().toString().trim());
                prefs.set("videoArgs",videoArgs.getText().toString());
                if (!localToken.getText().toString().isEmpty())
                    prefs.setToken("nightmare",localToken.getText().toString().trim());
                if (!hfToken.getText().toString().isEmpty())
                    prefs.setToken("huggingface",hfToken.getText().toString().trim());
                notifyUser("Settings saved (tokens encrypted)");
            } catch (Exception e) { notifyUser(e.getMessage()); }
        });
        button("Test local API: installed models + flows", () -> {
            notifyUser("Checking... (save settings first)");
            utilityExecutor.execute(() -> {
                try { String details = renderer.localInfo();
                    runOnUiThread(() -> new android.app.AlertDialog.Builder(this)
                            .setTitle("Nightmare API reply").setMessage(details)
                            .setPositiveButton("OK",null).show());
                } catch (Exception e) { notifyUser("Local test failed: " + e.getMessage()); }
            });
        });
        EditText inspectFlow = field("Nightmare flow ID to inspect node options", "current", false);
        button("Inspect selected local flow → node IDs / settings", () -> {
            notifyUser("Reading flow graph…");
            utilityExecutor.execute(() -> {
                try {
                    String json = renderer.localFlowInfo(inspectFlow.getText().toString().trim());
                    String shown = json.length() > 16000 ? json.substring(0,16000) + "\n…(truncated)" : json;
                    runOnUiThread(() -> new android.app.AlertDialog.Builder(this)
                            .setTitle("Flow node configuration").setMessage(shown)
                            .setPositiveButton("OK",null).show());
                } catch (Exception e) { notifyUser("Flow inspection failed: " + e.getMessage()); }
            });
        });
        button("Open Hugging Face Spaces documentation", () -> startActivity(new Intent(
                Intent.ACTION_VIEW, Uri.parse("https://huggingface.co/docs/hub/en/spaces-api-endpoints"))));
        button("Inspect image Space API (save settings first)", () -> inspectSpace(false));
        button("Inspect video Space API (save settings first)", () -> inspectSpace(true));
        button("Open Nightmare Mobile setup guide", () -> startActivity(new Intent(
                Intent.ACTION_VIEW, Uri.parse("https://abrahampaulj.github.io/nightmare-mobile/reference/api/"))));
        note("SECURITY: Never expose the Nightmare HTTP API to the public internet. Use only trusted LAN addresses and protect its token. This app enables cleartext HTTP for its local API; use HTTPS for cloud.");
    }
    private void inspectSpace(boolean video) {
        notifyUser("Checking configured Space OpenAPI schema…");
        utilityExecutor.execute(() -> {
            try {
                String reply = renderer.cloudInfo(video);
                String shown = reply.length() > 14000 ? reply.substring(0,14000) + "\n…(truncated)" : reply;
                runOnUiThread(() -> new android.app.AlertDialog.Builder(this)
                        .setTitle(video ? "Video Space API" : "Image Space API")
                        .setMessage(shown).setPositiveButton("OK",null).show());
            } catch (Exception e) { notifyUser("Space API check failed: " + e.getMessage()); }
        });
    }

}
