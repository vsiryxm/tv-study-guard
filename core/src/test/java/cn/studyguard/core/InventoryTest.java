package cn.studyguard.core;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public final class InventoryTest {
    @Test public void eachSubjectHasAtLeastThreeHundredDistinctItems() {
        Set<String> ids=new HashSet<>(),prompts=new HashSet<>();Map<String,Integer> counts=new HashMap<>();
        for(QuestionBank.Item i:QuestionBank.inventory()) {
            assertTrue(ids.add(i.id));assertTrue(prompts.add(i.subject+":"+i.prompt.replaceAll("\\s+","")));
            assertTrue(Rules.SUBJECTS.contains(i.subject));assertEquals(4,new HashSet<>(i.choices).size());assertEquals(i.answer,i.choices.get(0));assertFalse(i.reference.isEmpty());assertFalse(i.topic.isEmpty());
            counts.put(i.subject,counts.getOrDefault(i.subject,0)+1);
        }
        for(String s:Rules.SUBJECTS)assertTrue(s,counts.get(s)>=300);
    }
    @Test public void allGradeTermDifficultyPoolsExistAndRespectScope() {
        QuestionBank bank=new QuestionBank(new Random(3));
        for(int g=1;g<=6;g++)for(int t=0;t<=2;t++)for(int d=1;d<=3;d++)for(String s:Rules.SUBJECTS){
            Rules r=new Rules(false,20,120,g,Arrays.asList(s),Rules.DEFAULT_QUIZ_HINT,t,d);
            List<QuestionBank.Item> pool=bank.eligible(r,s);assertFalse(s+g+t+d,pool.isEmpty());
            for(QuestionBank.Item i:pool){assertTrue(i.minGrade<=g && g<=i.maxGrade);assertTrue(i.difficulty<=d);assertTrue(t==0 || i.semester<=t);}
            Question q=bank.next(r);assertTrue(q.difficulty<=d);assertTrue(t==0 || q.semester<=t);assertFalse(q.itemId.isEmpty());
        }
    }
    @Test public void shuffleBagDoesNotRepeatBeforeEligiblePoolExhausted() {
        QuestionBank bank=new QuestionBank(new Random(99));Rules r=new Rules(false,20,120,3,Arrays.asList("math"),Rules.DEFAULT_QUIZ_HINT,1,2);Set<String> seen=new HashSet<>();
        int count=bank.eligible(r,"math").size();for(int k=0;k<count;k++)assertTrue(seen.add(bank.next(r).itemId));assertEquals(count,seen.size());
    }
    @Test public void randomSubjectRotationAvoidsRepeatsAndCoversAllSubjects() {
        QuestionBank bank=new QuestionBank(new Random(19));
        Map<String,Integer> counts=new HashMap<>();String last=null;
        for(int i=0;i<600;i++){
            Question q=bank.next(Rules.defaults(),new HashSet<String>(),last);
            if(last!=null)assertNotEquals("连续两题同科",last,q.subject);
            last=q.subject;counts.merge(q.subject,1,Integer::sum);
        }
        for(String s:Rules.SUBJECTS)assertTrue(s+" 出现过少",counts.getOrDefault(s,0)>=50);
        for(String s:Arrays.asList("riddle","science"))assertEquals(s,bank.next(2,Arrays.asList(s)).subject);
    }
    @Test public void dailyExclusionPreventsRepeatsAndExhaustionFallsBack() {
        QuestionBank bank=new QuestionBank(new Random(5));
        Rules r=new Rules(false,20,120,3,Arrays.asList("math"),Rules.DEFAULT_QUIZ_HINT,1,2);
        int poolSize=bank.eligible(r,"math").size();Set<String> seen=new HashSet<>();
        for(int k=0;k<poolSize;k++)seen.add(bank.next(r,seen,null).itemId);
        assertEquals(poolSize,seen.size());
        Question repeated=bank.next(r,seen,null);   // pool exhausted for today: repeat, never a dead end
        assertTrue(seen.contains(repeated.itemId));
    }
    @Test public void metadataDoesNotChangeStableReviewKeyAndRemovedQuestionsAreNotSynthesized() {
        Question q=new Question("math",2,"5 + 3 = ?",Arrays.asList("8","7","9","6"),0,"old");
        Question tagged=new Question("math",2,q.prompt,q.choices,q.correctIndex,q.explanation,"new",2,3);assertEquals(q.key(),tagged.key());
        assertNull(new QuestionBank(new Random(8)).restore("math",2,"removed question","8"));
    }
    @Test public void immediateLockPreservesLearningScope() {
        Rules rules=new Rules(false,20,120,4,Arrays.asList("poetry"),"动脑啦",2,3);GuardEngine e=new GuardEngine(rules,new GuardEngine.State("20261103"));e.lockNow(100000);assertEquals(2,e.rules().semester);assertEquals(3,e.rules().difficulty);assertEquals("poetry",e.rules().subjects.get(0));
    }
}
