/**
 * Example of using libmuse library on android.
 * Interaxon, Inc. 2015
 */

package com.interaxon.test.libmuse;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.Timer;
import java.util.ArrayList;
import java.util.List;
import java.util.TimerTask;
import java.util.*;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.app.Activity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

import java.io.IOException;
import java.io.InputStream;

import android.app.Activity;
import android.app.NotificationManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.interaxon.libmuse.Accelerometer;
import com.interaxon.libmuse.ConnectionState;
import com.interaxon.libmuse.Eeg;
import com.interaxon.libmuse.LibMuseVersion;
import com.interaxon.libmuse.Muse;
import com.interaxon.libmuse.MuseArtifactPacket;
import com.interaxon.libmuse.MuseConnectionListener;
import com.interaxon.libmuse.MuseConnectionPacket;
import com.interaxon.libmuse.MuseDataListener;
import com.interaxon.libmuse.MuseDataPacket;
import com.interaxon.libmuse.MuseDataPacketType;
import com.interaxon.libmuse.MuseFileWriter;
import com.interaxon.libmuse.MuseFileWriterFactory;
import com.interaxon.libmuse.MuseManager;
import com.interaxon.libmuse.MusePreset;
import com.interaxon.libmuse.MuseVersion;



/**
 * In this simple example MainActivity implements 2 MuseHeadband listeners
 * and updates UI when data from Muse is received. Similarly you can implement
 * listers for other data or register same listener to listen for different type
 * of data.
 * For simplicity we create Listeners as inner classes of MainActivity. We pass
 * reference to MainActivity as we want listeners to update UI thread in this
 * example app.
 * You can also connect multiple muses to the same phone and register same
 * listener to listen for data from different muses. In this case you will
 * have to provide synchronization for data members you are using inside
 * your listener.
 *
 * Usage instructions:
 * 1. Enable bluetooth on your device
 * 2. Pair your device with muse
 * 3. Run this project
 * 4. Press Refresh. It should display all paired Muses in Spinner
 * 5. Make sure Muse headband is waiting for connection and press connect.
 * It may take up to 10 sec in some cases.
 * 6. You should see EEG and accelerometer data as well as connection status,
 * Version information and MuseElements (BETA, beta, theta, delta, gamma waves)
 * on the screen.
 */
public class MainActivity extends Activity implements OnClickListener {
    /**
     * Connection listener updates UI with new connection status and logs it.
     */

    NotificationCompat.Builder mBuilder =
            new NotificationCompat.Builder(this)
                    .setSmallIcon(R.drawable.ic_launcher)
                    .setContentTitle("Focused Driving")
                    .setContentText("WAKE UP!");
    public double acc_x_init, acc_y_init, acc_z_init;
    double avgBETAInit1 = 0;
    double avgBETAInit2 = 0;
    double avgBETASoFar1 = 0;
    double avgBETASoFar2 = 0;
    int numSoFar = 2;
    int initCounter = 150;
    int initRefresh = 0;
    int initRefresh2 = 0;
    int initRefresh3 = 0;
    boolean initClicked = false, initFirstClicked = false, onClickConnect = false,
    onClickInit = false;
    Timer timer = new Timer();
    class ConnectionListener extends MuseConnectionListener {

        final WeakReference<Activity> activityRef;

        ConnectionListener(final WeakReference<Activity> activityRef) {
            this.activityRef = activityRef;
        }

