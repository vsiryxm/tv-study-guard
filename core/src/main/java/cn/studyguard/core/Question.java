package cn.studyguard.core;
import java.util.*;
public final class Question {
    public final String subject, prompt, explanation;
    public final int grade, correctIndex,semester,difficulty;
    public final String itemId;
    public final List<String> choices;
    public Question(String subject,int grade,String prompt,List<String> choices,int correctIndex,String explanation) {
        this(subject,grade,prompt,choices,correctIndex,explanation,"",0,1);
    }
    public Question(String subject,int grade,String prompt,List<String> choices,int correctIndex,String explanation,String itemId,int semester,int difficulty) {
        this.itemId=itemId;this.semester=semester;this.difficulty=difficulty;
        this.subject=subject;this.grade=grade;this.prompt=prompt;this.correctIndex=correctIndex;this.explanation=explanation;
        this.choices=Collections.unmodifiableList(new ArrayList<>(choices));
    }
    /** Stable across choice order, so a regenerated historical question is the same review item. */
    public String key() {
        String answer=choices.get(correctIndex);
        String value=subject.length()+":"+subject+":"+grade+":"+prompt.length()+":"+prompt+":"+answer.length()+":"+answer;
        try {
            byte[] digest=java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(QuestionBank.UTF8));
            StringBuilder out=new StringBuilder();for(byte b:digest)out.append(String.format(java.util.Locale.US,"%02x",b&255));return out.toString();
        }catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
}
