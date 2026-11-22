package cn.studyguard.tv;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.Looper;

/**
 * Short locally synthesized "哦豁" interjection for the first wrong answer.
 * Plays on the music stream so media volume and muting are always honored;
 * overlapping playback is suppressed ("不连续叠播"). No assets, no network.
 */
final class OopsSound {
    private static final int SAMPLE_RATE=22050;
    private static volatile byte[] clip;
    private static final Object lock=new Object();
    private static volatile boolean playing;
    private static final Handler HANDLER=new Handler(Looper.getMainLooper());

    private OopsSound() {}

    static void play(Context context) {
        synchronized(lock) { if(playing) return; playing=true; }
        try {
            AudioManager audio=(AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
            if(audio==null || audio.getStreamVolume(AudioManager.STREAM_MUSIC)<=0) { finish(); return; }
            final byte[] data=clip();
            int minBuffer=AudioTrack.getMinBufferSize(SAMPLE_RATE,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT);
            int bufferSize=Math.max(data.length,Math.max(minBuffer,1));
            final AudioTrack track=new AudioTrack(AudioManager.STREAM_MUSIC,SAMPLE_RATE,AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,bufferSize,AudioTrack.MODE_STATIC);
            if(track.getState()!=AudioTrack.STATE_INITIALIZED) { try{track.release();}catch(RuntimeException ignored){} finish(); return; }
            track.write(data,0,data.length);
            track.setNotificationMarkerPosition(data.length/2);
            track.setPlaybackPositionUpdateListener(new AudioTrack.OnPlaybackPositionUpdateListener() {
                @Override public void onMarkerReached(AudioTrack t) { release(t); }
                @Override public void onPeriodicNotification(AudioTrack t) { }
            });
            track.play();
            HANDLER.postDelayed(()->{ try{track.stop();track.release();}catch(RuntimeException ignored){} finish(); },1200);
        } catch(RuntimeException e) { finish(); }
    }

    private static void release(AudioTrack track) {
        HANDLER.removeCallbacksAndMessages(null);
        try { track.stop(); track.release(); }catch(RuntimeException ignored) { }
        finish();
    }

    private static void finish() { synchronized(lock) { playing=false; } }

    /** Two gently falling syllables, "哦" then "豁", ~0.42 s. */
    private static byte[] clip() {
        byte[] prepared=clip;
        if(prepared!=null) return prepared;
        synchronized(lock) {
            if(clip!=null) return clip;
            int total=(int)(SAMPLE_RATE*0.42);
            short[] samples=new short[total];
            int at=0;
            at=fillSyllable(samples,at,0.15,360,320,1.0);
            at+=SAMPLE_RATE*30/1000;      // small breath between syllables
            fillSyllable(samples,at,0.22,300,190,0.9);
            byte[] data=new byte[total*2];
            for(int i=0;i<total;i++) {
                data[i*2]=(byte)(samples[i]&0xff);
                data[i*2+1]=(byte)((samples[i]>>8)&0xff);
            }
            clip=data;
            return data;
        }
    }

    private static int fillSyllable(short[] out,int start,double seconds,double fromHz,double toHz,double gain) {
        int count=(int)(SAMPLE_RATE*seconds);
        for(int i=0;i<count && start+i<out.length;i++) {
            double t=i/(double)count;
            double hz=fromHz+(toHz-fromHz)*t;
            double vibrato=1.0+0.012*Math.sin(2*Math.PI*6.0*i/SAMPLE_RATE);
            double envelope=Math.min(1.0,i/(SAMPLE_RATE*0.012))*Math.min(1.0,(count-i)/(double)(SAMPLE_RATE*0.05));
            double value=Math.sin(2*Math.PI*hz*vibrato*i/SAMPLE_RATE)*0.55+Math.sin(4*Math.PI*hz*vibrato*i/SAMPLE_RATE)*0.12;
            out[start+i]=(short)(int)Math.max(Short.MIN_VALUE,Math.min(Short.MAX_VALUE,value*gain*envelope*20000));
        }
        return start+count;
    }
}
