package studio.realframe;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.media.Image;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Encodes real diffusion frames as an H.264 MP4 on the phone.
 * Uses Android's YUV_420_888 image planes (flexible YUV420) to avoid shipping
 * any separate MP4/FFmpeg native codec.
 *
 * Experimental: some Android encoders cannot accept flexible-YUV CPU buffers.
 * If initialization fails, an informative error is surfaced; no fake output.
 */
public final class LocalVideoEncoder implements AutoCloseable {
    private final ContentResolver resolver;
    private final Uri galleryUri;
    private final ParcelFileDescriptor descriptor;
    private final MediaMuxer muxer;
    private final MediaCodec codec;
    private final int width,height,fps;
    private int frames;
    private int track=-1;
    private boolean muxerStarted,complete,closed;
    private final MediaCodec.BufferInfo bufferInfo=new MediaCodec.BufferInfo();

    public LocalVideoEncoder(Context source,int w,int h,int videoFps) throws Exception {
        if(w<192||h<192||w>512||h>512||(w&1)!=0||(h&1)!=0||videoFps<1||videoFps>60)
            throw new IllegalArgumentException("Invalid video size or frame rate");
        width=w;height=h;fps=videoFps;
        resolver=source.getContentResolver();
        Uri out=null;
        ParcelFileDescriptor fd=null;
        MediaMuxer mediaMuxer=null;
        MediaCodec encoder=null;
        try {
            ContentValues values=new ContentValues();
            values.put(MediaStore.Video.Media.DISPLAY_NAME,
                "realframe_local_video_"+System.currentTimeMillis()+".mp4");
            values.put(MediaStore.Video.Media.MIME_TYPE,"video/mp4");
            values.put(MediaStore.Video.Media.RELATIVE_PATH,"Movies/RealFrameStudio");
            values.put(MediaStore.Video.Media.IS_PENDING,1);
            out=resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,values);
            if(out==null)throw new IOException("Could not create movie in Gallery");
            fd=resolver.openFileDescriptor(out,"w");
            if(fd==null)throw new IOException("Could not open Gallery movie file");
            mediaMuxer=new MediaMuxer(fd.getFileDescriptor(),MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            encoder=MediaCodec.createEncoderByType("video/avc");
            MediaFormat format=MediaFormat.createVideoFormat("video/avc",w,h);
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT,MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
            format.setInteger(MediaFormat.KEY_BIT_RATE,Math.max(500_000,w*h*fps/2));
            format.setInteger(MediaFormat.KEY_FRAME_RATE,videoFps);
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,1);
            encoder.configure(format,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);
            encoder.start();
            descriptor=fd;muxer=mediaMuxer;codec=encoder;galleryUri=out;
        } catch(Exception error){
            if(encoder!=null){
                try{encoder.stop();}catch(Exception ignored){}
                try{encoder.release();}catch(Exception ignored){}
            }
            if(mediaMuxer!=null)try{mediaMuxer.release();}catch(Exception ignored){}
            if(fd!=null)try{fd.close();}catch(Exception ignored){}
            if(out!=null)resolver.delete(out,null,null);
            throw new IOException("H.264 encoder initialization failed: "+error.getMessage(),error);
        }
    }

    private static byte clamp(int val){return (byte)Math.max(0,Math.min(255,val));}
    private static void set(Image.Plane plane,int x,int y,byte value){
        int index=y*plane.getRowStride()+x*plane.getPixelStride();
        ByteBuffer buffer=plane.getBuffer();
        if(index<0||index>=buffer.capacity())throw new IllegalStateException("YUV plane buffer too small");
        buffer.put(index,value);
    }
    private void fillYuv(Image image,int[] argb){
        if(image.getFormat()!=android.graphics.ImageFormat.YUV_420_888)
            throw new IllegalStateException("Codec input format is not YUV_420_888");
        Image.Plane[] planes=image.getPlanes();
        if(planes==null||planes.length!=3)
            throw new IllegalStateException("Encoder must expose three YUV planes");
        // BT.601 limited-range RGB → YUV420; chroma subsampling 2×2.
        for(int y=0;y<height;y++){
            for(int x=0;x<width;x++){
                int color=argb[y*width+x];
                int r=(color>>>16)&255,g=(color>>>8)&255,b=color&255;
                int Y=((66*r+129*g+25*b+128)>>8)+16;
                set(planes[0],x,y,clamp(Y));
            }
        }
        for(int y=0;y<height;y+=2){
            for(int x=0;x<width;x+=2){
                int sr=0,sg=0,sb=0;
                for(int yy=0;yy<2;yy++)for(int xx=0;xx<2;xx++){
                    int color=argb[(y+yy)*width+x+xx];
                    sr+=(color>>>16)&255;
                    sg+=(color>>>8)&255;
                    sb+=color&255;
                }
                int r=sr/4,g=sg/4,b=sb/4;
                int U=((-38*r-74*g+112*b+128)>>8)+128;
                int V=((112*r-94*g-18*b+128)>>8)+128;
                set(planes[1],x/2,y/2,clamp(U));
                set(planes[2],x/2,y/2,clamp(V));
            }
        }
    }

    public void append(int[] argb) throws Exception {
        if(closed||complete)throw new IllegalStateException("Encoder closed");
        if(argb==null||argb.length!=width*height)
            throw new IllegalArgumentException("Bad frame dimensions");
        int index=codec.dequeueInputBuffer(15_000_000);
        if(index<0)throw new IOException("Timed out waiting for H.264 encoder input buffer");
        Image image=codec.getInputImage(index);
        if(image==null)throw new IOException("Phone H.264 encoder lacks CPU YUV420 image-input support");
        try {fillYuv(image,argb);}
        finally {image.close();}
        long pts=frames*1_000_000L/fps;
        codec.queueInputBuffer(index,0,width*height*3/2,pts,0);
        frames++;
        drain(false);
    }

    private void drain(boolean eos) throws Exception {
        int waits=0;
        for(;;){
            int index=codec.dequeueOutputBuffer(bufferInfo,eos?50_000:0);
            if(index==MediaCodec.INFO_TRY_AGAIN_LATER){
                if(!eos)return;
                if(++waits>100)throw new IOException("H.264 encoder did not finish the file");
                continue;
            }
            if(index==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){
                if(muxerStarted)throw new IOException("Encoder format changed twice");
                track=muxer.addTrack(codec.getOutputFormat());
                muxer.start();
                muxerStarted=true;
                continue;
            }
            if(index<0)continue;
            ByteBuffer bytes=codec.getOutputBuffer(index);
            if(bytes==null)throw new IOException("Encoder output buffer missing");
            boolean codecConfig=(bufferInfo.flags&MediaCodec.BUFFER_FLAG_CODEC_CONFIG)!=0;
            if(!codecConfig&&bufferInfo.size>0){
                if(!muxerStarted)throw new IOException("H.264 output track was not created");
                bytes.position(bufferInfo.offset);
                bytes.limit(bufferInfo.offset+bufferInfo.size);
                muxer.writeSampleData(track,bytes,bufferInfo);
            }
            boolean finished=(bufferInfo.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0;
            codec.releaseOutputBuffer(index,false);
            if(finished)return;
        }
    }

    public Uri finish() throws Exception {
        if(closed||complete)throw new IllegalStateException("Encoder already finalized");
        if(frames<1)throw new IllegalStateException("No generated video frames");
        int idx=codec.dequeueInputBuffer(15_000_000);
        if(idx<0)throw new IOException("Cannot signal encoder end of stream");
        codec.queueInputBuffer(idx,0,0,frames*1_000_000L/fps,MediaCodec.BUFFER_FLAG_END_OF_STREAM);
        drain(true);
        if(!muxerStarted)throw new IOException("No MP4 video track");
        muxer.stop();
        complete=true;
        ContentValues done=new ContentValues();
        done.put(MediaStore.Video.Media.IS_PENDING,0);
        resolver.update(galleryUri,done,null,null);
        close();
        return galleryUri;
    }

    @Override public void close() {
        if(closed)return;
        closed=true;
        try{codec.stop();}catch(Exception ignored){}
        try{codec.release();}catch(Exception ignored){}
        if(!complete&&muxerStarted)try{muxer.stop();}catch(Exception ignored){}
        try{muxer.release();}catch(Exception ignored){}
        try{descriptor.close();}catch(Exception ignored){}
        if(!complete)try{resolver.delete(galleryUri,null,null);}catch(Exception ignored){}
    }
}
