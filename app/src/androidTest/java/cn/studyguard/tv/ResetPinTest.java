package cn.studyguard.tv;
import android.content.*;
import android.test.InstrumentationTestCase;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Dedicated emulator only: verifies removed legacy recovery stays unavailable. */
public final class ResetPinTest extends InstrumentationTestCase {
    private Context context;
    @Override protected void setUp() throws Exception {super.setUp();context=getInstrumentation().getTargetContext();}
    public void testLegacyResetReceiverIsNotRegistered() throws Exception {
        try{context.getPackageManager().getReceiverInfo(new ComponentName(context,"cn.studyguard.tv.ResetPinReceiver"),0);fail("Legacy reset receiver is still registered");}
        catch(android.content.pm.PackageManager.NameNotFoundException expected){}
    }
    public void testLegacyResetBroadcastCannotChangeExistingCredential() throws Exception {
        context.getSharedPreferences("parent",0).edit().clear().commit();ParentAuth auth=new ParentAuth(context);auth.setup("482951","482951");String before=context.getSharedPreferences("parent",0).getString("hash","");
        CountDownLatch delivered=new CountDownLatch(1);
        context.sendOrderedBroadcast(new Intent("cn.studyguard.tv.action.RESET_PARENT").setPackage(context.getPackageName()).putExtra("code","12345678"),null,new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){delivered.countDown();}},null,0,null,null);
        assertTrue(delivered.await(3,TimeUnit.SECONDS));assertEquals(before,context.getSharedPreferences("parent",0).getString("hash",""));assertTrue(new ParentAuth(context).verify("482951"));
    }
}
