package cn.studyguard.core;

import java.security.*;
import java.util.Locale;

/** A numeric fallback and a high-entropy QR secret share one single-use, ten-minute offer. */
public final class PairingGate {
    public static final long LIFETIME_MILLIS=600000;
    public static final class Offer {
        public final String code,qrToken;
        public final long expiresAt;
        private Offer(String code,String qr,long expiresAt) {this.code=code;qrToken=qr;this.expiresAt=expiresAt;}
    }
    private final SecureRandom random=new SecureRandom();
    private Offer current;
    private int failures;
    private long lockedUntil;
    public synchronized Offer issue(long now) {
        current=new Offer(String.format(Locale.US,"%04d",random.nextInt(10000)),secret(),now+LIFETIME_MILLIS);failures=0;lockedUntil=0;return current;
    }
    public synchronized void revoke() {current=null;}
    public synchronized boolean valid(Offer offer,long now) {return current==offer && current!=null && now<current.expiresAt;}
    public synchronized boolean redeem(String code,String qr,long now) {
        if(current==null || now>=current.expiresAt || now<lockedUntil)return false;
        // An explicitly supplied QR secret must be verified as QR; never fall back to an empty code.
        boolean ok=qr!=null?!qr.isEmpty() && equal(current.qrToken,qr):code!=null && equal(current.code,code);
        if(!ok){if(++failures>=5){lockedUntil=now+60000;failures=0;}return false;}
        current=null;return true;
    }
    public static String secret() {
        byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format(Locale.US,"%02x",b&255));return out.toString();
    }
    private static boolean equal(String a,String b) {return MessageDigest.isEqual(a.getBytes(QuestionBank.UTF8),b.getBytes(QuestionBank.UTF8));}
}
