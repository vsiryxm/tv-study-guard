package cn.studyguard.tv;
import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.*;
import java.util.Random;

/**
 * Full-screen local fireworks; no images, network, sound, or input that can skip the wait.
 * Rockets launch from the bottom with a rising trail, burst at altitude into gravity-affected
 * particles with twinkle and fading trails. Preallocated arrays keep old TVs at ~30 fps.
 */
final class CelebrationView extends View {
    private static final int ROCKETS=12, PARTICLES=460, PER_BURST=58;
    private static final int[] COLORS={0xffffd36e,0xff79dac3,0xffff87bd,0xff8eb7ff,0xfff2f7ff,0xffbd99ff,0xfffde28f};
    private final Paint line=new Paint(Paint.ANTI_ALIAS_FLAG),dot=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] rocketX=new float[ROCKETS],rocketTop=new float[ROCKETS];
    private final long[] rocketBirth=new long[ROCKETS],rocketRise=new long[ROCKETS];
    private final boolean[] rocketBurst=new boolean[ROCKETS];
    private final float[] px=new float[PARTICLES],py=new float[PARTICLES],pvx=new float[PARTICLES],pvy=new float[PARTICLES];
    private final long[] pBirth=new long[PARTICLES],pLife=new long[PARTICLES];
    private final float[] pSpark=new float[PARTICLES];
    private final int[] pColor=new int[PARTICLES];
    private final long duration;
    private long started=-1;
    private final Runnable finished;
    private final Random random=new Random(20261103L);
    private int allocation;

    CelebrationView(Context context,long duration,Runnable finished) {
        super(context);this.duration=Math.max(1,duration);this.finished=finished;
        setBackgroundColor(Ui.BG);setFocusableInTouchMode(true);setContentDescription("答对了，礼花庆祝");
        Random seed=new Random();
        for(int i=0;i<ROCKETS;i++) {
            rocketBirth[i]=(long)(i*(duration*0.58/ROCKETS)+seed.nextDouble()*220);
            rocketRise[i]=520+(long)(seed.nextDouble()*260);
            rocketX[i]=0.12f+seed.nextFloat()*0.76f;
            rocketTop[i]=0.16f+seed.nextFloat()*0.34f;
        }
    }
    @Override protected void onAttachedToWindow() {super.onAttachedToWindow();started=SystemClock.elapsedRealtime();requestFocus();postDelayed(finished,duration);}
    @Override protected void onDetachedFromWindow() {removeCallbacks(finished);super.onDetachedFromWindow();}
    @Override public boolean dispatchKeyEvent(KeyEvent event) {return true;}

    private void burst(float x,float y,long now) {
        int color=COLORS[random.nextInt(COLORS.length)];
        for(int n=0;n<PER_BURST;n++) {
            int slot=-1;
            for(int attempt=0;attempt<PARTICLES;attempt++) {
                int candidate=(allocation+n*7+attempt)%PARTICLES;
                if(pBirth[candidate]<=0 || now-pBirth[candidate]>pLife[candidate]) {slot=candidate;break;}
            }
            if(slot<0)return;
            double angle=random.nextDouble()*Math.PI*2;
            float speed=(0.10f+random.nextFloat()*0.34f)*Math.min(getWidth(),getHeight());
            px[slot]=x;py[slot]=y;
            pvx[slot]=(float)Math.cos(angle)*speed;
            pvy[slot]=(float)Math.sin(angle)*speed-0.06f*Math.min(getWidth(),getHeight());
            pBirth[slot]=now+1;pLife[slot]=850+(long)(random.nextDouble()*650);
            pSpark[slot]=random.nextFloat()*(float)(Math.PI*2);
            pColor[slot]=random.nextInt(4)==0?0xfff6fbff:color;
        }
        allocation=(allocation+PER_BURST*7)%PARTICLES;
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if(started<0)started=SystemClock.elapsedRealtime();
        long now=SystemClock.elapsedRealtime(),elapsed=now-started;
        float width=getWidth(),height=getHeight(),scale=Math.min(width,height);
        float gravity=0.34f*height;
        boolean any=false;
        line.setStyle(Paint.Style.STROKE);line.setStrokeCap(Paint.Cap.ROUND);
        dot.setStyle(Paint.Style.FILL);

        for(int i=0;i<ROCKETS;i++) {
            long t=elapsed-rocketBirth[i];
            if(t<0)continue;
            if(t<rocketRise[i]) {
                any=true;
                float progress=t/(float)rocketRise[i];
                float eased=1f-(1f-progress)*(1f-progress);          // decelerating climb
                float x=rocketX[i]*width,y=height-(height-rocketTop[i]*height)*eased;
                line.setStrokeWidth(Ui.dp(getContext(),2.4f));line.setColor(0xffe8f7ff);
                float trail=scale*0.075f*(1f-progress*0.55f);
                canvas.drawLine(x,y+trail*0.2f,x,y+trail,line);
                dot.setColor(0xfffde9c8);canvas.drawCircle(x,y,Ui.dp(getContext(),2.6f),dot);
            } else if(!rocketBurst[i]) {
                rocketBurst[i]=true;
                burst(rocketX[i]*width,rocketTop[i]*height,now);
            }
        }

        for(int i=0;i<PARTICLES;i++) {
            long age=now-pBirth[i];
            if(pBirth[i]<=0 || age<0 || age>pLife[i])continue;
            any=true;
            float seconds=age/1000f;
            float x=px[i]+pvx[i]*seconds;
            float y=py[i]+pvy[i]*seconds+0.5f*gravity*seconds*seconds;
            float k=1f-age/(float)pLife[i];
            if(k<=0f)continue;
            float twinkle=0.62f+0.38f*(float)Math.sin(pSpark[i]+age*0.045f);
            int alpha=(int)(255*k*twinkle);
            if(alpha<=3)continue;
            float dx=pvx[i],dy=pvy[i]+gravity*seconds;
            float norm=(float)Math.sqrt(dx*dx+dy*dy);
            if(norm>1f) {dx/=norm;dy/=norm;}
            float tail=scale*0.024f*k;
            line.setStrokeWidth(Ui.dp(getContext(),k>0.45f?2.2f:1.5f));
            line.setColor(pColor[i]);line.setAlpha(alpha);
            canvas.drawLine(x,y,x-dx*tail,y-dy*tail,line);
            dot.setColor(pColor[i]);dot.setAlpha(alpha);
            canvas.drawCircle(x,y,k>0.5f?Ui.dp(getContext(),2.4f):Ui.dp(getContext(),1.6f),dot);
        }
        if(any || elapsed<duration)postInvalidateDelayed(33);
    }
}
