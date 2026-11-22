package cn.studyguard.tv;
import android.content.*;
import android.test.InstrumentationTestCase;
import android.view.*;
import android.widget.*;
import cn.studyguard.core.*;
import java.lang.reflect.*;
import java.util.Arrays;

/** Dedicated emulator only. Verifies real views and elapsed unlock time. */
public final class QuizPresentationTest extends InstrumentationTestCase {
    private MainActivity activity;private Store store;
    private final Question q=new Question("math",2,"6 × 3 = ?",Arrays.asList("18","9","12","16"),0,"");
    @Override protected void setUp() throws Exception {
        super.setUp();getInstrumentation().setInTouchMode(false);Context c=getInstrumentation().getTargetContext();store=Store.get(c);
        activity=(MainActivity)getInstrumentation().startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        getInstrumentation().runOnMainSync(()->{try {
            store.updateRules(new Rules(true,120,480,2,Arrays.asList("math"),"先休息，再动脑"));
            store.engine.state.intervalAnchorMillis=0;store.engine.state.nextQuestionAtMillis=0;store.engine.state.pending=1;store.engine.state.usedMillis=0;store.engine.state.currentHadWrong=false;store.engine.state.celebrationMillis=0;store.engine.state.bonusGranted=false;
            Field f=Store.class.getDeclaredField("current");f.setAccessible(true);f.set(store,q);
            ParentAuth auth=new ParentAuth(c);if(!auth.configured())auth.setup("482951","482951");
            Method home=MainActivity.class.getDeclaredMethod("home");home.setAccessible(true);home.invoke(activity);
        }catch(Exception e){throw new RuntimeException(e);}});getInstrumentation().waitForIdleSync();
    }
    @Override protected void tearDown() throws Exception {getInstrumentation().runOnMainSync(()->activity.finish());store.updateRules(Rules.defaults());super.tearDown();}
    private <T> T view(View root,Class<T> kind) {
        if(kind.isInstance(root))return kind.cast(root);
        if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){T found=view(((ViewGroup)root).getChildAt(i),kind);if(found!=null)return found;}return null;
    }
    private <T> T view(Class<T> kind) {return view(activity.getWindow().getDecorView(),kind);}
    private int texts(View root) {int count=root instanceof TextView?1:0;if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++)count+=texts(((ViewGroup)root).getChildAt(i));return count;}
    public void testHintQuestionFourChoicesAndParentEntryDirectionalGrid() throws Throwable {
        runTestOnUiThread(()->{QuizView quiz=view(QuizView.class);assertNotNull(quiz);assertEquals(7,texts(quiz));assertEquals(4,quiz.options.length);assertTrue(quiz.options[0].isFocused());
            int[] a=new int[2],b=new int[2],c=new int[2],d=new int[2];quiz.options[0].getLocationInWindow(a);quiz.options[1].getLocationInWindow(b);quiz.options[2].getLocationInWindow(c);quiz.options[3].getLocationInWindow(d);
            assertTrue(Math.abs(a[1]-b[1])<=Ui.dp(activity,3));assertEquals(c[1],d[1]);assertTrue(Math.abs(a[0]-c[0])<=Ui.dp(activity,10));assertTrue(b[0]>a[0]);assertTrue(c[1]>a[1]);});
        getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT);runTestOnUiThread(()->assertTrue(view(QuizView.class).options[1].isFocused()));
        getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);runTestOnUiThread(()->assertTrue(view(QuizView.class).options[3].isFocused()));
    }
    public void testFlatOptionsKeepGoldFocusAndEnlargedCenteredText() throws Throwable {
        runTestOnUiThread(()->{QuizView quiz=view(QuizView.class);TextView title=findText(quiz,"6 × 3 = ?"),hint=findText(quiz,"先休息，再动脑");assertNotNull(title);assertNotNull(hint);assertEquals(Ui.GOLD,title.getCurrentTextColor());assertTrue(title.getTypeface().isBold());assertEquals(48f,title.getTextSize()/activity.getResources().getDisplayMetrics().scaledDensity);assertEquals(32f,hint.getTextSize()/activity.getResources().getDisplayMetrics().scaledDensity);assertNull(hint.getBackground());assertEquals(Gravity.CENTER,hint.getGravity());
            android.graphics.Bitmap reference=null;
            for(Button b:quiz.options){assertTrue(title.getTextSize()>b.getTextSize());assertEquals(30f,b.getTextSize()/activity.getResources().getDisplayMetrics().scaledDensity);assertEquals(1f,b.getScaleX());assertEquals(1f,b.getScaleY());
                android.graphics.drawable.Drawable background=b.getBackground();android.graphics.Rect bounds=new android.graphics.Rect(background.getBounds());background.setBounds(0,0,120,80);background.setState(new int[]{});
                android.graphics.Bitmap image=android.graphics.Bitmap.createBitmap(120,80,android.graphics.Bitmap.Config.ARGB_8888);background.draw(new android.graphics.Canvas(image));
                assertEquals(Ui.OPTION_IDLE,image.getPixel(60,40));if(reference==null)reference=image;else assertTrue(reference.sameAs(image));background.setBounds(bounds);background.setState(b.getDrawableState());
                b.requestFocus();assertEquals(Ui.GOLD,b.getCurrentTextColor());
            }
            quiz.options[0].requestFocus();assertEquals(Ui.TEXT,quiz.options[3].getCurrentTextColor());
        });
    }
    public void testWrongKeepsSameQuestionAndMenuOpensQrWithoutUnlock() throws Throwable {
        runTestOnUiThread(()->{QuizView quiz=view(QuizView.class);quiz.options[1].performClick();assertSame(q,store.question());assertSame(quiz,view(QuizView.class));assertEquals(2,store.pending());
            quiz.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_MENU));assertNull(view(QuizView.class));
        });
    }
    private TextView findText(View root,String text) {
        if(root instanceof TextView && text.contentEquals(((TextView)root).getText()))return (TextView)root;
        if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){TextView found=findText(((ViewGroup)root).getChildAt(i),text);if(found!=null)return found;}return null;
    }
    public void testWrongThenRetryEntersPracticeWithoutMidFireworks() throws Throwable {
        runTestOnUiThread(()->{QuizView first=view(QuizView.class);first.options[1].performClick();});
        getInstrumentation().waitForIdleSync();
        runTestOnUiThread(()->{QuizView quiz=view(QuizView.class);assertSame(quiz,view(QuizView.class));quiz.options[0].performClick();});  // retry correct
        getInstrumentation().waitForIdleSync();
        runTestOnUiThread(()->{
            QuizView practice=view(QuizView.class);assertNotNull(practice);
            assertNull("中途不放礼花",view(CelebrationView.class));
            assertEquals(1,store.pending());assertTrue(store.practiceActive());
            assertNotNull("加练题显示加练提示语",findText(practice,Store.PRACTICE_HINT));
            practice.options[store.question().correctIndex].performClick();
        });
        getInstrumentation().waitForIdleSync();
        runTestOnUiThread(()->{assertNotNull(view(CelebrationView.class));assertEquals(0,store.pending());});
    }
    public void testSubjectPrefixHiddenOnQuizTitle() throws Throwable {
        Question poetry=new Question("poetry",6,"唐诗接句：李绅《悯农·其一》中，“春种一粒粟”的下一句是？",Arrays.asList("秋收万颗子","处处闻啼鸟","谁知盘中餐","白日依山尽"),0,"");
        runTestOnUiThread(()->activity.setContentView(new QuizView(activity,"休息提示",poetry,index->{},()->{})));getInstrumentation().waitForIdleSync();
        runTestOnUiThread(()->{
            QuizView quiz=view(QuizView.class);assertNotNull(quiz);
            assertNull("答题页不显示科目字样",findText(quiz,poetry.prompt));
            assertNotNull("显示去前缀后的纯题目",findText(quiz,poetry.prompt.substring("唐诗接句：".length())));
        });
    }
    private void verifyTextFits(View root) {
        if(root instanceof TextView){TextView text=(TextView)root;assertNotNull(text.getLayout());assertTrue("clipped: "+text.getText(),text.getLayout().getHeight()+text.getCompoundPaddingTop()+text.getCompoundPaddingBottom()<=text.getHeight()+2);int last=text.getLayout().getLineCount()-1;assertEquals(0,text.getLayout().getEllipsisCount(last));assertEquals(text.length(),text.getLayout().getLineEnd(last));}
        if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++)verifyTextFits(((ViewGroup)root).getChildAt(i));
    }
    public void testLongQuestionsChoicesAndMaximumHintRemainFullyVisible() throws Throwable {
        StringBuilder hint=new StringBuilder();for(int i=0;i<80;i++)hint.append("学");
        for(String subject:Rules.SUBJECTS){QuestionBank.Item longest=null,choice=null;int longestOption=-1;
            for(QuestionBank.Item item:QuestionBank.inventory())if(item.subject.equals(subject)){if(longest==null || item.prompt.length()>longest.prompt.length())longest=item;for(String option:item.choices)if(option.length()>longestOption){choice=item;longestOption=option.length();}}
            for(QuestionBank.Item item:new QuestionBank.Item[]{longest,choice}){Question question=new Question(subject,6,item.prompt,item.choices,0,item.explanation);
                runTestOnUiThread(()->activity.setContentView(new QuizView(activity,hint.toString(),question,index->{},()->{})));getInstrumentation().waitForIdleSync();
                runTestOnUiThread(()->{QuizView quiz=view(QuizView.class);assertNotNull(quiz);verifyTextFits(quiz);for(Button b:quiz.options){int[] pos=new int[2];b.getLocationInWindow(pos);assertTrue(b.getHeight()>=Ui.dp(activity,104));assertTrue(b.isFocusable());}});
            }
        }
    }
    public void testCorrectShowsFullScreenFireworksForFiveSecondsBeforeUnlock() throws Throwable {
        long started=android.os.SystemClock.elapsedRealtime();
        runTestOnUiThread(()->{view(QuizView.class).options[0].performClick();assertNotNull(view(CelebrationView.class));assertTrue(store.blocked());assertEquals(0,store.pending());});
        Thread.sleep(4700);runTestOnUiThread(()->{assertTrue(store.blocked());assertNotNull(view(CelebrationView.class));});
        long deadline=started+6700;
        boolean[] watching={false};
        do {
            Thread.sleep(100);
            runTestOnUiThread(()->watching[0]="watching".equals(activityField("page"))&&!(Boolean)activityField("visible"));
        }while(!watching[0] && android.os.SystemClock.elapsedRealtime()<deadline);
        runTestOnUiThread(()->{assertFalse(store.blocked());assertTrue(watching[0]);assertFalse(activity.isFinishing());});
        Context c=getInstrumentation().getTargetContext();c.startActivity(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        deadline=android.os.SystemClock.elapsedRealtime()+2500;
        boolean[] returned={false};
        do {
            Thread.sleep(50);
            runTestOnUiThread(()->returned[0]="home".equals(activityField("page"))&&(Boolean)activityField("visible"));
        }while(!returned[0] && android.os.SystemClock.elapsedRealtime()<deadline);
        assertTrue("The launcher should bring the home page back to the foreground",returned[0]);
        assertTrue(android.os.SystemClock.elapsedRealtime()-started>=5000);
    }
    public void testManualLauncherReopenDoesNotReplayFinishedCelebration() throws Throwable {
        runTestOnUiThread(()->{
            store.engine.state.pending=0;
            store.engine.state.celebrationMillis=1000;
            activity.onNewIntent(new Intent(activity,MainActivity.class));
        });
        runTestOnUiThread(()->{assertEquals("home",activityField("page"));assertFalse(store.celebrating());assertTrue((Boolean)activityField("visible"));});
    }
    private Object activityField(String name){try{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(activity);}catch(Exception e){throw new RuntimeException(e);}}
    public void testCelebrationRefreshReturnsToWatchingWithoutOpeningHome() throws Throwable {
        runTestOnUiThread(()->{try{
            view(QuizView.class).options[0].performClick();assertNotNull(view(CelebrationView.class));
            store.engine.state.celebrationMillis=0;
            ((Runnable)activityField("refresh")).run();assertEquals("watching",activityField("page"));
        }catch(Exception e){throw new RuntimeException(e);}});
        getInstrumentation().waitForIdleSync();runTestOnUiThread(()->assertFalse((Boolean)activityField("visible")));
    }
    public void testCelebrationCompletionKeepsPendingQuestionInForeground() throws Throwable {
        runTestOnUiThread(()->{try{
            view(QuizView.class).options[0].performClick();store.engine.state.celebrationMillis=0;store.engine.state.pending=1;
            Method done=MainActivity.class.getDeclaredMethod("finishCelebration");done.setAccessible(true);done.invoke(activity);
            assertEquals("quiz",activityField("page"));assertNotNull(view(QuizView.class));assertTrue((Boolean)activityField("visible"));assertTrue(store.blocked());
        }catch(Exception e){throw new RuntimeException(e);}});
    }
}
