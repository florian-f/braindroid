package org.florian.eeg.braindroid;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;


public class MainActivity extends Activity {

    final static private String TAG = "BRAINDROID_ACTIVITY";

    private Button button;
    private Button toggleStreamingButton;
    private Button recordButton;
    private CheckBox testSignalCheckBox;

    FileOutputStream fileOutputStream = null;
    private boolean recording = false;
    private boolean streaming = false;
    private byte[] remainingBytes;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        button = (Button) findViewById(R.id.button);
        toggleStreamingButton = (Button) findViewById(R.id.button2);
        recordButton = (Button) findViewById(R.id.button3);
        testSignalCheckBox = (CheckBox) findViewById(R.id.checkBox);

        testSignalCheckBox.setChecked(false);
        testSignalCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                String cmd = "";
                if (isChecked) {
                    cmd = "-";
                } else {
                    cmd = "d";
                }
                Intent intent = new Intent(BrainDroidService.SEND_COMMAND);
                intent.putExtra(BrainDroidService.COMMAND_EXTRA, cmd);
                sendBroadcast(intent);
            }
        });
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(BrainDroidService.SEND_COMMAND);
                intent.putExtra(BrainDroidService.COMMAND_EXTRA, "v");
                sendBroadcast(intent);
            }
        });


        toggleStreamingButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!streaming) {
                    toggleStreamingButton.setText("stop");
                    streaming = true;
                    Intent intent = new Intent(BrainDroidService.SEND_COMMAND);
                    intent.putExtra(BrainDroidService.COMMAND_EXTRA, "b");
                    sendBroadcast(intent);

                } else {
                    toggleStreamingButton.setText("start streaming");
                    streaming = false;
                    Intent intent = new Intent(BrainDroidService.SEND_COMMAND);
                    intent.putExtra(BrainDroidService.COMMAND_EXTRA, "s");
                    sendBroadcast(intent);
                }
            }
        });

        recordButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if (!recording) {

                    Calendar calendar = Calendar.getInstance();
                    SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-mm-dd-hh-mm-ss");
                    String dateString = simpleDateFormat.format(calendar.getTime());
                    String filename = "openbci_"+dateString+".txt";
                    File dir = new File(getApplicationContext().getExternalFilesDir(null), "braindroid");
                    if (!dir.mkdirs()) {
                        Log.e(TAG, "Directory not created");
                    }

                    File file = new File(dir, filename);

                    try {
                        fileOutputStream = new FileOutputStream(file);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    recordButton.setText("stop recording");
                    recording = true;
                    Intent intent = new Intent(BrainDroidService.SEND_COMMAND);
                    intent.putExtra(BrainDroidService.COMMAND_EXTRA, "b");
                    sendBroadcast(intent);

                } else {
                    recordButton.setText("start recording");
                    recording = false;
                    if (fileOutputStream != null) {
                        try {
                            fileOutputStream.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    Intent intent = new Intent(BrainDroidService.SEND_COMMAND);
                    intent.putExtra(BrainDroidService.COMMAND_EXTRA, "s");
                    sendBroadcast(intent);
                }

            }
        });
        IntentFilter filter = new IntentFilter();
        filter.addAction(BrainDroidService.DATA_RECEIVED_INTENT);
        filter.addAction(UsbManager.ACTION_USB_ACCESSORY_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED);
