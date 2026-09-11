package com.tomoe.iosanim;

import android.animation.TimeInterpolator;
import android.content.Context;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

/**
 * iOS-feel animation curves. The visible transitions (app open/close, recents,
 * shade) don't go through AnimationUtils.loadInterpolator -- they read hardcoded
 * constants like Interpolators.EMPHASIZED built with `new PathInterpolator(...)`
 * at class-load. So we OVERWRITE those static interpolator constants directly with
 * an iOS easeOutQuint curve, and also keep the loadInterpolator hook as fallback.
 *
 * ponytail: replaces every non-linear static Interpolator field in the known holder
 * classes. Static bezier, not a true interruptible spring. LINEAR/CYCLE/BOUNCE/
 * OVERSHOOT/ANTICIPATE/SPRING left alone (scrolling flings, progress bars).
 * Tune the 4 control points in ios().
 */
public class Hook implements IXposedHookLoadPackage {

    private static final String TAG = "iOSAnim";

    // Two curves. Overshoot (cy>1, settles past target then back) is the iOS
    // signature -- but it drives interpolated values ABOVE 1.0, which corrupts
    // any anim mapping progress->alpha/clip/dim. Safe on launcher app-open/close;
    // NOT on SystemUI shade/QS/notif (those clip & flicker = "ngebug"). So:
    //   launcher -> overshoot; systemui/framework -> plain easeOutQuint (stays [0,1]).
    private static Interpolator overshoot() {
        try {
            return new PathInterpolator(0.22f, 1.12f, 0.36f, 1.0f);
        } catch (Throwable t) {
            return new PathInterpolator(0.23f, 1f, 0.32f, 1f);
        }
    }
    private static Interpolator easeOut() {
        return new PathInterpolator(0.23f, 1f, 0.32f, 1f); // easeOutQuint, no overshoot
    }
    private static Interpolator ios(String pkg) {
        return "com.android.launcher3".equals(pkg) ? overshoot() : easeOut();
    }

    // interpolator-holder classes across AOSP/SystemUI versions
    private static final String[] HOLDERS = {
            "com.android.app.animation.Interpolators",       // A13+ shared
            "com.android.systemui.animation.Interpolators",  // older SystemUI
            "com.android.launcher3.anim.Interpolators",      // launcher3 legacy
    };

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) {
        Interpolator ios = ios(lpparam.packageName);
        int total = 0;
        for (String holder : HOLDERS) {
            total += replaceFields(holder, lpparam.classLoader, ios);
        }
        if (total > 0) {
            XposedBridge.log(TAG + ": replaced " + total + " interpolator(s) in " + lpparam.packageName);
        }

        // fallback: XML-driven anims via AnimationUtils.loadInterpolator
        try {
            XposedHelpers.findAndHookMethod(
                    "android.view.animation.AnimationUtils", lpparam.classLoader,
                    "loadInterpolator", Context.class, int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            int id = (int) param.args[1];
                            switch (id) {
                                case android.R.interpolator.fast_out_slow_in:
                                case android.R.interpolator.fast_out_linear_in:
                                case android.R.interpolator.linear_out_slow_in:
                                case android.R.interpolator.accelerate_decelerate:
                                case android.R.interpolator.accelerate_cubic:
                                case android.R.interpolator.accelerate_quad:
                                case android.R.interpolator.accelerate_quint:
                                case android.R.interpolator.decelerate_cubic:
                                case android.R.interpolator.decelerate_quad:
                                case android.R.interpolator.decelerate_quint:
                                    param.setResult(ios(lpparam.packageName));
                                    break;
                                default:
                            }
                        }
                    });
        } catch (Throwable ignored) {
        }
    }

    private int replaceFields(String className, ClassLoader cl, Interpolator ios) {
        int n = 0;
        try {
            // force static init so defaults exist before we overwrite them
            Class<?> c = Class.forName(className, true, cl);
            for (Field f : c.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers())) continue;
                if (!TimeInterpolator.class.isAssignableFrom(f.getType())) continue;
                String name = f.getName().toUpperCase();
                if (name.equals("LINEAR") || name.contains("CYCLE") || name.contains("BOUNCE")
                        || name.contains("OVERSHOOT") || name.contains("ANTICIPATE")
                        || name.contains("SPRING") || name.contains("SCROLL")
                        || name.contains("PANEL_CLOSER") || name.contains("TOSS")) {
                    continue;
                }
                try {
                    f.setAccessible(true);
                    f.set(null, ios);
                    n++;
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
            // class not present in this process -- fine
        }
        return n;
    }
}
