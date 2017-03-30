package me.nichel.simonsaysapp;

import android.app.Application;

import timber.log.Timber;

/**
 * Created by nichel on 28/03/2017.
 */

public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        Timber.plant(new Timber.DebugTree());
    }
}
