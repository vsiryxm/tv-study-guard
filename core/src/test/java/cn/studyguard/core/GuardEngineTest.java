package cn.studyguard.core;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.Arrays;

public class GuardEngineTest {
    private Rules rules() { return new Rules(true, 1, 10, 2, Arrays.asList("math", "chinese", "english")); }
    @Test public void intervalBlocksAndWrongAddsQuestion() {
        GuardEngine e = new GuardEngine(rules(), new GuardEngine.State("20261102"));
        e.tick(60000, true, "20261102");
        assertEquals(1, e.state.pending);
        e.answer(false);
        assertEquals(2, e.state.pending);
        assertTrue(e.state.bonusGranted);
        e.answer(true);
        assertEquals(1, e.state.pending);
        assertFalse("答错后重试答对不放中途礼花", e.celebrating());
        assertTrue(e.blocked());
        e.answer(true);
        assertTrue(e.blocked());
        assertTrue(e.celebrating());
        e.tick(4999,true,"20261102");assertTrue(e.blocked());
        e.tick(1,true,"20261102");
        assertFalse(e.blocked());
        assertEquals(0, e.state.sinceQuestionMillis);
    }
    @Test public void firstTryCorrectCelebratesAndUnlocks() {
        GuardEngine e = new GuardEngine(rules(), new GuardEngine.State("20261102"));
        e.requestQuestion();
        e.answer(true);
        assertEquals(0, e.state.pending);
        assertTrue(e.celebrating());
        assertFalse(e.state.bonusGranted);
        e.tick(5000,true,"20261102");
        assertFalse(e.blocked());
    }
    @Test public void repeatedWrongOnSameQuestionAddsOnlyOnePractice() {
        GuardEngine e=new GuardEngine(rules(),new GuardEngine.State("20261102"));e.requestQuestion();
        e.answer(false);e.answer(false);e.answer(false);assertEquals(2,e.state.pending);assertTrue(e.state.currentHadWrong);
        e.answer(true);assertEquals(1,e.state.pending);assertFalse(e.state.currentHadWrong);
        // Practice wrong only retries itself: never a third question.
        e.answer(false);assertEquals(1,e.state.pending);
        e.answer(false);assertEquals(1,e.state.pending);
        e.answer(true);assertEquals(0,e.state.pending);assertTrue(e.celebrating());
        e.tick(5000,true,"20261102");assertFalse(e.blocked());
    }
    @Test public void chainRestoredFromPersistedStateAddsNoExtraPractice() {
        GuardEngine.State s=new GuardEngine.State("20261102");s.pending=1;s.bonusGranted=true;
        GuardEngine e=new GuardEngine(rules(),s);
        e.answer(false);assertEquals(1,e.state.pending); // restored practice wrong adds nothing
        e.answer(true);assertEquals(0,e.state.pending);assertTrue(e.celebrating());
    }
    @Test public void fireworksPauseWhenScreenOffAndDoNotConsumeViewingTime() {
        GuardEngine e=new GuardEngine(rules(),new GuardEngine.State("20261102"));e.requestQuestion();e.answer(true);
        e.tick(9000,false,"20261102");assertEquals(5000,e.state.celebrationMillis);
        e.tick(4999,true,"20261102");assertTrue(e.celebrating());assertEquals(0,e.state.usedMillis);
        e.tick(1,true,"20261102");assertFalse(e.blocked());assertEquals(0,e.state.usedMillis);
    }
    @Test public void screenOffAndQuizDoNotConsumeBudget() {
        GuardEngine e = new GuardEngine(rules(), new GuardEngine.State("20261102"));
        e.tick(10000, false, "20261102");
        assertEquals(0, e.state.usedMillis);
        e.tick(60000, true, "20261102");
        e.tick(60000, true, "20261102");
        assertEquals(60000, e.state.usedMillis);
    }
    @Test public void pendingQuizOnlyPausesBudgetWhenShown() {
        GuardEngine e = new GuardEngine(rules(), new GuardEngine.State("20261102"));
        e.tick(60000, true, "20261102");
        assertTrue(e.blocked());
        e.tick(120000, true, "20261102", 0, false);
        assertEquals(180000, e.state.usedMillis);
        assertEquals(1, e.state.pending);
        e.tick(60000, true, "20261102", 0, true);
        assertEquals(180000, e.state.usedMillis);
    }
    @Test public void dailyCapCannotBeAnsweredAway() {
        GuardEngine e = new GuardEngine(new Rules(true, 10, 1, 2, Arrays.asList("math")), new GuardEngine.State("20261102"));
        e.tick(60000, true, "20261102");
        assertTrue(e.dailyLimitReached());
        e.answer(true);
        assertTrue(e.blocked());
        e.tick(1000, true, "20261103");
        assertFalse(e.dailyLimitReached());
    }
    @Test public void bootRestoresChallengeAndClockRollbackDoesNotResetBudget() {
        GuardEngine.State s = new GuardEngine.State("20261102"); s.usedMillis = 50000; s.pending = 3;
        GuardEngine e = new GuardEngine(rules(), s); e.onBoot();
        assertEquals(3, s.pending);
        e.tick(1000, true, "20261101");
        assertEquals(50000, s.usedMillis);
        assertEquals("20261102", s.day);
        e.tick(1000, true, "20261103");
        assertEquals(0, s.usedMillis);
        assertEquals(3, s.pending);
    }
    @Test public void disablingDoesNotErasePendingOrUsage() {
        GuardEngine e = new GuardEngine(rules(), new GuardEngine.State("20261102")); e.tick(60000, true, "20261102");
        e.setRules(new Rules(false, 1, 10, 2, Arrays.asList("math")));
        assertFalse(e.blocked()); e.tick(60000, true, "20261102");
        assertEquals(60000, e.state.usedMillis);
        e.setRules(rules()); assertTrue(e.blocked());
    }
    @Test public void shorterIntervalAppliesImmediately() {
        GuardEngine e = new GuardEngine(new Rules(true, 10, 60, 2, Arrays.asList("math")), new GuardEngine.State("20261102"));
        e.tick(120000, true, "20261102"); e.setRules(rules()); assertTrue(e.blocked());
    }
    @Test public void bootRequiresOneQuestionEvenWithoutExistingChallenge() {
        GuardEngine e=new GuardEngine(rules(),new GuardEngine.State("20261102"));e.onBoot();assertEquals(1,e.state.pending);
    }
    @Test public void capReductionTakesEffectAndQuestionCannotClearCap() {
        GuardEngine e=new GuardEngine(new Rules(true,120,120,2,Arrays.asList("math")),new GuardEngine.State("20261102"));
        e.tick(120000,true,"20261102");e.setRules(new Rules(true,120,1,2,Arrays.asList("math")));assertTrue(e.dailyLimitReached());e.answer(true);assertTrue(e.blocked());
    }
    @Test public void longElapsedCannotOverflowAndNegativeTimeIsIgnored() {
        GuardEngine e=new GuardEngine(rules(),new GuardEngine.State("20261102"));e.tick(-100,true,"20261102");assertEquals(0,e.state.usedMillis);
        e.tick(Long.MAX_VALUE,true,"20261102");assertEquals(480L*60000,e.state.usedMillis);assertTrue(e.dailyLimitReached());
    }
    @Test(expected=IllegalArgumentException.class) public void invalidRulesRejected() { new Rules(true, 0, 10, 2, Arrays.asList("math")); }
    @Test(expected=IllegalArgumentException.class) public void unknownSubjectRejected() { new Rules(true, 1, 10, 2, Arrays.asList("unknown")); }
}
