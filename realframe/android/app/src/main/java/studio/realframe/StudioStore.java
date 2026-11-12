package studio.realframe;

import android.content.Context;
import android.util.AtomicFile;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Persistent, private per-app records. No rendering credentials enter exported job records. */
public final class StudioStore {
    private final AtomicFile jobsFile;
    private final AtomicFile charactersFile;
    private final AtomicFile clipsFile;

    public StudioStore(Context c) {
        jobsFile = new AtomicFile(new File(c.getFilesDir(), "jobs.json"));
        charactersFile = new AtomicFile(new File(c.getFilesDir(), "characters.json"));
        clipsFile = new AtomicFile(new File(c.getFilesDir(), "clips.json"));
    }

    private JSONArray read(AtomicFile file) {
        try (FileInputStream in = file.openRead()) {
            byte[] bytes = in.readAllBytes();
            return new JSONArray(new String(bytes, StandardCharsets.UTF_8));
        } catch (Exception ignored) { return new JSONArray(); }
    }

    private void write(AtomicFile file, JSONArray items) throws Exception {
        FileOutputStream output = file.startWrite();
        try {
            output.write(items.toString(2).getBytes(StandardCharsets.UTF_8));
            file.finishWrite(output);
        } catch (Exception e) { file.failWrite(output); throw e; }
    }

    private List<JSONObject> list(AtomicFile file) {
        List<JSONObject> records = new ArrayList<>();
        JSONArray arr = read(file);
        for (int i = 0; i < arr.length(); ++i) {
            JSONObject obj = arr.optJSONObject(i);
            if (obj != null) records.add(obj);
        }
        return records;
    }

    private void put(AtomicFile file, JSONObject record) throws Exception {
        JSONArray updated = new JSONArray();
        String id = record.optString("id");
        boolean found = false;
        for (JSONObject previous : list(file)) {
            if (previous.optString("id").equals(id)) {
                updated.put(record); found = true;
            } else updated.put(previous);
        }
        if (!found) updated.put(record);
        write(file, updated);
    }

    public synchronized List<JSONObject> jobs() { return list(jobsFile); }
    public synchronized List<JSONObject> characters() { return list(charactersFile); }
    public synchronized List<JSONObject> clips() { return list(clipsFile); }

    public synchronized JSONObject findJob(String id) {
        for (JSONObject obj : jobs()) if (id.equals(obj.optString("id"))) return obj;
        return null;
    }

    public synchronized void putJob(JSONObject job) throws Exception { put(jobsFile, job); }

    /** Append a fully validated queue in one atomic write (efficient for long films).
     * No partial batches on malformed input or disk errors.
     */
    public synchronized void putJobs(List<JSONObject> batch) throws Exception {
        if (batch.isEmpty()) return;
        java.util.HashSet<String> ids = new java.util.HashSet<>();
        for (JSONObject job : batch) {
            String id = job.optString("id");
            if (id.isEmpty() || !ids.add(id)) throw new IllegalArgumentException("Duplicate/missing job ID");
        }
        JSONArray all = read(jobsFile);
        for (int i=0; i<all.length(); i++) {
            JSONObject existing = all.optJSONObject(i);
            if (existing != null && ids.contains(existing.optString("id")))
                throw new IllegalArgumentException("Job already exists: " + existing.optString("id"));
        }
        for (JSONObject j : batch) all.put(j);
        write(jobsFile, all);
    }

    /** Retry all failed scenes in a project, preserving IDs, order and dependencies.
     * Interrupted network renders may still complete remotely and can duplicate.
     */
    public synchronized int retryProject(String projectId) throws Exception {
        if (projectId.isEmpty()) throw new IllegalArgumentException("Missing project ID");
        JSONArray all = read(jobsFile);
        int reset = 0;
        for (int i=0; i<all.length(); i++) {
            JSONObject j = all.optJSONObject(i);
            if (j == null || !projectId.equals(j.optString("projectId"))) continue;
            String status = j.optString("status");
            if ("error".equals(status) || "blocked".equals(status) ||
                    "cancelled".equals(status) || "interrupted".equals(status)) {
                j.put("status", "queued");
                j.put("message", "Retry queued; remote interrupted output may duplicate");
                j.remove("resultUri");
                reset++;
            }
        }
        if (reset > 0) write(jobsFile, all);
        return reset;
    }
    /** Cancel one job that has not started; in-flight requests are not interrupted. */
    public synchronized boolean cancelWaitingJob(String id) throws Exception {
        JSONArray all = read(jobsFile);
        boolean changed = false;
        for (int i = 0; i < all.length(); i++) {
            JSONObject job = all.optJSONObject(i);
            if (job == null || !id.equals(job.optString("id"))) continue;
            String state = job.optString("status");
            if ("queued".equals(state) || "blocked".equals(state) ||
                    "error".equals(state) || "interrupted".equals(state)) {
                job.put("status", "cancelled");
                job.put("message", "Cancelled by user before rendering");
                changed = true;
            }
            break;
        }
        if (changed) write(jobsFile, all);
        return changed;
    }

    /** Cancel all waiting jobs while leaving already-rendered or running media untouched. */
    public synchronized int cancelWaitingJobs() throws Exception {
        JSONArray all = read(jobsFile);
        int count = 0;
        for (int i = 0; i < all.length(); i++) {
            JSONObject job = all.optJSONObject(i);
            if (job == null || !"queued".equals(job.optString("status"))) continue;
            job.put("status", "cancelled");
            job.put("message", "Batch cancelled before start");
            count++;
        }
        if (count > 0) write(jobsFile, all);
        return count;
    }

