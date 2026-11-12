package studio.realframe;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.provider.MediaStore;
import android.content.ContentValues;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

/** Two real API adapters: local Nightmare Mobile /run and configurable Gradio Space. */
public final class Renderer {
    private final Context context;
    private final SecretVault settings;

    public Renderer(Context context, SecretVault settings) {
        this.context = context.getApplicationContext(); this.settings = settings;
    }

    private HttpURLConnection connection(String url, String method, String token) throws Exception {
        URL u = new URL(url);
        if (!"http".equals(u.getProtocol()) && !"https".equals(u.getProtocol()))
            throw new IOException("Only HTTP(S) API URLs accepted");
        HttpURLConnection conn = (HttpURLConnection) u.openConnection();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(1200000); // a queued video job can take many minutes
        conn.setRequestProperty("User-Agent", "RealFrameStudio/1.1 Android");
        if (!token.isEmpty()) conn.setRequestProperty("Authorization", "Bearer " + token);
        return conn;
    }

    private String body(HttpURLConnection c) throws Exception {
        int status = c.getResponseCode();
        InputStream stream = status < 400 ? c.getInputStream() : c.getErrorStream();
        String s = stream == null ? "" : new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        if (status >= 400) throw new IOException("HTTP " + status + ": " + s.substring(0, Math.min(s.length(), 600)));
        return s;
    }

    public String getJson(String url, String token) throws Exception {
        HttpURLConnection c = connection(url, "GET", token);
        try { return body(c); } finally { c.disconnect(); }
    }

    private String postJson(String url, String token, String payload) throws Exception {
        HttpURLConnection c = connection(url, "POST", token);
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        try (OutputStream os = c.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }
        try { return body(c); } finally { c.disconnect(); }
    }