//        filter.addAction(ArduinoCommunicatorService.DATA_SENT_INTERNAL_INTENT);
        registerReceiver(mBroadCastReceiver, filter);

        launchOpenBciService();


    }

    private void launchOpenBciService() {
        UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        UsbDevice usbDevice = null;

        HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();

        for (Map.Entry<String, UsbDevice> entry : deviceList.entrySet()) {
            UsbDevice device = entry.getValue();
            if (device.getVendorId() == OpenBci.VENDOR_ID) {
                usbDevice = device;
            }
        }

        if (null == usbDevice) {
            Log.d(TAG, "no device found");
        } else {
            Intent intent = new Intent(getApplicationContext(), BrainDroidService.class);
            intent.putExtra(UsbManager.EXTRA_DEVICE, usbDevice);
            startService(intent);
        }
    }

    private void stopOpenBciService() {
        Intent intent = new Intent(getApplicationContext(), BrainDroidService.class);
        stopService(intent);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        if (UsbManager.ACTION_USB_DEVICE_ATTACHED.contains(intent.getAction())) {
            launchOpenBciService();
        }
        if (UsbManager.ACTION_USB_DEVICE_DETACHED.contains(intent.getAction())) {
            stopOpenBciService();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopOpenBciService();
        unregisterReceiver(mBroadCastReceiver);
    }

    BroadcastReceiver mBroadCastReceiver = new BroadcastReceiver() {

//        private void handleTransferedData(Intent intent, boolean receiving) {
//            if (mIsReceiving == null || mIsReceiving != receiving) {
//                mIsReceiving = receiving;
//                mTransferedDataList.add(new ByteArray());
//            }
//
//            final byte[] newTransferedData = intent.getByteArrayExtra(ArduinoCommunicatorService.DATA_EXTRA);
////            if (DEBUG)
////                Log.i(TAG, "data: " + newTransferedData.length + " \"" + new String(newTransferedData) + "\"");
////                try {
//            StringBuffer sb = new StringBuffer();
//            char[] data = new char[newTransferedData.length];
//            for (int i = 0; i < newTransferedData.length; i++) {
//                char c = (char) (newTransferedData[i] & 0xFF);
//                data[i] = c;
//                sb.append(c);
//            }
//            Log.d(TAG, "data: " + sb.toString());
//            Log.d(TAG, "char: " + data);
//
////                } catch (UnsupportedEncodingException e) {
////                    Log.e(TAG, e.getMessage(), e);
////                }
//            ByteArray transferedData = mTransferedDataList.get(mTransferedDataList.size() - 1);
//            transferedData.add(newTransferedData);
//            mTransferedDataList.set(mTransferedDataList.size() - 1, transferedData);
//            mDataAdapter.notifyDataSetChanged();
//        }

        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
//            if (DEBUG) Log.d(TAG, "onReceive() " + action);
//
//            if (ArduinoCommunicatorService.DATA_RECEIVED_INTENT.equals(action)) {
//                handleTransferedData(intent, true);
//            } else if (ArduinoCommunicatorService.DATA_SENT_INTERNAL_INTENT.equals(action)) {
//                handleTransferedData(intent, false);
//            }
            Log.d(TAG, "RECEIVED BROADCAST: " + action);
            if (action.equals(BrainDroidService.DATA_RECEIVED_INTENT)) {
                processData(intent, true);
            }
        }
    };

    // todo move this into the service
    private void processData(Intent intent, boolean isEegData) {

        Log.d(TAG, "PROCESS_DATA: received data");
        byte[] data = intent.getByteArrayExtra(BrainDroidService.DATA_EXTRA);

        if (!isEegData) {
            // todo process whatever the device sends
            return;
        }

        if (recording){
            writeToFile(data);
        }
    }

    private void writeToFile(byte[] data) {
        if (fileOutputStream != null) {
            try {
                String dataString = getStringtoWrite(data);
                Log.d(TAG, "writing " + dataString);
                fileOutputStream.write(dataString.getBytes());
                fileOutputStream.flush();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private String getStringtoWrite(byte[] data) {

        // in case a packet was cut off
        // should only happen if previously,
        // data.length was < TRANSFER_SIZE
        // and data.length % 33 != 0
        // (see below)
        if (data[0] != OpenBci.START_BYTE) {
            if (remainingBytes != null) {
                byte[] dataTmp = new byte[remainingBytes.length + data.length];
                System.arraycopy(remainingBytes, 0, dataTmp, 0, remainingBytes.length);
                System.arraycopy(data, 0, dataTmp, remainingBytes.length, data.length);
                remainingBytes = null;
                data = dataTmp;
            }
        }

        String write = "";
        for (int i = 0; i < data.length; i++) {
            if (data[i] == OpenBci.START_BYTE) {
                if (data.length > i + 32) {
                    int c = data[i + 1] & 0xFF;
                    float ch1 = OpenBci.convertByteToMicroVolts(Arrays.copyOfRange(data, i + 2, i + 5));
                    float ch2 = OpenBci.convertByteToMicroVolts(Arrays.copyOfRange(data, i + 5, i + 8));
                    float ch3 = OpenBci.convertByteToMicroVolts(Arrays.copyOfRange(data, i + 8, i + 11));
                    float ch4 = OpenBci.convertByteToMicroVolts(Arrays.copyOfRange(data, i + 11, i + 14));

                    float ch5 = OpenBci.convertByteToMicroVolts(Arrays.copyOfRange(data, i + 14, i + 17));
                    float ch6 = OpenBci.convertByteToMicroVolts(Arrays.copyOfRange(data, i + 17, i + 20));
                    float ch7 = OpenBci.convertByteToMicroVolts(Arrays.copyOfRange(data, i + 20, i + 23));
                    float ch8 = OpenBci.convertByteToMicroVolts(Arrays.copyOfRange(data, i + 23, i + 26));

                    float accelX = OpenBci.interpret16bitAsInt32(Arrays.copyOfRange(data, i + 26, i + 28));
                    float accelY = OpenBci.interpret16bitAsInt32(Arrays.copyOfRange(data, i + 28, i + 30));
                    float accelZ = OpenBci.interpret16bitAsInt32(Arrays.copyOfRange(data, i + 30, i + 32));

                    i = i + 32;

                    write = write + c + ", " + ch1 + ", " + ch2 + ", " + ch3 + ", " + ch4 + ", " + ch5 + ", " + ch6 + ", " + ch7 + ", " + ch8 + ", " + accelX + ", " + accelY + ", " + accelZ + "\n";

                    Log.d(TAG, "DATA: " + c + ", " + ch1 + ", " + ch2);
                } else {
                    if (data.length < BrainDroidService.TRANSFER_SIZE) {
                        if (data.length % OpenBci.PACKET_SIZE != 0) {
                            remainingBytes = Arrays.copyOfRange(data, i, data.length - 1);
                        }
                    }
                }
            }
        }
        return write;
    }
}
