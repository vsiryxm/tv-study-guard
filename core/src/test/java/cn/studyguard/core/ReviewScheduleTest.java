package cn.studyguard.core;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

public class ReviewScheduleTest {
    @Test public void intervalsFollowReviewStagesAndClampLongHistory() {
        long minute=60000;
        assertEquals(20*minute,ReviewSchedule.delayMillis(1));assertEquals(60*minute,ReviewSchedule.delayMillis(2));
        assertEquals(9*60*minute,ReviewSchedule.delayMillis(3));assertEquals(24*60*minute,ReviewSchedule.delayMillis(4));
        assertEquals(30L*24*60*minute,ReviewSchedule.delayMillis(Integer.MAX_VALUE));
    }
    @Test public void identitySurvivesOptionShuffleButSeparatesGradeAndSubject() {
        Question a=new Question("math",2,"6 × 3 = ?",Arrays.asList("18","17","19","20"),0,"");
        Question b=new Question("math",2,"6 × 3 = ?",Arrays.asList("20","19","17","18"),3,"");
        assertEquals(a.key(),b.key());
        assertFalse(a.key().equals(new Question("math",4,a.prompt,a.choices,0,"").key()));
        assertFalse(a.key().equals(new Question("english",2,a.prompt,a.choices,0,"").key()));
    }
    @Test public void historicalQuestionsCanBeRestoredWithoutLosingCorrectAnswer() {
        QuestionBank bank=new QuestionBank(new Random(7));
        for(int grade=1;grade<=6;grade++)for(String subject:Rules.SUBJECTS) {
            Question old=bank.next(grade,Arrays.asList(subject));
            Question restored=bank.restore(subject,grade,old.prompt,old.choices.get(old.correctIndex));
            assertNotNull(restored);assertEquals(old.key(),restored.key());assertEquals(4,new HashSet<>(restored.choices).size());
        }
    }
    @Test public void hintDefaultsAndValidation() {
        assertEquals("休息一下，答对才能继续看电视",Rules.defaults().quizHint);
        assertEquals("休息一下",new Rules(false,20,120,2,Rules.SUBJECTS," 休息一下 ").quizHint);
        try {new Rules(false,20,120,2,Rules.SUBJECTS,"   ");fail();}catch(IllegalArgumentException expected){}
    }
}