    /** Nightmare also exposes Automatic1111-compatible image API.
     * Its authentication is HTTP Basic, unlike Nightmare's /run API (Bearer).
     * See https://abrahampaulj.github.io/nightmare-mobile/reference/api/ .
     */
    private HttpURLConnection a1111Connection(String url, String method) throws Exception {
        HttpURLConnection conn = connection(url, method, "");
        String token = settings.token("nightmare");
        if (!token.isEmpty()) {
            String value = "realframe:" + token;
            String encoded = Base64.encodeToString(value.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
            conn.setRequestProperty("Authorization", "Basic " + encoded);
        }
        return conn;
    }

    public String localWebUiModels() throws Exception {
        String base = cleanBase(settings.get("localUrl", "http://127.0.0.1:8820"));
        HttpURLConnection conn = a1111Connection(base + "/sdapi/v1/sd-models", "GET");
        try { return body(conn); } finally { conn.disconnect(); }
    }

    private String postA1111(String url, JSONObject json) throws Exception {
        HttpURLConnection conn = a1111Connection(url, "POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        try (OutputStream out = conn.getOutputStream()) {
            out.write(json.toString().getBytes(StandardCharsets.UTF_8));
        }
        try { return body(conn); } finally { conn.disconnect(); }
    }

    /** Generates a real locally computed image, provided Nightmare + model is installed.
     * This route needs no custom flow graph setup.
     */
    private String fromAutomatic1111(JSONObject job) throws Exception {
        if ("video".equals(job.optString("kind")))
            throw new IOException("Automatic1111 image API cannot generate video. Select a video flow.");
        String base = cleanBase(settings.get("localUrl", "http://127.0.0.1:8820"));
        JSONObject args = new JSONObject()
                .put("prompt", job.optString("prompt"))
                .put("negative_prompt", job.optString("negative"))
                .put("seed", job.optLong("seed", -1))
                .put("width", job.optInt("width", 512))
                .put("height", job.optInt("height", 768))
                .put("steps", job.optInt("steps", 20))
                .put("cfg_scale", job.optDouble("cfg", 6.0))
                .put("batch_size", 1);
        JSONObject result = new JSONObject(postA1111(base + "/sdapi/v1/txt2img", args));
        JSONArray images = result.optJSONArray("images");
        if (images == null || images.length() == 0)
            throw new IOException("Nightmare image API returned no images. Check selected model.");
        String encoded = images.optString(0, "");
        if (encoded.startsWith("data:") && encoded.contains(","))
            encoded = encoded.substring(encoded.indexOf(',') + 1);
        if (encoded.isEmpty()) throw new IOException("Empty image from local API");
        byte[] bytes = Base64.decode(encoded, Base64.DEFAULT);
        // Validate before creating a Gallery entry.
        String mime = MediaSignature.detect(bytes);
        if (!MediaSignature.matchesKind(mime, "image"))
            throw new IOException("Nightmare returned a non-image payload");
        return saveBytes(bytes, mime, "image_" + System.currentTimeMillis() + MediaSignature.extension(mime));
    }

    /** Local flow identifiers for first-run selection. Supports array and wrapped API shapes. */
    public java.util.List<String> localFlowIds() throws Exception {
        String base = cleanBase(settings.get("localUrl", "http://127.0.0.1:8820"));
        String json = getJson(base + "/flows", settings.token("nightmare"));
        Object root = new org.json.JSONTokener(json).nextValue();
        java.util.LinkedHashSet<String> found = new java.util.LinkedHashSet<>();
        collectFlowIds(root, found, 0);
        return new java.util.ArrayList<>(found);
    }

    private static void collectFlowIds(Object root, java.util.Set<String> out, int depth) {
        if (root == null || depth > 3 || out.size() >= 120) return;
        if (root instanceof JSONArray) {
            JSONArray items = (JSONArray) root;
            for (int i=0; i<items.length(); i++) collectFlowIds(items.opt(i), out, depth + 1);
        } else if (root instanceof JSONObject) {
            JSONObject value = (JSONObject) root;
            for (String key : new String[]{"id", "flow", "flow_id"}) {
                String id = value.optString(key, "").trim();
                if (!id.isEmpty()) { out.add(id); break; }
            }
            for (String key : new String[]{"flows", "items", "recipes", "saved", "data"})
                collectFlowIds(value.opt(key), out, depth + 1);
        } else if (root instanceof String) {
            String value = ((String)root).trim();
            if (!value.isEmpty()) out.add(value);
        }
    }

    public String localInfo() throws Exception {
        String url = cleanBase(settings.get("localUrl", "http://127.0.0.1:8820"));
        String token = settings.token("nightmare");
        String info = getJson(url + "/info", "");
        String flows = getJson(url + "/flows", token);
        String models = getJson(url + "/models", token);
        return "INFO " + info + "\n\nINSTALLED MODELS " + models + "\n\nFLOWS " + flows;
    }

    public String localFlowInfo(String flowId) throws Exception {
        if (flowId.isEmpty() || flowId.contains("/") || flowId.contains("?") || flowId.contains("#"))
            throw new IOException("Invalid flow ID; choose from /flows, e.g. current or saved:MyFlow");
        String base = cleanBase(settings.get("localUrl", "http://127.0.0.1:8820"));
        return getJson(base + "/flows/" + URLEncoder.encode(flowId, "UTF-8"), settings.token("nightmare"));
    }

    private String cleanBase(String base) {
        String result = base.trim();
        while (result.endsWith("/")) result = result.substring(0, result.length()-1);
        return result;
    }

    public String generate(JSONObject job) throws Exception {
        String backend = job.optString("backend", "Local");
        if ("Demo".equals(backend)) return fromDemo(job);
        if ("Cloud".equals(backend)) return fromGradio(job);
        if ("image".equals(job.optString("kind")) &&
                "a1111".equals(job.optString("flow"))) return fromAutomatic1111(job);
        return fromNightmare(job);
    }

    /** Built-in diagnostics: visible test artefacts, NEVER presented as AI output. */
    private String fromDemo(JSONObject job) throws Exception {
        if ("video".equals(job.optString("kind"))) {
            byte[] bytes = Base64.decode(DEMO_MP4_BASE64, Base64.DEFAULT);
            return saveBytes(bytes, "video/mp4", "realframe_TEST_NOT_AI_" + System.currentTimeMillis() + ".mp4");
        }
        Bitmap image = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(image);
        canvas.drawColor(android.graphics.Color.rgb(20, 27, 51));
        android.graphics.Paint p = new android.graphics.Paint(3);
        p.setTextSize(35); p.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        p.setColor(android.graphics.Color.WHITE);
        canvas.drawText("RealFrame Studio", 38, 190, p);
        p.setTextSize(26); p.setTypeface(android.graphics.Typeface.DEFAULT);
        canvas.drawText("TEST IMAGE • NOT AI", 38, 245, p);
        canvas.drawText("Gallery + queue OK", 38, 290, p);
        canvas.drawText("\u2713 Offline diagnostic", 38, 335, p);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            image.compress(Bitmap.CompressFormat.PNG, 100, out);
            return saveBytes(out.toByteArray(), "image/png", "realframe_TEST_NOT_AI_" + System.currentTimeMillis() + ".png");
        } finally { image.recycle(); }
    }
    private static final String DEMO_MP4_BASE64 =
            "AAAAIGZ0eXBpc29tAAACAGlzb21pc28yYXZjMW1wNDEAAAOBbW9vdgAAAGxtdmhkAAAAAAAAAAAAAAAAAAAD6AAAB9AAAQAAAQAAAAAAAAAAAAAAAAEAAAAA"
            + "AAAAAAAAAAAAAAABAAAAAAAAAAAAAAAAAABAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAgAAAqx0cmFrAAAAXHRraGQAAAADAAAAAAAAAAAAAAAB"
            + "AAAAAAAAB9AAAAAAAAAAAAAAAAAAAAAAAAEAAAAAAAAAAAAAAAAAAAABAAAAAAAAAAAAAAAAAABAAAAAAUAAAADwAAAAAAAkZWR0cwAAABxlbHN0AAAAAAAA"
            + "AAEAAAfQAAAAAAABAAAAAAIkbWRpYQAAACBtZGhkAAAAAAAAAAAAAAAAAAAwAAAAYABVxAAAAAAALWhkbHIAAAAAAAAAAHZpZGUAAAAAAAAAAAAAAABWaWRl"
            + "b0hhbmRsZXIAAAABz21pbmYAAAAUdm1oZAAAAAEAAAAAAAAAAAAAACRkaW5mAAAAHGRyZWYAAAAAAAAAAQAAAAx1cmwgAAAAAQAAAY9zdGJsAAAAt3N0c2QA"
            + "AAAAAAAAAQAAAKdhdmMxAAAAAAAAAAEAAAAAAAAAAAAAAAAAAAAAAUAA8ABIAAAASAAAAAAAAAABFUxhdmM2MS4xOS4xMDEgbGlieDI2NAAAAAAAAAAAAAAA"
            + "GP//AAAALWF2Y0MBQsAM/+EAFmdCwAzaBQfsBEAAAAMAQAAABgPFCqgBAARozg/IAAAAEHBhc3AAAAABAAAAAQAAABRidHJ0AAAAAAAAETQAAAAAAAAAGHN0"
            + "dHMAAAAAAAAAAQAAABgAAAQAAAAAFHN0c3MAAAAAAAAAAQAAAAEAAAAcc3RzYwAAAAAAAAABAAAAAQAAABgAAAABAAAAdHN0c3oAAAAAAAAAAAAAABgAAANQ"
            + "AAAACwAAAAsAAAALAAAACwAAAAsAAAALAAAACwAAAAsAAAALAAAACwAAAAsAAAALAAAACwAAAAsAAAALAAAACwAAAAsAAAALAAAACwAAAAsAAAALAAAACwAA"
            + "AAsAAAAUc3RjbwAAAAAAAAABAAADsQAAAGF1ZHRhAAAAWW1ldGEAAAAAAAAAIWhkbHIAAAAAAAAAAG1kaXJhcHBsAAAAAAAAAAAAAAAALGlsc3QAAAAkqXRv"
            + "bwAAABxkYXRhAAAAAQAAAABMYXZmNjEuNy4xMDMAAAAIZnJlZQAABFVtZGF0AAACVAYF//9Q3EXpvebZSLeWLNgg2SPu73gyNjQgLSBjb3JlIDE2NCByMzEw"
            + "OCAzMWUxOWY5IC0gSC4yNjQvTVBFRy00IEFWQyBjb2RlYyAtIENvcHlsZWZ0IDIwMDMtMjAyMyAtIGh0dHA6Ly93d3cudmlkZW9sYW4ub3JnL3gyNjQuaHRt"
            + "bCAtIG9wdGlvbnM6IGNhYmFjPTAgcmVmPTEgZGVibG9jaz0wOjA6MCBhbmFseXNlPTA6MCBtZT1kaWEgc3VibWU9MCBwc3k9MSBwc3lfcmQ9MS4wMDowLjAw"
            + "IG1peGVkX3JlZj0wIG1lX3JhbmdlPTE2IGNocm9tYV9tZT0xIHRyZWxsaXM9MCA4eDhkY3Q9MCBjcW09MCBkZWFkem9uZT0yMSwxMSBmYXN0X3Bza2lwPTEg"
            + "Y2hyb21hX3FwX29mZnNldD0wIHRocmVhZHM9NyBsb29rYWhlYWRfdGhyZWFkcz0xIHNsaWNlZF90aHJlYWRzPTAgbnI9MCBkZWNpbWF0ZT0xIGludGVybGFj"
            + "ZWQ9MCBibHVyYXlfY29tcGF0PTAgY29uc3RyYWluZWRfaW50cmE9MCBiZnJhbWVzPTAgd2VpZ2h0cD0wIGtleWludD0yNTAga2V5aW50X21pbj0xMiBzY2Vu"
            + "ZWN1dD0wIGludHJhX3JlZnJlc2g9MCByYz1jcmYgbWJ0cmVlPTAgY3JmPTIzLjAgcWNvbXA9MC42MCBxcG1pbj0wIHFwbWF4PTY5IHFwc3RlcD00IGlwX3Jh"
            + "dGlvPTEuNDAgYXE9MACAAAAA9GWIhDoRigACMXHAAORwBk5OTk5OTk5OTk5OTk5OTk5OTk66666666666666666666666666666666666666666666666666"
            + "666666666666666666666666666666666666666666666666666666666666666666666666666666666666666666666666666666666666666666666666"
            + "666666666666666666666666666666666666666666666666666666666666666666666666666666666666666666666666666666666666668AAAAHQZog"
            + "NoAlsAAAAAdBmkA2gCWwAAAAB0GaYDaAJbAAAAAHQZqAOoAlsAAAAAdBmqA6gCWwAAAAB0GawDqAJbAAAAAHQZrgOoAlsAAAAAdBmwA6gCWwAAAAB0GbIDqA"
            + "JbAAAAAHQZtAOoAlsAAAAAdBm2A6gCWwAAAAB0GbgDqAJbAAAAAHQZugOoAlsAAAAAdBm8A6gCWwAAAAB0Gb4DqAJbAAAAAHQZoAOoAlsAAAAAdBmiA6gCWw"
            + "AAAAB0GaQDqAJbAAAAAHQZpgOoAlsAAAAAdBmoA6gCWwAAAAB0GaoDqAJbAAAAAHQZrAOoAlsAAAAAdBmuA6gCWw";

