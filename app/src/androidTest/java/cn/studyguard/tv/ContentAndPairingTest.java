package cn.studyguard.tv;
import android.content.*;
import android.database.sqlite.SQLiteDatabase;
import android.test.InstrumentationTestCase;
import cn.studyguard.core.*;
import org.json.*;
import java.net.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;

/** Only the dedicated emulator: HTTP integration, optional-field migration and review scoping. */
public final class ContentAndPairingTest extends InstrumentationTestCase {
    private Store store;private Context context;
    @Override protected void setUp() throws Exception {
        super.setUp();context=getInstrumentation().getTargetContext();store=Store.get(context);
        store.engine.state.pending=0;store.engine.state.celebrationMillis=0;store.engine.state.usedMillis=0;store.engine.state.currentHadWrong=false;store.updateRules(Rules.defaults());
        getInstrumentation().runOnMainSync(()->GuardService.start(context));
        long until=android.os.SystemClock.elapsedRealtime()+5000;while(ParentServer.active()==null && android.os.SystemClock.elapsedRealtime()<until)Thread.sleep(40);
        assertNotNull(ParentServer.active());
    }
    @Override protected void tearDown() throws Exception {store.updateRules(Rules.defaults());super.tearDown();}
    public void testOldRulesDefaultAndNewFieldsRoundTripStrictly() throws Exception {
        JSONObject old=Store.ruleJson(Rules.defaults());old.remove("semester");old.remove("difficulty");assertEquals(0,Store.parseRules(old).semester);assertEquals(2,Store.parseRules(old).difficulty);
        Rules r=new Rules(false,17,110,5,Rules.SUBJECTS,"一起动脑",2,3);JSONObject json=Store.ruleJson(r);Rules restored=Store.parseRules(json);assertEquals(2,restored.semester);assertEquals(3,restored.difficulty);assertEquals(Rules.SUBJECTS.size(),restored.subjects.size());
        for(Object invalid:new Object[]{1.5,"1",-1,3}){json.put("semester",invalid);try{Store.parseRules(json);fail("invalid semester");}catch(IllegalArgumentException expected){}}
        json=Store.ruleJson(r);for(Object invalid:new Object[]{1.5,"2",0,4}){json.put("difficulty",invalid);try{Store.parseRules(json);fail("invalid difficulty");}catch(IllegalArgumentException expected){}}
    }
    public void testQrHttpPairRefreshMutualExclusionAndRevocation() throws Exception {
        ParentServer server=ParentServer.active();PairingGate.Offer old=server.offer(),offer=server.offer();
        assertEquals(400,request("/api/pair","POST",new JSONObject().put("qrToken",old.qrToken),null).status);
        Reply ok=request("/api/pair","POST",new JSONObject().put("qrToken",offer.qrToken),null);assertEquals(200,ok.status);String ticket=ok.json.getString("ticket");assertFalse(ok.json.has("token"));assertEquals(401,request("/api/state","GET",null,ticket).status);
        context.getSharedPreferences("parent",0).edit().clear().commit();new ParentAuth(context).setup("482951","482951");
        String token=request("/api/auth/login","POST",new JSONObject().put("pin","482951"),null).json.getString("token");
        assertEquals(400,request("/api/pair","POST",new JSONObject().put("code",offer.code),null).status);
        assertFalse(server.offerValid(offer));assertEquals(200,request("/api/state","GET",null,token).status);
        JSONObject rules=Store.ruleJson(new Rules(false,19,99,5,Arrays.asList("poetry","riddle","science","english"),"休息一下",2,3));
        assertEquals(200,request("/api/rules","PUT",rules,token).status);assertEquals(2,store.rules().semester);assertEquals(3,store.rules().difficulty);assertEquals(4,store.rules().subjects.size());
        server.revoke();assertEquals(401,request("/api/state","GET",null,token).status);
        offer=server.offer();assertEquals(200,request("/api/pair","POST",new JSONObject().put("code",offer.code),null).status);assertEquals(400,request("/api/pair","POST",new JSONObject().put("qrToken",offer.qrToken),null).status);
    }
    public void testReviewMetadataSurvivesJsonAndHonorsTermAndDifficulty() throws Exception {
        SQLiteDatabase db=new Store.Records(context).getWritableDatabase();String key="v05-scoped-review-test";
        JSONObject q=new JSONObject().put("subject","poetry").put("grade",3).put("prompt","专用复习范围测试").put("choices",new JSONArray(Arrays.asList("甲","乙","丙","丁"))).put("correctIndex",0).put("explanation","测试").put("itemId","fixture").put("semester",2).put("difficulty",3);
        android.content.ContentValues v=new android.content.ContentValues();v.put("question_key",key);v.put("question",q.toString());v.put("prompt","专用复习范围测试");v.put("subject","poetry");v.put("grade",3);v.put("wrong_count",1);v.put("lapse_count",1);v.put("due_at",0);v.put("mastered",0);v.put("last_wrong_at",0);v.put("mastered_at",0);db.insertWithOnConflict("reviews",null,v,SQLiteDatabase.CONFLICT_REPLACE);
        Method due=Store.class.getDeclaredMethod("dueQuestion");due.setAccessible(true);
        try {
            store.updateRules(new Rules(false,120,480,3,Arrays.asList("poetry"),"提示",1,2));Question result=(Question)due.invoke(store);assertTrue(result==null || !result.itemId.equals("fixture"));
            store.updateRules(new Rules(false,120,480,3,Arrays.asList("poetry"),"提示",2,3));result=(Question)due.invoke(store);assertNotNull(result);assertEquals("fixture",result.itemId);assertEquals(2,result.semester);assertEquals(3,result.difficulty);
            q.remove("semester");q.remove("difficulty");q.remove("itemId");v.clear();v.put("question",q.toString());db.update("reviews",v,"question_key=?",new String[]{key});
            store.updateRules(new Rules(false,120,480,3,Arrays.asList("poetry"),"提示",1,1));result=(Question)due.invoke(store);assertNotNull(result);assertEquals(0,result.semester);assertEquals(1,result.difficulty);
        }finally{db.delete("reviews","question_key=?",new String[]{key});db.close();}
    }
    public void testFirstSetupRequiresTvTicketAndQrNeverAuthorizesManagement() throws Exception {
        context.getSharedPreferences("parent",0).edit().clear().commit();context.getSharedPreferences("parent_browsers",0).edit().clear().commit();
        JSONObject input=new JSONObject().put("pin","482951").put("confirm","482951").put("remember",true).put("label","HTTP test").put("phrase","一家人一起散步");
        assertFalse(request("/api/auth/status","GET",null,null).json.getBoolean("configured"));
        assertEquals(400,request("/api/auth/setup","POST",input,null).status);
        PairingGate.Offer offer=ParentServer.active().offer();Reply connection=request("/api/pair","POST",new JSONObject().put("qrToken",offer.qrToken),null);
        assertEquals(200,connection.status);assertFalse(connection.json.has("token"));String ticket=connection.json.getString("ticket");
        assertEquals(401,request("/api/rules","PATCH",new JSONObject().put("grade",3),ticket).status);
        input.put("ticket",ticket);Reply setup=request("/api/auth/setup","POST",input,null);assertEquals(200,setup.status);String token=setup.json.getString("token");
        assertEquals(200,request("/api/state","GET",null,token).status);assertEquals(400,request("/api/auth/setup","POST",input,null).status);
        assertTrue(request("/api/auth/status","GET",null,null).json.getBoolean("phraseConfigured"));
        assertEquals(401,request("/api/auth/phrase","POST",new JSONObject().put("phrase","未经授权改暗语"),null).status);
        Reply status=request("/api/auth/phrase","GET",null,token);assertTrue(status.json.getBoolean("configured"));assertFalse(status.json.has("phrase"));
        assertEquals(200,request("/api/auth/phrase","POST",new JSONObject().put("phrase","周末一起去公园"),token).status);
        JSONObject phraseRecovery=new JSONObject().put("phrase","周末一起去公园").put("pin","482951").put("confirm","482951");
        assertEquals(400,request("/api/auth/recover","POST",phraseRecovery,null).status);
        Reply recoveryPair=request("/api/pair","POST",new JSONObject().put("qrToken",ParentServer.active().offer().qrToken),null);
        phraseRecovery.put("ticket",recoveryPair.json.getString("ticket"));Reply phraseRecovered=request("/api/auth/recover","POST",phraseRecovery,null);assertEquals(200,phraseRecovered.status);
        assertEquals(401,request("/api/state","GET",null,token).status);token=phraseRecovered.json.getString("token");

        assertEquals(400,request("/api/auth/login","POST",new JSONObject().put("pin","000000"),null).status);
        Reply second=request("/api/auth/login","POST",new JSONObject().put("pin","482951"),null);assertEquals(200,second.status);
        JSONArray grants=request("/api/browsers","GET",null,token).json.getJSONArray("items");String otherId="";for(int i=0;i<grants.length();i++)if(!grants.getJSONObject(i).getBoolean("current"))otherId=grants.getJSONObject(i).getString("id");
        assertFalse(otherId.isEmpty());assertEquals(200,request("/api/browsers/revoke","POST",new JSONObject().put("id",otherId),token).status);
        assertEquals(401,request("/api/state","GET",null,second.json.getString("token")).status);
        assertEquals(401,request("/api/auth/recovery-code","GET",null,null).status);
        assertEquals(404,request("/api/auth/recovery-code","GET",null,token).status);
        JSONObject recover=new JSONObject().put("recoveryCode","12345678").put("pin","765432").put("confirm","765432");
        assertEquals(400,request("/api/auth/recover","POST",recover,null).status);
        String rules=Store.ruleJson(store.rules()).toString();int records=store.summary().getInt("total");
        offer=ParentServer.active().offer();recover.put("ticket",request("/api/pair","POST",new JSONObject().put("code",offer.code),null).json.getString("ticket"));
        assertEquals(400,request("/api/auth/recover","POST",recover,null).status);
        recover.remove("recoveryCode");recover.put("phrase","周末一起去公园");
        offer=ParentServer.active().offer();recover.put("ticket",request("/api/pair","POST",new JSONObject().put("code",offer.code),null).json.getString("ticket"));
        Reply recovered=request("/api/auth/recover","POST",recover,null);assertEquals(200,recovered.status);
        assertEquals(401,request("/api/state","GET",null,token).status);assertEquals(200,request("/api/state","GET",null,recovered.json.getString("token")).status);
        assertEquals(rules,Store.ruleJson(store.rules()).toString());assertEquals(records,store.summary().getInt("total"));
        assertEquals(400,request("/api/auth/login","POST",new JSONObject().put("pin","482951"),null).status);
    }
    private static final class Reply {int status;JSONObject json;Reply(int status,JSONObject json){this.status=status;this.json=json;}}
    private Reply request(String path,String method,JSONObject body,String token) throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL("http://127.0.0.1:"+ParentServer.PORT+path).openConnection();c.setRequestMethod(method);c.setConnectTimeout(3000);c.setReadTimeout(5000);
        if(token!=null)c.setRequestProperty("Authorization","Bearer "+token);if(body!=null){c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");try(OutputStream out=c.getOutputStream()){out.write(body.toString().getBytes("UTF-8"));}}
        int status=c.getResponseCode();InputStream in=status>=400?c.getErrorStream():c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream();if(in!=null){byte[] bytes=new byte[1024];int n;while((n=in.read(bytes))!=-1)out.write(bytes,0,n);in.close();}c.disconnect();return new Reply(status,new JSONObject(out.toString("UTF-8")));
    }
}
