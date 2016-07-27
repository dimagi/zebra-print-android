package com.dimagi.android.zebraprinttool;

import android.app.Activity;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import com.dimagi.android.zebraprinttool.util.PrintTaskListener;
import com.dimagi.android.zebraprinttool.util.TaskHolderFragment;

import java.util.ArrayList;

public class PrintReceiverActivity extends Activity implements BluetoothStateHolder.BluetoothStateListener, PrintTaskListener {

    private static int REQUEST_PRINTER = 1;

    private static String KEY_BUNDLE_LIST = "zebra:bundle_list";
    private static String FRAGMENT_PRINT_JOB = "print_fragment";

    ArrayList<String> bundleKeyList;
    ZebraPrintTask.PrintJob[] jobs;

    String taskUpdateMessage;

    BluetoothStateHolder bluetoothService;

    Button cancelPrint;
    Button updateSettings;

    TextView statusText;

    PrintJobListAdapter adapter;

    ListView listView;
    boolean listViewTouched = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_print_receiver);

        ensureFragmentConnected();

        cancelPrint = (Button)findViewById(R.id.button_cancel_print);
        updateSettings = (Button)findViewById(R.id.button_update_settings);

        cancelPrint.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                TaskHolderFragment printTaskFragment =
                        (TaskHolderFragment)getFragmentManager().findFragmentByTag(FRAGMENT_PRINT_JOB);
                if(printTaskFragment == null) {
                    v.setEnabled(false);
                    return;
                }

                printTaskFragment.cancelPrintTask();
                cancelPrint.setText("Cancelling...");
                cancelPrint.setEnabled(false);

                if(adapter != null) {
                    adapter.setModeCancelled();
                    adapter.notifyDataSetChanged();
                }
            }
        });

        updateSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                calloutToSelectPrinter();
            }
        });
        statusText = (TextView)findViewById(R.id.current_status_text);

        adapter = new PrintJobListAdapter(this, jobs);

        listView = (ListView)this.findViewById(R.id.list_print_jobs);
        listView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                listViewTouched = true;
                return false;
            }
        });
        listView.setAdapter(adapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        BluetoothStateHolder.attachContextListener(this, this);

        TaskHolderFragment printTaskFragment =
                (TaskHolderFragment)getFragmentManager().findFragmentByTag(FRAGMENT_PRINT_JOB);
        printTaskFragment.ensureTaskRunning(bluetoothService);
        updateStatusText();
    }

    @Override
    public void attachStateHolder(BluetoothStateHolder bluetoothStateHolder) {
        this.bluetoothService = bluetoothStateHolder;
    }

    private void ensureFragmentConnected() {
        FragmentManager fragmentManager = this.getFragmentManager();

        TaskHolderFragment printTaskFragment =
                (TaskHolderFragment)fragmentManager.findFragmentByTag(FRAGMENT_PRINT_JOB);

        if(printTaskFragment == null) {
            bundleKeyList = getIntent().getStringArrayListExtra(KEY_BUNDLE_LIST);
            if (bundleKeyList == null || bundleKeyList.size() == 0) {
                throw new RuntimeException("No print jobs provided to print activity!");
            }

            jobs = generatePrintJobs();

            printTaskFragment = new TaskHolderFragment();
            printTaskFragment.setJobs(jobs);

            FragmentTransaction transaction = fragmentManager.beginTransaction();
            transaction.add(printTaskFragment, FRAGMENT_PRINT_JOB);
            transaction.commit();
        } else {
            //the fragment will attach on its own, rely on that process to trigger a redraw
            this.jobs = printTaskFragment.getPrintJobs();
        }
    }

    private ZebraPrintTask.PrintJob[] generatePrintJobs() {
        Bundle[] printSets = new Bundle[bundleKeyList.size()];
        for(int i = 0 ; i < printSets.length; ++i) {
            printSets[i] = this.getIntent().getBundleExtra(bundleKeyList.get(i));
        }

        ZebraPrintTask.PrintJob[] jobs = new ZebraPrintTask.PrintJob[printSets.length];
        for(int i = 0; i < printSets.length ; ++i ){
            jobs[i] = new ZebraPrintTask.PrintJob(i, printSets[i]);
        }
        return jobs;
    }

    @Override
    protected void onPause() {
        super.onPause();
        bluetoothService.detachStateListener(this);

        if(this.isFinishing()) {
            TaskHolderFragment fragment = this.getTaskFragment();
            if(fragment != null) {
                fragment.signalKill();
            }
        }
    }

    private void calloutToSelectPrinter() {
        Intent i = new Intent(this, MainActivity.class);
        if(bluetoothService == null) {
            i.putExtra(MainActivity.RETURN_WHEN_SELECTED, true);
        } else {
            boolean haveActivePrinter = bluetoothService.getActivePrinter() != null;
            i.putExtra(MainActivity.RETURN_WHEN_SELECTED, !haveActivePrinter);
        }
        this.startActivityForResult(i, REQUEST_PRINTER);
    }


    private void finishAndExit() {
        //TODO: Job results
        this.setResult(Activity.RESULT_OK);
        this.finish();
    }

    private TaskHolderFragment getTaskFragment() {
        return (TaskHolderFragment)this.getFragmentManager().findFragmentByTag(FRAGMENT_PRINT_JOB);
    }


    @Override
    public void onBluetoothStateUpdate() {
        updateStatusText();

        TaskHolderFragment printTaskFragment = getTaskFragment();

        if(!bluetoothService.inDiscovery() &&
                bluetoothService.getDiscoveredPrinters().size() > 0 &&
                printTaskFragment != null &&
                printTaskFragment.firePrintConnectionSpringIfLoaded()) {
            this.calloutToSelectPrinter();
        }
    }

    private void updateStatusText() {
        String taskUpdateMessage = getTaskFragment().getCurrentTaskMessage();
        if(bluetoothService.getActivePrinter() != null) {
            if(taskUpdateMessage != null) {
                statusText.setText(taskUpdateMessage);
            } else {
                statusText.setText("Printer: Connected");
            }
        } else if(bluetoothService.inDiscovery()) {
            if(bluetoothService.getDefaultPrinterId() != null) {
                statusText.setText("Printer: Connecting...");
            } else {
                statusText.setText("Printer: Searching for bluetooth devices");
            }
        } else {
            String status = "";
            if(bluetoothService.getDefaultPrinterId() != null) {
                status += "Printer not found! ";
            }
            int devices = bluetoothService.getDiscoveredPrinters().size();
            if(devices > 0) {
                status += devices + " bluetooth devices found. ";
            } else{
                status += "No devices discovered. ";
            }
            if(bluetoothService.getDiscoveryErrorMessage() != null) {
                status += "\nError searching for devices:\n" + bluetoothService.getDiscoveryErrorMessage();
            }
            statusText.setText(status);
        }
    }

    @Override
    public void taskUpdate(ZebraPrintTask task) {
        if(adapter != null) {
            updateStatusText();
            adapter.notifyDataSetChanged();
            this.cancelPrint.setEnabled(true);

            if(!listViewTouched) {
                listView.smoothScrollToPosition(task.getCurrentJobNumber());
            }
        }
    }

    @Override
    public void taskFinished(final boolean taskSuccesful) {
        cancelPrint.setEnabled(true);
        int succesfulJobs = getNumberOfSuccesfulJobs();
        if(taskSuccesful) {
            if(succesfulJobs == jobs.length) {
                finishAndExit();
            } else {
                cancelPrint.setText("Return");
                if(adapter != null) {
                    adapter.notifyDataSetChanged();
                }
            }
        } else {
            if(succesfulJobs == 0) {
                finishAndExit();
            } else {
                cancelPrint.setText("Return");
                if(adapter != null) {
                    adapter.notifyDataSetChanged();
                }
            }
        }
        cancelPrint.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finishAndExit();
            }
        });
    }

    private int getNumberOfSuccesfulJobs() {
        int numSuccesful = 0;
        for(ZebraPrintTask.PrintJob job : jobs) {
            if(job.getStatus() == ZebraPrintTask.PrintJob.Status.SUCCESS) {
                numSuccesful++;
            }
        }
        return numSuccesful;
    }
}
