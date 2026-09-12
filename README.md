# iOS Animation Curves for Android (Xposed Module)

A lightweight, high-performance Xposed module that replaces default Android animation interpolators across **AOSP**, **SystemUI**, and **Launcher3** with authentic iOS fluid cubic-bezier curves (`easeOutQuint`, `easeInQuint`, and `easeInOut`).

---

## 🌟 Features

* **Authentic iOS Multi-Curve System**:
  * **`IOS_EASE_OUT` (`0.215, 0.61, 0.355, 1.0`)**: Quintic ease-out curve applied to entering elements and `DECELERATE` / `SLOW_IN` interpolators for smooth deceleration.
  * **`IOS_EASE_IN` (`0.55, 0.055, 0.675, 0.19`)**: Quintic ease-in curve applied to exiting elements and `ACCELERATE` interpolators for dynamic exit motions.
  * **`IOS_EASE_IN_OUT` (`0.40, 0.0, 0.20, 1.0`)**: Apple fluid S-curve applied to general motion paths and standard interpolators.
* **WM Shell Compatible**: Bounded strictly within $[0.0, 1.0]$ to prevent window clipping and flickering bugs on Android 12+ / 13+ RemoteAnimation Shell Transitions.
* **Safe ART Reflection**: Uses `XposedHelpers.setStaticObjectField` to override `static final` constants across modern Android ART runtimes.
* **XML Fallback**: Hooks `AnimationUtils.loadInterpolator` to override legacy XML-defined animations.
* **Zero Gradle Dependency**: Clean, lightweight shell script (`build.sh`) for rapid compilation using standalone Android SDK tools (`javac`, `d8`, `aapt2`, `apksigner`).

---

## 📖 Technical Architecture & Documentation

### 1. How It Works Under the Hood

Android Framework transitions read hardcoded static interpolator constants (such as `Interpolators.EMPHASIZED` or `Interpolators.FAST_OUT_SLOW_IN`) defined at class-load time in key holder classes:
- `com.android.app.animation.Interpolators` (Android 13+ shared framework)
- `com.android.systemui.animation.Interpolators` (SystemUI)
- `com.android.launcher3.anim.Interpolators` (Launcher3 / QuickStep)

Because these constants are declared as `public static final`, standard Java reflection `field.set(null, value)` fails on Android ART with an `IllegalAccessException`. This module leverages `XposedHelpers.setStaticObjectField(...)` to bypass ART field immutability and replace the static instances dynamically upon package load (`IXposedHookLoadPackage`).

### 2. Bezier Curve Mathematical Specifications

| Curve Name | Control Points $(P_1, P_2)$ | Target Interpolators | Motion Characteristics |
| :--- | :--- | :--- | :--- |
| **`IOS_EASE_OUT`** | `(0.22, 0.45, 0.25, 1.0)` | `DECELERATE`, `SLOW_IN`, `LEGACY_DECELERATE` | Fast initial acceleration with a fluid, continuous deceleration tail; zero pixel-jumping or framedrop artifacts. |
| **`IOS_EASE_IN`** | `(0.45, 0.00, 0.55, 0.3)` | `ACCELERATE`, `FAST_OUT_LINEAR_IN` | Fast dynamic exit without lingering on screen. |
| **`IOS_EASE_IN_OUT`** | `(0.30, 0.00, 0.15, 1.0)` | `STANDARD`, `FAST_OUT_SLOW_IN`, `TOUCH_RESPONSE` | Prompt, responsive S-curve for general motion paths and navigation gestures. |

> **Note on Shell Transition Compatibility**:
> On Android 12+ (WM Shell), window bounds and clip rects use linear interpolation (`lerp`). If $y > 1.0$ (overshoot), window clip rect calculations exceed physical screen dimensions, causing sharp window corners and wallpaper flickering. All curves in this module are strictly clamped within $y \in [0.0, 1.0]$ to ensure zero visual glitches.

### 3. XML Interpolator Fallback Hook

In addition to static field replacement, the module hooks `android.view.animation.AnimationUtils.loadInterpolator(Context, int)` to intercept XML-driven animation resources (`@android:interpolator/...`) and redirect them to the corresponding iOS curves.

---

## 📱 Compatibility & Requirements

* **Android Version**: Android 8.1 (API 27) – Android 13+ (API 33+)
* **Xposed Framework**: LSPosed / Vector / EdXposed
* **Target Package Scope**:
  * `android` (System Framework)
  * `com.android.systemui` (System UI)
  * `com.android.launcher3` (or OEM Launcher / Pixel Launcher)

---

## 🛠️ Project Structure

```text
.
├── AndroidManifest.xml          # Xposed module manifest & scope definition
├── api-82.jar                   # Xposed API v82 dependency
├── build.sh                     # Custom standalone build script
├── README.md                    # Project documentation
├── res/                         # Module resources (scope definition)
│   └── values/arrays.xml
└── src/
    └── com/tomoe/iosanim/
        └── Hook.java            # Core Xposed hook & interpolator logic
```

---

## 🚀 Building & Installing

### 1. Build APK from Source
Ensure Android SDK build-tools and `android.jar` are available on your system, then run:

```bash
./build.sh
```

This generates `ios-anim.apk` in the root project directory.

### 2. Install via ADB

```bash
adb install -r ios-anim.apk
```

### 3. Enable in LSPosed / Vector CLI

Using **LSPosed Manager** app or **Vector CLI**:

```bash
# Enable module
vector-cli modules enable com.tomoe.iosanim

# Set target scopes
vector-cli scope set com.tomoe.iosanim android/0 com.android.systemui/0 com.android.launcher3/0
```

### 4. Apply Changes
Reboot your device or restart Launcher3 to apply the new interpolator curves immediately:

```bash
adb shell am force-stop com.android.launcher3
```

---

## 🔍 Verification & Logs

You can verify that interpolators are being hooked by checking LSPosed / Vector logs or logcat:

```bash
# Check LSPosed / Vector module logs
adb shell su -c "grep -i 'iOSAnim' /data/adb/lspd/log/modules_*.log"
```

Expected output:
```text
[VectorLegacyBridge] iOSAnim: replaced 22 interpolator(s) in com.android.systemui
[VectorLegacyBridge] iOSAnim: replaced 54 interpolator(s) in com.android.launcher3
```

---

## 🔧 Customization Guide

To adjust animation curves or add new holder classes:

1. Open `src/com/tomoe/iosanim/Hook.java`.
2. Modify the `PathInterpolator` control points in `IOS_EASE_OUT`, `IOS_EASE_IN`, or `IOS_EASE_IN_OUT`.
3. Add any custom OEM launcher interpolator holder class name to the `HOLDERS` array.
4. Run `./build.sh` and reinstall.

---

## 📄 License

Open source under the [MIT License](LICENSE).
