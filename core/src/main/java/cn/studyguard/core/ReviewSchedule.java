package cn.studyguard.core;

/** Common spaced-review intervals, used until a review is correct on its first attempt. */
public final class ReviewSchedule {
    private static final long[] MINUTES={20,60,540,1440,2880,5760,10080,21600,43200};
    private ReviewSchedule() { }
    public static long delayMillis(int lapseCount) {
        return MINUTES[Math.max(0,Math.min(MINUTES.length-1,lapseCount-1))]*60000L;
    }
}
