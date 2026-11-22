package cn.studyguard.tv;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import android.os.SystemClock;
import org.json.*;
import cn.studyguard.core.*;
import java.text.SimpleDateFormat;
import java.util.*;

final class Store {
    private static Store instance;
    static synchronized Store get(Context c) { if(instance==null) instance=new Store(c.getApplicationContext()); return instance; }
    static final String PRACTICE_HINT="刚才答错了，接着奏乐接着舞";
    private final SharedPreferences prefs;
    private final Records records;
    private final QuestionBank bank=new QuestionBank(new java.security.SecureRandom());
    final GuardEngine engine;
    private Question current;
    private long lastSave,celebrationTick;
    private String lastCorrectKey="";
    private String presentedDay,lastSubject;
    private final LinkedHashSet<String> presentedIds=new LinkedHashSet<>();
    private boolean regretPending;
    private String storageError="";
    private static final String STORAGE_ERROR="电视存储写入失败，请检查存储空间后重试；本次操作未确认保存";

    /** Only used around explicit changes; the periodic usage checkpoint may lag by five seconds. */
    private final class Checkpoint {
        final Rules rules=engine.rules();
        final GuardEngine.State state=new GuardEngine.State(engine.state);
        final Question question=current;
        final long celebration=celebrationTick;
        final String correct=lastCorrectKey,day=presentedDay,subject=lastSubject;
        final Set<String> presented=new LinkedHashSet<>(presentedIds);
        final boolean regret=regretPending;
        void restore() {
            engine.setRules(rules);engine.state.copyFrom(state);current=question;
            celebrationTick=celebration;lastCorrectKey=correct;presentedDay=day;lastSubject=subject;
            presentedIds.clear();presentedIds.addAll(presented);regretPending=regret;
        }
    }
    private void rollback(Checkpoint before) {
        before.restore();
        // SharedPreferences.commit(false) may already have changed its in-memory map.
        // Rewrite the old values too, so an unrelated later commit cannot publish the failed edit.
        save();storageError=STORAGE_ERROR;
    }
    private void commitChange(Checkpoint before) {
        if(save())return;
        rollback(before);throw new IllegalStateException(STORAGE_ERROR);
    }
    synchronized String storageError() {return storageError;}
    private Store(Context context) {
        prefs=context.getSharedPreferences("guard",Context.MODE_PRIVATE); records=new Records(context);
        Rules rules=Rules.defaults();
        try { rules=parseRules(new JSONObject(prefs.getString("rules","{}"))); } catch(Exception ignored) { }
        GuardEngine.State state=new GuardEngine.State(prefs.getString("day",today()));
        state.usedMillis=Math.max(0,prefs.getLong("used",0)); state.sinceQuestionMillis=Math.max(0,prefs.getLong("since",0)); state.pending=Math.max(0,prefs.getInt("pending",0));
        int flowVersion=prefs.getInt("quizFlowVersion",0);
        if(flowVersion<3) {state.pending=Math.min(1,state.pending);}
        else {state.currentHadWrong=prefs.getBoolean("currentHadWrong",false);state.celebrationMillis=Math.max(0,prefs.getLong("celebrationMillis",0));}
        // v4: practice chain grant flag; derive it conservatively from pre-v4 states.
        if(flowVersion<4) {state.bonusGranted=state.currentHadWrong || state.pending>=2;}
        else {state.bonusGranted=prefs.getBoolean("bonusGranted",false);}
        prefs.edit().putInt("quizFlowVersion",4).commit();
        celebrationTick=SystemClock.elapsedRealtime();lastCorrectKey=prefs.getString("lastCorrectKey","");
        presentedDay=today();lastSubject=prefs.getString("lastSubject",null);
        if(!presentedDay.equals(state.day)) {prefs.edit().remove("presented").remove("lastSubject").commit();lastSubject=null;}
        else try { JSONArray a=new JSONArray(prefs.getString("presented","[]"));for(int i=0;i<a.length();i++)presentedIds.add(a.getString(i)); }catch(JSONException ignored) { }
        state.intervalAnchorMillis=prefs.getLong("intervalAnchorMillis",0);state.nextQuestionAtMillis=prefs.getLong("nextQuestionAtMillis",0);
        engine=new GuardEngine(rules,state);
        try { current=parseQuestion(new JSONObject(prefs.getString("question","{}"))); } catch(Exception ignored) { }
        // Open lazily: a full or unavailable database must not prevent the TV home from opening.
    }
    static String today() { return new SimpleDateFormat("yyyyMMdd",Locale.US).format(new Date()); }
    static boolean wasEnabled(Context context) {
        String saved=context.getSharedPreferences("guard",Context.MODE_PRIVATE).getString("rules","{}");
        try {return new JSONObject(saved).optBoolean("enabled",false);}
        catch(JSONException e) {return false;}
    }
    static Rules parseRules(JSONObject j) throws JSONException {
        JSONArray a=j.getJSONArray("subjects"); List<String> subjects=new ArrayList<>(); for(int i=0;i<a.length();i++) subjects.add(a.getString(i));
        if(!(j.get("enabled") instanceof Boolean))throw new IllegalArgumentException("enabled 须为布尔值");
        if(j.has("quizHint") && !(j.get("quizHint") instanceof String))throw new IllegalArgumentException("答题提示语须为文字");
        return new Rules(j.getBoolean("enabled"),integer(j,"intervalMinutes"),integer(j,"dailyLimitMinutes"),integer(j,"grade"),subjects,j.optString("quizHint",Rules.DEFAULT_QUIZ_HINT),j.has("semester")?integer(j,"semester"):0,j.has("difficulty")?integer(j,"difficulty"):2);
    }
    private static int integer(JSONObject j,String key) throws JSONException {
        Object value=j.get(key);if(!(value instanceof Number))throw new IllegalArgumentException("请输入整数："+key);
        double number=((Number)value).doubleValue();if(Double.isNaN(number) || Double.isInfinite(number) || number!=Math.rint(number) || number<Integer.MIN_VALUE || number>Integer.MAX_VALUE)throw new IllegalArgumentException("请输入整数："+key);
        return (int)number;
    }
    static JSONObject ruleJson(Rules r) throws JSONException {
        return new JSONObject().put("enabled",r.enabled).put("intervalMinutes",r.intervalMinutes).put("dailyLimitMinutes",r.dailyLimitMinutes).put("grade",r.grade).put("subjects",new JSONArray(r.subjects)).put("quizHint",r.quizHint).put("semester",r.semester).put("difficulty",r.difficulty);
    }
    private static JSONObject questionJson(Question q) throws JSONException {
        return new JSONObject().put("subject",q.subject).put("grade",q.grade).put("prompt",q.prompt).put("choices",new JSONArray(q.choices)).put("correctIndex",q.correctIndex).put("explanation",q.explanation).put("itemId",q.itemId).put("semester",q.semester).put("difficulty",q.difficulty);
    }
    private static Question parseQuestion(JSONObject j) throws JSONException {
        List<String> choices=new ArrayList<>(); JSONArray a=j.getJSONArray("choices");for(int i=0;i<a.length();i++)choices.add(a.getString(i));
        return new Question(j.getString("subject"),j.getInt("grade"),j.getString("prompt"),choices,j.getInt("correctIndex"),j.getString("explanation"),j.optString("itemId",""),j.optInt("semester",0),j.optInt("difficulty",1));
    }
    synchronized Rules rules() { return engine.rules(); }
    synchronized void updateRule(String key,Object value) throws JSONException {
        JSONObject rules=ruleJson(rules());if(!rules.has(key))throw new IllegalArgumentException("未知设置项");
        rules.put(key,value);updateRules(parseRules(rules));
    }
    synchronized void updateRules(Rules r) {
        Checkpoint before=new Checkpoint();
        Rules old=rules();boolean changed=old.grade!=r.grade || old.semester!=r.semester || old.difficulty!=r.difficulty || !new HashSet<>(old.subjects).equals(new HashSet<>(r.subjects));
        engine.setRules(r);if(old.intervalMinutes!=r.intervalMinutes)engine.reschedule(System.currentTimeMillis());
        if(changed) {current=null;engine.state.currentHadWrong=false;engine.state.bonusGranted=false;engine.state.pending=Math.min(1,engine.state.pending);}
        commitChange(before);
    }
    synchronized void recover() { engine.onBoot();save(); }
    synchronized void tick(long elapsed,boolean screen) {
        tick(elapsed,screen,true);
    }
    synchronized void tick(long elapsed,boolean screen,boolean blockingShown) {
        int previous=engine.state.pending;long previousSlot=engine.state.nextQuestionAtMillis;boolean limit=engine.dailyLimitReached(),wasCelebrating=celebrating();String day=engine.state.day;
        long now=SystemClock.elapsedRealtime();
        engine.tick(wasCelebrating?Math.max(0,now-celebrationTick):Math.min(Math.max(0,elapsed),Math.max(0,now-celebrationTick)),screen,today(),System.currentTimeMillis(),blockingShown);celebrationTick=now;
        if(previousSlot!=engine.state.nextQuestionAtMillis || previous!=engine.state.pending || wasCelebrating!=celebrating() || limit!=engine.dailyLimitReached() || !day.equals(engine.state.day) || now-lastSave>5000)save();
    }
    synchronized boolean celebrating() { return engine.celebrating(); }
    synchronized void skipCelebrationOnManualOpen() {if(celebrating()){engine.state.celebrationMillis=0;celebrationTick=SystemClock.elapsedRealtime();save();}}
    synchronized long celebrationRemainingMillis() { return Math.max(0,engine.state.celebrationMillis-Math.max(0,SystemClock.elapsedRealtime()-celebrationTick)); }
    synchronized boolean blocked() { return engine.blocked(); }
    synchronized boolean limitReached() { return engine.dailyLimitReached(); }
    synchronized int pending() { return engine.state.pending; }
    synchronized long usedMillis() { return engine.state.usedMillis; }
    synchronized long remainingMillis() { return engine.state.intervalAnchorMillis>0?Math.max(0,engine.state.nextQuestionAtMillis-System.currentTimeMillis()):Math.max(0,rules().intervalMinutes*60000L-engine.state.sinceQuestionMillis); }
    synchronized void lockNow() {
        Checkpoint before=new Checkpoint();
        engine.lockNow(System.currentTimeMillis());celebrationTick=SystemClock.elapsedRealtime();commitChange(before);
    }
    /** Phone-side "解锁屏幕": switch protection off and clear the pending question chain. */
    synchronized void unlockNow() throws JSONException {
        Checkpoint before=new Checkpoint();
        JSONObject rules=ruleJson(rules());rules.put("enabled",false);engine.setRules(parseRules(rules));
        engine.state.pending=0;engine.state.celebrationMillis=0;engine.state.currentHadWrong=false;engine.state.bonusGranted=false;current=null;celebrationTick=SystemClock.elapsedRealtime();commitChange(before);
    }
    synchronized Question question() {
        if(!presentedDay.equals(engine.state.day)) {presentedDay=engine.state.day;presentedIds.clear();lastSubject=null;}
        if(current==null) {
            try {current=dueQuestion();}
            catch(SQLiteException e) {storageError=STORAGE_ERROR;}
            if(current==null) {
                current=bank.next(rules(),presentedIds,lastSubject);
                for(int attempt=0;attempt<32 && current.key().equals(lastCorrectKey);attempt++)current=bank.next(rules(),presentedIds,lastSubject);
            }
            if(current!=null && !current.itemId.isEmpty())presentedIds.add(current.itemId);
            if(current!=null)lastSubject=current.subject;
            save();
        }
        return current;
    }
    /** True while the currently served question is the single granted practice question. */
    synchronized boolean practiceActive() {return engine.state.pending==1 && engine.state.bonusGranted;}
    synchronized String quizHintForDisplay() {return practiceActive()?PRACTICE_HINT:rules().quizHint;}
    synchronized int presentedTodayCount() {return presentedIds.size();}
    private Question dueQuestion() {
        Rules r=rules();List<String> args=new ArrayList<>();args.add(String.valueOf(System.currentTimeMillis()));args.add(String.valueOf(r.grade));args.addAll(r.subjects);
        String marks=android.text.TextUtils.join(",",Collections.nCopies(r.subjects.size(),"?"));
        try(Cursor c=records.getReadableDatabase().rawQuery("SELECT question FROM reviews WHERE mastered=0 AND due_at<=? AND grade=? AND subject IN ("+marks+") ORDER BY due_at ASC",args.toArray(new String[0]))) {
            while(c.moveToNext())try{Question q=parseQuestion(new JSONObject(c.getString(0)));if(q.difficulty<=r.difficulty && (r.semester==0 || q.semester<=r.semester))return q;}catch(JSONException ignored){}
        }
        return null;
    }
    synchronized String answer(Question expected,int index) {
        regretPending=false;
        if(!blocked() || limitReached() || celebrating())return "";
        Question q=question();if(q!=expected)return "规则已更新，请回答新题目";if(index<0 || index>=q.choices.size())return "";
        boolean correct=index==q.correctIndex,hadWrong=engine.state.currentHadWrong,granted=engine.state.bonusGranted;long now=System.currentTimeMillis();
        ContentValues v=new ContentValues();v.put("at",now);v.put("subject",q.subject);v.put("grade",q.grade);v.put("prompt",q.prompt);v.put("chosen",q.choices.get(index));v.put("answer",q.choices.get(q.correctIndex));v.put("correct",correct?1:0);
        Checkpoint before=new Checkpoint();
        try {
            SQLiteDatabase db=records.getWritableDatabase();db.beginTransaction();
            try {
                db.insertOrThrow("answers",null,v);recordReview(db,q,correct,hadWrong,now);
                db.execSQL("DELETE FROM answers WHERE id NOT IN (SELECT id FROM answers ORDER BY id DESC LIMIT 5000)");
                engine.answer(correct);
                if(!correct && !hadWrong && !granted)regretPending=true;
                if(correct) {lastCorrectKey=q.key();current=null;celebrationTick=SystemClock.elapsedRealtime();}
                if(!save())throw new IllegalStateException(STORAGE_ERROR);
                db.setTransactionSuccessful();
            }finally{db.endTransaction();}
            return correct?"答对了！":"答错了，请再试一次";
        }catch(SQLiteException | IllegalStateException e) {
            rollback(before);return "未能保存本次作答，请检查电视存储空间后重试";
        }
    }
    /** One-shot flag for the first-question first-wrong regret feedback (face + "哦豁"). */
    synchronized boolean consumeFirstWrongFeedback() {boolean value=regretPending;regretPending=false;return value;}
    private void recordReview(SQLiteDatabase db,Question q,boolean correct,boolean hadWrong,long now) {
        String key=q.key();int lapses=0,wrongCount=0;boolean exists=false,mastered=false;
        try(Cursor c=db.rawQuery("SELECT lapse_count,wrong_count,mastered FROM reviews WHERE question_key=?",new String[]{key})) {
            if(c.moveToFirst()){exists=true;lapses=c.getInt(0);wrongCount=c.getInt(1);mastered=c.getInt(2)!=0;}
        }
        if(correct && !exists)return;
        ContentValues v=new ContentValues();
        if(!correct) {
            if(!hadWrong)lapses=mastered?1:Math.min(1000000,lapses+1);
            v.put("question_key",key);v.put("subject",q.subject);v.put("grade",q.grade);v.put("prompt",q.prompt);
            try{v.put("question",questionJson(q).toString());}catch(JSONException e){throw new IllegalStateException(e);}
            v.put("wrong_count",wrongCount+1);v.put("lapse_count",Math.max(1,lapses));v.put("mastered",0);v.put("last_wrong_at",now);v.put("mastered_at",0);
            v.put("due_at",now+ReviewSchedule.delayMillis(lapses));
            if(db.insertWithOnConflict("reviews",null,v,SQLiteDatabase.CONFLICT_REPLACE)<0)throw new SQLiteException("review write failed");
        }else {
            v.put("mastered",hadWrong?0:1);v.put("mastered_at",hadWrong?0:now);
            if(hadWrong)v.put("due_at",now+ReviewSchedule.delayMillis(lapses));
            db.update("reviews",v,"question_key=?",new String[]{key});
        }
    }
    synchronized void requestQuestion() { engine.requestQuestion();save(); }
    private JSONObject stateJson() throws JSONException {
        return new JSONObject().put("rules",ruleJson(rules())).put("usedMinutes",Math.round(usedMillis()/6000.0)/10.0).put("pending",pending()).put("dailyLimitReached",limitReached()).put("blocked",blocked()).put("celebrating",celebrating()).put("day",engine.state.day).put("presentedToday",presentedTodayCount()).put("intervalAnchorMillis",engine.state.intervalAnchorMillis).put("nextQuestionAtMillis",engine.state.nextQuestionAtMillis).put("remainingMillis",remainingMillis());
    }
    private JSONObject statistics(JSONObject j) throws JSONException {
        try(Cursor c=records.getReadableDatabase().rawQuery("SELECT COUNT(*), COALESCE(SUM(correct),0) FROM answers",null)) {
            c.moveToFirst();j.put("total",c.getInt(0));j.put("correct",c.getInt(1));
        }
        try(Cursor c=records.getReadableDatabase().rawQuery("SELECT COUNT(*),COALESCE(SUM(mastered=0),0),COALESCE(SUM(mastered=0 AND due_at<=?),0) FROM reviews",new String[]{String.valueOf(System.currentTimeMillis())})) {
            c.moveToFirst();j.put("reviewTotal",c.getInt(0)).put("reviewActive",c.getInt(1)).put("reviewDue",c.getInt(2));
        }
        return j;
    }
    synchronized JSONObject summary() throws JSONException {
        JSONObject state=stateJson();
        try {return statistics(state);}
        catch(SQLiteException e) {return state.put("recordsUnavailable",true);}
    }
    synchronized JSONObject snapshot() throws JSONException {
        return statistics(stateJson()).put("records",historyRows(false,100,0)).put("reviews",historyRows(true,100,0));
    }
    /** TV pagination reads the complete retained table, independent of the mobile snapshot limit. */
    synchronized JSONObject historyPage(boolean review,int page,int pageSize) throws JSONException {
        if(pageSize<1 || pageSize>100)throw new IllegalArgumentException("每页数量须为 1–100");
        JSONObject j=statistics(stateJson());int count=j.getInt(review?"reviewTotal":"total");
        int pages=Math.max(1,(int)Math.ceil(count/(double)pageSize)),selected=Math.max(0,Math.min(page,pages-1));
        return j.put("page",selected).put("pageCount",pages).put("itemCount",count).put("pageSize",pageSize).put("items",historyRows(review,pageSize,selected*pageSize));
    }
    private JSONArray historyRows(boolean review,int limit,int offset) throws JSONException {
        JSONArray rows=new JSONArray();String[] args={String.valueOf(limit),String.valueOf(offset)};
        if(review)try(Cursor c=records.getReadableDatabase().rawQuery("SELECT question_key,prompt,subject,grade,wrong_count,lapse_count,due_at,mastered FROM reviews ORDER BY mastered ASC,due_at ASC,question_key ASC LIMIT ? OFFSET ?",args)) {
            while(c.moveToNext())rows.put(new JSONObject().put("questionKey",c.getString(0)).put("prompt",c.getString(1)).put("subject",c.getString(2)).put("grade",c.getInt(3)).put("wrongCount",c.getInt(4)).put("stage",c.getInt(5)).put("nextReviewAt",c.getLong(6)).put("mastered",c.getInt(7)!=0));
        }else try(Cursor c=records.getReadableDatabase().rawQuery("SELECT id,at,subject,grade,prompt,chosen,answer,correct FROM answers ORDER BY id DESC LIMIT ? OFFSET ?",args)) {
            while(c.moveToNext())rows.put(new JSONObject().put("recordId",c.getLong(0)).put("at",c.getLong(1)).put("subject",c.getString(2)).put("grade",c.getInt(3)).put("prompt",c.getString(4)).put("chosen",c.getString(5)).put("answer",c.getString(6)).put("correct",c.getInt(7)==1));
        }
        return rows;
    }
    synchronized boolean save() {
        try {
            SharedPreferences.Editor editor=prefs.edit().putString("rules",ruleJson(rules()).toString()).putString("day",engine.state.day).putLong("used",engine.state.usedMillis).putLong("since",engine.state.sinceQuestionMillis).putInt("pending",engine.state.pending).putBoolean("currentHadWrong",engine.state.currentHadWrong).putBoolean("bonusGranted",engine.state.bonusGranted).putLong("celebrationMillis",engine.state.celebrationMillis).putString("lastCorrectKey",lastCorrectKey).putString("presentedDay",presentedDay).putString("presented",new JSONArray(presentedIds).toString());
            if(lastSubject==null)editor.remove("lastSubject");else editor.putString("lastSubject",lastSubject);
            if(current==null) editor.remove("question");else editor.putString("question",questionJson(current).toString());
            editor.putLong("intervalAnchorMillis",engine.state.intervalAnchorMillis).putLong("nextQuestionAtMillis",engine.state.nextQuestionAtMillis);
            boolean saved=editor.commit();if(saved){lastSave=SystemClock.elapsedRealtime();storageError="";}else storageError=STORAGE_ERROR;return saved;
        } catch(JSONException | RuntimeException e) {storageError=STORAGE_ERROR;return false;}
    }
    static final class Records extends SQLiteOpenHelper {
        Records(Context c) { this(c,"answers.db"); }
        Records(Context c,String name) { super(c,name,null,2); }
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE answers(id INTEGER PRIMARY KEY AUTOINCREMENT,at INTEGER NOT NULL,subject TEXT NOT NULL,grade INTEGER NOT NULL,prompt TEXT NOT NULL,chosen TEXT NOT NULL,answer TEXT NOT NULL,correct INTEGER NOT NULL)");createReviews(db);
        }
        private static void createReviews(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE reviews(question_key TEXT PRIMARY KEY,question TEXT NOT NULL,prompt TEXT NOT NULL,subject TEXT NOT NULL,grade INTEGER NOT NULL,wrong_count INTEGER NOT NULL,lapse_count INTEGER NOT NULL,due_at INTEGER NOT NULL,mastered INTEGER NOT NULL,last_wrong_at INTEGER NOT NULL,mastered_at INTEGER NOT NULL)");
            db.execSQL("CREATE INDEX reviews_due ON reviews(mastered,grade,due_at)");
        }
        public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion) {
            if(oldVersion<2) {
                createReviews(db);QuestionBank bank=new QuestionBank(new java.security.SecureRandom());
                try(Cursor c=db.rawQuery("SELECT subject,grade,prompt,answer,COUNT(*),MAX(at) FROM answers WHERE correct=0 GROUP BY subject,grade,prompt,answer",null)) {
                    while(c.moveToNext()) {
                        Question q=bank.restore(c.getString(0),c.getInt(1),c.getString(2),c.getString(3));if(q==null)continue;
                        ContentValues v=new ContentValues();v.put("question_key",q.key());v.put("subject",q.subject);v.put("grade",q.grade);v.put("prompt",q.prompt);
                        try{v.put("question",questionJson(q).toString());}catch(JSONException e){throw new IllegalStateException(e);}
                        v.put("wrong_count",c.getInt(4));v.put("lapse_count",1);v.put("due_at",c.getLong(5)+ReviewSchedule.delayMillis(1));v.put("mastered",0);v.put("last_wrong_at",c.getLong(5));v.put("mastered_at",0);
                        db.insertOrThrow("reviews",null,v);
                    }
                }
            }
        }
    }
}
