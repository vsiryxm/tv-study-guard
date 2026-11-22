package cn.studyguard.tv;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.test.InstrumentationTestCase;
import cn.studyguard.core.*;
import java.lang.reflect.Field;
import java.util.Arrays;

/** Dedicated emulator only: changes test questions and records. */
public final class QuizFlowTest extends InstrumentationTestCase {
    private Context context;private Store store;private SQLiteDatabase db;
    private final Question fixed=new Question("math",2,"6 × 3 = ?",Arrays.asList("18","9","12","16"),0,"");
    @Override protected void setUp() throws Exception {
        super.setUp();context=getInstrumentation().getTargetContext();store=Store.get(context);
        resetRound();store.snapshot();db=SQLiteDatabase.openDatabase(context.getDatabasePath("answers.db").getPath(),null,SQLiteDatabase.OPEN_READWRITE);db.delete("reviews",null,null);
    }
    @Override protected void tearDown() throws Exception {db.close();store.updateRules(Rules.defaults());super.tearDown();}
    private void resetRound() throws Exception {
        store.updateRules(new Rules(true,120,480,2,Arrays.asList("math")));
        store.engine.state.intervalAnchorMillis=0;store.engine.state.nextQuestionAtMillis=0;store.engine.state.pending=1;store.engine.state.usedMillis=0;store.engine.state.celebrationMillis=0;store.engine.state.currentHadWrong=false;store.engine.state.bonusGranted=false;
        setQuestion(fixed);
    }
    private void setQuestion(Question question) throws Exception {Field f=Store.class.getDeclaredField("current");f.setAccessible(true);f.set(store,question);}
    public void testWrongRetainsQuestionAndDoesNotRevealAnswerOrStackPractice() throws Exception {
        assertEquals("答错了，请再试一次",store.answer(fixed,1));assertSame(fixed,store.question());
        assertEquals("答错了，请再试一次",store.answer(fixed,2));assertSame(fixed,store.question());assertEquals(2,store.pending());
        assertTrue(context.getSharedPreferences("guard",0).getBoolean("currentHadWrong",false));
        assertTrue(context.getSharedPreferences("guard",0).getBoolean("bonusGranted",false));
        store.answer(fixed,0);assertEquals(1,store.pending());
        assertFalse("重试答对后中途不放礼花",store.celebrating());assertTrue(store.blocked());
        Question practice=store.question();assertNotSame(fixed,practice);
        assertTrue("加练题使用加练提示语",store.practiceActive());
        store.answer(practice,practice.correctIndex);assertEquals(0,store.pending());assertTrue(store.celebrating());assertTrue(store.blocked());
    }
    public void testPracticeWrongNeverAddsAThirdQuestionAndChainSurvivesRestart() throws Exception {
        store.answer(fixed,1);store.answer(fixed,0);   // wrong then retry correct: practice stage
        assertEquals(1,store.pending());
        Question practice=store.question();
        store.answer(practice,(practice.correctIndex+1)%4);assertEquals(1,store.pending());  // practice wrong adds nothing
        store.answer(practice,(practice.correctIndex+1)%4);assertEquals(1,store.pending());
        Field instance=Store.class.getDeclaredField("instance");instance.setAccessible(true);
        instance.set(null,null);
        Store restarted=Store.get(context);
        assertEquals(1,restarted.pending());assertTrue(restarted.practiceActive());
        Question resumed=restarted.question();assertEquals(practice.key(),resumed.key());
        restarted.answer(resumed,resumed.correctIndex);assertEquals(0,restarted.pending());assertTrue(restarted.celebrating());
        // Restore the one process-wide singleton: the running service and its pairing server
        // still hold the original Store, and later test classes must keep sharing it.
        instance.set(null,store);
    }
    public void testFirstWrongOfQuizSignalsRegretFeedbackOnlyOnce() throws Exception {
        assertFalse(store.consumeFirstWrongFeedback());
        store.answer(fixed,1);assertTrue(store.consumeFirstWrongFeedback());   // first wrong of the first question
        assertFalse(store.consumeFirstWrongFeedback());
        store.answer(fixed,2);assertFalse(store.consumeFirstWrongFeedback());  // repeated wrong: no stacking
        store.answer(fixed,0);assertFalse(store.consumeFirstWrongFeedback());  // retry correct
        Question practice=store.question();
        store.answer(practice,(practice.correctIndex+1)%4);assertFalse(store.consumeFirstWrongFeedback());  // practice wrong: face+sound reserved for the first question
    }
    public void testDueReviewFirstTryMasteryAndPersistentWrongHistory() throws Exception {
        db.delete("reviews","question_key=?",new String[]{fixed.key()});
        store.answer(fixed,1);store.answer(fixed,2);store.answer(fixed,0);
        try(Cursor c=db.rawQuery("SELECT wrong_count,lapse_count,mastered,due_at FROM reviews WHERE question_key=?",new String[]{fixed.key()})) {
            assertTrue(c.moveToFirst());assertEquals(2,c.getInt(0));assertEquals(1,c.getInt(1));assertEquals(0,c.getInt(2));assertTrue(c.getLong(3)>System.currentTimeMillis());
        }
        resetRound();setQuestion(null);ContentValues due=new ContentValues();due.put("due_at",System.currentTimeMillis()-1);db.update("reviews",due,"question_key=?",new String[]{fixed.key()});
        Question review=store.question();assertEquals(fixed.key(),review.key());store.answer(review,review.correctIndex);
        try(Cursor c=db.rawQuery("SELECT wrong_count,mastered FROM reviews WHERE question_key=?",new String[]{fixed.key()})) {
            assertTrue(c.moveToFirst());assertEquals(2,c.getInt(0));assertEquals(1,c.getInt(1));
        }
        assertTrue(store.snapshot().getInt("reviewTotal")>=1);
    }
    public void testFailedReviewMovesToNextIntervalOnlyOncePerPresentation() throws Exception {
        db.delete("reviews","question_key=?",new String[]{fixed.key()});store.answer(fixed,1);store.answer(fixed,0);
        resetRound();store.answer(fixed,1);store.answer(fixed,2);store.answer(fixed,0);
        try(Cursor c=db.rawQuery("SELECT lapse_count,due_at FROM reviews WHERE question_key=?",new String[]{fixed.key()})) {
            assertTrue(c.moveToFirst());assertEquals(2,c.getInt(0));long wait=c.getLong(1)-System.currentTimeMillis();assertTrue(wait>3590000 && wait<=3600000);
        }
    }
    public void testFutureAndOtherGradeReviewsDoNotAppearEarly() throws Exception {
        store.answer(fixed,1);store.answer(fixed,0);resetRound();setQuestion(null);
        assertFalse(fixed.key().equals(store.question().key()));
        ContentValues otherGrade=new ContentValues();otherGrade.put("due_at",0);otherGrade.put("grade",3);db.update("reviews",otherGrade,"question_key=?",new String[]{fixed.key()});
        setQuestion(null);assertFalse(fixed.key().equals(store.question().key()));
    }
    public void testHintRoundTripAndLegacyRulesDefault() throws Exception {
        org.json.JSONObject old=Store.ruleJson(Rules.defaults());old.remove("quizHint");assertEquals(Rules.DEFAULT_QUIZ_HINT,Store.parseRules(old).quizHint);
        old.put("quizHint","先休息，再动脑");Rules custom=Store.parseRules(old);assertEquals("先休息，再动脑",Store.ruleJson(custom).getString("quizHint"));
    }
    public void testDatabaseUpgradePreservesAnswersAndSchedulesKnownWrongItems() throws Exception {
        String name="quiz-migration-test.db";context.deleteDatabase(name);
        SQLiteDatabase legacy=context.openOrCreateDatabase(name,0,null);
        legacy.execSQL("CREATE TABLE answers(id INTEGER PRIMARY KEY AUTOINCREMENT,at INTEGER NOT NULL,subject TEXT NOT NULL,grade INTEGER NOT NULL,prompt TEXT NOT NULL,chosen TEXT NOT NULL,answer TEXT NOT NULL,correct INTEGER NOT NULL)");
        QuestionBank.Item item=QuestionBank.inventory().get(0);
        Question known=new QuestionBank(new java.util.Random(1)).restore(item.subject,item.minGrade,item.prompt,item.answer);
        legacy.execSQL("INSERT INTO answers(at,subject,grade,prompt,chosen,answer,correct) VALUES(?,?,?,?,?,?,?)",new Object[]{1000,item.subject,item.minGrade,item.prompt,item.choices.get(1),item.answer,0});legacy.setVersion(1);legacy.close();
        Store.Records helper=new Store.Records(context,name);
        try {
            SQLiteDatabase upgraded=helper.getWritableDatabase();assertEquals(2,upgraded.getVersion());
            try(Cursor c=upgraded.rawQuery("SELECT COUNT(*) FROM answers",null)){assertTrue(c.moveToFirst());assertEquals(1,c.getInt(0));}
            try(Cursor c=upgraded.rawQuery("SELECT question_key,wrong_count,mastered FROM reviews",null)){assertTrue(c.moveToFirst());assertEquals(known.key(),c.getString(0));assertEquals(1,c.getInt(1));assertEquals(0,c.getInt(2));}
        }finally{helper.close();context.deleteDatabase(name);}
    }
}
