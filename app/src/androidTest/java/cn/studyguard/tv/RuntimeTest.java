package cn.studyguard.tv;

import android.content.*;
import android.test.InstrumentationTestCase;
import cn.studyguard.core.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public final class RuntimeTest extends InstrumentationTestCase {
    private Context context;
    private Store store;
    @Override protected void setUp() throws Exception {
        super.setUp();context=getInstrumentation().getTargetContext();store=Store.get(context);
        store.engine.state.intervalAnchorMillis=0;store.engine.state.nextQuestionAtMillis=0;store.engine.state.pending=0;store.engine.state.currentHadWrong=false;store.engine.state.celebrationMillis=0;store.engine.state.usedMillis=0;store.engine.state.sinceQuestionMillis=0;store.updateRules(Rules.defaults());
    }
    public void testParentPinHasNoDefaultAndRateLimits() {
        context.getSharedPreferences("parent",Context.MODE_PRIVATE).edit().clear().commit();
        ParentAuth auth=new ParentAuth(context);assertFalse(auth.configured());
        try {auth.setup("123","123");fail();}catch(IllegalArgumentException expected){}
        auth.setup("482951","482951");assertTrue(auth.configured());
        for(int i=0;i<5;i++)assertFalse(auth.verify("000000"));
        try {auth.verify("482951");fail();}catch(IllegalArgumentException expected){}
        context.getSharedPreferences("parent",Context.MODE_PRIVATE).edit().putLong("lockedUntil",0).commit();assertTrue(auth.verify("482951"));
    }
    public void testFourDigitPinVerification() {
        context.getSharedPreferences("parent",Context.MODE_PRIVATE).edit().clear().commit();
        ParentAuth auth=new ParentAuth(context);auth.setup("4829","4829");

        auth.changePin("5829","5829");assertFalse(auth.verify("4829"));assertTrue(auth.verify("5829"));
        auth.changePin("482951","482951");assertTrue(auth.verify("482951"));
    }
    public void testStorePersistsAndRecordsActualAnswer() throws Exception {
        store.updateRules(new Rules(true,1,120,2,Arrays.asList("math")));
        store.requestQuestion();Question q=store.question();int before=store.snapshot().getInt("total");int pending=store.pending();
        store.answer(q,(q.correctIndex+1)%4);assertEquals(pending+1,store.pending());
        q=store.question();store.answer(q,q.correctIndex);assertEquals(pending,store.pending());
        assertEquals(before+2,store.snapshot().getInt("total"));
        android.content.SharedPreferences prefs=context.getSharedPreferences("guard",Context.MODE_PRIVATE);
        assertEquals(store.pending(),prefs.getInt("pending",-1));assertTrue(prefs.getString("rules","").contains("\"enabled\":true"));
        store.updateRules(Rules.defaults());
    }
    public void testStaleQuestionDoesNotAnswerNewRules() {
        store.updateRules(new Rules(true,1,120,2,Arrays.asList("math")));store.requestQuestion();Question old=store.question();int before=store.pending();
        store.updateRules(new Rules(true,1,120,3,Arrays.asList("english")));
        String result=store.answer(old,old.correctIndex);assertTrue(result.contains("规则已更新"));assertEquals(before,store.pending());store.updateRules(Rules.defaults());
    }
    public void testLanPairingValidationAndSessionRevocation() throws Throwable {
        runTestOnUiThread(()->GuardService.start(context));
        long deadline=android.os.SystemClock.elapsedRealtime()+5000;while(ParentServer.active()==null && android.os.SystemClock.elapsedRealtime()<deadline)Thread.sleep(50);
        ParentServer server=ParentServer.active();assertNotNull(server);
        assertEquals(401,request("/api/state","GET",null,null,null).status);
        assertEquals(403,request("/api/pair","POST","{}",null,"http://evil.example").status);
        String code=server.pair();Reply pair=request("/api/pair","POST",new JSONObject().put("code",code).toString(),null,null);assertEquals(200,pair.status);
        String ticket=new JSONObject(pair.body).getString("ticket");assertEquals(401,request("/api/state","GET",null,ticket,null).status);
        context.getSharedPreferences("parent",0).edit().clear().commit();new ParentAuth(context).setup("482951","482951");
        Reply login=request("/api/auth/login","POST","{\"pin\":\"482951\",\"remember\":true}",null,null);assertEquals(200,login.status);String token=new JSONObject(login.body).getString("token");assertEquals(400,request("/api/pair","POST",new JSONObject().put("code",code).toString(),null,null).status);
        assertEquals(200,request("/api/state","GET",null,token,null).status);
        assertEquals(200,request("/api/history?review=0&page=0","GET",null,token,null).status);
        assertEquals(400,request("/api/history?review=2&page=0","GET",null,token,null).status);
        assertEquals(401,request("/api/history?review=0&page=0","GET",null,null,null).status);
        JSONObject rules=Store.ruleJson(Rules.defaults());rules.put("intervalMinutes",0);assertEquals(400,request("/api/rules","PUT",rules.toString(),token,null).status);
        rules.put("intervalMinutes",1.5);assertEquals(400,request("/api/rules","PUT",rules.toString(),token,null).status);
        rules=Store.ruleJson(new Rules(false,17,90,4,Arrays.asList("math","english")));
        assertEquals(200,request("/api/rules","PUT",rules.toString(),token,null).status);assertEquals(17,store.rules().intervalMinutes);assertEquals(4,store.rules().grade);
        assertEquals(200,request("/api/rules","PATCH","{\"grade\":5}",token,null).status);assertEquals(5,store.rules().grade);assertEquals(17,store.rules().intervalMinutes);
        assertEquals(400,request("/api/rules","PATCH","{\"subjects\":[]}",token,null).status);assertEquals(400,request("/api/rules","PATCH","{\"grade\":2,\"enabled\":false}",token,null).status);assertEquals(5,store.rules().grade);
        assertEquals(401,request("/api/rules","PATCH","{\"grade\":2}",null,null).status);
        assertEquals(200,request("/api/logout","POST","{}",token,null).status);assertEquals(401,request("/api/state","GET",null,token,null).status);
        store.updateRules(Rules.defaults());
    }
    public void testSnapshotExposesServerCountdownWithoutChangingTimer() throws Exception {
        store.updateRules(new Rules(true,20,120,2,Rules.SUBJECTS));
        store.engine.state.intervalAnchorMillis=System.currentTimeMillis();
        store.engine.state.nextQuestionAtMillis=System.currentTimeMillis()+45000;
        long due=store.engine.state.nextQuestionAtMillis;
        long remaining=store.snapshot().getLong("remainingMillis");
        assertTrue(remaining>44000 && remaining<=45000);
        assertEquals(due,store.engine.state.nextQuestionAtMillis);
        store.engine.state.intervalAnchorMillis=0;store.engine.state.nextQuestionAtMillis=0;store.engine.state.sinceQuestionMillis=12345;
        assertEquals(20*60000L-12345,store.snapshot().getLong("remainingMillis"));
        store.unlockNow();
    }
    public void testParentServerRetriesAfterPortIsReleased() throws Throwable {
        runTestOnUiThread(()->context.stopService(new Intent(context,GuardService.class)));
        long deadline=android.os.SystemClock.elapsedRealtime()+5000;
        while(ParentServer.active()!=null && android.os.SystemClock.elapsedRealtime()<deadline)Thread.sleep(50);
        assertNull(ParentServer.active());
        try(ServerSocket occupied=new ServerSocket()) {
            occupied.setReuseAddress(true);
            occupied.bind(new InetSocketAddress(ParentServer.PORT));
            runTestOnUiThread(()->GuardService.start(context));
            deadline=android.os.SystemClock.elapsedRealtime()+5000;
            while(ParentServer.startupError().isEmpty() && android.os.SystemClock.elapsedRealtime()<deadline)Thread.sleep(50);
            assertFalse(ParentServer.startupError().isEmpty());
            assertNull(ParentServer.active());
        }
        deadline=android.os.SystemClock.elapsedRealtime()+15000;
        while(ParentServer.active()==null && android.os.SystemClock.elapsedRealtime()<deadline)Thread.sleep(50);
        assertNotNull("The management server should retry its bind",ParentServer.active());
        assertTrue(ParentServer.startupError().isEmpty());
        assertEquals(200,request("/api/auth/status","GET",null,null,null).status);
    }
    public void testConnectionOfferRefreshDoesNotWaitForCredentialWork() throws Throwable {
        runTestOnUiThread(()->GuardService.start(context));
        long deadline=android.os.SystemClock.elapsedRealtime()+5000;
        while(ParentServer.active()==null && android.os.SystemClock.elapsedRealtime()<deadline)Thread.sleep(50);
        ParentServer server=ParentServer.active();assertNotNull(server);
        String pairBody=new JSONObject().put("qrToken",server.offer().qrToken).toString();
        String ticket=new JSONObject(request("/api/pair","POST",pairBody,null,null).body).getString("ticket");
        String setupBody=new JSONObject().put("ticket",ticket).toString();
        java.lang.reflect.Field field=BrowserAuth.class.getDeclaredField("LOCK");field.setAccessible(true);
        ExecutorService workers=Executors.newFixedThreadPool(2);
        try {
            Future<Reply> setup;
            synchronized(field.get(null)) {
                setup=workers.submit(()->request("/api/auth/setup","POST",setupBody,null,null));
                deadline=android.os.SystemClock.elapsedRealtime()+3000;
                while(!setupWaiting() && android.os.SystemClock.elapsedRealtime()<deadline)Thread.sleep(20);
                assertTrue("The setup request should be waiting for credential work",setupWaiting());
                assertNotNull(workers.submit(server::connectionOffer).get(1,TimeUnit.SECONDS));
            }
            assertEquals(400,setup.get(3,TimeUnit.SECONDS).status);
        } finally {workers.shutdownNow();}
    }
    private static boolean setupWaiting() {
        for(StackTraceElement[] stack:Thread.getAllStackTraces().values()) {
            for(StackTraceElement frame:stack) {
                if(BrowserAuth.class.getName().equals(frame.getClassName()) && "setupWithPhrase".equals(frame.getMethodName()))return true;
            }
        }
        return false;
    }
    private static final class Reply {int status;String body;Reply(int status,String body){this.status=status;this.body=body;}}
    private Reply request(String path,String method,String body,String token,String origin) throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL("http://127.0.0.1:"+ParentServer.PORT+path).openConnection();
        c.setRequestMethod(method);c.setConnectTimeout(3000);c.setReadTimeout(5000);
        if(token!=null)c.setRequestProperty("Authorization","Bearer "+token);if(origin!=null)c.setRequestProperty("Origin",origin);
        if(body!=null){c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");byte[] bytes=body.getBytes("UTF-8");c.setFixedLengthStreamingMode(bytes.length);try(OutputStream out=c.getOutputStream()){out.write(bytes);}}
        int status=c.getResponseCode();InputStream in=status>=400?c.getErrorStream():c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream();if(in!=null){byte[] b=new byte[1024];int n;while((n=in.read(b))!=-1)out.write(b,0,n);in.close();}c.disconnect();return new Reply(status,out.toString("UTF-8"));
    }
}
