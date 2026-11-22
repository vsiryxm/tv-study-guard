package cn.studyguard.tv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/**
 * Happy little TV mascot for the quiz surface, drawn entirely with Canvas
 * primitives (no emoji or font glyphs, so it renders identically on every TV
 * ROM) in the same hand-drawn language as RegretFace. Uses the legacy
 * RectF drawArc/drawRoundRect overloads, which exist since API 1 — the
 * float-argument variants only appeared in API 21.
 */
final class Mascot extends View {
    private final Paint fill=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line=new Paint(Paint.ANTI_ALIAS_FLAG);

    Mascot(Context context) {
        super(context);
        setWillNotDraw(false);
    }

    @Override protected void onMeasure(int widthSpec,int heightSpec) {
        setMeasuredDimension(Ui.dp(getContext(),78),Ui.dp(getContext(),72));
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w=getWidth(),h=getHeight();
        float bodyL=w*.06f,bodyT=h*.30f,bodyR=w*.94f,bodyB=h*.97f;

        // antennas with candy tip beads
        line.setStyle(Paint.Style.STROKE);line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeWidth(w*.035f);
        line.setColor(0xFF79DAC3);
        canvas.drawLine(w*.30f,bodyT+h*.02f,w*.20f,h*.06f,line);
        line.setColor(0xFFFFD9A8);
        canvas.drawLine(w*.70f,bodyT+h*.02f,w*.80f,h*.06f,line);
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(0xFF79DAC3);canvas.drawCircle(w*.20f,h*.055f,w*.045f,fill);
        fill.setColor(0xFFFFD9A8);canvas.drawCircle(w*.80f,h*.055f,w*.045f,fill);

        // body: soft indigo rounded set with a mint hairline
        fill.setColor(0xFF24425F);
        android.graphics.RectF body=new android.graphics.RectF(bodyL,bodyT,bodyR,bodyB);
        canvas.drawRoundRect(body,w*.14f,w*.14f,fill);
        line.setColor(0xFF79DAC3);line.setStrokeWidth(w*.028f);
        canvas.drawRoundRect(body,w*.14f,w*.14f,line);

        // screen
        float insetX=w*.11f,insetY=h*.13f;
        fill.setColor(0xFF0A1523);
        android.graphics.RectF screen=new android.graphics.RectF(bodyL+insetX,bodyT+insetY,bodyR-insetX,bodyB-insetY*.9f);
        canvas.drawRoundRect(screen,w*.09f,w*.09f,fill);

        // face: bright eyes, mint smile, pink blush
        float cx=w/2f,eyeY=screen.top+screen.height()*.38f,eyeDx=screen.width()*.21f,eyeR=w*.052f;
        fill.setColor(0xFFF0F6FA);
        canvas.drawCircle(cx-eyeDx,eyeY,eyeR,fill);
        canvas.drawCircle(cx+eyeDx,eyeY,eyeR,fill);
        line.setStrokeCap(Paint.Cap.ROUND);line.setStrokeWidth(w*.035f);line.setColor(0xFF9BEFD9);
        float mouthY=screen.top+screen.height()*.62f,mouthR=screen.width()*.17f;
        android.graphics.RectF mouth=new android.graphics.RectF(cx-mouthR,mouthY-mouthR*.85f,cx+mouthR,mouthY+mouthR*.85f);
        canvas.drawArc(mouth,18f,144f,false,line);
        fill.setColor(0x66FF9EB5);
        canvas.drawCircle(cx-eyeDx*1.55f,mouthY-mouthR*.2f,w*.045f,fill);
        canvas.drawCircle(cx+eyeDx*1.55f,mouthY-mouthR*.2f,w*.045f,fill);

        // one little sparkle beside the antenna
        sparkle(canvas,w*.95f,h*.16f,w*.055f);
    }

    /** Four-point sparkle drawn with quadratic curves. */
    private void sparkle(Canvas canvas,float cx,float cy,float r) {
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(0xFFFFD9A8);
        android.graphics.Path p=new android.graphics.Path();
        p.moveTo(cx,cy-r);p.quadTo(cx+r*.18f,cy-r*.18f,cx+r,cy);
        p.quadTo(cx+r*.18f,cy+r*.18f,cx,cy+r);
        p.quadTo(cx-r*.18f,cy+r*.18f,cx-r,cy);
        p.quadTo(cx-r*.18f,cy-r*.18f,cx,cy-r);
        p.close();
        canvas.drawPath(p,fill);
    }
}
