package cn.studyguard.tv;

import android.app.admin.DevicePolicyManager;
import android.content.*;
import android.os.Build;
import android.test.InstrumentationTestCase;
import java.util.Collections;

/** Run on a dedicated test device. The owner-only test requires explicit ADB provisioning. */
public final class ManagedProtectionTest extends InstrumentationTestCase {
    public void testOrdinaryInstallCannotEnableManagedProtection() {
        Context c=getInstrumentation().getTargetContext();
        ManagedProtection protection=new ManagedProtection(c);
        if(protection.status().deviceOwner)return;
        try { protection.enable();fail("A normal install must not be able to enable owner policies"); }
        catch(IllegalStateException expected) { }
        assertFalse(protection.status().requested);
        assertFalse(protection.status().uninstallBlocked);
        assertFalse(protection.status().userControlDisabled);
    }

    public void testOwnerProtectionAndRelease() {
        Context c=getInstrumentation().getTargetContext();
        DevicePolicyManager dpm=(DevicePolicyManager)c.getSystemService(Context.DEVICE_POLICY_SERVICE);
        if(!dpm.isDeviceOwnerApp(c.getPackageName()))return;
        ParentAuth auth=new ParentAuth(c);
        if(!auth.configured())auth.setup("482951","482951");
        ManagedProtection protection=new ManagedProtection(c);
        protection.enable();
        assertTrue(protection.status().requested);
        assertTrue(dpm.isUninstallBlocked(ManagedProtection.admin(c),c.getPackageName()));
        if(Build.VERSION.SDK_INT>=30)assertTrue(dpm.getUserControlDisabledPackages(ManagedProtection.admin(c)).contains(c.getPackageName()));
        // Re-applying an existing policy must preserve any unrelated package policies.
        if(Build.VERSION.SDK_INT>=30) {
            dpm.setUserControlDisabledPackages(ManagedProtection.admin(c),java.util.Arrays.asList(c.getPackageName(),"com.android.settings"));
            protection.restore();
            assertTrue(dpm.getUserControlDisabledPackages(ManagedProtection.admin(c)).contains("com.android.settings"));
        }
        protection.disable();
        assertFalse(protection.status().requested);
        assertFalse(dpm.isUninstallBlocked(ManagedProtection.admin(c),c.getPackageName()));
        if(Build.VERSION.SDK_INT>=30) {
            assertFalse(dpm.getUserControlDisabledPackages(ManagedProtection.admin(c)).contains(c.getPackageName()));
            assertTrue(dpm.getUserControlDisabledPackages(ManagedProtection.admin(c)).contains("com.android.settings"));
            dpm.setUserControlDisabledPackages(ManagedProtection.admin(c),Collections.emptyList());
        }
        assertTrue(protection.status().deviceOwner);
    }

    public void testOwnerBlocksStopAndDataClearThenCanRelease() throws Throwable {
        Context c=getInstrumentation().getTargetContext();
        DevicePolicyManager dpm=(DevicePolicyManager)c.getSystemService(Context.DEVICE_POLICY_SERVICE);
        if(!dpm.isDeviceOwnerApp(c.getPackageName()))return;
        ParentAuth auth=new ParentAuth(c);
        if(!auth.configured())auth.setup("482951","482951");
        ManagedProtection protection=new ManagedProtection(c);
        protection.enable();
        runTestOnUiThread(()->GuardService.start(c));
        shell("am force-stop "+c.getPackageName());
        assertTrue(protection.status().userControlDisabled);
        String marker=java.util.UUID.randomUUID().toString();
        c.getSharedPreferences("managed_probe",Context.MODE_PRIVATE).edit().putString("marker",marker).commit();
        shell("pm clear "+c.getPackageName());
        try(java.io.InputStream in=new java.io.FileInputStream(new java.io.File(c.getApplicationInfo().dataDir,"shared_prefs/managed_probe.xml"))) {
            java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();byte[] bytes=new byte[1024];int count;
            while((count=in.read(bytes))!=-1)out.write(bytes,0,count);
            assertTrue("Protected on-disk data must survive the clear request",out.toString("UTF-8").contains(marker));
        }
        assertTrue(auth.configured());
        assertTrue(protection.status().uninstallBlocked);
        // Simulate a missing policy, then exercise restoration of the saved opt-in.
        dpm.setUserControlDisabledPackages(ManagedProtection.admin(c),Collections.emptyList());
        protection.restore();
        assertTrue(protection.status().userControlDisabled);
        try {
            protection.releaseOwnership();
            assertFalse(dpm.isDeviceOwnerApp(c.getPackageName()));
        } catch(IllegalStateException e) {
            // API 33 ATV image advertises telephony but lacks its managed-APN provider.
            // The app must report the failure and leave its own policies released.
            assertTrue(e.getMessage().contains("策略已关闭"));
            assertFalse(protection.status().uninstallBlocked);
            assertFalse(protection.status().userControlDisabled);
            assertTrue(protection.status().error.contains("未能解除"));
        }
        assertFalse(protection.status().requested);
        assertTrue(auth.configured());
    }

    private String shell(String command) throws Exception {
        try(android.os.ParcelFileDescriptor fd=getInstrumentation().getUiAutomation().executeShellCommand(command);
            java.io.InputStream in=new android.os.ParcelFileDescriptor.AutoCloseInputStream(fd);
            java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()) {
            byte[] bytes=new byte[1024];int count;
            while((count=in.read(bytes))!=-1)out.write(bytes,0,count);
            return out.toString("UTF-8");
        }
    }
}
