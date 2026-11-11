package local.hicarlab;

import android.app.AlarmManager;
import android.app.Application;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import com.rockjesus.icarplay.IcarLauncherActivity;

/**
 * Last-resort containment for the experimental receiver.
 *
 * <p>This class never changes system partitions, system settings, default-home bindings or
 * device-admin state. It only records crashes inside the app, disables the app's own opt-in boot
 * receiver after a repeated crash, and asks the system to bring the ordinary launcher back.</p>
 */
public final class SafetyGuard {
    private static final String TAG = "iCarPlaySafety";
    private static final String PREFS = "safety";
    private static final long CRASH_WINDOW_MS = 5 * 60 * 1000L;
    private static final long HOME_DELAY_MS = 800L;
    private static final long PULSE_MS = 5 * 1000L;
    private static final long STALL_MS = 90 * 1000L;
    private static boolean installed;
    private static volatile long lastPulse;
    private static volatile boolean pulseRunning;

    private SafetyGuard() {}

    public static synchronized void install(Application app) {
        if (installed) return;
        installed = true;
        try {
            AutoStart.syncBootReceiver(app);
            if (safeMode(app)) {
                // A previous launch crashed before it could be used. Keep auto-start off until
                // the user clears safe mode explicitly.
                AutoStart.setEnabled(app, false);
                Log.w(TAG, "safe mode is active; boot receiver kept disabled");
            }
            final Thread.UncaughtExceptionHandler previous =
                    Thread.getDefaultUncaughtExceptionHandler();
            Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
                onCrash(app, error);
                if (previous != null) {
                    previous.uncaughtException(thread, error);
                } else {
                    android.os.Process.killProcess(android.os.Process.myPid());
                    System.exit(10);
                }
            });
            startMainThreadWatchdog(app);
        } catch (Throwable error) {
            Log.e(TAG, "guard install failed", error);
        }
    }

    private static void onCrash(Context context, Throwable error) {
        try {
            long now = SystemClock.elapsedRealtime();
            SharedPreferences prefs = context.getSharedPreferences(PREFS, 0);
            long last = prefs.getLong("last_crash_ms", 0);
            int count = prefs.getInt("crash_count", 0);
            if (now - last > CRASH_WINDOW_MS || now < last) count = 0;
            count++;
            prefs.edit()
                    .putLong("last_crash_ms", now)
                    .putInt("crash_count", count)
                    .putString("last_error", error.getClass().getName())
                    .apply();
            Log.e(TAG, "crash #" + count + " " + error.getClass().getName(), error);
            if (count >= 2) {
                prefs.edit().putBoolean("safe_mode", true).apply();
                AutoStart.setEnabled(context, false);
                Log.w(TAG, "repeated crash; safe mode on, boot receiver disabled");
            }
            scheduleHome(context);
        } catch (Throwable ignored) {
            // The crash handler must never crash itself.
        }
    }

    /**
     * Covers every projection service, not only HiCar: a blocked main thread for 90 s opens the
     * normal entry page, turns auto-start off and ends this app process so a background service
     * cannot keep the head unit occupied.
     */
    private static void startMainThreadWatchdog(Application app) {
        if (pulseRunning) return;
        pulseRunning = true;
        lastPulse = SystemClock.elapsedRealtime();
        Handler main = new Handler(Looper.getMainLooper());
        Runnable heartbeat = new Runnable() {
            @Override public void run() {
                lastPulse = SystemClock.elapsedRealtime();
                if (pulseRunning) main.postDelayed(this, PULSE_MS);
            }
        };
        main.post(heartbeat);
        Thread watchdog = new Thread(() -> {
            while (pulseRunning) {
                try { Thread.sleep(15000); } catch (InterruptedException e) { break; }
                if (!pulseRunning) break;
                long gap = SystemClock.elapsedRealtime() - lastPulse;
                if (gap > STALL_MS) {
                    pulseRunning = false;
                    Log.e(TAG, "main thread stalled " + gap + " ms; fail-safe");
                    try {
                        app.getSharedPreferences(PREFS, 0).edit()
                                .putBoolean("safe_mode", true)
                                .putString("last_error", "MainThreadStall")
                                .apply();
                        AutoStart.setEnabled(app, false);
                        scheduleHome(app);
                    } catch (Throwable ignored) {}
                    try { Thread.sleep(1500); } catch (InterruptedException ignored) {}
                    android.os.Process.killProcess(android.os.Process.myPid());
                    break;
                }
            }
        }, "icarplay-main-watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
    }

    private static void scheduleHome(Context context) {
        try {
            Intent home = new Intent(context, IcarLauncherActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_CLEAR_TOP
                            | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent pending = PendingIntent.getActivity(context, 100, home,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            AlarmManager alarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (alarm != null) {
                alarm.set(AlarmManager.RTC_WAKEUP,
                        System.currentTimeMillis() + HOME_DELAY_MS, pending);
            } else {
                context.startActivity(home);
            }
        } catch (Throwable ignored) {
            // If the vendor blocks background activity starts, the system home remains usable.
        }
    }

    /** Stop the experimental service and put the normal app entry back on screen. */
    public static void returnHome(Context context, String reason) {
        Log.w(TAG, "fail-safe: " + reason);
        try {
            context.stopService(new Intent(context, ReceiverService.class));
        } catch (Throwable ignored) {}
        scheduleHome(context);
    }

    public static boolean safeMode(Context context) {
        return context.getSharedPreferences(PREFS, 0).getBoolean("safe_mode", false);
    }

    public static void clearSafeMode(Context context) {
        context.getSharedPreferences(PREFS, 0).edit()
                .putBoolean("safe_mode", false)
                .putInt("crash_count", 0)
                .putLong("last_crash_ms", 0)
                .remove("last_error")
                .apply();
    }
}