    private String fromNightmare(JSONObject job) throws Exception {
        String url = cleanBase(settings.get("localUrl", "http://127.0.0.1:8820"));
        String token = settings.token("nightmare");
        String prompt = job.optString("prompt");
        JSONObject args = new JSONObject()
                .put("flow", job.optString("flow", "txt2img"))
                .put("prompt", prompt)
                .put("seed", job.optLong("seed", 0L));
        if (!job.optString("model").isEmpty()) args.put("model", job.optString("model"));
        String flowParams = job.optString("flowParams", "{}");
        JSONObject nodeParams = new JSONObject(flowParams);
        if (nodeParams.length() > 0) args.put("params", nodeParams);
        if (!job.optString("negative").isEmpty()) args.put("negative", job.optString("negative"));
        if (job.has("width") && job.has("height")) {
            args.put("width", job.optInt("width"));
            args.put("height", job.optInt("height"));
        }
        String reference = referenceForJob(job);
        if (!reference.isEmpty()) {
            // Nightmare's /run accepts base64 on an image node in the selected flow.
            args.put("images", new JSONObject().put("image", encodeImage(reference)));
        }
        String text = postJson(url + "/run", token, args.toString());
        JSONObject result = new JSONObject(text);
        JSONArray results = result.optJSONArray("results");
        if (results == null || results.length() == 0) throw new IOException("No outputs returned: " + text);
        JSONObject first = null;
        for (int i=0; i<results.length(); i++) {
            JSONObject candidate = results.optJSONObject(i);
            if (candidate == null) continue;
            String path = candidate.optString("url", "").toLowerCase(Locale.ROOT);
            if ("video".equals(job.optString("kind")) && path.contains(".mp4")) { first = candidate; break; }
            if (!"video".equals(job.optString("kind")) &&
                    (path.contains(".png") || path.contains(".jpg") || path.contains(".webp"))) { first = candidate; break; }
        }
        if (first == null) throw new IOException("Local flow did not return expected " + job.optString("kind") +
                " media. Check video flow ID and model; got: " + text.substring(0, Math.min(300,text.length())));
        String path = first.optString("url");
        String download = path.startsWith("http") ? path : url + (path.startsWith("/") ? "" : "/") + path;
        return downloadToGallery(download, token, job.optString("kind"), "Local");
    }

