package cn.studyguard.tv;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import cn.studyguard.core.*;

/** TV owns viewing and questions. All parent forms live in the LAN browser. */
public final class MainActivity extends Activity {
    private static final int BACKGROUND=0xff101b26, SURFACE=0xff1c2c3a, BORDER=0xff405466, INK=0xffedf3f5, GREEN=0xff9bdfc0, MUTED=0xffb4c3ce, PRIMARY=0xffa7e4c6, FOCUS=0xffefc878;
    private Store store;
    private String page="home",shownHint="",connectionKey="";
    private boolean visible,shownLimit;
    private Question shownQuestion;
    private TextView status,connectionInfo,ruleSummary;
    private LinearLayout qrBox;
    private long addressesAt;private String cachedAddresses="";
    private static volatile MainActivity currentActivity;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable refresh=new Runnable(){public void run(){
        if(!visible)return;
        if("home".equals(page) && store.blocked())home();
        else if("quiz".equals(page) && (!store.blocked() || store.celebrating() || shownLimit!=store.limitReached() || (!shownLimit && (shownQuestion!=store.question() || !shownHint.equals(store.quizHintForDisplay())))))home();
        else if("celebration".equals(page) && !store.celebrating())finishCelebration();
        if("home".equals(page) || "pair".equals(page)){updateStatus();updateConnection();}
        reportBlockingSurface();
        handler.postDelayed(this,1000);
    }};
    static void remoteLock(){
        final MainActivity activity=currentActivity;
        if(activity!=null)activity.runOnUiThread(()->{if(activity.visible){activity.home();activity.reportBlockingSurface();}});
    }
    static void refreshAfterUnlock(){
        final MainActivity activity=currentActivity;
        // Leave the practice UI for an unlocked home while preserving an open pairing page.
        if(activity!=null)activity.runOnUiThread(()->{
            if(activity.visible && ("quiz".equals(activity.page)||"celebration".equals(activity.page))){
                activity.home();
                activity.reportBlockingSurface();
            }
        });
    }
    private void reportBlockingSurface(){GuardService.activityBlocking(visible && store.blocked() && ("quiz".equals(page)||"celebration".equals(page)||"pair".equals(page)));}
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);store=Store.get(this);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        GuardService.start(this);if(saved==null&&!getIntent().getBooleanExtra("parent",false))store.skipCelebrationOnManualOpen();home();
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},1);
        if(getIntent().getBooleanExtra("parent",false))pair();
    }
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);if(intent.getBooleanExtra("parent",false))pair();else{store.skipCelebrationOnManualOpen();home();}}
    @Override protected void onResume(){super.onResume();currentActivity=this;visible=true;GuardService.visible(true);handler.removeCallbacks(refresh);handler.post(refresh);if("home".equals(page)||"quiz".equals(page)||"watching".equals(page))home();}
    @Override protected void onPause(){if(currentActivity==this)currentActivity=null;visible=false;GuardService.visible(false);reportBlockingSurface();handler.removeCallbacks(refresh);super.onPause();}
    private int dp(float v){return Ui.dp(this,v);}
    private TextView label(String text,int size,int color){TextView v=Ui.text(this,text,size,color);v.setPadding(0,dp(4),0,dp(4));return v;}
    private GradientDrawable panel(int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(20));d.setStroke(dp(1),BORDER);return d;}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private Button action(String title,Runnable run){return action(title,run,false);}
    private Button action(String title,Runnable run,boolean primary){
        Button b=new Button(this);b.setText(title);b.setTextSize(18);b.setTextColor(primary?BACKGROUND:INK);b.setAllCaps(false);b.setGravity(Gravity.CENTER);b.setPadding(dp(20),0,dp(20),0);b.setMinHeight(dp(52));
        int base=primary?PRIMARY:SURFACE;StateListDrawable states=new StateListDrawable();GradientDrawable focused=panel(base);focused.setStroke(dp(4),FOCUS);
        states.addState(new int[]{android.R.attr.state_pressed},panel(primary?0xff81cba8:0xff344c5d));states.addState(new int[]{android.R.attr.state_focused},focused);states.addState(new int[]{},panel(base));Ui.bg(b,states);
        b.setOnClickListener(v->run.run());LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(56));lp.topMargin=dp(12);b.setLayoutParams(lp);return b;
    }
    private LinearLayout shell(String name){
        page=name;status=null;ruleSummary=null;qrBox=null;connectionInfo=null;connectionKey="";
        LinearLayout root=column();root.setPadding(dp(40),dp(22),dp(40),dp(24));GradientDrawable backdrop=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xff182a38,BACKGROUND,0xff142332});Ui.bg(root,backdrop);
        TextView brand=label("学习守护   /   看电视有时限，休息一下再继续",16,GREEN);brand.setTypeface(null,Typeface.BOLD);root.addView(brand);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.addView(root,new ScrollView.LayoutParams(-1,-1));setContentView(scroll);return root;
    }
    private void home(){
        if(store.celebrating()){celebrate();return;}if(store.blocked()){quiz();return;}
        LinearLayout root=shell("home");LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,0,1);rp.topMargin=dp(12);root.addView(row,rp);
        LinearLayout hero=column();hero.setPadding(0,dp(12),dp(32),dp(12));row.addView(hero,new LinearLayout.LayoutParams(0,-2,1.25f));
        hero.addView(label("看电视有时限，\n完成答题再继续。",32,INK));hero.addView(label("观看时长与答题内容，由家长设置。",18,MUTED));
        status=label("",19,GREEN);status.setPadding(dp(18),dp(12),dp(18),dp(12));Ui.bg(status,panel(SURFACE));LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2);sp.topMargin=dp(16);hero.addView(status,sp);updateStatus();ruleSummary=label("",16,MUTED);hero.addView(ruleSummary);updateStatus();
        Button watch=action("返回观看",this::returnToWatching,true);hero.addView(watch);hero.addView(action("手机／电脑管理  →",this::pair));if(!Ui.overlaysGranted(this))hero.addView(action("完成系统权限授权",this::permissions));
        row.addView(connectionCard(),new LinearLayout.LayoutParams(0,-2,1));watch.requestFocus();updateConnection();
    }
    private LinearLayout connectionCard(){
        LinearLayout card=column();card.setGravity(Gravity.CENTER_HORIZONTAL);card.setPadding(dp(20),dp(14),dp(20),dp(14));Ui.bg(card,panel(SURFACE));
        TextView title=label("手机／电脑管理",22,INK);title.setTypeface(null,Typeface.BOLD);card.addView(title);card.addView(label("连接同一家庭网络后扫码",16,MUTED));
        qrBox=column();qrBox.setGravity(Gravity.CENTER);qrBox.setPadding(dp(6),dp(6),dp(6),dp(6));GradientDrawable qrPaper=new GradientDrawable();qrPaper.setColor(Color.WHITE);qrPaper.setCornerRadius(dp(10));Ui.bg(qrBox,qrPaper);card.addView(qrBox,new LinearLayout.LayoutParams(dp(184),dp(184)));
        connectionInfo=label("正在启动本地管理服务…",15,INK);connectionInfo.setGravity(Gravity.CENTER);card.addView(connectionInfo,new LinearLayout.LayoutParams(-1,-2));return card;
    }
    private void updateStatus(){if(status==null)return;Rules r=store.rules();if(ruleSummary!=null)ruleSummary.setText("小学 "+r.grade+" 年级 · 每 "+r.intervalMinutes+" 分钟休息");String serviceError=GuardService.startError(),storageError=store.storageError();status.setText((!serviceError.isEmpty()?serviceError:!storageError.isEmpty()?storageError:!r.enabled?"守护未开启 · 请家长扫码设置":!Ui.overlaysGranted(this)?"等待系统授权 · 暂时无法拦截":"下次休息  "+Ui.countdown(store.remainingMillis()))+"\n今日观看  "+(store.usedMillis()/60000)+" / "+r.dailyLimitMinutes+" 分钟");}
    private void updateConnection(){
        if(qrBox==null)return;ParentServer server=ParentServer.active();if(SystemClock.elapsedRealtime()-addressesAt>5000 || cachedAddresses.isEmpty()){cachedAddresses=ParentServer.addresses(this);addressesAt=SystemClock.elapsedRealtime();}String addresses=cachedAddresses;
        if(server==null || !server.running() || !addresses.startsWith("http://")){
            String failure=GuardService.startError();if(failure.isEmpty())failure=ParentServer.startupError();String key=server==null?(failure.isEmpty()?"starting":failure):addresses;if(!key.equals(connectionKey)){connectionKey=key;qrBox.removeAllViews();connectionInfo.setText(server==null?(failure.isEmpty()?"管理服务启动中，请稍候。":failure):addresses);}return;
        }
        PairingGate.Offer offer=server.connectionOffer();String base=addresses.split("\n")[0];String key=base+offer.qrToken;
        if(!key.equals(connectionKey)){connectionKey=key;qrBox.removeAllViews();qrBox.addView(new QrView(this,base+"/#pair="+offer.qrToken),new LinearLayout.LayoutParams(-1,-1));}
        connectionInfo.setText(base+"\n首次设置连接码："+offer.code+"\n扫码只打开页面，管理需家长口令");
    }
    private void pair(){
        LinearLayout root=shell("pair");root.addView(label("家长管理，在手机或电脑上完成。",28,INK));LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);root.addView(row,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout steps=column();steps.setPadding(0,dp(8),dp(28),0);row.addView(steps,new LinearLayout.LayoutParams(0,-2,1));
        steps.addView(label("01   手机或电脑连接家庭网络\n02   手机扫码，或在浏览器输入右侧网址\n03   输入家长口令，管理观看与学习",21,INK));steps.addView(label("可以收藏网址，下次直接打开。\n若电视网络地址改变，请重新扫码。\n打开此页不会解除限制，电视计时照常进行。",16,MUTED));
        Button back=action(store.blocked()?"返回练习":"返回首页",this::home,true);steps.addView(back);if(!Ui.overlaysGranted(this))steps.addView(action("系统权限授权",this::permissions));row.addView(connectionCard(),new LinearLayout.LayoutParams(0,-2,1));back.requestFocus();updateConnection();
    }
    private void celebrate(){page="celebration";status=null;qrBox=null;setContentView(new CelebrationView(this,store.celebrationRemainingMillis(),this::finishCelebration));}
    private void finishCelebration(){
        if(!"celebration".equals(page))return;
        store.tick(0,Ui.screenOn((PowerManager)getSystemService(POWER_SERVICE)));
        if(store.celebrating())return;
        if(store.blocked()){home();return;}
        page="watching";moveTaskToBack(true);
    }
    private void quiz(){
        shownLimit=store.limitReached();if(shownLimit){LinearLayout root=shell("quiz");root.addView(label("今天的电视时间用完了",32,INK));root.addView(label("休息一下吧，明天再继续。",24,MUTED));root.addView(action("手机／电脑管理",this::pair));return;}
        page="quiz";status=null;qrBox=null;Question q=store.question();shownQuestion=q;shownHint=store.quizHintForDisplay();final QuizView[] surface={null};
        surface[0]=new QuizView(this,shownHint,q,index->{String result=store.answer(q,index);if(store.consumeFirstWrongFeedback()){surface[0].showRegret();return;}if(store.celebrating()||!store.blocked()||store.question()!=q){home();return;}if(!result.isEmpty())Toast.makeText(this,result,Toast.LENGTH_SHORT).show();},this::pair);setContentView(surface[0]);
    }
    private void permissions(){
        if(Build.VERSION.SDK_INT<23){Toast.makeText(this,BrandGuide.instructions(),Toast.LENGTH_LONG).show();return;}Intent intent=new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName()));
        try{startActivity(intent);}catch(RuntimeException e){new AlertDialog.Builder(this).setTitle("请允许悬浮窗").setMessage(BrandGuide.instructions()+"\n请到电视系统的应用权限设置中允许学习守护显示悬浮窗。").setPositiveButton("知道了",null).show();}
    }
    private void returnToWatching(){Toast.makeText(this,"需要回来时，从电视应用列表打开“学习守护”",Toast.LENGTH_LONG).show();moveTaskToBack(true);}
    @Override public void onBackPressed(){if("home".equals(page))returnToWatching();else if("pair".equals(page))home();else if("quiz".equals(page))Toast.makeText(this,"完成练习后即可继续观看",Toast.LENGTH_SHORT).show();}
    @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);super.onDestroy();}
}