        @Override
        public void receiveMuseConnectionPacket(MuseConnectionPacket p) {
            final ConnectionState current = p.getCurrentConnectionState();
            final String status = p.getPreviousConnectionState().toString() +
                         " -> " + current;
            final String full = "Muse " + p.getSource().getMacAddress() +
                                " " + status;
            Log.i("Muse Headband", full);
            Activity activity = activityRef.get();
            // UI thread is used here only because we need to update
            // TextView values. You don't have to use another thread, unless
            // you want to run disconnect() or connect() from connection packet
            // handler. In this case creating another thread is required.
            if (activity != null) {
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        TextView statusText =
                                (TextView) findViewById(R.id.con_status);
                        statusText.setText(status);
                        TextView museVersionText =
                                (TextView) findViewById(R.id.version);
                        if (current == ConnectionState.CONNECTED) {
                            MuseVersion museVersion = muse.getMuseVersion();
                            String version = museVersion.getFirmwareType() +
                                 " - " + museVersion.getFirmwareVersion() +
                                 " - " + Integer.toString(
                                    museVersion.getProtocolVersion());
                            museVersionText.setText(version);
                        } else {
                            museVersionText.setText(R.string.undefined);
                        }
                    }
                });
            }
        }
    }

    /**
     * Data listener will be registered to listen for: Accelerometer,
     * Eeg and Relative BETA bandpower packets. In all cases we will
     * update UI with new values.
     * We also will log message if Artifact packets contains "blink" flag.
     * DataListener methods will be called from execution thread. If you are
     * implementing "serious" processing algorithms inside those listeners,
     * consider to create another thread.
     */
    class DataListener extends MuseDataListener {

        final WeakReference<Activity> activityRef;
        private MuseFileWriter fileWriter;

        DataListener(final WeakReference<Activity> activityRef) {
            this.activityRef = activityRef;
        }

        @Override
        public void receiveMuseDataPacket(MuseDataPacket p) {
            switch (p.getPacketType()) {
//                case ACCELEROMETER:
//                    updateAccelerometer(p.getValues());
//                    break;
//                case BETA_RELATIVE:
//                    updateBETARelative(p.getValues());
//                    break;
                case BETA_ABSOLUTE:
                    updateBETAAbsolute(p.getValues());
                    break;
                case BATTERY:
                    fileWriter.addDataPacket(1, p);
                    // It's library client responsibility to flush the buffer,
                    // otherwise you may get memory overflow. 
                    if (fileWriter.getBufferedMessagesSize() > 8096)
                        fileWriter.flush();
                    break;
                default:
                    break;
            }
        }

        @Override
        public void receiveMuseArtifactPacket(MuseArtifactPacket p) {
            if (p.getHeadbandOn() && p.getBlink()) {
                Log.i("Artifacts", "blink");
            }
        }

//        private void updateAccelerometer(final ArrayList<Double> data) {
//            Activity activity = activityRef.get();
//            if (activity != null) {
//                activity.runOnUiThread(new Runnable() {
//                    @Override
//                    public void run() {
//                        TextView acc_x = (TextView) findViewById(R.id.acc_x);
//                        TextView acc_y = (TextView) findViewById(R.id.acc_y);
//                        TextView acc_z = (TextView) findViewById(R.id.acc_z);
//                        acc_x.setText(String.format(
//                            "%6.2f", data.get(Accelerometer.FORWARD_BACKWARD.ordinal())));
//                        acc_y.setText(String.format(
//                            "%6.2f", data.get(Accelerometer.UP_DOWN.ordinal())));
//                        acc_z.setText(String.format(
//                                "%6.2f", data.get(Accelerometer.LEFT_RIGHT.ordinal())));
//                        if (initClicked){
//                            TextView acc_x_init_txt = (TextView) findViewById(R.id.acc_x_init);
//                            TextView acc_y_init_txt = (TextView) findViewById(R.id.acc_y_init);
//                            TextView acc_z_init_txt = (TextView) findViewById(R.id.acc_z_init);
//                            acc_x_init = data.get(Accelerometer.FORWARD_BACKWARD.ordinal());
//                            acc_y_init = data.get(Accelerometer.UP_DOWN.ordinal());
//                            acc_z_init = data.get(Accelerometer.LEFT_RIGHT.ordinal());
//
//                            acc_x_init_txt.setText(String.format(
//                                    "%6.2f",acc_x_init));
//                            acc_y_init_txt.setText(String.format(
//                                    "%6.2f", acc_y_init));
//                            acc_z_init_txt.setText(String.format(
//                                    "%6.2f", acc_z_init));
//                            initClicked = false;
//                        }
//                        if (initRefresh == 0) {
//                            if (initFirstClicked && ((acc_x_init > data.get(Accelerometer.FORWARD_BACKWARD.ordinal()) + 400) ||
//                                    (acc_x_init < data.get(Accelerometer.FORWARD_BACKWARD.ordinal()) - 400)||
//                            (acc_z_init < data.get(Accelerometer.LEFT_RIGHT.ordinal()) - 400 )||
//                                    (acc_z_init > data.get(Accelerometer.LEFT_RIGHT.ordinal()) + 400))) {
//                                notice();
//                            }
//                            initRefresh = 150;
//                        }
//                        initRefresh --;
//                    }
//                });
//
//            }
//        }


        private void updateBETAAbsolute (final ArrayList<Double> data){
            Activity activity = activityRef.get();
            if (activity != null) {
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run(){
//                        TextView bAbsolute1 = (TextView) findViewById(R.id.bAbsolute1);
                        TextView bAbsolute2 = (TextView) findViewById(R.id.bAbsolute2);
                        TextView bAbsolute3 = (TextView) findViewById(R.id.bAbsolute3);
//                        TextView bAbsolute4 = (TextView) findViewById(R.id.bAbsolute4);
//                        bAbsolute1.setText(String.format(
//                                "%6.2f", data.get(Eeg.TP9.ordinal())));
                        bAbsolute2.setText(String.format(
                                "%6.2f", data.get(Eeg.FP1.ordinal())));
                        bAbsolute3.setText(String.format(
                                "%6.2f", data.get(Eeg.FP2.ordinal())));
//                        bAbsolute4.setText(String.format(
//                                "%6.2f", data.get(Eeg.TP10.ordinal())));
                        avgBETASoFar1 = (data.get(Eeg.FP1.ordinal())+
                                avgBETASoFar1*(numSoFar-2))/numSoFar;
                        avgBETASoFar2 = (data.get(Eeg.FP2.ordinal())+
                                avgBETASoFar2*(numSoFar-2))/numSoFar;
                        numSoFar += 2;
                        if (initCounter >0){
                            avgBETAInit1 = avgBETASoFar1;
                            avgBETAInit2 = avgBETASoFar2;
                            initCounter--;
                        }   else{
                            if (initRefresh2 == 0){
                                if ((((data.get(Eeg.FP1.ordinal())) < avgBETAInit1 - 0.4)
                                ||(data.get(Eeg.FP2.ordinal()) < avgBETAInit2 - 0.4))
                                        &&(initRefresh3 == 0)) {
                                    notice();
                                    initRefresh3 = 50;
                                }
                                if (initRefresh3 > 0){
                                    initRefresh3 --;
                                }
                                initRefresh2 = 5;

                            }
                            initRefresh2 --;
                        }
                        TextView avgBETAInit1_txt = (TextView) findViewById(R.id.avgBETAInit1);
                        TextView avgBETASoFar1_txt = (TextView) findViewById(R.id.avgBETASoFar1);
                        TextView avgBETAInit2_txt = (TextView) findViewById(R.id.avgBETAInit2);
                        TextView avgBETASoFar2_txt = (TextView) findViewById(R.id.avgBETASoFar2);
                        avgBETAInit1_txt.setText(String.format("%6.2f", avgBETAInit1));
                        avgBETASoFar1_txt.setText(String.format("%6.2f", avgBETASoFar1));
                        avgBETAInit2_txt.setText(String.format("%6.2f", avgBETAInit2));
                        avgBETASoFar2_txt.setText(String.format("%6.2f", avgBETASoFar2));
                    }
                });
            }
        }
