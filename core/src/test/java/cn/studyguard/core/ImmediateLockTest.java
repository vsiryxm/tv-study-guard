package cn.studyguard.core;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.Arrays;

public class ImmediateLockTest {
    private static final long START=1800000000000L,MIN=60000;
    private GuardEngine engine() {return new GuardEngine(new Rules(false,15,120,2,Arrays.asList("math")),new GuardEngine.State("20261103"));}
    @Test public void lockEnablesAndAnchorsWithoutClearingDailyUsage() {
        GuardEngine e=engine();e.state.usedMillis=12345;e.state.sinceQuestionMillis=30000;e.lockNow(START);
        assertTrue(e.rules().enabled);assertTrue(e.blocked());assertEquals(1,e.state.pending);assertEquals(12345,e.state.usedMillis);
        assertEquals(START,e.state.intervalAnchorMillis);assertEquals(START+15*MIN,e.state.nextQuestionAtMillis);assertEquals(0,e.state.sinceQuestionMillis);
    }
    @Test public void answerAndFireworksDoNotMoveTheAnchor() {
        GuardEngine e=engine();e.lockNow(START);e.answer(true);e.tick(5000,true,"20261103",START+5000);assertFalse(e.blocked());
        e.tick(MIN,true,"20261103",START+15*MIN-1);assertFalse(e.blocked());
        e.tick(1,true,"20261103",START+15*MIN);assertEquals(1,e.state.pending);assertEquals(START+30*MIN,e.state.nextQuestionAtMillis);
    }
    @Test public void missedSlotWhileAnsweringOrSleepingDoesNotStack() {
        GuardEngine e=engine();e.lockNow(START);e.tick(20*MIN,true,"20261103",START+20*MIN);assertEquals(1,e.state.pending);assertEquals(START+30*MIN,e.state.nextQuestionAtMillis);
        e.answer(true);e.tick(5000,true,"20261103",START+20*MIN+5000);e.tick(15*MIN,false,"20261103",START+35*MIN);
        assertFalse(e.blocked());assertEquals(START+45*MIN,e.state.nextQuestionAtMillis);
    }
    @Test public void intervalChangeAndClockRollbackKeepNextSlotInFuture() {
        GuardEngine e=engine();e.lockNow(START);e.setRules(new Rules(true,20,120,2,Arrays.asList("math")));e.reschedule(START+3*MIN);
        assertEquals(START+20*MIN,e.state.nextQuestionAtMillis);e.tick(0,true,"20261102",START-MIN);
        assertEquals(START-MIN,e.state.intervalAnchorMillis);assertEquals(START+19*MIN,e.state.nextQuestionAtMillis);assertEquals("20261103",e.state.day);
    }
    @Test public void repeatingLockStartsNewCycleAndDoesNotErasePracticeDebt() {
        GuardEngine e=engine();e.lockNow(START);e.answer(false);e.lockNow(START+2*MIN);
        assertEquals(2,e.state.pending);assertTrue(e.state.currentHadWrong);assertEquals(START+17*MIN,e.state.nextQuestionAtMillis);
    }
}
