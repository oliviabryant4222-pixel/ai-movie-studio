package studio.realframe;

import android.content.Context;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import java.nio.ByteBuffer;

/**
 * Checks the saved Gallery MP4 by REOPENING it and reading encoded H.264 samples.
 * This is a real output/container test, not a synthetic assertion.
 * It does NOT evaluate motion quality or identity accuracy.
 */
public final class VideoOutputVerifier {
    public static final class Result {
        public final boolean valid;
        public final String detail;
        private Result(boolean ok,String message){valid=ok;detail=message;}
    }
    private VideoOutputVerifier(){}

    public static Result verify(Context context,Uri galleryUri,int minimumSamples) {
        if(context==null||galleryUri==null||minimumSamples<2)
            return new Result(false,"Invalid movie verifier arguments");
        MediaExtractor extractor=new MediaExtractor();
        try{
            extractor.setDataSource(context,galleryUri,null);
            int videoIndex=-1,width=0,height=0;
            String mime="";
            for(int i=0;i<extractor.getTrackCount();i++){
                MediaFormat f=extractor.getTrackFormat(i);
                String candidate=f.getString(MediaFormat.KEY_MIME);
                if(candidate!=null&&candidate.startsWith("video/")){
                    videoIndex=i;mime=candidate;
                    width=f.containsKey(MediaFormat.KEY_WIDTH)?f.getInteger(MediaFormat.KEY_WIDTH):0;
                    height=f.containsKey(MediaFormat.KEY_HEIGHT)?f.getInteger(MediaFormat.KEY_HEIGHT):0;
                    break;
                }
            }
            if(videoIndex<0||!mime.equals("video/avc")||width<=0||height<=0)
                return new Result(false,"MP4 does not contain an H.264 video track");
            extractor.selectTrack(videoIndex);
            ByteBuffer buffer=ByteBuffer.allocateDirect(1_500_000);
            int samples=0;
            long first=-1,last=-1;
            for(int i=0;i<minimumSamples;i++){
                int n=extractor.readSampleData(buffer,0);
                if(n<0)break;
                long pts=extractor.getSampleTime();
                if(pts<0)break;
                if(samples==0)first=pts;
                last=pts;
                samples++;
                if(!extractor.advance())break;
            }
            if(samples<minimumSamples||last<=first)
                return new Result(false,"Video track has fewer than "+minimumSamples+
                    " readable, sequential H.264 samples");
            return new Result(true,"H.264 MP4 verified: "+width+"x"+height+
                ", at least "+samples+" encoded samples with increasing timestamps");
        }catch(Throwable e){
            return new Result(false,"Could not reopen/decode MP4 container: "+
                (e.getMessage()==null?e.getClass().getSimpleName():e.getMessage()));
        }finally{extractor.release();}
    }
}
