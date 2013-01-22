package com.augmentari.roadworks.sensorlogger;

import android.app.Activity;
import android.content.*;
import android.graphics.Color;
import android.os.*;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import com.augmentari.roadworks.sensorlogger.util.Formats;
import com.augmentari.roadworks.sensorlogger.util.Log;

import java.util.Timer;
import java.util.TimerTask;


public class MainActivity extends Activity {
    private enum ServiceState {
        DISCONNECTED,
        STARTED,
        STOPPED
    }

    private Timer timer = null;

    public static final int UPDATE_PERIOD_MSEC = 2000;

    private Button serviceControlButton;
    private ServiceState serviceState = ServiceState.DISCONNECTED;

    private TextView timeLoggedTextView;
    private TextView statementsLoggedTextView;

    private SensorLoggerService.SessionLoggerServiceBinder binder = null;

    private Handler statsUpdateHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            timeLoggedTextView.setText(Long.toString((System.currentTimeMillis() - binder.getStartTimeMillis()) / 1000));
            statementsLoggedTextView.setText(Formats.formatWithSuffices(binder.getStatementsLogged()));
        }
    };

    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            binder = (SensorLoggerService.SessionLoggerServiceBinder) service;
            if (binder.isStarted()) {
                setServiceState(ServiceState.STARTED);
            } else {
                setServiceState(ServiceState.STOPPED);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            binder = null;
            setServiceState(ServiceState.DISCONNECTED);
        }
    };

    private View.OnClickListener buttonOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            switch (view.getId()) {
                case R.id.serviceControlButton:
                    switch (serviceState) {
                        case DISCONNECTED:
                            throw new IllegalArgumentException("Should not be accessible");

                        case STARTED:
                            Intent stopServiceIntent = new Intent(MainActivity.this, SensorLoggerService.class);
                            stopService(stopServiceIntent);

                            unbindService(connection);

                            Intent intent = new Intent(MainActivity.this, SensorLoggerService.class);
                            bindService(intent, connection, BIND_AUTO_CREATE);

                            setServiceState(ServiceState.STOPPED);
                            break;

                        case STOPPED:
                            Toast.makeText(MainActivity.this, R.string.dataGatheringStarted, Toast.LENGTH_SHORT).show();

                            Intent testServiceIntent = new Intent(MainActivity.this, SensorLoggerService.class);
                            startService(testServiceIntent);

                            setServiceState(ServiceState.STARTED);
                            break;
                    }
                    break;
                default:
                    Log.logNotImplemented(MainActivity.this);
                    break;
            }
        }
    };


    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.main);
        serviceControlButton = (Button) findViewById(R.id.serviceControlButton);
        serviceControlButton.setOnClickListener(buttonOnClickListener);

        statementsLoggedTextView = (TextView) findViewById(R.id.statementsLoggedTextView);
        timeLoggedTextView = (TextView) findViewById(R.id.timeLoggedTextView);
    }

    public void setServiceState(ServiceState serviceState) {
        switch (serviceState) {
            case DISCONNECTED:
                serviceControlButton.setEnabled(false);
                serviceControlButton.setText(getString(R.string.service_disconnected));
                serviceControlButton.setBackgroundColor(Color.GRAY);
                break;

            case STARTED:
                statsUpdateHandler.sendEmptyMessage(0);

                serviceControlButton.setEnabled(true);
                serviceControlButton.setText(getString(R.string.service_stop_command));
                serviceControlButton.setBackgroundColor(Color.RED);
                break;

            case STOPPED:
                serviceControlButton.setEnabled(true);
                serviceControlButton.setText(getString(R.string.service_start_command));
                serviceControlButton.setBackgroundColor(Color.GREEN);
                break;
        }
        this.serviceState = serviceState;
    }

    @Override
    protected void onStart() {
        super.onStart();

        timer = new Timer("SensorLoggerService.BroadcastResultsUpdater");
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (serviceState == ServiceState.STARTED) {
                    statsUpdateHandler.sendEmptyMessage(0);
                }
            }
        }, 0, UPDATE_PERIOD_MSEC);


        setServiceState(ServiceState.DISCONNECTED);

        Intent intent = new Intent(this, SensorLoggerService.class);
        bindService(intent, connection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();

        timer.cancel();

        if (binder != null) {
            unbindService(connection);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.showSessionList:
                Intent showResultsList = new Intent(this, SessionListActivity.class);
                startActivity(showResultsList);
                break;

            case R.id.settingsMenu:
                Intent settingsActivityIntent = new Intent(this, PrefActivity.class);
                startActivity(settingsActivityIntent);
                break;

            case R.id.testNetworking:
                new TestNetworkingTask(this).execute();
                break;
        }
        return true;
    }


}
