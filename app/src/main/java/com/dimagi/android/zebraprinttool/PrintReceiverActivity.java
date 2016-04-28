package com.dimagi.android.zebraprinttool;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import com.dimagi.android.zebraprinttool.util.IoUtil;
import com.zebra.sdk.comm.Connection;
import com.zebra.sdk.comm.ConnectionException;
import com.zebra.sdk.printer.FieldDescriptionData;
import com.zebra.sdk.printer.ZebraPrinter;
import com.zebra.sdk.printer.ZebraPrinterFactory;
import com.zebra.sdk.printer.ZebraPrinterLanguageUnknownException;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PrintReceiverActivity extends Activity {

    private static int REQUEST_PRINTER = 1;

    private static String KEY_TEMPLATE_PATH = "zebra:template_file_path";

    private String zplFilePath;
    private String zplFileData;
    private String zplTemplateTitle;

    private Map<Integer, String> templateVariables;

    FieldDescriptionData[] templateVariableDescriptors;

    boolean printHasSucceeded = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_print_receiver);

        zplFilePath = getIntent().getStringExtra(KEY_TEMPLATE_PATH);
        if (zplFilePath == null) {
            throw new RuntimeException("No template path in print request!");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (MainActivity.activePrinter == null) {
            calloutToSelectPrinter();
            return;
        }
        else if(printHasSucceeded) {
            finishAndExit();
            return;
        } else {
            triggerPrint();
            return;
        }
    }

    private void calloutToSelectPrinter() {
        Intent i = new Intent(this, MainActivity.class);
        i.putExtra(MainActivity.RETURN_WHEN_SELECTED, true);
        this.startActivityForResult(i, REQUEST_PRINTER);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        // If we didn't get a printer from the result path, we should assume the user wasn't
        // able to identify one and we need to bail before the external intent gets triggered
        // again
        if(MainActivity.activePrinter == null) {
            this.setResult(RESULT_CANCELED);
            this.finish();
            return;
        }
    }


    public void triggerPrint() {
        populateZplFileData();
        populateZplFileTemplateName();

        try {
            Connection connection = MainActivity.activePrinter.getConnection();
            connection.open();
            ZebraPrinter activePrinter = ZebraPrinterFactory.getInstance(connection);


            populateTemplateVariableDescriptors(activePrinter);

            loadVariablesFromIntent();

            printTemplate(connection, activePrinter);

            connection.close();

            printHasSucceeded = true;

            finishAndExit();
        } catch (ConnectionException e) {
            MainActivity.SignalActivePrinterUnavailable();
            //If the connection broke in the middle, we need to re-evaluate the connected printer
            calloutToSelectPrinter();
            return;
        } catch (ZebraPrinterLanguageUnknownException e) {
            throw new RuntimeException(e);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    private void finishAndExit() {
        this.setResult(Activity.RESULT_OK);
        this.finish();
    }

    private void printTemplate(Connection connection, ZebraPrinter activePrinter) throws UnsupportedEncodingException, ConnectionException {
        //First, ensure that the template is populated on the device
        connection.write(zplFileData.getBytes("UTF-8"));

        activePrinter.printStoredFormat(zplTemplateTitle, templateVariables);
    }

    private void loadVariablesFromIntent() {
        Bundle intentData = getIntent().getExtras();
        templateVariables = new HashMap<>();
        for(FieldDescriptionData variableDescriptor : templateVariableDescriptors) {
            Integer destination = variableDescriptor.fieldNumber;
            String key = variableDescriptor.fieldName;

            String value = intentData.getString(key);
            if(value != null) {
                templateVariables.put(destination, value);
            }
        }
    }

    private void populateZplFileTemplateName() {
        //Try to extract the template name from the file directly
        zplTemplateTitle = attemptToExtractFormatTitle(zplFileData);
        //No guarantee this works...
        if(zplTemplateTitle == null) {
            //Hope the file is the same (this is what Zebra's app does...)
            zplTemplateTitle = IoUtil.extractFilename(zplFilePath);
        }
    }

    public static String attemptToExtractFormatTitle(String zplData) {
        Pattern p = Pattern.compile("\\^DFE:(.*)\\^FS");
        Matcher m = p.matcher(zplData);
        if(m.find()) {
            return m.group(1);
        }
        return null;
    }

    private void populateZplFileData() {
        try {
            zplFileData = IoUtil.fileToString(zplFilePath);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void populateTemplateVariableDescriptors(ZebraPrinter activePrinter) {
        templateVariableDescriptors = activePrinter.getVariableFields(zplFileData);
    }
}
