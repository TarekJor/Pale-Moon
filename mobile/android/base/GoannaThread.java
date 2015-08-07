/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.goanna;

import org.mozilla.goanna.mozglue.GoannaLoader;
import org.mozilla.goanna.util.GoannaEventListener;

import org.json.JSONObject;

import android.content.Intent;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.SystemClock;
import android.util.Log;
import android.app.Activity;


import java.util.Locale;

public class GoannaThread extends Thread implements GoannaEventListener {
    private static final String LOGTAG = "GoannaThread";

    public enum LaunchState {
        Launching,
        WaitForDebugger,
        Launched,
        GoannaRunning,
        GoannaExiting
    };

    private static LaunchState sLaunchState = LaunchState.Launching;

    private Intent mIntent;
    private final String mUri;

    GoannaThread(Intent intent, String uri) {
        mIntent = intent;
        mUri = uri;
        setName("Goanna");
        GoannaAppShell.getEventDispatcher().registerEventListener("Goanna:Ready", this);
    }

    private String initGoannaEnvironment() {
        // At some point while loading the goanna libs our default locale gets set
        // so just save it to locale here and reset it as default after the join
        Locale locale = Locale.getDefault();

        if (locale.toString().equalsIgnoreCase("zh_hk")) {
            locale = Locale.TRADITIONAL_CHINESE;
            Locale.setDefault(locale);
        }

        Context app = GoannaAppShell.getContext();
        String resourcePath = "";
        Resources res  = null;
        String[] pluginDirs = null;
        try {
            pluginDirs = GoannaAppShell.getPluginDirectories();
        } catch (Exception e) {
            Log.w(LOGTAG, "Caught exception getting plugin dirs.", e);
        }
        
        if (app instanceof Activity) {
            Activity activity = (Activity)app;
            resourcePath = activity.getApplication().getPackageResourcePath();
            res = activity.getBaseContext().getResources();
            GoannaLoader.setupGoannaEnvironment(activity, pluginDirs, GoannaProfile.get(app).getFilesDir().getPath());
        }
        GoannaLoader.loadSQLiteLibs(app, resourcePath);
        GoannaLoader.loadNSSLibs(app, resourcePath);
        GoannaLoader.loadGoannaLibs(app, resourcePath);

        Locale.setDefault(locale);

        Configuration config = res.getConfiguration();
        config.locale = locale;
        res.updateConfiguration(config, res.getDisplayMetrics());

        return resourcePath;
    }

    private String getTypeFromAction(String action) {
        if (action != null && action.startsWith(GoannaApp.ACTION_WEBAPP_PREFIX)) {
            return "-webapp";
        }
        if (GoannaApp.ACTION_BOOKMARK.equals(action)) {
            return "-bookmark";
        }
        return null;
    }

    private String addCustomProfileArg(String args) {
        String profile = GoannaAppShell.getGoannaInterface() == null || GoannaApp.sIsUsingCustomProfile ? "" : (" -P " + GoannaAppShell.getGoannaInterface().getProfile().getName());
        return (args != null ? args : "") + profile;
    }

    @Override
    public void run() {
        String path = initGoannaEnvironment();

        Log.w(LOGTAG, "zerdatime " + SystemClock.uptimeMillis() + " - runGoanna");

        String args = addCustomProfileArg(mIntent.getStringExtra("args"));
        String type = getTypeFromAction(mIntent.getAction());
        mIntent = null;

        // and then fire us up
        Log.i(LOGTAG, "RunGoanna - args = " + args);
        GoannaAppShell.runGoanna(path, args, mUri, type);
    }

    @Override
    public void handleMessage(String event, JSONObject message) {
        if ("Goanna:Ready".equals(event)) {
            GoannaAppShell.getEventDispatcher().unregisterEventListener(event, this);
            setLaunchState(LaunchState.GoannaRunning);
            GoannaAppShell.sendPendingEventsToGoanna();
        }
    }

    public static boolean checkLaunchState(LaunchState checkState) {
        synchronized (sLaunchState) {
            return sLaunchState == checkState;
        }
    }

    static void setLaunchState(LaunchState setState) {
        synchronized (sLaunchState) {
            sLaunchState = setState;
        }
    }

    /**
     * Set the launch state to <code>setState</code> and return true if the current launch
     * state is <code>checkState</code>; otherwise do nothing and return false.
     */
    static boolean checkAndSetLaunchState(LaunchState checkState, LaunchState setState) {
        synchronized (sLaunchState) {
            if (sLaunchState != checkState)
                return false;
            sLaunchState = setState;
            return true;
        }
    }
}