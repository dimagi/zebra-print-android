package com.dimagi.android.zebraprinttool;

import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;

import com.dimagi.android.zebraprinttool.util.IoUtil;
import com.dimagi.android.zebraprinttool.util.PrintTaskListener;
import com.zebra.sdk.comm.Connection;
import com.zebra.sdk.comm.ConnectionException;
import com.zebra.sdk.printer.FieldDescriptionData;
import com.zebra.sdk.printer.PrinterStatus;
import com.zebra.sdk.printer.ZebraPrinter;
import com.zebra.sdk.printer.ZebraPrinterFactory;
import com.zebra.sdk.printer.ZebraPrinterLanguageUnknownException;
import com.zebra.sdk.printer.discovery.DiscoveredPrinterBluetooth;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by ctsims on 7/22/2016.
 */
public class ZebraPrintTask extends AsyncTask<ZebraPrintTask.PrintJob,String,Boolean>{
    private static final String TAG = ZebraPrintTask.class.getName();

    private PrintJob[] jobs;
    private int currentJob;

    private static String KEY_TEMPLATE_PATH = "zebra:template_file_path";

    private boolean isWaitingForPrinter = false;

    BluetoothStateHolder bluetoothService;

    public ZebraPrintTask(BluetoothStateHolder bluetoothService) {
        this.bluetoothService = bluetoothService;
    }

    @Override
    protected void onCancelled() {
        super.onCancelled();
        listener.taskFinished(false);
    }

    @Override
    protected void onPostExecute(Boolean aBoolean) {
        super.onPostExecute(aBoolean);
        boolean allSuccess = true;
        for(PrintJob job : jobs) {
            if (job.getStatus() != PrintJob.Status.SUCCESS) {
                allSuccess = false;
                return;
            }
        }
        listener.taskFinished(allSuccess);
    }

    @Override
    protected void onProgressUpdate(String... values) {
        super.onProgressUpdate(values);
        listener.taskUpdate(this);
    }

