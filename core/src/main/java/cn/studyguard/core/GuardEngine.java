package cn.studyguard.core;

public final class GuardEngine {
    public static final class State {
        public String day;
        public long usedMillis, sinceQuestionMillis;
        public int pending;
        public boolean currentHadWrong;
        /** True once the single practice question has been granted for this chain. */
        public boolean bonusGranted;
        public long celebrationMillis;
        public long intervalAnchorMillis,nextQuestionAtMillis;
        public State(String day) { this.day=day; }
        public State(State other) { copyFrom(other); }
        public void copyFrom(State other) {
            day=other.day;usedMillis=other.usedMillis;sinceQuestionMillis=other.sinceQuestionMillis;
            pending=other.pending;currentHadWrong=other.currentHadWrong;bonusGranted=other.bonusGranted;
            celebrationMillis=other.celebrationMillis;intervalAnchorMillis=other.intervalAnchorMillis;
            nextQuestionAtMillis=other.nextQuestionAtMillis;
        }
    }
    private Rules rules;
    public final State state;
    public GuardEngine(Rules rules, State state) { this.rules=rules; this.state=state; }
    public Rules rules() { return rules; }
    public void setRules(Rules rules) { this.rules=rules; checkInterval(); }
    public void onBoot() { if(rules.enabled) {state.pending=Math.max(1,state.pending);state.celebrationMillis=0;} }
    public void requestQuestion() { state.pending=Math.max(1,state.pending); }
    public void lockNow(long now) {
        if(now<=0)throw new IllegalArgumentException("无效的锁屏时间");
        rules=new Rules(true,rules.intervalMinutes,rules.dailyLimitMinutes,rules.grade,rules.subjects,rules.quizHint,rules.semester,rules.difficulty);
        state.pending=Math.max(1,state.pending);state.celebrationMillis=0;state.sinceQuestionMillis=0;
        state.intervalAnchorMillis=now;reschedule(now);
    }
    public void reschedule(long now) {
        if(state.intervalAnchorMillis<=0)return;
        if(now<state.intervalAnchorMillis)state.intervalAnchorMillis=now;
        long period=rules.intervalMinutes*60000L,elapsed=Math.max(0,now-state.intervalAnchorMillis);
        long wait=period-elapsed%period;state.nextQuestionAtMillis=now>Long.MAX_VALUE-wait?Long.MAX_VALUE:now+wait;
    }
    public void tick(long elapsedMillis, boolean screenOn, String day) {
        tick(elapsedMillis,screenOn,day,0);
    }
    public void tick(long elapsedMillis, boolean screenOn, String day,long now) {
        tick(elapsedMillis,screenOn,day,now,true);
    }
    /** A pending question alone cannot pause the budget if the TV failed to show it. */
    public void tick(long elapsedMillis, boolean screenOn, String day,long now,boolean blockingShown) {
        if(day.compareTo(state.day)>0) { state.day=day; state.usedMillis=0; state.sinceQuestionMillis=0; }
        if(state.intervalAnchorMillis>0 && now>0) {
            if(now<state.intervalAnchorMillis)reschedule(now);
            else if(now>=state.nextQuestionAtMillis) {
                if(rules.enabled && screenOn && !blocked())state.pending=1;
                reschedule(now);
            }
        }
        if(celebrating() && blockingShown) {if(screenOn)state.celebrationMillis=Math.max(0,state.celebrationMillis-Math.max(0,elapsedMillis));return;}
        if(rules.enabled && screenOn && (!blocked() || !blockingShown)) {
            long delta=Math.max(0,Math.min(elapsedMillis,480L*60000));
            state.usedMillis=Math.min(480L*60000, state.usedMillis+delta);
            if(!blocked())state.sinceQuestionMillis=Math.min(120L*60000, state.sinceQuestionMillis+delta);
            checkInterval();
        }
    }
    private void checkInterval() {
        if(state.intervalAnchorMillis==0 && rules.enabled && state.sinceQuestionMillis>=rules.intervalMinutes*60000L && state.pending==0) state.pending=1;
    }
    public boolean dailyLimitReached() { return rules.enabled && state.usedMillis>=rules.dailyLimitMinutes*60000L; }
    public boolean celebrating() { return rules.enabled && state.celebrationMillis>0 && !dailyLimitReached(); }
    public boolean blocked() { return rules.enabled && (state.pending>0 || state.celebrationMillis>0 || dailyLimitReached()); }
    /**
     * Answer flow (v4, "answer-wrong adds one practice"):
     * first question correct on first try -> 5s fireworks -> unlock;
     * first question wrong -> retry same question -> on retry correct go straight to ONE new practice
     * question without mid-chain fireworks; practice wrong only retries itself (no third question);
     * practice correct -> 5s fireworks -> unlock. Fireworks only ever fire when pending reaches zero.
     */
    public void answer(boolean correct) {
        if(!rules.enabled || dailyLimitReached() || celebrating() || state.pending<=0) return;
        if(correct) {
            state.pending--;
            if(state.pending>0) { state.currentHadWrong=false; state.bonusGranted=true; }
            else { state.celebrationMillis=5000; state.sinceQuestionMillis=0; state.currentHadWrong=false; state.bonusGranted=false; }
        } else {
            if(!state.bonusGranted) { state.pending=Math.max(2,state.pending); state.bonusGranted=true; }
            state.currentHadWrong=true;
        }
    }
}
