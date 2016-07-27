package com.dimagi.android.zebraprinttool.util;

import com.dimagi.android.zebraprinttool.ZebraPrintTask;

/**
 * Created by ctsims on 7/25/2016.
 */
public interface PrintTaskListener {
    void taskUpdate(ZebraPrintTask task);

    void taskFinished(boolean taskSuccesful);
}
