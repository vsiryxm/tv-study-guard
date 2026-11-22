package cn.studyguard.tv;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;

/** Device-owner provisioning is performed externally by the parent, never by the app itself. */
public final class GuardAdminReceiver extends DeviceAdminReceiver {
    @Override public void onEnabled(Context context,Intent intent) {
        new ManagedProtection(context).restore();
    }
}
