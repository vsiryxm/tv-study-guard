package cn.studyguard.tv;
import android.content.*;
import android.test.InstrumentationTestCase;
import org.json.*;

public final class BrowserAuthTest extends InstrumentationTestCase {
    private Context c;private BrowserAuth auth;
    @Override protected void setUp() throws Exception {super.setUp();c=getInstrumentation().getTargetContext();c.getSharedPreferences("parent",0).edit().clear().commit();c.getSharedPreferences("parent_recovery",0).edit().clear().commit();c.getSharedPreferences("parent_browsers",0).edit().clear().commit();auth=new BrowserAuth(c);}
    public void testExistingPinAndIndependentPersistentBrowserGrants() throws Exception {new ParentAuth(c).setup("482951","482951");JSONObject a=auth.login("482951",true,"手机"),b=auth.login("482951",false,"电脑");String token=a.getString("token");assertTrue(new BrowserAuth(c).authorized(token));assertEquals(30L*24*60*60*1000,a.getLong("expires")-auth.list(token).getJSONObject(0).getLong("issued"));assertTrue(auth.authorized(b.getString("token")));auth.logout(token);assertFalse(auth.authorized(token));assertTrue(auth.authorized(b.getString("token")));assertFalse(c.getSharedPreferences("parent_browsers",0).getString("grants","").contains(b.getString("token")));}
    public void testPinChangeRevokesOtherBrowsersAndRejectsOldPin() throws Exception {String first=auth.setup("482951","482951",true,"first").getString("token"),second=auth.login("482951",true,"second").getString("token");String next=auth.change("482951","765432","765432",false,"new").getString("token");assertFalse(auth.authorized(first));assertFalse(auth.authorized(second));assertTrue(auth.authorized(next));try{auth.login("482951",true,"old");fail();}catch(IllegalArgumentException expected){}assertTrue(auth.authorized(auth.login("765432",false,"ok").getString("token")));}
    public void testExpiryRevokeAndCredentialResetInvalidateGrants() throws Exception {String token=auth.setup("482951","482951",false,"test").getString("token");JSONObject grant=new JSONArray(c.getSharedPreferences("parent_browsers",0).getString("grants","[]")).getJSONObject(0);assertEquals(7200000L,grant.getLong("expires")-grant.getLong("issued"));grant.put("expires",System.currentTimeMillis()-1);c.getSharedPreferences("parent_browsers",0).edit().putString("grants",new JSONArray().put(grant).toString()).commit();assertFalse(auth.authorized(token));token=auth.login("482951",true,"test").getString("token");auth.revoke(auth.list(token).getJSONObject(0).getString("id"));assertFalse(auth.authorized(token));token=auth.login("482951",true,"test").getString("token");c.getSharedPreferences("parent",0).edit().clear().commit();assertFalse(auth.authorized(token));}
    public void testRecoveryPreservesRulesAndRejectsWrongPhraseAndInvalidPin() throws Exception {String before=auth.setupWithPhrase("482951","482951","一家人一起散步",true,"before").getString("token");String rules=c.getSharedPreferences("guard",0).getString("rules","");try{auth.recoverWithPhrase("invalid","123456","123456",false,"bad");fail();}catch(IllegalArgumentException expected){}assertTrue(auth.authorized(before));try{auth.recoverWithPhrase("一家人一起散步","12","12",false,"bad");fail();}catch(IllegalArgumentException expected){}assertTrue(auth.authorized(before));String recovered=auth.recoverWithPhrase("一家人一起散步","123456","123456",true,"after").getString("token");assertFalse(auth.authorized(before));assertTrue(auth.authorized(recovered));assertEquals(rules,c.getSharedPreferences("guard",0).getString("rules",""));}
    public void testLegacyPinMigratesOnlyAfterSuccessAndSurvivesRestart() throws Exception {
        auth.setup("482951","482951",true,"test");
        android.content.SharedPreferences p=c.getSharedPreferences("parent",0);
        String salt=p.getString("salt",""),hash=p.getString("hash","");
        p.edit().remove("quickPinHash").remove("quickPinFor").commit();
        try{auth.login("111111",false,"bad");fail();}catch(IllegalArgumentException expected){}
        assertFalse(p.contains("quickPinHash"));
        String token=auth.login("482951",true,"migrate").getString("token");
        assertTrue(p.contains("quickPinHash"));assertEquals(hash,p.getString("quickPinFor",""));
        assertEquals(salt,p.getString("salt",""));assertEquals(hash,p.getString("hash",""));
        assertTrue(new BrowserAuth(c).authorized(token));
        // An unusable legacy value with a matching marker proves the second login uses quick verification.
        p.edit().putString("hash","legacy-sentinel").putString("quickPinFor","legacy-sentinel").commit();
        assertTrue(new BrowserAuth(c).login("482951",false,"restart").has("token"));
        try{new BrowserAuth(c).login("111111",false,"bad");fail();}catch(IllegalArgumentException expected){}
        p.edit().putString("hash",hash).putString("quickPinFor",hash).commit();
    }
    public void testOlderVersionPinChangeInvalidatesQuickVerifier() throws Exception {
        auth.setup("482951","482951",false,"old");
        android.content.SharedPreferences p=c.getSharedPreferences("parent",0);
        byte[] salt=new byte[16];java.util.Arrays.fill(salt,(byte)7);
        javax.crypto.spec.PBEKeySpec spec=new javax.crypto.spec.PBEKeySpec("765432".toCharArray(),salt,60000,256);
        String next=android.util.Base64.encodeToString(javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).getEncoded(),android.util.Base64.NO_WRAP);spec.clearPassword();
        // Older versions change only salt/hash and leave unknown keys intact.
        p.edit().putString("salt",android.util.Base64.encodeToString(salt,android.util.Base64.NO_WRAP)).putString("hash",next).commit();
        try{auth.login("482951",false,"stale");fail();}catch(IllegalArgumentException expected){}
        assertTrue(auth.login("765432",false,"new").has("token"));assertEquals(next,p.getString("quickPinFor",""));
        assertFalse(p.getString("quickPinHash","").equals("765432"));
    }
    public void testPlaintextPhrasePersistsEditsAndRecoversWithoutLosingFamilyData() throws Exception {
        String first=auth.setupWithPhrase("482951","482951","一家人一起散步",true,"first").getString("token");
        String second=auth.login("482951",true,"second").getString("token");
        String rules=c.getSharedPreferences("guard",0).getString("rules","");
        assertEquals("一家人一起散步",c.getSharedPreferences("parent_recovery",0).getString("phrase",""));
        BrowserAuth restored=new BrowserAuth(c);assertTrue(restored.phraseConfigured());
        restored.changePhrase("周末一起去公园");
        try{restored.recoverWithPhrase("一家人一起散步","765432","765432",false,"bad");fail();}catch(IllegalArgumentException expected){}
        assertTrue(restored.authorized(first));
        String next=restored.recoverWithPhrase("周末一起去公园","765432","765432",false,"recovered").getString("token");
        assertFalse(restored.authorized(first));assertFalse(restored.authorized(second));assertTrue(restored.authorized(next));
        assertEquals(rules,c.getSharedPreferences("guard",0).getString("rules",""));
        assertEquals("周末一起去公园",c.getSharedPreferences("parent_recovery",0).getString("phrase",""));
        assertTrue(restored.login("765432",false,"new pin").has("token"));
    }
    public void testPhraseValidationAndPersistentFiveAttemptThrottle() throws Exception {
        auth.setupWithPhrase("482951","482951","一家人一起散步",false,"test");
        for(String invalid:new String[]{"短暗语","12345","123456\nX"})try{auth.changePhrase(invalid);fail();}catch(IllegalArgumentException expected){}
        assertEquals("一家人一起散步",c.getSharedPreferences("parent_recovery",0).getString("phrase",""));
        for(int i=0;i<5;i++)try{auth.recoverWithPhrase("incorrect","765432","765432",false,"bad");fail();}catch(IllegalArgumentException expected){}
        try{new BrowserAuth(c).recoverWithPhrase("一家人一起散步","765432","765432",false,"locked");fail();}catch(IllegalArgumentException expected){assertTrue(expected.getMessage().contains("一分钟"));}
        assertTrue(auth.login("482951",false,"unchanged").has("token"));
        c.getSharedPreferences("parent_recovery",0).edit().putLong("lockedUntil",0).commit();
        assertTrue(auth.recoverWithPhrase("一家人一起散步","765432","765432",false,"ok").has("token"));
    }
    public void testExistingInstallCanAddSinglePhraseWithoutChangingPin() throws Exception {
        String before=auth.setup("482951","482951",false,"existing").getString("token");assertFalse(auth.phraseConfigured());
        try{auth.recoverWithPhrase("12345678","765432","765432",false,"removed fallback");fail();}catch(IllegalArgumentException expected){assertTrue(expected.getMessage().contains("尚未设置"));}
        assertTrue(auth.authorized(before));assertTrue(auth.login("482951",false,"unchanged pin").has("token"));
        auth.changePhrase("一家人一起散步");assertTrue(new BrowserAuth(c).phraseConfigured());
        assertTrue(auth.recoverWithPhrase("一家人一起散步","765432","765432",false,"phrase recovery").has("token"));assertFalse(auth.authorized(before));
    }
}
