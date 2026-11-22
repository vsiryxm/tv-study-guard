package cn.studyguard.tv;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.util.Locale;
final class Ui {
    // ---- Visual system (v0.7.2 beautify): layered midnight-blue + mint glow ----
    // OLED-style dark for the 10-foot living-room screen: deep layered background,
    // top-lit gradient cards, dual-ring focus glow visible from 3 m. All built from
    // GradientDrawable/LinearGradient primitives available since API 1 (ICS-safe).
    static final int BG=Color.rgb(10,21,35),CARD=Color.rgb(24,42,66),TEXT=Color.rgb(240,246,250),MUTED=Color.rgb(169,189,207),MINT=Color.rgb(121,218,195),ACCENT=Color.rgb(255,217,168);
    private static final int BG_TOP=Color.rgb(18,36,57),BG_BOTTOM=Color.rgb(7,15,24);
    private static final int CARD_TOP=Color.rgb(30,50,80),CARD_BOTTOM=Color.rgb(21,35,58),CARD_EDGE=Color.rgb(44,65,96);
    private static final int MINT_BRIGHT=Color.rgb(155,239,217),MINT_DEEP=Color.rgb(95,201,174);
    private static final int MINT_GLOW=Color.argb(96,121,218,195),WHITE_EDGE=Color.argb(70,255,255,255);
    static final int CORNER=16,FOCUS_CORNER=18;
    static final int GOLD=0xffefc878;
    static final int OPTION_IDLE=0xff1c2c3a,OPTION_FOCUS=0xff205d40,OPTION_EDGE=0xff566c7c;
    /** Flat, identical resting options; saturated fill and gold type identify focus. */
    static android.graphics.drawable.StateListDrawable option(Context c) {
        android.graphics.drawable.StateListDrawable states=new android.graphics.drawable.StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed},optionSurface(c,0xff194b33,0xffefc878,3));
        states.addState(new int[]{android.R.attr.state_focused},optionSurface(c,OPTION_FOCUS,0xff9bdfc0,3));
        states.addState(new int[]{},optionSurface(c,OPTION_IDLE,OPTION_EDGE,1));return states;
    }
    private static GradientDrawable optionSurface(Context c,int fill,int edge,int width) {
        GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(c,12));d.setStroke(dp(c,width),edge);return d;
    }
    static android.content.res.ColorStateList optionText() {
        return new android.content.res.ColorStateList(new int[][]{new int[]{android.R.attr.state_pressed},new int[]{android.R.attr.state_focused},new int[]{}},new int[]{GOLD,GOLD,TEXT});
    }
    static void flatButton(Button b) {
        bg(b,option(b.getContext()));b.setTextColor(optionText());
        if(Build.VERSION.SDK_INT>=21){b.setStateListAnimator(null);b.setElevation(0);}
    }
    /** Circular A/B/C/D badge: dark disc + state-colored letter drawn with Canvas text,
     *  so it renders identically on every TV font (no emoji/glyph dependence). */
    static final class Badge extends Drawable {
        private final android.graphics.Paint disc=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.Paint letter=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final String text;private final int size;
        Badge(Context c,String text) {
            this.text=text;disc.setColor(BG);size=dp(c,26);
            letter.setColor(TEXT);letter.setTypeface(Typeface.DEFAULT_BOLD);
            letter.setTextSize(dp(c,15));letter.setTextAlign(android.graphics.Paint.Align.CENTER);
        }
        @Override public void draw(Canvas canvas) {
            android.graphics.Rect b=getBounds();if(b.isEmpty())return;
            float cx=b.exactCenterX(),cy=b.exactCenterY(),r=Math.min(b.width(),b.height())/2f;
            canvas.drawCircle(cx,cy,r,disc);
            canvas.drawText(text,cx,cy-(letter.descent()+letter.ascent())/2f,letter);
        }
        @Override public boolean isStateful() {return true;}
        @Override protected boolean onStateChange(int[] state) {int color=optionText().getColorForState(state,TEXT);if(letter.getColor()==color)return false;letter.setColor(color);invalidateSelf();return true;}
        @Override public void setAlpha(int alpha) {disc.setAlpha(alpha);letter.setAlpha(alpha);}
        @Override public void setColorFilter(ColorFilter filter) {disc.setColorFilter(filter);letter.setColorFilter(filter);}
        @Override public int getOpacity() {return android.graphics.PixelFormat.TRANSPARENT;}
        @Override public int getIntrinsicWidth() {return size;}
        @Override public int getIntrinsicHeight() {return size;}
    }
    /** Speech-bubble background: rounded card with mint outline and a left tail
     *  pointing at the mascot. Tail fill is painted after the rect stroke so the
     *  mouth opens seamlessly over the outline. */
    static final class Bubble extends Drawable {
        private final android.graphics.Paint fill=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.Paint line=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final float radius,tailW,tailH;
        Bubble(Context c) {
            fill.setStyle(android.graphics.Paint.Style.FILL);fill.setColor(CARD);
            line.setStyle(android.graphics.Paint.Style.STROKE);line.setStrokeWidth(dp(c,1.4f));line.setColor(MINT);
            line.setStrokeJoin(android.graphics.Paint.Join.ROUND);line.setStrokeCap(android.graphics.Paint.Cap.ROUND);
            radius=dp(c,14);tailW=dp(c,13);tailH=dp(c,18);
        }
        @Override public void draw(Canvas canvas) {
            android.graphics.Rect b=getBounds();if(b.isEmpty())return;
            android.graphics.RectF rect=new android.graphics.RectF(b.left+tailW,b.top+2,b.right-2,b.bottom-2);
            float cy=b.exactCenterY();
            canvas.drawRoundRect(rect,radius,radius,fill);
            canvas.drawRoundRect(rect,radius,radius,line);
            android.graphics.Path tail=new android.graphics.Path();
            tail.moveTo(b.left+tailW,cy-tailH*.42f);
            tail.lineTo(b.left+1,cy);
            tail.lineTo(b.left+tailW,cy+tailH*.42f);
            tail.close();
            canvas.drawPath(tail,fill);
            canvas.drawLine(b.left+tailW,cy-tailH*.42f,b.left+1,cy,line);
            canvas.drawLine(b.left+1,cy,b.left+tailW,cy+tailH*.42f,line);
        }
        @Override public void setAlpha(int alpha) {fill.setAlpha(alpha);line.setAlpha(alpha);}
        @Override public void setColorFilter(ColorFilter filter) {fill.setColorFilter(filter);line.setColorFilter(filter);}
        @Override public int getOpacity() {return android.graphics.PixelFormat.TRANSLUCENT;}
    }
    // ---- Android 4.0 (API 15) compatibility helpers (kept in sync with the compat branch) ----
    /** StandardCharsets is API 19; resolve once via name. */
    static final java.nio.charset.Charset UTF8=java.nio.charset.Charset.forName("UTF-8");
    static final java.nio.charset.Charset ASCII=java.nio.charset.Charset.forName("US-ASCII");
    private static final java.util.concurrent.atomic.AtomicInteger VIEW_IDS=new java.util.concurrent.atomic.AtomicInteger(0x50000001);
    /** View.generateViewId appeared in API 17; allocate app-private ids outside the resource range. */
    static int id() {return VIEW_IDS.getAndIncrement();}
    /** View.setBackground appeared in API 16; fall back to its deprecated twin. */
    @SuppressWarnings("deprecation")
    static void bg(View view,Drawable background) {if(Build.VERSION.SDK_INT>=16)view.setBackground(background);else view.setBackgroundDrawable(background);}
    /** Overlay permission is granted automatically at install below API 23. */
    static boolean overlaysGranted(Context context) {return Build.VERSION.SDK_INT<23||Settings.canDrawOverlays(context);}
    /** PowerManager.isInteractive appeared in API 20; isScreenOn before that. */
    @SuppressWarnings("deprecation")
    static boolean screenOn(PowerManager power) {return Build.VERSION.SDK_INT>=20?power.isInteractive():power.isScreenOn();}
    /** ViewTreeObserver.removeOnGlobalLayoutListener appeared in API 16. */
    @SuppressWarnings("deprecation")
    static void removeGlobalLayout(ViewTreeObserver observer,ViewTreeObserver.OnGlobalLayoutListener listener) {if(Build.VERSION.SDK_INT>=16)observer.removeOnGlobalLayoutListener(listener);else observer.removeGlobalOnLayoutListener(listener);}
    static int dp(Context c,float v) { return (int)(c.getResources().getDisplayMetrics().density*v+.5f); }
    static GradientDrawable shape(int color) { GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(CORNER);return d; }
    /** Full-screen midnight gradient: subtle top light, deep bottom — spatial depth on TV. */
    static GradientDrawable root(Context c) {
        GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{BG_TOP,BG_BOTTOM});
        d.setCornerRadius(0);return d;
    }
    /** Layered card: top-lit vertical gradient with a hairline highlight edge. */
    static GradientDrawable card(Context c) {
        GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{CARD_TOP,CARD_BOTTOM});
        d.setCornerRadius(dp(c,CORNER));d.setStroke(dp(c,1),CARD_EDGE);return d;
    }
    /** Focused mint surface: bright-to-deep mint gradient with an inner white hairlight. */
    static GradientDrawable mint(Context c) {
        GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{MINT_BRIGHT,MINT_DEEP});
        d.setCornerRadius(dp(c,CORNER));d.setStroke(dp(c,1),WHITE_EDGE);return d;
    }
    /** Dual-ring focus glow for transparent controls: solid mint ring + translucent halo. */
    static android.graphics.drawable.LayerDrawable glow(Context c,boolean focused) {
        GradientDrawable halo=new GradientDrawable();halo.setColor(Color.TRANSPARENT);halo.setCornerRadius(dp(c,FOCUS_CORNER+3));
        GradientDrawable core=new GradientDrawable();core.setColor(Color.TRANSPARENT);core.setCornerRadius(dp(c,FOCUS_CORNER));
        if(focused) {halo.setStroke(dp(c,5),MINT_GLOW);core.setStroke(dp(c,2),MINT);}
        android.graphics.drawable.LayerDrawable layers=new android.graphics.drawable.LayerDrawable(new GradientDrawable[]{halo,core});
        return layers;
    }
    /** Transparent focus-aware background: invisible at rest, mint ring when focused. */
    static GradientDrawable outline(Context c,boolean focused) {
        GradientDrawable d=new GradientDrawable();d.setColor(Color.TRANSPARENT);d.setCornerRadius(dp(c,CORNER));
        if(focused)d.setStroke(dp(c,2),MINT);
        return d;
    }
    static LinearLayout column(Context c) { LinearLayout l=new LinearLayout(c);l.setOrientation(LinearLayout.VERTICAL);return l; }
    static TextView text(Context c,String s,int sp,int color) { TextView t=new TextView(c);t.setText(s);t.setTextSize(sp);t.setTextColor(color);t.setPadding(0,dp(c,4),0,dp(c,6));return t; }
    static TextView title(Context c,String s,int size) { TextView t=text(c,s,size,TEXT);t.setTypeface(null,Typeface.BOLD);return t; }
    /** Hero headline: high-contrast mint (OLED dark-mode guidance keeps text solid for
     *  7:1 readability; the gradient accent moves to a decorative rule below it). */
    static TextView hero(Context c,String s,int size) { TextView t=title(c,s,size);t.setTextColor(MINT);return t; }
    /** Magazine-style accent rule: full-width mint fading right into transparency.
     *  Note: inside a VERTICAL LinearLayout, layout_weight acts on height, so a
     *  partial-width line needs MATCH_PARENT width with the fade built into the gradient. */
    static View rule(Context c,int heightDp) {
        View v=new View(c);
        android.widget.LinearLayout.LayoutParams p=new android.widget.LinearLayout.LayoutParams(-1,dp(c,heightDp));
        p.topMargin=dp(c,6);p.bottomMargin=dp(c,2);v.setLayoutParams(p);
        GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
            new int[]{MINT,Color.argb(140,121,218,195),Color.argb(0,121,218,195)});
        d.setCornerRadius(dp(c,heightDp/2f));bg(v,d);return v;
    }
    static Button button(Context c,String label,Runnable action) {
        Button b=new Button(c);b.setText(label);b.setTextSize(17);b.setTextColor(TEXT);b.setAllCaps(false);b.setPadding(dp(c,20),dp(c,10),dp(c,20),dp(c,10));bg(b,card(c));
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.setMargins(0,dp(c,8),0,dp(c,8));b.setLayoutParams(params);b.setMinHeight(dp(c,50));
        b.setOnFocusChangeListener((v,focused)->{bg(b,focused?mint(c):card(c));b.setTextColor(focused?Color.rgb(8,20,32):TEXT);});b.setOnClickListener(v->action.run());return b;
    }
    static void gap(LinearLayout l,int dp) { Space s=new Space(l.getContext());l.addView(s,new LinearLayout.LayoutParams(1,Ui.dp(l.getContext(),dp))); }
    /** "距离下次休息" friendly display: hours / minutes / seconds. */
    static String countdown(long millis) {
        long total=(Math.max(0,millis)+999)/1000;
        long hours=total/3600,minutes=total%3600/60,seconds=total%60;
        if(hours>0)return hours+" 小时 "+String.format(Locale.US,"%02d",minutes)+" 分 "+String.format(Locale.US,"%02d",seconds)+" 秒";
        if(minutes>0)return minutes+" 分 "+String.format(Locale.US,"%02d",seconds)+" 秒";
        return total+" 秒";
    }
    /** Reliable dropdown chevron drawn with canvas lines; no font-dependent glyphs. */
    static final class Chevron extends Drawable {
        private final android.graphics.Paint paint=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final int widthDp,heightDp;
        Chevron(Context c,int color) {
            paint.setStyle(android.graphics.Paint.Style.STROKE);
            paint.setStrokeCap(android.graphics.Paint.Cap.ROUND);
            paint.setStrokeWidth(dp(c,2.2f));
            paint.setColor(color);
            widthDp=dp(c,16);heightDp=dp(c,10);
        }
        @Override public void draw(Canvas canvas) {
            android.graphics.Rect bounds=getBounds();
            if(bounds.isEmpty())return;
            float halfW=bounds.width()*0.36f,cx=bounds.centerX(),top=bounds.height()*0.28f,bottom=bounds.height()*0.72f;
            canvas.drawLine(cx-halfW,top,cx,bottom,paint);
            canvas.drawLine(cx,bottom,cx+halfW,top,paint);
        }
        @Override public void setAlpha(int alpha) {paint.setAlpha(alpha);}
        @Override public void setColorFilter(ColorFilter filter) {paint.setColorFilter(filter);}
        @Override public int getOpacity() {return android.graphics.PixelFormat.TRANSLUCENT;}
        @Override public int getIntrinsicWidth() {return widthDp;}
        @Override public int getIntrinsicHeight() {return heightDp;}
    }
}