    @Override
    protected Boolean doInBackground(PrintJob... printJobs) {
        this.jobs = printJobs;

        currentJob = 0;

        while(currentJob < jobs.length) {
            if(this.isCancelled()) {
                return false;
            }

            PrintJob current = jobs[currentJob];
            if(current.getStatus() == PrintJob.Status.ERROR) {
                currentJob++;
                continue;
            }

            if(!isPrinterAvailable() || isWaitingForPrinter) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {

                }
                if(!isPrinterAvailable()) {
                    continue;
                }
            }

            try {
                advanceJob(current);
                this.publishProgress(null);
                if(current.getStatus() == PrintJob.Status.SUCCESS) {
                    currentJob++;
                }
            }catch(ConnectionException connectionIssue) {
                connectionIssue.printStackTrace();
                signalPrinterUnavailable();
            } catch(Exception e) {
                current.setJobToError(e.getMessage());
                this.publishProgress(null);
                currentJob++;
            }
        }
        return true;
    }

    private void signalPrinterUnavailable() {
        bluetoothService.SignalActivePrinterUnavailable();
    }

    private boolean isPrinterAvailable() {
        return bluetoothService.getActivePrinter() != null;
    }

    private void advanceJob(PrintJob job) throws ConnectionException {
        switch(job.getStatus()) {
            case PENDING:
                job.prepare();
                this.publishProgress(null);
            case PRINTING:
                attemptJobPrint(job);
                return;
        }
    }

    private void attemptJobPrint(PrintJob job) throws ConnectionException {
        Connection connection = getCurrentConnection();
        try {
            connection.open();
            ZebraPrinter activePrinter = ZebraPrinterFactory.getInstance(connection);

            PrinterStatus status = activePrinter.getCurrentStatus();

            if(!status.isReadyToPrint) {
                isWaitingForPrinter = true;
                connection.close();
                return;
            } else {
                isWaitingForPrinter = false;
            }

            FieldDescriptionData[] templateVariableDescriptors=
                    populateTemplateVariableDescriptors(activePrinter, job.getPrintData());

            Map<Integer, String> templateVariables =
                    loadVariablesFromBundle(job.getJobParameters(), templateVariableDescriptors);

            Log.v(TAG, "Starting Print");
            printTemplate(connection, activePrinter, job.getPrintData(), job.getTemplateTitle(), templateVariables);
            Log.v(TAG, "Print submitted");
            connection.close();
            Log.v(TAG, "Connection Closed");

            job.setPrintSuccessful();
        } catch (ZebraPrinterLanguageUnknownException e) {
            throw wrap("Unrecognized language for template at: " + job.getPrintFilename(), e);
        } catch (UnsupportedEncodingException e) {
            throw wrap("Unsupported encoding for template file at: " + job.getPrintFilename(), e);
        } finally {
            if(connection.isConnected()) {
                connection.close();
            }
        }
    }

    private Connection getCurrentConnection() throws ConnectionException {
        DiscoveredPrinterBluetooth activePrinter = bluetoothService.getActivePrinter();
        if(activePrinter == null) {
            throw new ConnectionException("No Active Printer");
        }
        return activePrinter.getConnection();
    }

    private void printTemplate(Connection connection, ZebraPrinter activePrinter,
                               String zplFileData,
                               String zplTemplateTitle,
                               Map<Integer, String> templateVariables)
            throws UnsupportedEncodingException, ConnectionException {

        //First, ensure that the template is populated on the device
        connection.write(zplFileData.getBytes("UTF-8"));

        activePrinter.printStoredFormat(zplTemplateTitle, templateVariables);
    }


    private static RuntimeException wrap(String message, Exception e) {
        e.printStackTrace();

        RuntimeException r = new RuntimeException(message + " | " + e.getMessage());
        r.initCause(e);
        return r;
    }

    private Map<Integer, String> loadVariablesFromBundle(Bundle bundle, FieldDescriptionData[] templateVariableDescriptors) {
        Map<Integer, String> templateVariables = new HashMap<>();
        String unFilledVariables = "";
        for(FieldDescriptionData variableDescriptor : templateVariableDescriptors) {
            Integer destination = variableDescriptor.fieldNumber;
            String key = variableDescriptor.fieldName;

            String value = bundle.getString(key);

            if(value != null) {
                templateVariables.put(destination, value);
            } else {
                unFilledVariables += key + ", ";
            }
        }
        if(!"".equals(unFilledVariables)) {
            Log.w(TAG, "Some Varibles not provided by intent! Provided ");
        }
        return templateVariables;
    }

    private FieldDescriptionData[] populateTemplateVariableDescriptors(ZebraPrinter activePrinter, String zplFileData) {
        return activePrinter.getVariableFields(zplFileData);
    }

    private static String populateZplFileTemplateName(String zplFilePath, String zplFileData) {
        //Try to extract the template name from the file directly
        String zplTemplateTitle = attemptToExtractFormatTitle(zplFileData);
        //No guarantee this works...
        if(zplTemplateTitle == null) {
            //Hope the file is the same (this is what Zebra's app does...)
            zplTemplateTitle = IoUtil.extractFilename(zplFilePath);
        }
        return zplTemplateTitle;
    }

    public static String attemptToExtractFormatTitle(String zplData) {
        Pattern p = Pattern.compile("\\^DFE:(.*)\\^FS");
        Matcher m = p.matcher(zplData);
        if(m.find()) {
            return m.group(1);
        }
        return null;
    }

    private static String populateZplFileData(String zplFilePath) {
        try {
            return IoUtil.fileToString(zplFilePath);
        } catch (IOException e) {
            throw wrap("Error loading zebra template at: " + zplFilePath, e);
        }
    }

    PrintTaskListener listener;

    public void attachListener(PrintTaskListener listener) {
        this.listener = listener;
    }

    public void detachListener(PrintTaskListener listener) {
        this.listener = listener;
    }

    public boolean isWaiting() {
        return isWaitingForPrinter;
    }

    public static class PrintJob {


        public enum Status {
            PENDING,
            PRINTING,
            SUCCESS,
            ERROR
        }

        String zplFileName;
        String zplFileData;
        String zplTemplateTitle;

        int id;

        private Status status;
        private Bundle parameters;

        private String errorMessage;

        public PrintJob(int id, Bundle parameters) {
            this.id = id;
            this.parameters = parameters;
            this.status = Status.PENDING;
        }
        public int getJobId() {
            return id;
        }

        public String getDisplayName() {
            return "Print Job: " + id;
        }

        public Status getStatus() {
            return status;
        }

        public void setJobToError(String erroMessage) {
            this.status = Status.ERROR;
            this.errorMessage = erroMessage;
        }

        public Bundle getJobParameters() {
            return parameters;
        }

        public void prepare() {
            zplFileName = parameters.getString(KEY_TEMPLATE_PATH);
            zplFileData = populateZplFileData(zplFileName);
            zplTemplateTitle = populateZplFileTemplateName(zplFileName, zplFileData);

            this.status = Status.PRINTING;
        }
        public void setPrintSuccessful() {
            this.status = Status.SUCCESS;
        }

        public String getPrintData() {
            return zplFileData;
        }

        public String getPrintFilename() {
            return zplFileName;
        }

        public String getTemplateTitle() {
            return zplTemplateTitle;
        }

        /**
         * @return True if this job's status will not change further. False if the job status may
         * still change.
         */
        public boolean isFinished() {
            switch(this.getStatus()) {
                case PENDING:
                    return false;
                case PRINTING:
                    return false;
                case SUCCESS:
                    return true;
                case ERROR:
                    return true;
            }
            return true;
        }
    }

    public interface ZebraTaskPrintListener {
        public void notifyConnectionFailure();

    }
}
