package com.dimagi.android.zebraprinttool;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.zebra.sdk.comm.ConnectionException;
import com.zebra.sdk.printer.discovery.BluetoothDiscoverer;
import com.zebra.sdk.printer.discovery.DiscoveredPrinter;
import com.zebra.sdk.printer.discovery.DiscoveredPrinterBluetooth;
import com.zebra.sdk.printer.discovery.DiscoveryHandler;

import java.util.ArrayList;

public class MainActivity extends Activity {

    public static final String RETURN_WHEN_SELECTED = "return_on_select";
    ArrayList<DiscoveredPrinterBluetooth> printers = new ArrayList<>();

    boolean bluetoothDiscoveryComplete = true;

    private final static String ACTIVE_PRINTER = "key_active_printer_address";

    public static DiscoveredPrinterBluetooth activePrinter = null;
    private boolean returnWhenSelected;

    private String discoveryErrorMessage = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        returnWhenSelected = this.getIntent().getBooleanExtra(RETURN_WHEN_SELECTED, false);

        this.findViewById(R.id.discover_printers).
                setOnClickListener(new View.OnClickListener() {
                                       @Override
                                       public void onClick(View v) {
                                           startBluetoothDiscovery(true);
                                           updateCurrentPrinterView();

                                       }
                                   }

                );

        this.findViewById(R.id.view_printers)
                .setOnClickListener(new View.OnClickListener() {
                                        @Override
                                        public void onClick(View v) {
                                            launchPrinterSelect(printers);
                                        }
                                    }

                );