    private String referenceForJob(JSONObject job) throws Exception {
        String ref = job.optString("reference");
        String previous = job.optString("dependsOn");
        if (!previous.isEmpty()) {
            StudioStore store = new StudioStore(context);
            JSONObject ancestor = store.findJob(previous);
            if (ancestor == null || !"done".equals(ancestor.optString("status")))
                throw new IOException("Dependent clip not finished: " + previous);
            String lastVideo = ancestor.optString("resultUri");
            if (lastVideo.isEmpty()) throw new IOException("Previous clip has no result file");
            // Grab a near-final frame to carry appearance/motion to next clip.
            return extractEndFrame(lastVideo);
        }
        return ref;
    }

    private String extractEndFrame(String uriString) throws Exception {
        Uri uri = Uri.parse(uriString);
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(context, uri);
            String ms = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            long micros = Math.max(0, Long.parseLong(ms == null ? "0" : ms)*1000 - 100000);
            Bitmap frame = retriever.getFrameAtTime(micros, MediaMetadataRetriever.OPTION_CLOSEST);
            if (frame == null) throw new IOException("Cannot decode last frame");
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            frame.compress(Bitmap.CompressFormat.JPEG, 91, buffer); frame.recycle();
            // Private cache file: last-frame references should not clutter the user's Gallery.
            java.io.File cached = new java.io.File(context.getCacheDir(), "realframe_last_frame.jpg");
            try (java.io.FileOutputStream output = new java.io.FileOutputStream(cached)) {
                output.write(buffer.toByteArray());
            }
            return Uri.fromFile(cached).toString();
        } finally { retriever.release(); }
    }

    private String encodeImage(String uriString) throws Exception {
        try (InputStream in = context.getContentResolver().openInputStream(Uri.parse(uriString))) {
            if (in == null) throw new IOException("Cannot read reference image");
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            byte[] buf = new byte[8192]; int n;
            // Keep requests reasonable for mobile; no 300 MB base64 payloads.
            while ((n = in.read(buf)) != -1) {
                stream.write(buf, 0, n);
                if (stream.size() > 16_000_000) throw new IOException("Reference image exceeds 16MB; resize first");
            }
            return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP);
        }
    }

    private String uploadGradio(String base, String token, String uriString) throws Exception {
        String boundary = "----RealFrame" + UUID.randomUUID().toString().replace("-", "");
        HttpURLConnection c = connection(base + "/gradio_api/upload", "POST", token);
        c.setDoOutput(true);
        c.setChunkedStreamingMode(32768);
        c.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        Uri uri = Uri.parse(uriString);
        try (OutputStream out = c.getOutputStream();
             InputStream input = context.getContentResolver().openInputStream(uri)) {
            if (input == null) throw new IOException("Could not open image");
            String header = "--" + boundary + "\r\nContent-Disposition: form-data; name=\"files\"; filename=\"reference.jpg\"\r\nContent-Type: image/jpeg\r\n\r\n";
            out.write(header.getBytes(StandardCharsets.UTF_8));
            byte[] buf = new byte[8192]; int len;
            while ((len = input.read(buf)) != -1) out.write(buf, 0, len);
            out.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        }
        try {
            JSONArray arr = new JSONArray(body(c));
            if (arr.length() == 0) throw new IOException("Gradio image upload returned no paths");
            return arr.getString(0);
        } finally { c.disconnect(); }
    }

    private Object inject(Object value, JSONObject vars) throws Exception {
        if (value instanceof String) {
            String updated = (String) value;
            // Whole-token placeholders keep their JSON types (seed must be numeric).
            if (updated.equals("{{seed}}")) return vars.get("seed");
            if (updated.equals("{{duration}}")) return vars.get("duration");
            if (updated.equals("{{width}}")) return vars.get("width");
            if (updated.equals("{{height}}")) return vars.get("height");
            java.util.Iterator<String> it = vars.keys();
            while (it.hasNext()) {
                String key = it.next();
                updated = updated.replace("{{" + key + "}}", vars.optString(key));
            }
            return updated;
        }
        if (value instanceof JSONObject) {
            JSONObject obj = (JSONObject) value;
            JSONObject copy = new JSONObject();
            java.util.Iterator<String> it = obj.keys();
            while (it.hasNext()) { String key = it.next(); copy.put(key, inject(obj.get(key), vars)); }
            return copy;
        }
        if (value instanceof JSONArray) {
            JSONArray arr = (JSONArray) value, copy = new JSONArray();
            for (int i=0; i<arr.length(); i++) copy.put(inject(arr.get(i), vars));
            return copy;
        }
        return value;
    }

    public String cloudInfo(boolean video) throws Exception {
        String prefix = video ? "video" : "image";
        String base = cleanBase(settings.get(prefix + "Space", ""));
        validateSpace(base);
        // Hugging Face docs: Gradio exposes this OpenAPI spec for every Space.
        JSONObject spec = new JSONObject(getJson(base + "/gradio_api/openapi.json", settings.token("huggingface")));
        JSONObject paths = spec.optJSONObject("paths");
        if (paths == null) throw new IOException("No API paths present in Space OpenAPI response");
        String configured = settings.get(prefix + "Endpoint", "/predict");
        StringBuilder msg = new StringBuilder("Configured: " + base + " (" + configured + ")\n\nAPI paths:\n");
        java.util.Iterator<String> keys = paths.keys();
        while (keys.hasNext()) {
            String path = keys.next();
            if (path.startsWith("/gradio_api/call/")) msg.append(path).append("\n");
        }
        msg.append("\nMatch the chosen endpoint and JSON argument order against this Space's 'Use via API' instructions. An endpoint existing does not guarantee free quota or functioning video.");
        return msg.toString();
    }

    private void validateSpace(String base) throws IOException {
        if (!base.matches("https://[A-Za-z0-9-]+\\.hf\\.space"))
            throw new IOException("Cloud Space must be https://<subdomain>.hf.space (no path/query)");
    }

    private String fromGradio(JSONObject job) throws Exception {
        boolean video = "video".equals(job.optString("kind"));
        String prefix = video ? "video" : "image";
        String base = cleanBase(settings.get(prefix + "Space", ""));
        validateSpace(base);
        String endpoint = settings.get(prefix + "Endpoint", "/predict");
        if (!endpoint.matches("/[a-zA-Z0-9_-]+")) throw new IOException("Invalid API endpoint");
        String token = settings.token("huggingface");
        String jsonText = settings.get(prefix + "Args", "[\"{{prompt}}\"]");
        JSONArray template = new JSONArray(jsonText);
        // Only upload image if the Space's API template actually expects it.
        String uploaded = "";
        if (jsonText.contains("{{image_path}}")) {
            String reference = referenceForJob(job);
            if (reference.isEmpty())
                throw new IOException("This Space requires an input image. Attach an image/character reference before queuing.");
            uploaded = uploadGradio(base, token, reference);
        }
        JSONObject vars = new JSONObject().put("prompt", job.optString("prompt"))
                .put("negative", job.optString("negative", ""))
                .put("seed", job.optLong("seed", 0))
                .put("duration", job.optInt("duration", 5))
                .put("width", job.optInt("width", 512))
                .put("height", job.optInt("height", 768))
                .put("image_path", uploaded);
        JSONArray inputs = (JSONArray) inject(template, vars);
        String response = postJson(base + "/gradio_api/call" + endpoint, token,
                new JSONObject().put("data", inputs).toString());
        String eventId = new JSONObject(response).optString("event_id");
        if (eventId.isEmpty()) throw new IOException("Gradio request not queued: " + response);
        String url = base + "/gradio_api/call" + endpoint + "/" +
                URLEncoder.encode(eventId, "UTF-8");
        HttpURLConnection c = connection(url, "GET", token);
        String event = "";
        try {
            if (c.getResponseCode() >= 400) {
                String ignored = body(c); // body() throws with useful provider error
            }
        } catch (Exception e) { c.disconnect(); throw e; }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("event:")) event = line.substring(6).trim();
                if (line.startsWith("data:")) {
                    String data = line.substring(5).trim();
                    if ("error".equals(event)) throw new IOException("Gradio error: " + data);
                    if ("complete".equals(event)) {
                        Object result = new JSONArray(data);
                        String mediaUrl = searchMedia(result, base, video);
                        if (mediaUrl == null) throw new IOException("No downloadable media in result: " + data);
                        return downloadToGallery(mediaUrl, token, job.optString("kind"), "Cloud");
                    }
                }
            }
        } finally { c.disconnect(); }
        throw new IOException("Gradio stream ended before completion; check quota or Space status");
    }

    /** Gradio outputs often contain a thumbnail image before the final MP4.
     * Match the expected media kind, avoiding corrupt MP4 files containing PNG bytes.
     */
    private boolean isExpectedMedia(String value, String mime, boolean video) {
        String path = value.toLowerCase(Locale.ROOT);
        String mediaType = mime == null ? "" : mime.toLowerCase(Locale.ROOT);
        if (video) return mediaType.startsWith("video/mp4") ||
                path.matches("(?s).*\\.mp4(?:[?&#/].*)?$");
        return mediaType.startsWith("image/") ||
                path.matches("(?s).*\\.(?:png|jpe?g|webp)(?:[?&#/].*)?$");
    }

    private String searchMedia(Object data, String base, boolean video) throws Exception {
        if (data instanceof JSONObject) {
            JSONObject obj = (JSONObject)data;
            String mime = obj.optString("mime_type", "");
            String url = obj.optString("url", "");
            if (url.startsWith("https://") && isExpectedMedia(url, mime, video)) return url;
            String path = obj.optString("path", "");
            if (!path.isEmpty() && isExpectedMedia(path, mime, video))
                return base + "/gradio_api/file=" + URLEncoder.encode(path, "UTF-8");
            java.util.Iterator<String> it = obj.keys();
            while (it.hasNext()) {
                String key = it.next();
                if ("url".equals(key) || "path".equals(key) || "mime_type".equals(key)) continue;
                String found = searchMedia(obj.get(key), base, video);
                if (found != null) return found;
            }
        } else if (data instanceof JSONArray) {
            JSONArray arr = (JSONArray)data;
            for (int i=0; i<arr.length(); i++) {
                String found = searchMedia(arr.get(i), base, video);
                if (found != null) return found;
            }
        } else if (data instanceof String) {
            String path = (String)data;
            if (!isExpectedMedia(path, "", video)) return null;
            if (path.startsWith("https://")) return path;
            if (path.startsWith("/gradio_api/")) return base + path;
            if (path.startsWith("/tmp/"))
                return base + "/gradio_api/file=" + URLEncoder.encode(path, "UTF-8");
        }
        return null;
    }

    private String downloadToGallery(String url, String token, String kind, String backend) throws Exception {
        boolean video = "video".equals(kind);
        URL source = new URL(url);
        String host = source.getHost().toLowerCase(Locale.ROOT);
        String safeToken = "";
        if ("Cloud".equals(backend)) {
            if (!"https".equals(source.getProtocol()))
                throw new IOException("Cloud returned insecure HTTP media URL");
            String space = settings.get((video ? "video" : "image") + "Space", "");
            URL configured = new URL(space);
            // Do not leak the HF token to arbitrary third-party/other .hf.space hosts.
            if (host.equals(configured.getHost().toLowerCase(Locale.ROOT)) ||
                    host.equals("huggingface.co")) safeToken = token;
        } else {
            URL configured = new URL(settings.get("localUrl", "http://127.0.0.1:8820"));
            if (!host.equals(configured.getHost().toLowerCase(Locale.ROOT)) ||
                    source.getPort() != configured.getPort())
                throw new IOException("Local renderer returned media from a different host/port");
            safeToken = token;
        }
        HttpURLConnection c = connection(url, "GET", safeToken);
        try {
            if (c.getResponseCode() >= 400) throw new IOException("Media download HTTP " + c.getResponseCode());
            try (BufferedInputStream in = new BufferedInputStream(c.getInputStream())) {
                in.mark(32);
                byte[] head = new byte[16];
                int n = 0;
                while (n < head.length) {
                    int r = in.read(head, n, head.length - n);
                    if (r < 0) break;
                    n += r;
                }
                in.reset();
                String actualMime = MediaSignature.detect(Arrays.copyOf(head, n));
                if (!MediaSignature.matchesKind(actualMime, kind))
                    throw new IOException("API returned non-" + kind + " bytes (possibly an HTML quota/login error), refusing to save a corrupt Gallery file");
                String name = (video ? "clip_" : "image_") + System.currentTimeMillis() + MediaSignature.extension(actualMime);
                return saveStream(in, actualMime, name);
            }
        } finally { c.disconnect(); }
    }

    private String saveBytes(byte[] bytes, String mime, String filename) throws Exception {
        return saveStream(new java.io.ByteArrayInputStream(bytes), mime, filename);
    }

    private String saveStream(InputStream in, String mime, String filename) throws Exception {
        ContentResolver resolver = context.getContentResolver();
        boolean video = mime.startsWith("video/");
        android.net.Uri collection = video ? MediaStore.Video.Media.EXTERNAL_CONTENT_URI :
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, filename);
        values.put(MediaStore.MediaColumns.MIME_TYPE, mime);
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, (video ? "Movies/" : "Pictures/") + "RealFrameStudio");
        values.put(MediaStore.MediaColumns.IS_PENDING, 1);
        Uri uri = resolver.insert(collection, values);
        if (uri == null) throw new IOException("Could not create output in gallery");
        try {
            try (OutputStream out = resolver.openOutputStream(uri)) {
                if (out == null) throw new IOException("Cannot open new gallery file");
                byte[] buf = new byte[65536]; int len;
                while ((len = in.read(buf)) != -1) out.write(buf, 0, len);
            }
            values.clear(); values.put(MediaStore.MediaColumns.IS_PENDING, 0);
            resolver.update(uri, values, null, null);
            return uri.toString();
        } catch (Exception e) {
            resolver.delete(uri, null, null); throw e;
        }
    }
}
