package cn.studyguard.core;

import java.io.*;
import java.util.*;

/** Versioned, finite and auditable offline items. Choice shuffles never create extra inventory. */
public final class QuestionBank {
    /** StandardCharsets is API 19 on Android; resolve once by name for API 15. */
    static final java.nio.charset.Charset UTF8=java.nio.charset.Charset.forName("UTF-8");
    public static final class Item {
        public final String id,subject,prompt,answer,explanation,reference,topic;
        public final int minGrade,maxGrade,semester,difficulty;
        public final List<String> choices;
        Item(String[] c) {
            id=c[0];subject=c[1];minGrade=Integer.parseInt(c[2]);maxGrade=Integer.parseInt(c[3]);semester=Integer.parseInt(c[4]);difficulty=Integer.parseInt(c[5]);prompt=c[6];answer=c[7];
            choices=Collections.unmodifiableList(Arrays.asList(c[7],c[8],c[9],c[10]));explanation=c[11];reference=c[12];topic=c[13];
        }
        boolean eligible(Rules r) {return r.grade>=minGrade && r.grade<=maxGrade && (r.semester==0 || semester==0 || semester<=r.semester) && difficulty<=r.difficulty;}
        Question question(int grade,Random random) {List<String> values=new ArrayList<>(choices);Collections.shuffle(values,random);return new Question(subject,grade,prompt,values,values.indexOf(answer),explanation,id,semester,difficulty);}
    }
    private static final List<Item> ITEMS=load();
    private final Random random;
    private final Map<String,Deque<Item>> bags=new HashMap<>();
    private final Map<String,List<Item>> pools=new HashMap<>();
    public QuestionBank(Random random) {this.random=Objects.requireNonNull(random);}
    public static List<Item> inventory() {return ITEMS;}
    private static List<Item> load() {
        List<Item> items=new ArrayList<>();Set<String> ids=new HashSet<>(),questions=new HashSet<>();
        InputStream input=QuestionBank.class.getResourceAsStream("/questionbank/questions.tsv");if(input==null)throw new IllegalStateException("离线题库未打包");
        try(BufferedReader reader=new BufferedReader(new InputStreamReader(input,UTF8))) {
            String line;while((line=reader.readLine())!=null){if(line.startsWith("#") || line.isEmpty())continue;String[] fields=line.split("\\t",-1);if(fields.length!=14)throw new IOException("题库列数不正确");Item item=new Item(fields);
                if(!ids.add(item.id) || !questions.add(item.subject+":"+item.prompt))throw new IOException("题库重复题");
                if(!Rules.SUBJECTS.contains(item.subject) || new HashSet<>(item.choices).size()!=4 || item.prompt.isEmpty() || item.explanation.isEmpty() || item.reference.isEmpty())throw new IOException("题库条目无效");
                items.add(item);
            }
        }catch(IOException | IllegalArgumentException e){throw new IllegalStateException("离线题库读取失败",e);}
        return Collections.unmodifiableList(items);
    }
    public Question next(int grade,List<String> subjects) {return next(new Rules(false,20,120,grade,subjects));}
    public Question next(Rules rules) {return next(rules,Collections.<String>emptySet(),null);}
    /**
     * Subject rotation (v4): pick uniformly at random among enabled subjects that still have
     * age-appropriate items, avoiding the previously served subject whenever another choice exists.
     * Newly presented items never repeat within the same local day while the eligible pool lasts;
     * when every enabled subject's pool was already served today, the caller receives a repeated
     * item from a freshly shuffled pool instead of a dead end.
     */
    public Question next(Rules rules,Set<String> presentedToday,String lastSubject) {
        List<String> candidates=new ArrayList<>();
        for(String subject:rules.subjects)if(!pool(rules,subject).isEmpty())candidates.add(subject);
        if(candidates.isEmpty())throw new IllegalStateException("当前规则没有可用题目");
        List<String> preferred=new ArrayList<>(candidates);
        if(preferred.size()>1 && lastSubject!=null)preferred.remove(lastSubject);
        List<String> unseen=new ArrayList<>();
        for(String subject:preferred)if(hasUnseen(rules,subject,presentedToday))unseen.add(subject);
        List<String> pick=unseen.isEmpty()?preferred:unseen;
        String subject=pick.get(random.nextInt(pick.size()));
        Item item=draw(rules,subject,presentedToday);
        if(item==null)item=draw(rules,subject,Collections.<String>emptySet());
        if(item==null)throw new IllegalStateException("当前规则没有可用题目");
        return item.question(rules.grade,random);
    }
    private boolean hasUnseen(Rules r,String subject,Set<String> exclude) {
        if(exclude.isEmpty())return true;
        for(Item item:pool(r,subject))if(!exclude.contains(item.id))return true;
        return false;
    }
    private Item draw(Rules r,String subject,Set<String> exclude) {
        String key=scope(r,subject);Deque<Item> bag=bags.get(key);
        if(bag==null || bag.isEmpty()){List<Item> candidates=new ArrayList<>(pool(r,subject));Collections.shuffle(candidates,random);bag=new ArrayDeque<>(candidates);bags.put(key,bag);}
        if(exclude.isEmpty())return bag.removeFirst();
        for(Iterator<Item> it=bag.iterator();it.hasNext();) {
            Item item=it.next();
            if(!exclude.contains(item.id)){it.remove();return item;}
        }
        List<Item> candidates=new ArrayList<>(pool(r,subject));Collections.shuffle(candidates,random);
        bags.put(key,new ArrayDeque<>(candidates));
        return candidates.isEmpty()?null:candidates.remove(0);
    }
    public List<Item> eligible(Rules rules,String subject) {return Collections.unmodifiableList(pool(rules,subject));}
    private String scope(Rules r,String subject) {return subject+":"+r.grade+":"+r.semester+":"+r.difficulty;}
    private List<Item> pool(Rules r,String subject) {String key=scope(r,subject);List<Item> values=pools.get(key);if(values==null){values=new ArrayList<>();for(Item item:ITEMS)if(item.subject.equals(subject) && item.eligible(r))values.add(item);pools.put(key,values);}return values;}
    public Question restore(String subject,int grade,String prompt,String answer) {
        for(Item item:ITEMS)if(item.subject.equals(subject) && item.prompt.equals(prompt) && item.answer.equals(answer))return item.question(grade,random);
        return null;
    }
}