        ((CheckBox)this.findViewById(R.id.cbx_view_details)).setOnCheckedChangeListener(
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        findViewById(R.id.connect_advanced_pane).setVisibility(isChecked ? View.VISIBLE : View.INVISIBLE);
                    }
                }
        );

        this.findViewById(R.id.connect_btn_scan_again).
                setOnClickListener(new View.OnClickListener() {
                                       @Override
                                       public void onClick(View v) {
                                           startBluetoothDiscovery(true);
                                           updateCurrentPrinterView();
                                       }
                                   }

                );

    }

    protected void onResume() {
        super.onResume();
        if(activePrinter == null && printers.isEmpty()) {
            startBluetoothDiscovery(returnWhenSelected);
        }
        updateCurrentPrinterView();
    }

    private void startBluetoothDiscovery(final boolean showDialog) {
        if(bluetoothDiscoveryComplete) {
            System.out.println("Discovery Started");
            final String expectedActivePrinterId = getDefaultPrinterId();
            discoveryErrorMessage = null;
            try {
                discoverButtonsDisabled();
                updateCurrentPrinterView();
                bluetoothDiscoveryComplete = false;

                printers.clear();
                BluetoothDiscoverer.findPrinters(this, new DiscoveryHandler() {

                    public void foundPrinter(final DiscoveredPrinter printer) {
                        DiscoveredPrinterBluetooth bluetoothPrinter = (DiscoveredPrinterBluetooth)printer;
                        System.out.println("Found Printer: " + printer.address + " | " + bluetoothPrinter.friendlyName);

                        if(activePrinter == null && bluetoothPrinter.address.equals(expectedActivePrinterId)) {
                            //Only do this if the activePrinterId hasn't been cleared
                            if(getDefaultPrinterId().equals(expectedActivePrinterId)) {
                                System.out.println("reconnected default printer");
                                MainActivity.this.setActivePrinter(bluetoothPrinter);
                            }
                        }
                        printers.add(bluetoothPrinter);
                    }

                    public void discoveryFinished() {
                        System.out.println("Discovery Finished");
                        endDiscovery();
                        if(showDialog && activePrinter == null && printers.size() > 0) {
                            launchPrinterSelect(printers);
                        }
                    }

                    public void discoveryError(String message) {
                        System.out.println("Discovery Error: " + message);
                        discoveryErrorMessage = message;
                        endDiscovery();
                    }
                });
            } catch (ConnectionException e) {
                e.printStackTrace();
                endDiscovery();
            }
        }
    }

    private void discoverButtonsDisabled() {
        this.findViewById(R.id.discover_printers).setEnabled(false);
        this.findViewById(R.id.connect_btn_scan_again).setVisibility(View.GONE);
    }

    private void updateCurrentPrinterView() {
        if(isFinishing()) { return; }

        ProgressBar progressBar = (ProgressBar)this.findViewById(R.id.connect_progress_bar);

        TextView advancedTextView = (TextView)this.findViewById(R.id.active_printer);
        TextView humanReadable = (TextView)this.findViewById(R.id.connect_human_text);

        Button scanAgain = (Button)this.findViewById(R.id.connect_btn_scan_again);
        Button discover = (Button)this.findViewById(R.id.discover_printers);

        String advancedText = "";

        if(activePrinter != null) {
            advancedText = "Connected Printer: " + activePrinter.friendlyName;
            humanReadable.setText("Printer connected! Starting print...");
            progressBar.setVisibility(View.GONE);
        }
        else if(bluetoothDiscoveryComplete) {
            if(printers.size() == 0) {
                advancedText = "No printers found!.";
                humanReadable.setText("No printers were found!");
            } else {
                advancedText = "Discovery finished, " + printers.size() + " printers found";
                humanReadable.setText("Choose a printer from the list");
            }
        } else if(getDefaultPrinterId() != null) {
            advancedText = "Searching for default printer: " + getDefaultPrinterId();
            humanReadable.setText("Reconnecting to printer...");
        } else {
            advancedText = "Searching for bluetooth printers...";
            humanReadable.setText("Searching for printers around you...");
        }

        if(discoveryErrorMessage != null) {
            advancedText += "\n\n" + "Error during discovery: " +"\n" + discoveryErrorMessage;
        }

        advancedTextView.setText(advancedText);


        if(bluetoothDiscoveryComplete) {
            progressBar.setVisibility(View.GONE);
            scanAgain.setVisibility(View.VISIBLE);
            discover.setEnabled(true);
        } else {
            progressBar.setVisibility(View.VISIBLE);
            scanAgain.setVisibility(View.GONE);
            discover.setEnabled(false);
        }
    }

    private void endDiscovery() {
        bluetoothDiscoveryComplete = true;
        if(isFinishing()) { return; }
        updateCurrentPrinterView();
    }

    private void launchPrinterSelect(final ArrayList<DiscoveredPrinterBluetooth> printers) {
        if(isFinishing()) { return; }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setAdapter(new ArrayAdapter<DiscoveredPrinterBluetooth>(this, -1, printers) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                DiscoveredPrinterBluetooth printer = this.getItem(position);
                if (convertView == null) {
                    convertView = View.inflate(MainActivity.this, R.layout.component_printer_display, null);
                }

                ((TextView)convertView.findViewById(R.id.printer_name)).setText(printer.friendlyName);
                ((TextView)convertView.findViewById(R.id.printer_address)).setText(printer.address);
                return convertView;
            }
        }, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                setActivePrinter(printers.get(which));
            }
        });
        builder.setTitle("Choose Printer");
        builder.show();
    }

    public void setActivePrinter(DiscoveredPrinterBluetooth activePrinter) {
        this.activePrinter = activePrinter;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.edit().putString(ACTIVE_PRINTER, activePrinter.address).commit();
        updateCurrentPrinterView();
        if(returnWhenSelected) {
            this.finish();
        }
    }

    public String getDefaultPrinterId() {
        return PreferenceManager.getDefaultSharedPreferences(this).getString(ACTIVE_PRINTER, null);
    }

    public void clearDefaultPrinterId() {
        PreferenceManager.getDefaultSharedPreferences(this).edit().remove(ACTIVE_PRINTER).commit();
        updateCurrentPrinterView();
    }

    /**
     * Signal that the printer being used currently is not available or is having connection
     * issues that need to be resolved before printing can proceed.
     */
    public static void SignalActivePrinterUnavailable() {
        //For now just clear the active printer and let it re-select on discovery
        activePrinter = null;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_activity, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        if(item.getItemId() == R.id.clear_active_printer) {
            activePrinter = null;
            clearDefaultPrinterId();
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }
}
