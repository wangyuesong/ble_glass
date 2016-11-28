package com.microchip.rn4020die;

import android.os.Handler;
import android.os.Looper;


/**
 * Created by yuesongwang on 11/27/16.
 */

public class UIAdapter {
    private Handler mHandler = new Handler(Looper.getMainLooper());

    private Runnable mStatusChecker;
    private int UPDATE_INTERVAL = 2000;

    public UIAdapter(final Runnable uiUpdater){
        mStatusChecker = new Runnable() {
            @Override
            public void run() {
                uiUpdater.run();
                mHandler.postDelayed(this, UPDATE_INTERVAL);
            }
        };
    }

    public synchronized void startUpdates(){
        mStatusChecker.run();
    }

    /**
     * Stops the periodical update routine from running,
     * by removing the callback.
     */
    public synchronized void stopUpdates(){
        mHandler.removeCallbacks(mStatusChecker);
    }
}
