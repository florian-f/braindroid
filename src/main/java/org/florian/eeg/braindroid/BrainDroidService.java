package org.florian.eeg.braindroid;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

import com.ftdi.j2xx.D2xxManager;
import com.ftdi.j2xx.FT_Device;

/**
 * Created by florian on 24.12.14.
 */
public class BrainDroidService extends Service {

    private final static String TAG = "BRAINDROID_SERVICE";

    private boolean mIsRunning = false;
    private SenderThread mSenderThread;
    public static D2xxManager ftD2xx = null;
    private FT_Device ftDevice = null;
    private UsbDevice mUsbDevice = null;

    int baudRate = OpenBci.BAUD_RATE; /*baud rate*/
    byte stopBit = (byte) 1; /*1:1stop bits, 2:2 stop bits*/
    byte dataBit = (byte) 8; /*8:8bit, 7: 7bit*/
    byte parity = (byte) 0;  /* 0: none, 1: odd, 2: even, 3: mark, 4: space*/
    byte flowControl = (byte) 0; /*0:none, 1: flow control(CTS,RTS)*/

//    private static final int TRANSFER_SIZE = 1023; // ...
    static final int TRANSFER_SIZE = 528; // ...

    private static final long SLEEP = 200;

    private volatile boolean receiverThreadRunning;
//    private static final int TRANSFER_SIZE = 512;
//private static final int TRANSFER_SIZE = 64;


    final static String DATA_RECEIVED_INTENT = "braindroid.intent.action.DATA_RECEIVED";
    final static String SEND_DATA_INTENT = "braindroid.intent.action.SEND_DATA";
    final static String DATA_SENT_INTERNAL_INTENT = "braindroid.internal.intent.action.DATA_SENT";
    final static String DATA_EXTRA = "braindroid.intent.extra.DATA";

    final static String SEND_COMMAND = "braindroid.intent.action.SEND_COMMAND";
    final static String COMMAND_EXTRA = "braindroid.intent.extra.COMMAND_EXTRA";

    @Override
    public IBinder onBind(Intent intent) {
        return null;

    }

    @Override
    public void onCreate() {
        super.onCreate();
        IntentFilter filter = new IntentFilter();
//        filter.addAction(SEND_DATA_INTENT);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        filter.addAction(SEND_COMMAND);
        registerReceiver(mReceiver, filter);

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (mIsRunning) {
            Log.i(TAG, "Service already running.");
            return Service.START_REDELIVER_INTENT;
        }

        mUsbDevice = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

        if (null == ftD2xx) {
            try {
                ftD2xx = D2xxManager.getInstance(this);
                ftD2xx.createDeviceInfoList(this);

            } catch (D2xxManager.D2xxException ex) {
                Log.e(TAG, ex.getMessage(), ex);
            }

        }

        // connect
        if (null == ftDevice)
        {
            ftDevice = ftD2xx.openByUsbDevice(this, mUsbDevice);
        }


        if (ftDevice == null || !ftDevice.isOpen()) {
            Log.e(TAG, "Opening ftDevice Failed");
            stopSelf();
            return Service.START_REDELIVER_INTENT;
        }

        ftDevice.setBitMode((byte) 0, D2xxManager.FT_BITMODE_RESET);
        ftDevice.setBaudRate(baudRate);

        ftDevice.setDataCharacteristics(dataBit, stopBit, parity);

        ftDevice.setFlowControl(flowControl, (byte) 0x0b, (byte) 0x0d);

        ftDevice.purge((byte) (D2xxManager.FT_PURGE_TX));

        Log.d(TAG, "LATENCY_TIMER: " + ftDevice.getLatencyTimer());
//        ftDevice.setLatencyTimer((byte) 200);

        ftDevice.restartInTask();

        mIsRunning = true;

        Log.i(TAG, "Receiving!");
        Toast.makeText(getBaseContext(), getString(R.string.receiving), Toast.LENGTH_SHORT).show();
        startReceiverThread();
        startSenderThread();

        return Service.START_REDELIVER_INTENT;
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "onDestroy()");
        super.onDestroy();

        receiverThreadRunning = false;

        try {
            Thread.sleep(SLEEP);
        } catch (InterruptedException e) {
            // ignore
        }

        if (null != ftDevice) {
            try {
                ftDevice.close();
            } catch (Exception e) {
                Log.e(TAG, "failed to close device", e);
            }
            ftDevice = null;
        }