//        private void updateBETARelative(final ArrayList<Double> data) {
//            Activity activity = activityRef.get();
//            if (activity != null) {
//                activity.runOnUiThread(new Runnable() {
//                    @Override
//                    public void run() {
//                        TextView aRelative1 = (TextView) findViewById(R.id.aRelative1);
//                        TextView aRelative2 = (TextView) findViewById(R.id.aRelative2);
//                        TextView aRelative3 = (TextView) findViewById(R.id.aRelative3);
//                        TextView aRelative4 = (TextView) findViewById(R.id.aRelative4);
//                        aRelative1.setText(String.format(
//                                "%6.2f", data.get(Eeg.TP9.ordinal())));
//                        aRelative2.setText(String.format(
//                                "%6.2f", data.get(Eeg.FP1.ordinal())));
//                        aRelative3.setText(String.format(
//                                "%6.2f", data.get(Eeg.FP2.ordinal())));
//                        aRelative4.setText(String.format(
//                                "%6.2f", data.get(Eeg.TP10.ordinal())));
//                        avgBETASoFar = (data.get(Eeg.TP10.ordinal())+data.get(Eeg.TP9.ordinal())+
//                                avgBETASoFar*(numSoFar-2))/numSoFar;
//                        numSoFar += 2;
//                        if (initCounter > 0) {
//                            avgBETAInit = avgBETASoFar;
//                            initCounter--;
//                        } else {
//                            if (initRefresh2 == 0){
//                                if (avgBETASoFar > avgBETAInit + 0.05){
//                                    notice();
//                                }
//                                initRefresh2 = 100;
//                            }
//                            initRefresh2 --;
//                        }
//                        TextView avgBETAInit_txt = (TextView) findViewById(R.id.avgBETAInit);
//                        TextView avgBETASoFar_txt = (TextView) findViewById(R.id.avgBETASoFar);
//                        avgBETAInit_txt.setText(String.format("%6.2f", avgBETAInit));
//                        avgBETASoFar_txt.setText(String.format("%6.2f", avgBETASoFar));
//                    }
//                });
//            }
//        }


        public void setFileWriter(MuseFileWriter fileWriter) {
            this.fileWriter  = fileWriter;
        }
    }

    private Muse muse = null;
    private ConnectionListener connectionListener = null;
    private DataListener dataListener = null;
    private boolean dataTransmission = true;
    private MuseFileWriter fileWriter = null;

    public MainActivity() {
        // Create listeners and pass reference to activity to them
        WeakReference<Activity> weakActivity =
                                new WeakReference<Activity>(this);

        connectionListener = new ConnectionListener(weakActivity);
        dataListener = new DataListener(weakActivity);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Button refreshButton = (Button) findViewById(R.id.refresh);
        refreshButton.setOnClickListener(this);
        Button connectButton = (Button) findViewById(R.id.connect);
        connectButton.setOnClickListener(this);
        Button initButton = (Button) findViewById(R.id.Init);
        initButton.setOnClickListener(this);
        fileWriter = MuseFileWriterFactory.getMuseFileWriter(new File(
                        getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
                        "testlibmusefile.muse"));
        Log.i("Muse Headband", "libmuse version=" + LibMuseVersion.SDK_VERSION);
        fileWriter.addAnnotationString(1, "MainActivity onCreate");
        dataListener.setFileWriter(fileWriter);
    }
    @Override
    public void onClick(View v) {
        Spinner musesSpinner = (Spinner) findViewById(R.id.muses_spinner);
        if (v.getId() == R.id.refresh) {
            MuseManager.refreshPairedMuses();
            List<Muse> pairedMuses = MuseManager.getPairedMuses();
            List<String> spinnerItems = new ArrayList<String>();
            for (Muse m: pairedMuses) {
                String dev_id = m.getName() + "-" + m.getMacAddress();
                Log.i("Muse Headband", dev_id);
                spinnerItems.add(dev_id);
            }
            ArrayAdapter<String> adapterArray = new ArrayAdapter<String> (
                    this, android.R.layout.simple_spinner_item, spinnerItems);
            musesSpinner.setAdapter(adapterArray);
        }
        else if (v.getId() == R.id.Init){
            avgBETASoFar1 = 0.0;
            avgBETASoFar2 = 0.0;
            avgBETAInit1 = 0.0;
            avgBETAInit2 = 0.0;
            onClickInit = true;
            initClicked = true;
            initFirstClicked = true;
            numSoFar = 2;
            initCounter = 150;

        }
        else if (v.getId() == R.id.connect) {
            List<Muse> pairedMuses = MuseManager.getPairedMuses();
            if (pairedMuses.size() < 1 ||
                musesSpinner.getAdapter().getCount() < 1) {
                Log.w("Muse Headband", "There is nothing to connect to");
            }
            else {
                muse = pairedMuses.get(musesSpinner.getSelectedItemPosition());
                ConnectionState state = muse.getConnectionState();
                if (state == ConnectionState.CONNECTED ||
                    state == ConnectionState.CONNECTING) {
                    Log.w("Muse Headband", "doesn't make sense to connect second time to the same muse");
                    return;
                }
                configure_library();
                fileWriter.open();
                fileWriter.addAnnotationString(1, "Connect clicked");
                /**
                 * In most cases libmuse native library takes care about
                 * exceptions and recovery mechanism, but native code still
                 * may throw in some unexpected situations (like bad bluetooth
                 * connection). Print all exceptions here.
                 */
                try {
                    muse.runAsynchronously();
                } catch (Exception e) {
                    Log.e("Muse Headband", e.toString());
                }
            }
        }
    }
    public void notice() {
        // Sets an ID for the notification
        int mNotificationId = 001;
        // Gets an instance of the NotificationManager service
        NotificationManager mNotifyMgr =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        // Builds the notification and issues it.
        mNotifyMgr.notify(mNotificationId, mBuilder.build());
        Spinner musesSpinner = (Spinner) findViewById(R.id.muses_spinner);
        try {
            Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            Ringtone r = RingtoneManager.getRingtone(getApplicationContext(), notification);
            mBuilder.setVibrate(new long[]{1000, 1000, 1000});
            r.play();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private void configure_library() {
        muse.registerConnectionListener(connectionListener);
        muse.registerDataListener(dataListener,
                MuseDataPacketType.ACCELEROMETER);
        muse.registerDataListener(dataListener,
                                  MuseDataPacketType.EEG);
        muse.registerDataListener(dataListener,
                                  MuseDataPacketType.BETA_ABSOLUTE);
//        muse.registerDataListener(dataListener,
//                                  MuseDataPacketType.BETA_RELATIVE);
        muse.registerDataListener(dataListener,
                                  MuseDataPacketType.ARTIFACTS);
        muse.registerDataListener(dataListener,
                                  MuseDataPacketType.BATTERY);
        //muse.registerDataListener(dataListener, MuseDataPacketType.MELLOW);
        muse.setPreset(MusePreset.PRESET_14);
        muse.enableDataTransmission(dataTransmission);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}

//// Sets an ID for the notification
//int mNotificationId = 001;
//// Gets an instance of the NotificationManager service
//NotificationManager mNotifyMgr =
//        (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
//// Builds the notification and issues it.
//mNotifyMgr.notify(mNotificationId, mBuilder.build());
//        Spinner musesSpinner = (Spinner) findViewById(R.id.muses_spinner);
//        try {
//        Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
//        Ringtone r = RingtoneManager.getRingtone(getApplicationContext(), notification);
//        r.play();
//        } catch (Exception e) {
//        e.printStackTrace();
//        }
//
//
//
//
//
//
//
//
//
//
//
//        with vibrate:
//
//
//

//// Sets an ID for the notification
//        int mNotificationId = 001;
//// Gets an instance of the NotificationManager service
//        NotificationManager mNotifyMgr =
//        (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
//// Builds the notification and issues it.
//        mNotifyMgr.notify(mNotificationId, mBuilder.build());
//        Spinner musesSpinner = (Spinner) findViewById(R.id.muses_spinner);
//        try {
//        Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
//        Ringtone r = RingtoneManager.getRingtone(getApplicationContext(), notification);
//        mBuilder.setVibrate(new long[] { 1000, 1000, 1000, 1000, 1000 });
//        r.play();
//        } catch (Exception e) {
//        e.printStackTrace();
//        }
//
//
//
//
//
//
//
//
//
//        NotificationCompat.Builder mBuilder =
//        new NotificationCompat.Builder(this)
//        .setSmallIcon(R.drawable.ic_launcher)
//        .setContentTitle("Focused Driving")
//        .setContentText("WAKE UP!");

