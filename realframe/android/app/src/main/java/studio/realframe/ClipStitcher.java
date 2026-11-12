package studio.realframe;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.MediaMuxer;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Lossless MP4 concat on Android. Requires identical video/audio codec and geometry.
 * For incompatible formats, use tools/stitch.py (FFmpeg), which transcodes first.
 */
public final class ClipStitcher {
    private static class Tracks {
        int video = -1, audio = -1;
        MediaFormat videoFormat, audioFormat;
        long durationUs;
    }

    private static Tracks inspect(Context context, Uri uri) throws Exception {
        Tracks tracks = new Tracks();
        MediaExtractor ex = new MediaExtractor();
        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        try {
            ex.setDataSource(context, uri, null);
            for (int i=0; i<ex.getTrackCount(); i++) {
                MediaFormat fmt = ex.getTrackFormat(i);
                String mime = fmt.getString(MediaFormat.KEY_MIME);
                if (mime == null) continue;
                if (mime.startsWith("video/") && tracks.video == -1) {
                    tracks.video = i; tracks.videoFormat = fmt;
                } else if (mime.startsWith("audio/") && tracks.audio == -1) {
                    tracks.audio = i; tracks.audioFormat = fmt;
                }
            }
            if (tracks.video == -1) throw new IOException("Selected clip has no video track");
            mmr.setDataSource(context, uri);
            String millis = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            tracks.durationUs = millis == null ? 0 : Long.parseLong(millis)*1000;
            return tracks;
        } finally { ex.release(); mmr.release(); }
    }

    private static boolean same(MediaFormat a, MediaFormat b, boolean video) {
        if (a == null || b == null) return a == b;
        if (!a.getString(MediaFormat.KEY_MIME).equals(b.getString(MediaFormat.KEY_MIME))) return false;
        String[] fields = video ? new String[]{MediaFormat.KEY_WIDTH, MediaFormat.KEY_HEIGHT} :
                new String[]{MediaFormat.KEY_SAMPLE_RATE, MediaFormat.KEY_CHANNEL_COUNT};
        for (String k : fields) {
            if (!a.containsKey(k) || !b.containsKey(k) || a.getInteger(k) != b.getInteger(k)) return false;
        }
        for (String csdKey : new String[]{"csd-0", "csd-1"}) {
            boolean ha = a.containsKey(csdKey), hb = b.containsKey(csdKey);
            if (ha != hb) return false;
            if (ha) {
                ByteBuffer aa = a.getByteBuffer(csdKey).duplicate();
                ByteBuffer bb = b.getByteBuffer(csdKey).duplicate();
                if (!aa.equals(bb)) return false;
            }
        }
        return true;
    }

    public static String join(Context context, List<String> uriStrings) throws Exception {
        if (uriStrings.size() < 2) throw new IOException("Select at least two MP4 clips");
        List<Tracks> tracks = new ArrayList<>();
        for (String s : uriStrings) tracks.add(inspect(context, Uri.parse(s)));
        Tracks first = tracks.get(0);
        for (int i=1; i<tracks.size(); i++) {
            Tracks next = tracks.get(i);
            if (!same(first.videoFormat, next.videoFormat, true) ||
                    !same(first.audioFormat, next.audioFormat, false)) {
                throw new IOException("Clips must match video size/codec and audio format. " +
                        "Use the included FFmpeg stitch.py to normalize mixed clips.");
            }
        }

        ContentResolver resolver = context.getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, "realframe_film_" + System.currentTimeMillis() + ".mp4");
        values.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4");
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, "Movies/RealFrameStudio");
        values.put(MediaStore.MediaColumns.IS_PENDING, 1);
        Uri output = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
        if (output == null) throw new IOException("Cannot open Movies output");
        MediaMuxer muxer = null;
        boolean started = false;
        try (ParcelFileDescriptor out = resolver.openFileDescriptor(output, "w")) {
            if (out == null) throw new IOException("Cannot open file descriptor");
            muxer = new MediaMuxer(out.getFileDescriptor(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            int videoTrack = muxer.addTrack(first.videoFormat);
            int audioTrack = first.audioFormat == null ? -1 : muxer.addTrack(first.audioFormat);
            muxer.start(); started = true;
            long offsetUs = 0;
            ByteBuffer buffer = ByteBuffer.allocateDirect(16 * 1024 * 1024);
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            for (int index=0; index<uriStrings.size(); index++) {
                Tracks t = tracks.get(index);
                MediaExtractor ex = new MediaExtractor();
                try {
                    ex.setDataSource(context, Uri.parse(uriStrings.get(index)), null);
                    ex.selectTrack(t.video);
                    if (t.audio >= 0) ex.selectTrack(t.audio);
                    long latestVideo = -1, latestAudio = -1;
                    while (true) {
                        int track = ex.getSampleTrackIndex();
                        if (track < 0) break;
                        buffer.clear();
                        int size = ex.readSampleData(buffer, 0);
                        if (size < 0) break;
                        long pts = ex.getSampleTime();
                        info.offset = 0;
                        info.size = size;
                        info.presentationTimeUs = Math.max(0, pts) + offsetUs;
                        info.flags = ex.getSampleFlags() & MediaExtractor.SAMPLE_FLAG_SYNC;
                        int dest = track == t.video ? videoTrack : audioTrack;
                        if (dest >= 0) muxer.writeSampleData(dest, buffer, info);
                        if (track == t.video) latestVideo = Math.max(latestVideo, pts);
                        else latestAudio = Math.max(latestAudio, pts);
                        if (!ex.advance()) break;
                    }
                    offsetUs += Math.max(t.durationUs, Math.max(latestVideo, latestAudio) + 45000);
                } finally { ex.release(); }
            }
            muxer.stop(); started = false; muxer.release(); muxer = null;
            values.clear(); values.put(MediaStore.MediaColumns.IS_PENDING, 0);
            resolver.update(output, values, null, null);
            return output.toString();
        } catch (Exception e) {
            if (muxer != null) {
                if (started) { try { muxer.stop(); } catch (Exception ignored) { } }
                try { muxer.release(); } catch (Exception ignored) { }
            }
            resolver.delete(output, null, null);
            throw e;
        }
    }
}
