package cn.studyguard.tv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/**
 * Transient regret face drawn entirely with Canvas primitives (no emoji glyphs,
 * so it renders identically on every TV font). Shown for the first wrong answer.
 */
final class RegretFace extends View {
    private final Paint stroke=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final android.graphics.Path drop=new android.graphics.Path();
    private final Runnable hide=this::hide;

    RegretFace(Context context) {
        super(context);
        setVisibility(GONE);
        setWillNotDraw(false);
    }

    void show(long durationMillis) {
        if(!isShown()) setVisibility(VISIBLE);
        removeCallbacks(hide);
        postDelayed(hide,durationMillis);
        invalidate();
    }

    void hide() { removeCallbacks(hide); setVisibility(GONE); }

    @Override protected void onMeasure(int widthSpec,int heightSpec) {
        int size=Ui.dp(getContext(),132);
        setMeasuredDimension(size,size);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float size=Math.min(getWidth(),getHeight());
        float cx=getWidth()/2f,cy=getHeight()/2f;
        float radius=size*.34f;

        // soft dark disc so the face reads over the question text
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(0xE60E1826);
        canvas.drawCircle(cx,cy,radius*1.18f,fill);

        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(size*.028f);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setColor(0xFFF2D9A0);

        // head
        canvas.drawCircle(cx,cy,radius,stroke);

        // closed sad eyes: small drooping arcs (legacy RectF overload: the float-bounds
        // drawArc variant only exists since API 21)
        float eyeY=cy-radius*.28f,eyeDx=radius*.42f,eyeR=radius*.16f;
        android.graphics.RectF eyeL=new android.graphics.RectF(cx-eyeDx-eyeR,eyeY-eyeR*.55f,cx-eyeDx+eyeR,eyeY+eyeR*.55f);
        android.graphics.RectF eyeR2=new android.graphics.RectF(cx+eyeDx-eyeR,eyeY-eyeR*.55f,cx+eyeDx+eyeR,eyeY+eyeR*.55f);
        canvas.drawArc(eyeL,15f,150f,false,stroke);
        canvas.drawArc(eyeR2,15f,150f,false,stroke);

        // downturned mouth
        float mouthY=cy+radius*.52f,mouthR=radius*.30f;
        android.graphics.RectF mouth=new android.graphics.RectF(cx-mouthR,mouthY-mouthR,cx+mouthR,mouthY+mouthR);
        canvas.drawArc(mouth,205f,130f,false,stroke);

        // single sweat drop
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(0xFF9AD2FF);
        float dx=cx+radius*.82f,dy=cy-radius*.66f;
        canvas.drawCircle(dx,dy+size*.016f,size*.016f,fill);
        drop.rewind();
        drop.moveTo(dx,dy-size*.052f);
        drop.quadTo(dx+size*.02f,dy-size*.006f,dx,dy+size*.016f);
        drop.quadTo(dx-size*.02f,dy-size*.006f,dx,dy-size*.052f);
        drop.close();
        canvas.drawPath(drop,fill);
    }
}
