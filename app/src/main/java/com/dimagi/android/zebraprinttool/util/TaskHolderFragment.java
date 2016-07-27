package com.dimagi.android.zebraprinttool.util;

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;

import com.dimagi.android.zebraprinttool.ZebraPrintTask;

/**
 * Created by ctsims on 7/25/2016.
 */
public class TaskHolderFragment extends Fragment implements PrintTaskListener {

    ZebraPrintTask printJob;

    boolean printerConnectionSpring = false;

    boolean taskFinished = false;
    boolean taskSuccesful = false;

    PrintTaskListener forwardingListener;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if(context instanceof PrintTaskListener) {
            this.forwardingListener = (PrintTaskListener)context;
            if(printJob != null) {
                forwardingListener.taskUpdate(printJob);
            }
            if(this.taskFinished) {
                forwardingListener.taskFinished(taskSuccesful);
            }
        }
    }

    public void setTask(ZebraPrintTask task) {
        this.printJob = task;
        task.attachListener(this);
    }

    @Override
    public void taskUpdate(ZebraPrintTask task) {
        if(forwardingListener == null) {
            //anything?
        } else {
            forwardingListener.taskUpdate(task);
        }
    }

    @Override
    public void taskFinished(boolean taskSuccesful) {
        taskFinished = true;
        this.taskSuccesful = false;
        if(forwardingListener != null) {
            forwardingListener.taskFinished(taskSuccesful);
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        forwardingListener = null;
    }

    public void cancelPrintTask() {
        printJob.cancel(false);
    }

    /**
     * Denotes that if printers are discovered, the app should prompt the user to connect to
     * one
     */
    public void setPrintConnectionSpringLoaded() {
        this.printerConnectionSpring = true;
    }

    public boolean firePrintConnectionSpringIfLoaded() {
        boolean fire = this.printerConnectionSpring;
        printerConnectionSpring = false;
        return fire;
    }


}
