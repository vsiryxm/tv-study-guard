package cn.studyguard.core;
import org.junit.Test;
import static org.junit.Assert.*;
public final class PairingGateTest {
    @Test public void numericAndQrCredentialsAreSingleUseAndMutuallyExclusive() {
        PairingGate gate=new PairingGate();PairingGate.Offer a=gate.issue(0);assertTrue(a.code.matches("[0-9]{4}"));assertTrue(a.qrToken.matches("[0-9a-f]{64}"));
        assertTrue(gate.redeem(null,a.qrToken,1));assertFalse(gate.valid(a,1));assertFalse(gate.redeem(a.code,null,2));
        PairingGate.Offer b=gate.issue(3);assertTrue(gate.redeem(b.code,null,4));assertFalse(gate.redeem(null,b.qrToken,5));
    }
    @Test public void expiryRefreshAndRevokeInvalidateSecrets() {
        PairingGate gate=new PairingGate();PairingGate.Offer a=gate.issue(10);assertTrue(gate.valid(a,600009));assertFalse(gate.redeem(a.code,null,600010));
        PairingGate.Offer b=gate.issue(600011);assertFalse(gate.valid(a,600011));assertFalse(gate.redeem(null,a.qrToken,600012));assertTrue(gate.valid(b,600012));gate.revoke();assertFalse(gate.redeem(b.code,null,600013));
    }
    @Test public void fiveBadAttemptsLockForOneMinuteAndEmptySecretDoesNotFallBack() {
        PairingGate gate=new PairingGate();PairingGate.Offer a=gate.issue(0);assertFalse(gate.redeem(a.code,"",1));
        for(int k=0;k<4;k++)assertFalse(gate.redeem("bad",null,1));assertFalse(gate.redeem(a.code,null,60000));assertTrue(gate.redeem(a.code,null,60001));
    }
}
