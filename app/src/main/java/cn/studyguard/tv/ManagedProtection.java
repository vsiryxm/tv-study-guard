package cn.studyguard.tv;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import java.util.ArrayList;
import java.util.List;

/** Optional device-owner policies. Ordinary installations never apply restrictions. */
final class ManagedProtection {
    static final class Status {
        final boolean deviceOwner, requested, uninstallBlocked, userControlDisabled;
        final boolean forceStopSupported;
        final String error;
        Status(boolean owner, boolean requested, boolean uninstall, boolean controls, String error) {
            deviceOwner=owner;this.requested=requested;uninstallBlocked=uninstall;
            userControlDisabled=controls;forceStopSupported=Build.VERSION.SDK_INT>=30;this.error=error;
        }
    }
    private final Context context;
    private final DevicePolicyManager dpm;
    private final SharedPreferences prefs;
    static ComponentName admin(Context context) { return new ComponentName(context,GuardAdminReceiver.class); }
    ManagedProtection(Context context) {
        this.context=context.getApplicationContext();
        dpm=(DevicePolicyManager)context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        prefs=context.getSharedPreferences("managed_protection",Context.MODE_PRIVATE);
    }
    synchronized Status status() {
        if(Build.VERSION.SDK_INT<21)return new Status(false,prefs.getBoolean("enabled",false),false,false,""); // device owner exists since API 21
        boolean owner=dpm!=null && Build.VERSION.SDK_INT>=18 && dpm.isDeviceOwnerApp(context.getPackageName()); // API 18
        boolean requested=prefs.getBoolean("enabled",false);
        if(!owner)return new Status(false,requested,false,false,"");
        try {
            boolean uninstall=dpm.isUninstallBlocked(admin(context),context.getPackageName());
            boolean controls=Build.VERSION.SDK_INT>=30 && dpm.getUserControlDisabledPackages(admin(context)).contains(context.getPackageName());
            return new Status(true,requested,uninstall,controls,prefs.getString("error",""));
        } catch(RuntimeException e) { return new Status(true,requested,false,false,describe(e)); }
    }
    private void requireOwner() {
        if(dpm==null || Build.VERSION.SDK_INT<18 || !dpm.isDeviceOwnerApp(context.getPackageName()))throw new IllegalStateException("此电视尚未配置设备所有者，普通安装不能启用受管保护");
    }
    synchronized void enable() {
        if(Build.VERSION.SDK_INT<21)throw new IllegalStateException("此 Android 版本不支持设备所有者保护");
        requireOwner();
        if(!new ParentAuth(context).configured())throw new IllegalStateException("请先设置家长口令");
        ComponentName admin=admin(context);String ownPackage=context.getPackageName();
        boolean previousUninstall=dpm.isUninstallBlocked(admin,ownPackage);
        List<String> previousControls=Build.VERSION.SDK_INT>=30?new ArrayList<>(dpm.getUserControlDisabledPackages(admin)):new ArrayList<>();
        try {
            dpm.setUninstallBlocked(admin,ownPackage,true);
            if(Build.VERSION.SDK_INT>=30) {
                List<String> packages=new ArrayList<>(previousControls);
                if(!packages.contains(ownPackage))packages.add(ownPackage);
                dpm.setUserControlDisabledPackages(admin,packages);
            }
            Status actual=status();
            if(!actual.uninstallBlocked || (Build.VERSION.SDK_INT>=30 && !actual.userControlDisabled))throw new IllegalStateException("系统未落实保护策略，请检查电视兼容性");
            if(!prefs.edit().putBoolean("enabled",true).remove("error").commit())throw new IllegalStateException("无法保存保护设置");
        } catch(RuntimeException e) {
            try {
                dpm.setUninstallBlocked(admin,ownPackage,previousUninstall);
                if(Build.VERSION.SDK_INT>=30)dpm.setUserControlDisabledPackages(admin,previousControls);
            }catch(RuntimeException rollback) { e.addSuppressed(rollback); }
            prefs.edit().putString("error",describe(e)).commit();
            throw new IllegalStateException(describe(e),e);
        }
    }
    synchronized void disable() {
        if(Build.VERSION.SDK_INT<21)throw new IllegalStateException("此 Android 版本不支持设备所有者保护");
        requireOwner();
        // Persist the opt-out before releasing policies so a concurrent restart cannot re-enable it.
        if(!prefs.edit().putBoolean("enabled",false).commit())throw new IllegalStateException("无法保存保护设置");
        try {
            if(Build.VERSION.SDK_INT>=30) {
                List<String> packages=new ArrayList<>(dpm.getUserControlDisabledPackages(admin(context)));
                packages.remove(context.getPackageName());dpm.setUserControlDisabledPackages(admin(context),packages);
            }
            dpm.setUninstallBlocked(admin(context),context.getPackageName(),false);
            Status actual=status();
            if(actual.uninstallBlocked || actual.userControlDisabled)throw new IllegalStateException("系统尚未解除保护，请重试");
            prefs.edit().remove("error").commit();
        } catch(RuntimeException e) { prefs.edit().putString("error",describe(e)).commit();throw new IllegalStateException(describe(e),e); }
    }
    synchronized void restore() {
        if(Build.VERSION.SDK_INT<21)return; // API 15 target TV: no device-owner support at all
        if(!prefs.getBoolean("enabled",false))return;
        if(!new ParentAuth(context).configured())return;
        try { enable(); }catch(RuntimeException e) { prefs.edit().putString("error",describe(e)).commit(); }
    }
    @SuppressWarnings("deprecation")
    synchronized void releaseOwnership() {
        if(Build.VERSION.SDK_INT<21)throw new IllegalStateException("此 Android 版本不支持设备所有者保护");
        requireOwner();disable();
        try { dpm.clearDeviceOwnerApp(context.getPackageName()); }
        catch(RuntimeException e) {
            String message="本应用策略已关闭，但电视系统未能解除设备所有者："+describe(e);
            prefs.edit().putString("error",message).commit();
            throw new IllegalStateException(message,e);
        }
        if(dpm.isDeviceOwnerApp(context.getPackageName()))throw new IllegalStateException("系统未解除设备所有者，请检查电视设置");
    }
    private static String describe(RuntimeException e) { String message=e.getMessage();return message==null?"系统不支持此设备管理操作":message; }
}
