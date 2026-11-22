package cn.studyguard.tv;
import android.test.InstrumentationTestCase;
import cn.studyguard.core.Rules;
import org.json.*;

public final class RuleAutosaveTest extends InstrumentationTestCase {
    private Store store;
    @Override protected void setUp() throws Exception{super.setUp();store=Store.get(getInstrumentation().getTargetContext());store.updateRules(Rules.defaults());}
    @Override protected void tearDown() throws Exception{store.updateRules(Rules.defaults());super.tearDown();}
    public void testSingleFieldMergePreservesOtherChanges() throws Exception {store.updateRule("grade",5);store.updateRule("intervalMinutes",17);store.updateRule("quizHint","看一会儿，歇一会儿");assertEquals(5,store.rules().grade);assertEquals(17,store.rules().intervalMinutes);assertEquals("看一会儿，歇一会儿",store.rules().quizHint);}
    public void testInvalidChangeDoesNotModifyPersistedRules() throws Exception {String before=getInstrumentation().getTargetContext().getSharedPreferences("guard",0).getString("rules","");for(String key:new String[]{"subjects","quizHint","intervalMinutes","unknown"}){try{store.updateRule(key,key.equals("subjects")?new JSONArray():key.equals("quizHint")?"":0);fail(key);}catch(IllegalArgumentException expected){}}assertEquals(before,getInstrumentation().getTargetContext().getSharedPreferences("guard",0).getString("rules",""));}
    public void testStorageFailureDoesNotReportRuleSaveSuccess() throws Exception {
        store.lockNow();
        cn.studyguard.core.Question question=store.question();
        Rules before=store.rules();
        int pending=store.pending();
        int answers=store.summary().getInt("total");
        java.lang.reflect.Field field=Store.class.getDeclaredField("prefs");field.setAccessible(true);
        final android.content.SharedPreferences real=(android.content.SharedPreferences)field.get(store);
        final java.lang.reflect.InvocationHandler editorHandler=(proxy,method,args)->{
            if(method.getName().equals("commit"))return false;
            if(method.getReturnType()==android.content.SharedPreferences.Editor.class)return proxy;
            throw new UnsupportedOperationException(method.getName());
        };
        android.content.SharedPreferences failing=(android.content.SharedPreferences)java.lang.reflect.Proxy.newProxyInstance(
            getClass().getClassLoader(),new Class[]{android.content.SharedPreferences.class},(proxy,method,args)->{
                if(method.getName().equals("edit"))return java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{android.content.SharedPreferences.Editor.class},editorHandler);
                return method.invoke(real,args);
            });
        try {
            field.set(store,failing);
            try {store.updateRule("grade",before.grade==6?5:6);fail("must reject a failed rule commit");}
            catch(IllegalStateException expected){assertTrue(expected.getMessage().contains("存储"));}
            try {store.unlockNow();fail("must reject a failed unlock commit");}
            catch(IllegalStateException expected){assertTrue(expected.getMessage().contains("存储"));}
            assertTrue(store.answer(question,question.correctIndex).contains("未能保存"));
            assertSame(before,store.rules());
            assertSame(question,store.question());
            assertEquals(pending,store.pending());
            assertEquals(answers,store.summary().getInt("total"));
        }
        finally {field.set(store,real);store.save();}
        store.unlockNow();
    }
}
