package cn.studyguard.tv;

import android.content.*;
import cn.studyguard.core.PairingGate;
import org.json.*;
import java.security.*;
import java.util.*;

/** Independent browser grants; only token hashes are persisted, bound to the current PIN. */
final class BrowserAuth {
    private final Context context;
    private final SharedPreferences prefs;
    private final ParentAuth parent;
    private static final Object LOCK=new Object();
    BrowserAuth(Context context){this.context=context.getApplicationContext();prefs=context.getSharedPreferences("parent_browsers",0);parent=new ParentAuth(context);}
    boolean configured(){return parent.configured();}
    private static String digest(String text){try{byte[] h=MessageDigest.getInstance("SHA-256").digest(text.getBytes(Ui.UTF8));StringBuilder b=new StringBuilder();for(byte v:h)b.append(String.format(Locale.US,"%02x",v&255));return b.toString();}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    private String credential(){SharedPreferences p=context.getSharedPreferences("parent",0);return digest(p.getString("salt","")+":"+p.getString("hash",""));}
    private JSONArray read() throws JSONException {
        JSONArray valid=new JSONArray(),all=new JSONArray(prefs.getString("grants","[]"));long now=System.currentTimeMillis();String credential=credential();
        for(int i=0;i<all.length();i++){JSONObject g=all.getJSONObject(i);if(parent.configured() && now>=g.getLong("issued") && now<g.getLong("expires") && credential.equals(g.optString("credential")))valid.put(g);}return valid;
    }
    private void save(JSONArray grants){if(!prefs.edit().putString("grants",grants.toString()).commit())throw new IllegalStateException("无法保存浏览器授权，请重试");}
    private JSONObject issue(boolean remember,String label) throws JSONException {
        String token=PairingGate.secret();JSONArray grants=read(),next=new JSONArray();int start=Math.max(0,grants.length()-15);for(int i=start;i<grants.length();i++)next.put(grants.get(i));
        long now=System.currentTimeMillis(),expires=now+(remember?30L*24*60*60*1000:2L*60*60*1000);
        next.put(new JSONObject().put("id",PairingGate.secret().substring(0,16)).put("hash",digest(token)).put("label",label==null||label.trim().isEmpty()?"浏览器":label.trim().substring(0,Math.min(label.trim().length(),60))).put("issued",now).put("expires",expires).put("remember",remember).put("credential",credential()));save(next);
        return new JSONObject().put("token",token).put("expires",expires).put("remember",remember);
    }
    boolean authorized(String token){synchronized(LOCK){if(token==null||!token.matches("[0-9a-f]{64}"))return false;try{JSONArray grants=read();String h=digest(token);for(int i=0;i<grants.length();i++)if(h.equals(grants.getJSONObject(i).getString("hash")))return true;}catch(JSONException ignored){}return false;}}
    JSONObject login(String pin,boolean remember,String label) throws JSONException {synchronized(LOCK){synchronized(ParentAuth.class){if(pin==null || !pin.matches("[0-9]{4,12}") || !parent.verify(pin))throw new IllegalArgumentException("家长口令不正确");return issue(remember,label);}}}
    JSONObject setup(String pin,String confirm,boolean remember,String label) throws JSONException {synchronized(LOCK){synchronized(ParentAuth.class){parent.setup(pin,confirm);return issue(remember,label);}}}
    JSONObject change(String current,String pin,String confirm,boolean remember,String label) throws JSONException {synchronized(LOCK){synchronized(ParentAuth.class){ParentAuth.validatePin(pin,confirm);if(current==null||!parent.verify(current))throw new IllegalArgumentException("原家长口令不正确");parent.changePin(pin,confirm);save(new JSONArray());return issue(remember,label);}}}
    boolean phraseConfigured(){synchronized(LOCK){return parent.phraseConfigured();}}
    void changePhrase(String phrase){synchronized(LOCK){synchronized(ParentAuth.class){parent.savePhrase(phrase);}}}
    JSONObject setupWithPhrase(String pin,String confirm,String phrase,boolean remember,String label) throws JSONException {synchronized(LOCK){synchronized(ParentAuth.class){
        ParentAuth.validatePhrase(phrase);parent.setup(pin,confirm);parent.savePhrase(phrase);return issue(remember,label);
    }}}
    JSONObject recoverWithPhrase(String phrase,String pin,String confirm,boolean remember,String label) throws JSONException {synchronized(LOCK){synchronized(ParentAuth.class){
        ParentAuth.validatePin(pin,confirm);parent.verifyPhrase(phrase);parent.replaceRecoveredPin(pin,confirm);save(new JSONArray());return issue(remember,label);
    }}}
    JSONArray list(String token) throws JSONException {synchronized(LOCK){JSONArray out=new JSONArray(),grants=read();String h=digest(token);for(int i=0;i<grants.length();i++){JSONObject g=grants.getJSONObject(i);out.put(new JSONObject().put("id",g.getString("id")).put("label",g.getString("label")).put("issued",g.getLong("issued")).put("expires",g.getLong("expires")).put("remember",g.getBoolean("remember")).put("current",h.equals(g.getString("hash"))));}return out;}}
    void logout(String token) throws JSONException {remove("hash",digest(token));}
    void revoke(String id) throws JSONException {remove("id",id);}
    private void remove(String key,String value) throws JSONException {synchronized(LOCK){JSONArray grants=read(),next=new JSONArray();for(int i=0;i<grants.length();i++){JSONObject g=grants.getJSONObject(i);if(!value.equals(g.getString(key)))next.put(g);}save(next);}}
    void revokeAll(){synchronized(LOCK){save(new JSONArray());}}
}
