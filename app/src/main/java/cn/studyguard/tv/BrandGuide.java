package cn.studyguard.tv;
import android.os.Build;
import java.util.Locale;
final class BrandGuide {
    static String name() { return Build.MANUFACTURER+" / "+Build.MODEL; }
    static String instructions() {
        String b=Build.MANUFACTURER.toLowerCase(Locale.ROOT);
        if(b.contains("xiaomi") || b.contains("redmi")) return "小米电视：在设置 → 应用管理中，允许学习守护自启动、后台运行和悬浮窗。不同 MIUI TV / 澎湃 OS 菜单可能不同。";
        if(b.contains("hisense")) return "海信电视：在应用权限/应用管理中允许悬浮窗、自启动与后台运行。请验证息屏唤醒与 HDMI 场景。";
        if(b.contains("tcl")) return "TCL 电视：在应用管理/安全中心中允许悬浮窗、自启动和后台运行。请验证重启恢复。";
        return "在系统应用管理中允许悬浮窗、自启动和后台运行。此应用使用标准 Android 接口；HDMI 覆盖需在当前机型上验证。";
    }
}
