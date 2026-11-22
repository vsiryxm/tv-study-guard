package cn.studyguard.tv;
import android.content.*;
import android.util.Base64;
import java.security.*;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

final class ParentAuth {
    private static final int LEGACY_ROUNDS=60000, QUICK_ROUNDS=1000;
    private final SharedPreferences prefs,recovery;
    ParentAuth(Context c) {
        prefs=c.getSharedPreferences("parent",Context.MODE_PRIVATE);
        recovery=c.getSharedPreferences("parent_recovery",Context.MODE_PRIVATE);
    }
    boolean configured() {return prefs.contains("hash");}
    private static String hash(String pin,byte[] salt,int rounds) {
        try { PBEKeySpec spec=new PBEKeySpec(pin.toCharArray(),salt,rounds,256);try { return Base64.encodeToString(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).getEncoded(),Base64.NO_WRAP); } finally { spec.clearPassword(); } }
        catch(Exception e) { throw new IllegalStateException(e); }
    }
    synchronized void setup(String pin,String confirm) {
        if(configured()) throw new IllegalArgumentException("请先验证原家长口令");
        savePin(pin,confirm);
    }
    synchronized void changePin(String pin,String confirm) {
        savePin(pin,confirm);
    }
    static void validatePin(String pin,String confirm) {
        if(pin==null || !pin.matches("[0-9]{4,12}")) throw new IllegalArgumentException("家长口令须为 4–12 位数字");
        if(!pin.equals(confirm)) throw new IllegalArgumentException("两次家长口令 不一致");
    }
    private void savePin(String pin,String confirm) {
        validatePin(pin,confirm);
        byte[] salt=new byte[16];new SecureRandom().nextBytes(salt);
        String legacy=hash(pin,salt,LEGACY_ROUNDS);
        if(!prefs.edit().putString("salt",Base64.encodeToString(salt,Base64.NO_WRAP)).putString("hash",legacy).putString("quickPinHash",hash(pin,salt,QUICK_ROUNDS)).putString("quickPinFor",legacy).commit())throw new IllegalStateException("家长口令未能保存，请重试");
    }
    synchronized boolean verify(String pin) {
        if(!configured() || pin==null || pin.length()>12) return false;
        long now=System.currentTimeMillis();
        if(now<prefs.getLong("lockedUntil",0)) throw new IllegalArgumentException("尝试过多，请一分钟后再试");
        byte[] salt=Base64.decode(prefs.getString("salt",""),Base64.NO_WRAP);
        // A salted 1000-round verifier trades offline guessing cost for usable family-TV login.
        // Existing installations obtain the quick verifier after their first valid PIN.
        String legacy=prefs.getString("hash","");
        boolean quick=legacy.equals(prefs.getString("quickPinFor","")) && prefs.contains("quickPinHash");
        String expected=quick?prefs.getString("quickPinHash",""):legacy;
        boolean ok=MessageDigest.isEqual(hash(pin,salt,quick?QUICK_ROUNDS:LEGACY_ROUNDS).getBytes(Ui.UTF8),expected.getBytes(Ui.UTF8));
        int failures=ok?0:prefs.getInt("failures",0)+1;
        SharedPreferences.Editor edit=prefs.edit().putInt("failures",failures>=5?0:failures).putLong("lockedUntil",failures>=5?now+60000:0);
        if(ok && !quick)edit.putString("quickPinHash",hash(pin,salt,QUICK_ROUNDS)).putString("quickPinFor",legacy);
        edit.commit();
        return ok;
    }

    // Parent-chosen phrase is intentionally plaintext in private local preferences.
    // Kept separate from PIN preferences so resetting the PIN preserves the phrase.
    boolean phraseConfigured(){return !recovery.getString("phrase","").isEmpty();}
    static String validatePhrase(String phrase){
        if(phrase==null)throw new IllegalArgumentException("请输入家长暗语");
        String clean=phrase.trim();int count=clean.codePointCount(0,clean.length());
        if(count<6 || count>32)throw new IllegalArgumentException("家长暗语须为 6–32 个字或字符");
        for(int i=0;i<clean.length();i++)if(Character.isISOControl(clean.charAt(i)))throw new IllegalArgumentException("家长暗语不能包含换行或控制字符");
        return clean;
    }
    void savePhrase(String phrase){
        String clean=validatePhrase(phrase);
        if(!recovery.edit().putString("phrase",clean).putInt("failures",0).putLong("lockedUntil",0).commit())throw new IllegalStateException("家长暗语未能保存，请重试");
    }
    void verifyPhrase(String phrase){
        if(!phraseConfigured())throw new IllegalArgumentException("尚未设置家长暗语，暂时无法找回；请先用原口令登录并设置暗语");
        long now=System.currentTimeMillis();if(now<recovery.getLong("lockedUntil",0))throw new IllegalArgumentException("暗语尝试过多，请一分钟后再试");
        boolean ok=phrase!=null && phrase.length()<=128 && recovery.getString("phrase","").equals(phrase.trim());
        int failures=ok?0:recovery.getInt("failures",0)+1;
        if(!recovery.edit().putInt("failures",failures>=5?0:failures).putLong("lockedUntil",failures>=5?now+60000:0).commit())throw new IllegalStateException("无法保存验证状态，请重试");
        if(!ok)throw new IllegalArgumentException(failures>=5?"暗语尝试过多，请一分钟后再试":"家长暗语不正确");
    }
    void replaceRecoveredPin(String pin,String confirm){
        savePin(pin,confirm);prefs.edit().putInt("failures",0).putLong("lockedUntil",0).commit();
    }

}
