package com.dimagi.android.zebraprinttool;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListAdapter;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.List;

/**
 * Created by ctsims on 7/22/2016.
 */
public class PrintJobListAdapter extends ArrayAdapter<ZebraPrintTask.PrintJob> {

    HashMap<Integer, WeakReference<View>> trackingTable = new HashMap<>();

    public PrintJobListAdapter(Context context, ZebraPrintTask.PrintJob[] jobs) {
        super(context, -1, jobs);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ZebraPrintTask.PrintJob job = this.getItem(position);
        if(convertView == null) {
            convertView = View.inflate(this.getContext(), R.layout.component_print_job, null);
            Integer tag = (Integer)convertView.getTag();
            if(trackingTable.containsKey(tag)){
                trackingTable.remove(tag);
            }
        }
        updateView(job, convertView);
        return convertView;
    }

    private void updateView(ZebraPrintTask.PrintJob job, View convertView) {
        ((TextView)convertView.findViewById(R.id.print_job_name)).setText(job.getDisplayName());
        ((TextView)convertView.findViewById(R.id.print_job_status)).setText(job.getStatus().toString());
        convertView.setTag(job.getJobId());
        trackingTable.put(job.getJobId(), new WeakReference<>(convertView));
    }
}
