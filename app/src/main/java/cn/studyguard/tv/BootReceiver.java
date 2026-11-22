package cn.studyguard.tv;
import android.content.*;
public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent i) {
        if(!Intent.ACTION_BOOT_COMPLETED.equals(i.getAction()) && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(i.getAction()))return;
        ParentAuth parent=new ParentAuth(c);
        // Avoid loading the question bank or opening SQLite in the broadcast callback.
        if(!parent.configured() && !Store.wasEnabled(c))return;
        GuardService.start(c);
    }
}
