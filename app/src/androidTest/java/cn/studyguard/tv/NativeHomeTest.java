package cn.studyguard.tv;
import android.content.*;
import android.test.InstrumentationTestCase;
import android.view.*;
import android.widget.*;
import cn.studyguard.core.*;
import java.lang.reflect.*;

/** Dedicated emulator: opening QR must not mutate guarding or require TV forms. */
public final class NativeHomeTest extends InstrumentationTestCase {
    private MainActivity activity;private Store store;
    @Override protected void setUp() throws Exception {super.setUp();Context c=getInstrumentation().getTargetContext();store=Store.get(c);store.unlockNow();activity=(MainActivity)getInstrumentation().startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));getInstrumentation().waitForIdleSync();}
    @Override protected void tearDown() throws Exception {getInstrumentation().runOnMainSync(()->activity.finish());store.unlockNow();super.tearDown();}
    private void show(String name){try{Method m=MainActivity.class.getDeclaredMethod(name);m.setAccessible(true);m.invoke(activity);}catch(Exception e){throw new RuntimeException(e);}}
    private String page(){try{Field f=MainActivity.class.getDeclaredField("page");f.setAccessible(true);return (String)f.get(activity);}catch(Exception e){throw new RuntimeException(e);}}
    private void noForms(View v){assertFalse(v instanceof EditText);assertFalse(v instanceof android.webkit.WebView);if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)noForms(((ViewGroup)v).getChildAt(i));}
    public void testNativeHomeAndPairHaveNoManagementForms() throws Throwable {runTestOnUiThread(()->{show("home");assertEquals("home",page());noForms(activity.getWindow().getDecorView());assertNotNull(activity.getCurrentFocus());show("pair");assertEquals("pair",page());noForms(activity.getWindow().getDecorView());assertTrue(activity.getCurrentFocus() instanceof Button);activity.onBackPressed();assertEquals("home",page());});}
    public void testQrReturnKeepsQuestionPracticeAndTiming() throws Throwable {runTestOnUiThread(()->{store.updateRules(new Rules(true,20,120,2,Rules.SUBJECTS));store.lockNow();Question q=store.question();store.answer(q,(q.correctIndex+1)%q.choices.size());int pending=store.pending();long used=store.usedMillis(),anchor=store.engine.state.intervalAnchorMillis;show("home");assertEquals("quiz",page());show("pair");assertTrue(store.blocked());assertEquals(pending,store.pending());assertEquals(used,store.usedMillis());assertEquals(anchor,store.engine.state.intervalAnchorMillis);activity.onBackPressed();assertEquals("quiz",page());assertSame(q,store.question());assertEquals(pending,store.pending());});}
    public void testQrDoesNotResetUnblockedTimerAndRemoteLockShowsQuiz() throws Throwable {runTestOnUiThread(()->{store.updateRules(new Rules(true,20,120,2,Rules.SUBJECTS));store.tick(12345,true);long used=store.usedMillis(),due=store.engine.state.nextQuestionAtMillis;show("pair");activity.onBackPressed();assertEquals(used,store.usedMillis());assertEquals(due,store.engine.state.nextQuestionAtMillis);store.lockNow();MainActivity.remoteLock();});getInstrumentation().waitForIdleSync();runTestOnUiThread(()->assertEquals("quiz",page()));}
    public void testDailyLimitCannotBeDismissedThroughQr() throws Throwable {runTestOnUiThread(()->{store.updateRules(new Rules(true,1,1,2,Rules.SUBJECTS));store.engine.state.usedMillis=60000;show("home");assertTrue(store.limitReached());show("pair");activity.onBackPressed();assertEquals("quiz",page());assertTrue(store.limitReached());assertTrue(store.blocked());});}
    public void testReturnWatchingAndBackKeepTaskForLauncherReentry() throws Throwable {
        final int taskId=activity.getTaskId();final long due=store.engine.state.nextQuestionAtMillis;
        for(int attempt=0;attempt<2;attempt++) {
            final boolean useBack=attempt==1;
            runTestOnUiThread(()->{if(useBack)activity.onBackPressed();else {View v=activity.getCurrentFocus();assertTrue(v instanceof Button);assertEquals("返回观看",((Button)v).getText().toString());v.performClick();}});
            getInstrumentation().waitForIdleSync();assertFalse(activity.isFinishing());
            Intent launch=getInstrumentation().getTargetContext().getPackageManager().getLaunchIntentForPackage("cn.studyguard.tv");
            assertNotNull(launch);getInstrumentation().getTargetContext().startActivity(launch);
            getInstrumentation().waitForIdleSync();
            long deadline=android.os.SystemClock.elapsedRealtime()+3000;
            Field f=MainActivity.class.getDeclaredField("currentActivity");f.setAccessible(true);
            while(f.get(null)!=activity && android.os.SystemClock.elapsedRealtime()<deadline)Thread.sleep(30);
            assertSame(activity,f.get(null));assertEquals(taskId,activity.getTaskId());assertEquals("home",page());
            assertEquals(due,store.engine.state.nextQuestionAtMillis);assertEquals(0,store.pending());
        }
    }
}
