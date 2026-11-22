package cn.studyguard.core;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
public class QuestionBankTest {
    @Test public void everyGradeAndSubjectHasValidUniqueChoices() {
        QuestionBank bank = new QuestionBank(new Random(7));
        for (int grade=1; grade<=6; grade++) for (String subject: Rules.SUBJECTS) for(int i=0;i<100;i++) {
            Question q=bank.next(grade, Arrays.asList(subject));
            assertEquals(subject,q.subject); assertEquals(grade,q.grade);
            assertEquals(4,q.choices.size()); assertEquals(4,new HashSet<>(q.choices).size());
            assertTrue(q.correctIndex>=0 && q.correctIndex<4); assertFalse(q.explanation.isEmpty());
        }
    }
}
