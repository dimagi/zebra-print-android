package com.dimagi.android.zebraprinttool;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.zebra.sdk.comm.ConnectionException;
import com.zebra.sdk.printer.discovery.BluetoothDiscoverer;
import com.zebra.sdk.printer.discovery.DiscoveredPrinter;
import com.zebra.sdk.printer.discovery.DiscoveredPrinterBluetooth;
import com.zebra.sdk.printer.discovery.DiscoveryHandler;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

/**
 * Created by ctsims on 7/22/2016.
 */
public class BluetoothStateHolder {
    ArrayList<DiscoveredPrinterBluetooth> discoveredPrinters = new ArrayList<>();

    boolean bluetoothDiscoveryComplete = true;

    private final static String ACTIVE_PRINTER = "key_active_printer_address";

    public DiscoveredPrinterBluetooth activePrinter = null;

    private String discoveryErrorMessage = null;

    private Context context;

    public BluetoothStateHolder(Context context) {
        this.context = context.getApplicationContext();
    }

    public DiscoveredPrinterBluetooth getActivePrinter() {
        return activePrinter;
    }

    public void startBluetoothDiscovery() {
        if(bluetoothDiscoveryComplete) {
            System.out.println("Discovery Started");
            final String expectedActivePrinterId = getDefaultPrinterId();
            discoveryErrorMessage = null;
            try {
                bluetoothDiscoveryComplete = false;
                triggerListenerUpdate();

                discoveredPrinters.clear();
                BluetoothDiscoverer.findPrinters(context, new DiscoveryHandler() {

                    public void foundPrinter(final DiscoveredPrinter printer) {
                        DiscoveredPrinterBluetooth bluetoothPrinter = (DiscoveredPrinterBluetooth)printer;
                        System.out.println("Found Printer: " + printer.address + " | " + bluetoothPrinter.friendlyName);

                        if(activePrinter == null && bluetoothPrinter.address.equals(expectedActivePrinterId)) {
                            //Only do this if the activePrinterId hasn't been cleared
                            if(getDefaultPrinterId().equals(expectedActivePrinterId)) {
                                System.out.println("reconnected default printer");
                                BluetoothStateHolder.this.setActivePrinter(bluetoothPrinter);
                            }
                        }
                        discoveredPrinters.add(bluetoothPrinter);
                    }

                    public void discoveryFinished() {
                        System.out.println("Discovery Finished");
                        endDiscovery();
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
        } else {
            triggerListenerUpdate();
        }
    }

    public String getDiscoveryErrorMessage() {
        return discoveryErrorMessage;
    }

    public boolean inDiscovery() {
        return !bluetoothDiscoveryComplete;
    }
    private void endDiscovery() {
        bluetoothDiscoveryComplete = true;
        triggerListenerUpdate();
    }

    public void setActivePrinter(DiscoveredPrinterBluetooth activePrinter) {
        this.activePrinter = activePrinter;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit().putString(ACTIVE_PRINTER, activePrinter.address).commit();
        triggerListenerUpdate();
    }

    public String getDefaultPrinterId() {
        return PreferenceManager.getDefaultSharedPreferences(context).getString(ACTIVE_PRINTER, null);
    }

    public void clearDefaultPrinterId() {
        PreferenceManager.getDefaultSharedPreferences(context).edit().remove(ACTIVE_PRINTER).commit();
    }

    /**
     * Signal that the printer being used currently is not available or is having connection
     * issues that need to be resolved before printing can proceed.
     */
    public void SignalActivePrinterUnavailable() {
        //For now just clear the active printer and let it re-select on discovery
        activePrinter = null;

        //Have to run bluetooth discover on the uiThread.
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                startBluetoothDiscovery();
            }
        });

    }

    public ArrayList<DiscoveredPrinterBluetooth> getDiscoveredPrinters() {
        return discoveredPrinters;
    }

    public void wipeActivePrinter() {
        activePrinter = null;
        clearDefaultPrinterId();
        triggerListenerUpdate();
    }

    BluetoothStateListener listener;

    static WeakReference<BluetoothStateHolder> lastReference;

    public synchronized static BluetoothStateHolder attachContextListener(Context context, BluetoothStateListener listener) {
        BluetoothStateHolder activeHolder = null;
        if (lastReference != null) {
            activeHolder = lastReference.get();
            if (activeHolder == null) {
                lastReference = null;
            }
        }

        boolean startDiscovery = false;
        if (activeHolder == null) {
            activeHolder = new BluetoothStateHolder(context);
            lastReference = new WeakReference<>(activeHolder);
            startDiscovery = true;
        } else {
            startDiscovery = !activeHolder.inDiscovery() &&
                              activeHolder.getActivePrinter() == null &&
                              activeHolder.getDiscoveredPrinters().isEmpty();
        }
        activeHolder.setListener(listener);

        if(startDiscovery) {
            activeHolder.startBluetoothDiscovery();
        }
        return activeHolder;
    }

    public void detachStateListener(BluetoothStateListener listener) {
        if(this.listener == listener) {
            this.listener = null;
        }
    }

    public void setListener(BluetoothStateListener listener) {
        this.listener = listener;
        listener.attachStateHolder(this);
    }

    private void triggerListenerUpdate() {
        if(listener != null) {
            listener.onBluetoothStateUpdate();
        }
    }

    public interface BluetoothStateListener {
        void onBluetoothStateUpdate();

        void attachStateHolder(BluetoothStateHolder bluetoothStateHolder);
    }
}
