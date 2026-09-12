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
 * Authentic iOS Fluid Animation System.
 * Applies dedicated iOS curves (easeOut, easeIn, easeInOut) to matching interpolator types
 * to ensure 100% natural, smooth fluid motion without choppiness or lag.
 */
public class Hook implements IXposedHookLoadPackage {

    private static final String TAG = "iOSAnim";

    // Authentic iOS Bezier Curves:
    // 1) iOS easeOutQuint (For decelerating/entering elements): (0.215, 0.61, 0.355, 1.0)
    private static final Interpolator IOS_EASE_OUT = new PathInterpolator(0.215f, 0.61f, 0.355f, 1.0f);

    // 2) iOS easeInQuint (For accelerating/exiting elements): (0.55, 0.055, 0.675, 0.19)
    private static final Interpolator IOS_EASE_IN = new PathInterpolator(0.55f, 0.055f, 0.675f, 0.19f);

    // 3) iOS easeInOut (For general fluid motion & standard paths): (0.40, 0.0, 0.20, 1.0)
    private static final Interpolator IOS_EASE_IN_OUT = new PathInterpolator(0.40f, 0.0f, 0.20f, 1.0f);

    // interpolator-holder classes across AOSP/SystemUI versions
    private static final String[] HOLDERS = {
            "com.android.app.animation.Interpolators",       // A13+ shared
            "com.android.systemui.animation.Interpolators",  // older SystemUI
            "com.android.launcher3.anim.Interpolators",      // launcher3 legacy
    };

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) {
        int total = 0;
        for (String holder : HOLDERS) {
            total += replaceFields(holder, lpparam.classLoader);
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
                                case android.R.interpolator.decelerate_cubic:
                                case android.R.interpolator.decelerate_quad:
                                case android.R.interpolator.decelerate_quint:
                                case android.R.interpolator.linear_out_slow_in:
                                    param.setResult(IOS_EASE_OUT);
                                    break;
                                case android.R.interpolator.accelerate_cubic:
                                case android.R.interpolator.accelerate_quad:
                                case android.R.interpolator.accelerate_quint:
                                case android.R.interpolator.fast_out_linear_in:
                                    param.setResult(IOS_EASE_IN);
                                    break;
                                case android.R.interpolator.fast_out_slow_in:
                                case android.R.interpolator.accelerate_decelerate:
                                    param.setResult(IOS_EASE_IN_OUT);
                                    break;
                                default:
                            }
                        }
                    });
        } catch (Throwable ignored) {
        }
    }

    private int replaceFields(String className, ClassLoader cl) {
        int n = 0;
        try {
            Class<?> c = Class.forName(className, true, cl);
            for (Field f : c.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers())) continue;
                if (!TimeInterpolator.class.isAssignableFrom(f.getType())) continue;
                String name = f.getName().toUpperCase();
                
                // Skip special non-linear / physics fields
                if (name.equals("LINEAR") || name.contains("CYCLE") || name.contains("BOUNCE")
                        || name.contains("OVERSHOOT") || name.contains("ANTICIPATE")
                        || name.contains("SPRING") || name.contains("SCROLL")
                        || name.contains("PANEL_CLOSER") || name.contains("TOSS")) {
                    continue;
                }

                // Match interpolator type and assign dedicated iOS curve
                Interpolator targetCurve;
                if (name.contains("ACCELERATE") && !name.contains("DECELERATE")) {
                    targetCurve = IOS_EASE_IN;
                } else if (name.contains("DECELERATE") || name.contains("SLOW_IN")) {
                    targetCurve = IOS_EASE_OUT;
                } else {
                    targetCurve = IOS_EASE_IN_OUT;
                }

                try {
                    XposedHelpers.setStaticObjectField(c, f.getName(), targetCurve);
                    n++;
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        return n;
    }
}