    public synchronized void putCharacter(JSONObject c) throws Exception { put(charactersFile, c); }

    public synchronized void removeCharacter(String id) throws Exception {
        JSONArray arr = new JSONArray();
        for (JSONObject c : characters()) if (!id.equals(c.optString("id"))) arr.put(c);
        write(charactersFile, arr);
    }

    public synchronized void addClip(String uri, String title) throws Exception {
        JSONArray arr = read(clipsFile);
        arr.put(new JSONObject().put("id", UUID.randomUUID().toString()).put("uri", uri).put("title", title));
        write(clipsFile, arr);
    }

    public synchronized void replaceClips(List<JSONObject> clips) throws Exception {
        JSONArray arr = new JSONArray();
        for (JSONObject c : clips) arr.put(c);
        write(clipsFile, arr);
    }

    /** Portable metadata only. Intentionally excludes passwords, API tokens and media bytes.
     * Original content:// references might not be valid on a newly installed app.
     */
    public synchronized String exportBackup() throws Exception {
        return new JSONObject().put("format", "realframe-project-backup-v1")
                .put("exportedAt", System.currentTimeMillis())
                .put("jobs", read(jobsFile))
                .put("characters", read(charactersFile))
                .put("clips", read(clipsFile))
                .toString(2);
    }

    public synchronized boolean hasRunningJobs() {
        for (JSONObject job : jobs())
            if ("running".equals(job.optString("status"))) return true;
        return false;
    }

    /** Replace project metadata from a versioned, validated archive.
     * All pending jobs become interrupted, never silently auto-render after restoration.
     */
    public synchronized void importBackup(String json) throws Exception {
        if (hasRunningJobs()) throw new IllegalStateException("Wait for current render to finish before restoring");
        JSONObject backup = new JSONObject(json);
        if (!"realframe-project-backup-v1".equals(backup.optString("format")))
            throw new IllegalArgumentException("Not a compatible RealFrame project backup");
        JSONArray jobs = backup.getJSONArray("jobs");
        JSONArray characters = backup.getJSONArray("characters");
        JSONArray clips = backup.getJSONArray("clips");
        validateArchiveSection(jobs, 10000, "jobs");
        validateArchiveSection(characters, 2000, "characters");
        validateArchiveSection(clips, 10000, "clips");
        for (int i=0; i<jobs.length(); i++) {
            JSONObject item = jobs.getJSONObject(i);
            if ("queued".equals(item.optString("status")) ||
                    "running".equals(item.optString("status"))) {
                item.put("status", "interrupted");
                item.put("message", "Restored job (paused for manual review; media references may be stale)");
            }
        }
        // Per-file AtomicFile writes. The 3-file restore as a whole is NOT transactional.
        write(charactersFile, characters);
        write(clipsFile, clips);
        write(jobsFile, jobs);
    }

    private static void validateArchiveSection(JSONArray records, int max, String label) throws Exception {
        if (records.length() > max) throw new IllegalArgumentException("Too many " + label + " in backup");
        java.util.HashSet<String> ids = new java.util.HashSet<>();
        for (int i=0; i<records.length(); i++) {
            JSONObject obj = records.getJSONObject(i);
            String id = obj.optString("id", "");
            if (id.isEmpty() || !ids.add(id)) throw new IllegalArgumentException("Invalid/duplicate ID in " + label);
            if ("characters".equals(label) && obj.optInt("minAge", 18) < 18)
                throw new IllegalArgumentException("Character must be an adult");
        }
    }

    public synchronized void recoverInterrupted() {
        try {
            JSONArray arr = new JSONArray();
            for (JSONObject job : jobs()) {
                if ("running".equals(job.optString("status"))) {
                    job.put("status", "interrupted");
                    job.put("message", "App/render worker stopped. Remote job might still have completed; inspect provider before Retry.");
                }
                arr.put(job);
            }
            write(jobsFile, arr);
        } catch (Exception ignored) { }
    }

    /** Picks only jobs whose parents completed. A failed parent blocks descendants. */
    public synchronized JSONObject nextReadyJob() {
        List<JSONObject> all = jobs();
        java.util.HashMap<String, JSONObject> byId = new java.util.HashMap<>();
        for (JSONObject j : all) byId.put(j.optString("id"), j);
        boolean changed = false;
        JSONObject ready = null;
        for (JSONObject j : all) {
            if (!"queued".equals(j.optString("status"))) continue;
            String parent = j.optString("dependsOn");
            if (parent.isEmpty()) { if (ready == null) ready = j; continue; }
            JSONObject dependency = byId.get(parent);
            if (dependency != null && "done".equals(dependency.optString("status"))) {
                if (ready == null) ready = j;
            } else if (dependency == null || "error".equals(dependency.optString("status")) ||
                    "cancelled".equals(dependency.optString("status")) ||
                    "interrupted".equals(dependency.optString("status")) ||
                    "blocked".equals(dependency.optString("status"))) {
                try {
                    j.put("status", "blocked");
                    j.put("message", "Previous scene failed, was cancelled, or is missing. Retry parent first, then this clip.");
                    changed = true;
                } catch (Exception ignored) { }
            }
        }
        if (changed) {
            try {
                JSONArray arr = new JSONArray();
                for (JSONObject j : all) arr.put(j);
                write(jobsFile, arr);
            } catch (Exception ignored) { }
        }
        return ready;
    }
}