        unregisterReceiver(mReceiver);

    }

    private byte[] getLineEncoding(int baudRate) {
        final byte[] lineEncodingRequest = {(byte) 0x80, 0x25, 0x00, 0x00, 0x00, 0x00, 0x08};
        //Get the least significant byte of baudRate,
        //and put it in first byte of the array being sent
        lineEncodingRequest[0] = (byte) (baudRate & 0xFF);

        //Get the 2nd byte of baudRate,
        //and put it in second byte of the array being sent
        lineEncodingRequest[1] = (byte) ((baudRate >> 8) & 0xFF);

        //ibid, for 3rd byte (my guess, because you need at least 3 bytes
        //to encode your 115200+ settings)
        lineEncodingRequest[2] = (byte) ((baudRate >> 16) & 0xFF);

        return lineEncodingRequest;

    }

    BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            Log.d(TAG, "onReceive() " + action);

            if (SEND_COMMAND.equals(action)) {
//                final byte[] dataToSend = intent.getByteArrayExtra(COMMAND_EXTRA);
                final String dataToSend = intent.getStringExtra(COMMAND_EXTRA);
                if (dataToSend == null) {
                    Log.i(TAG, "No " + DATA_EXTRA + " extra in intent!");
                    String text = String.format(getResources().getString(R.string.no_extra_in_intent), COMMAND_EXTRA);
                    Toast.makeText(context, text, Toast.LENGTH_LONG).show();
                    return;
                }

                mSenderThread.mHandler.obtainMessage(10, dataToSend).sendToTarget();
            }
        }
    };

    public void SendMessage(String writeData) {
        if (ftDevice.isOpen() == false) {
            Log.e("j2xx", "SendMessage: device not open");
            return;
        }

        ftDevice.setLatencyTimer((byte) 16);
//		ftDev.purge((byte) (D2xxManager.FT_PURGE_TX | D2xxManager.FT_PURGE_RX));

//        String writeData = "v";//writeText.getText().toString();
        byte[] OutData = writeData.getBytes();
        ftDevice.write(OutData, writeData.length());

    }

    private void startReceiverThread() {

        receiverThreadRunning = true;

        new Thread("receiver") {
            public void run() {
                byte[] readData = new byte[TRANSFER_SIZE];

                // todo receiverThreadRunning == true
                while (receiverThreadRunning) {
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        // ignore
                    }

                    synchronized (ftDevice) {
                        int queueStatus = ftDevice.getQueueStatus();
//                        ftDevice.
                        int available = queueStatus;
                        if (queueStatus > 0) {
                            Log.d(TAG, "queStatus: " + queueStatus);
                            if (available > TRANSFER_SIZE) {
                                available = TRANSFER_SIZE;
                            }

                            ftDevice.read(readData, available);

                                Intent intent = new Intent(DATA_RECEIVED_INTENT);
                                byte[] buffer = new byte[available];
                                System.arraycopy(readData, 0, buffer, 0, available);
                                intent.putExtra(DATA_EXTRA, buffer);
                                sendBroadcast(intent);
                            if (available % OpenBci.PACKET_SIZE == 0 && readData[0] == OpenBci.START_BYTE) {
                                Log.d(TAG, "received data (" + available + " bytes (" + ((float) available / OpenBci.PACKET_SIZE));

                            } else {
                                Log.d(TAG, "received data (" + available + " bytes ("+((float)available/ OpenBci.PACKET_SIZE)+", but packet size/ start byte (" + readData[0] + ") is incorrect");
                            }
                        }
                    }
                }

                Log.d(TAG, "receiver thread stopped.");
            }
        }.start();
    }

    private void startSenderThread() {
        mSenderThread = new SenderThread("arduino_sender");
        mSenderThread.start();

    }

    private class SenderThread extends Thread {

        public Handler mHandler;

        public SenderThread(String string) {
            super(string);
        }

        public void run() {

            Looper.prepare();

            mHandler = new Handler() {
                public void handleMessage(Message msg) {
                    Log.i(TAG, "handleMessage() " + msg.what);
                    if (msg.what == 10) {
//                        final byte[] dataToSend = (byte[]) msg.obj;
                        final String writeData = (String) msg.obj;
                        SendMessage(writeData);
                        Log.d(TAG, "calling bulkTransfer() out: "+writeData);
//                        final int len = mUsbConnection.bulkTransfer(mOutUsbEndpoint, dataToSend, dataToSend.length, 0);
//                        Log.d(TAG, len + " of " + dataToSend.length + " sent.");
//                        Intent sendIntent = new Intent(DATA_SENT_INTERNAL_INTENT);
//                        sendIntent.putExtra(DATA_EXTRA, dataToSend);
//                        sendBroadcast(sendIntent);
                    } else if (msg.what == 11) {
                        Looper.myLooper().quit();
                    }
                }
            };

            Looper.loop();
            Log.i(TAG, "sender thread stopped");
        }
    }
}
