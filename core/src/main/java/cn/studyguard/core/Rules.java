package cn.studyguard.core;

import java.util.*;

public final class Rules {
    public static final List<String> SUBJECTS=Collections.unmodifiableList(Arrays.asList("math","chinese","english","poetry","riddle","science","ai"));
    public final boolean enabled;
    public final int intervalMinutes,dailyLimitMinutes,grade,semester,difficulty;
    public final List<String> subjects;
    public static final String DEFAULT_QUIZ_HINT="休息一下，答对才能继续看电视";
    public final String quizHint;
    public Rules(boolean enabled,int intervalMinutes,int dailyLimitMinutes,int grade,List<String> subjects) {this(enabled,intervalMinutes,dailyLimitMinutes,grade,subjects,DEFAULT_QUIZ_HINT);}
    public Rules(boolean enabled,int intervalMinutes,int dailyLimitMinutes,int grade,List<String> subjects,String hint) {this(enabled,intervalMinutes,dailyLimitMinutes,grade,subjects,hint,0,2);}
    public Rules(boolean enabled,int intervalMinutes,int dailyLimitMinutes,int grade,List<String> subjects,String hint,int semester,int difficulty) {
        if(intervalMinutes<1 || intervalMinutes>120)throw new IllegalArgumentException("弹题间隔须为 1–120 分钟");
        if(dailyLimitMinutes<1 || dailyLimitMinutes>480)throw new IllegalArgumentException("每日上限须为 1–480 分钟");
        if(grade<1 || grade>6)throw new IllegalArgumentException("请选择小学 1–6 年级");
        if(semester<0 || semester>2)throw new IllegalArgumentException("请选择全年、上学期或下学期");
        if(difficulty<1 || difficulty>3)throw new IllegalArgumentException("请选择基础、标准或提升难度");
        if(subjects==null || subjects.isEmpty() || !SUBJECTS.containsAll(subjects) || new HashSet<>(subjects).size()!=subjects.size())throw new IllegalArgumentException("请至少选择一种出题内容，不可重复");
        if(hint==null || hint.trim().isEmpty() || hint.trim().length()>80)throw new IllegalArgumentException("答题提示语须为 1–80 字");
        this.enabled=enabled;this.intervalMinutes=intervalMinutes;this.dailyLimitMinutes=dailyLimitMinutes;this.grade=grade;this.semester=semester;this.difficulty=difficulty;
        this.subjects=Collections.unmodifiableList(new ArrayList<>(subjects));this.quizHint=hint.trim();
    }
    public static Rules defaults() {return new Rules(false,20,120,2,SUBJECTS);}
    public static String subjectName(String id) {
        switch(id){case "math":return "数学";case "chinese":return "语文";case "english":return "英语";case "poetry":return "唐诗接句";case "riddle":return "脑筋急转弯";case "science":return "十万个为什么";case "ai":return "AI常识";default:return "其他";}
    }
    public static String semesterName(int value) {return value==1?"上学期":value==2?"下学期":"全年";}
    public static String difficultyName(int value) {return value==1?"基础":value==3?"提升":"标准";}
}
