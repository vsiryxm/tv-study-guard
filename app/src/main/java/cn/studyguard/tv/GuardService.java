package cn.studyguard.tv;

import android.app.*;
import android.content.*;
import android.graphics.PixelFormat;
import android.os.*;
import android.util.Log;
import android.view.*;
import android.widget.*;
import cn.studyguard.core.*;

public final class GuardService extends Service {
    private static final String TAG="StudyGuard";
    private static final long WINDOW_RETRY_MS=5000, SERVER_RETRY_MS=10000;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private Store store;
    private WindowManager windows;
    private View overlay;
    private ParentServer parentServer;
    private PowerManager power;
    private long lastTick;
    private boolean active=true, overlayLimit,overlayCelebrating;
    private Question overlayQuestion;
    private String overlayHint="";
    private static volatile boolean foregroundVisible;
    private static volatile boolean activityBlocking;
    private static volatile GuardService runningService;
    private static volatile String startError="";
    private volatile String windowState="waiting";
    private long retryWindowAt,retryServerAt;
    static void visible(boolean visible) { foregroundVisible=visible; }
    static void activityBlocking(boolean shown) { activityBlocking=shown; }
    static String startError() {return startError;}
    static String interception() {
        GuardService service=runningService;
        if(service==null)return "unavailable";
        if(activityBlocking)return "activity";
        return service.windowState;
    }
    static void start(Context context) {
        try {
            Intent intent=new Intent(context,GuardService.class);
            if(Build.VERSION.SDK_INT>=26)context.startForegroundService(intent);else context.startService(intent);
            startError="";
        }catch(RuntimeException e) {
            startError="守护服务未能启动，请检查电视的自启动和后台运行权限。";
            Log.w(TAG,"Guard service start rejected",e);
        }
    }
    private boolean blockingShown() {
        return activityBlocking || ("overlay".equals(windowState) && Ui.overlaysGranted(this));
    }
    private final BroadcastReceiver screens=new BroadcastReceiver() {
        public void onReceive(Context c,Intent i) {
            long now=SystemClock.elapsedRealtime();
            boolean wasOn=!Intent.ACTION_SCREEN_ON.equals(i.getAction());
            store.tick(Math.max(0,now-lastTick),wasOn,blockingShown());lastTick=now;
            updateOverlay();
        }
    };
    private final Runnable ticker=new Runnable() {
        public void run() {
            if(!active)return;
            long now=SystemClock.elapsedRealtime();store.tick(Math.max(0,now-lastTick),Ui.screenOn(power),blockingShown());lastTick=now;
            updateOverlay();
            ensureParentServer(now);
            handler.postDelayed(this,1000);
        }
    };
    @Override public void onCreate() {
        super.onCreate();store=Store.get(this);power=(PowerManager)getSystemService(POWER_SERVICE);windows=(WindowManager)getSystemService(WINDOW_SERVICE);
        if(Build.VERSION.SDK_INT>=26) { NotificationChannel channel=new NotificationChannel("guard","学习守护运行状态",NotificationManager.IMPORTANCE_LOW);channel.setShowBadge(false);((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(channel); }
        Intent open=new Intent(this,MainActivity.class);int pendingFlags=PendingIntent.FLAG_UPDATE_CURRENT;
        if(Build.VERSION.SDK_INT>=23)pendingFlags|=PendingIntent.FLAG_IMMUTABLE; // flag value exists only since API 23
        PendingIntent pending=PendingIntent.getActivity(this,0,open,pendingFlags);
        Notification.Builder builder=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,"guard"):new Notification.Builder(this);
        // getNotification() predates build() (API 16) and keeps working on every version.
        startForeground(10,builder.setSmallIcon(cn.studyguard.tv.R.drawable.ic_guard).setContentTitle("学习守护正在运行").setContentText("打开应用查看权限、家长规则和运行状态").setContentIntent(pending).setOngoing(true).getNotification());
        new ManagedProtection(this).restore();
        store.recover();lastTick=SystemClock.elapsedRealtime();
        IntentFilter filter=new IntentFilter();filter.addAction(Intent.ACTION_SCREEN_ON);filter.addAction(Intent.ACTION_SCREEN_OFF);registerReceiver(screens,filter);
        runningService=this;
        handler.post(ticker);
    }
    private void ensureParentServer(long now) {
        if((parentServer!=null && !parentServer.terminated()) || now<retryServerAt)return;
        retryServerAt=now+SERVER_RETRY_MS;
        if(parentServer!=null)parentServer.stop();
        try {
            parentServer=new ParentServer(this,store);parentServer.start();
        }catch(java.io.IOException e) {ParentServer.reportFailure(e);}
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId) {return START_STICKY;}
    @Override public IBinder onBind(Intent intent) { return null; }
    private void updateOverlay() {
        if(!store.blocked() || !Ui.screenOn(power) || foregroundVisible) {
            removeOverlay();windowState="waiting";retryWindowAt=0;return;
        }
        if(!Ui.overlaysGranted(this)) {
            removeOverlay();windowState="permission";retryWindowAt=0;return;
        }
        if(SystemClock.elapsedRealtime()<retryWindowAt)return;
        Question q=store.limitReached() || store.celebrating()?null:store.question();
        if(overlay==null || overlayLimit!=store.limitReached() || overlayCelebrating!=store.celebrating() || overlayQuestion!=q || !overlayHint.equals(store.quizHintForDisplay()))showOverlay(q);
    }
    private void openParent() {
        Intent intent=new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP).putExtra("parent",true);
        try {startActivity(intent);}catch(RuntimeException e){Log.w(TAG,"Could not open parent QR page",e);}
    }
    private void showOverlay(Question q) {
        removeOverlay();overlayLimit=store.limitReached();overlayCelebrating=store.celebrating();overlayQuestion=q;overlayHint=store.quizHintForDisplay();
        if(overlayCelebrating)overlay=new CelebrationView(this,store.celebrationRemainingMillis(),()->{store.tick(0,Ui.screenOn(power),blockingShown());updateOverlay();});
        else if(overlayLimit) {
            LinearLayout content=Ui.column(this);content.setPadding(Ui.dp(this,52),Ui.dp(this,24),Ui.dp(this,52),Ui.dp(this,24));Ui.bg(content,Ui.root(this));
            content.addView(Ui.title(this,"今天的电视时间用完了",30));content.addView(Ui.text(this,"去活动一下，或者读一本喜欢的书吧。明天会自动恢复。",22,Ui.TEXT));content.addView(Ui.button(this,"家长入口",this::openParent));overlay=content;
        }else {
            final QuizView[] surface={null};
            surface[0]=new QuizView(this,overlayHint,q,index->{
                String result=store.answer(q,index);
                if(store.consumeFirstWrongFeedback()) {surface[0].showRegret();return;}
                if(!result.isEmpty() && !result.startsWith("答对"))Toast.makeText(this,result,Toast.LENGTH_SHORT).show();
                updateOverlay();
            },this::openParent);
            overlay=surface[0];
        }
        int type=Build.VERSION.SDK_INT>=26?WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY:WindowManager.LayoutParams.TYPE_PHONE;
        WindowManager.LayoutParams p=new WindowManager.LayoutParams(-1,-1,type,WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.OPAQUE);p.gravity=Gravity.TOP|Gravity.START;
        try {
            windows.addView(overlay,p);windowState="overlay";retryWindowAt=0;
        }catch(RuntimeException e){
            overlay=null;windowState="window_error";retryWindowAt=SystemClock.elapsedRealtime()+WINDOW_RETRY_MS;
            Log.w(TAG,"Could not attach guard window",e);
        }
    }
    private void removeOverlay() { if(overlay!=null) {try {windows.removeView(overlay);}catch(RuntimeException ignored){}overlay=null;} }
    @Override public void onDestroy() { active=false;if(runningService==this)runningService=null;handler.removeCallbacksAndMessages(null);unregisterReceiver(screens);removeOverlay();store.save();if(parentServer!=null)parentServer.stop();super.onDestroy(); }
}
