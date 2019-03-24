package com.okhiroyuki.plugin;

import java.util.List;
import org.apache.cordova.*;
import org.json.JSONArray;
import org.json.JSONException;
import android.content.Context;
import android.hardware.*;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.util.ArrayList;

public class Proximity extends CordovaPlugin implements SensorEventListener {

    public static int STOPPED = 0;
    public static int STARTING = 1;
    public static int RUNNING = 2;
    public static int ERROR_FAILED_TO_START = 3;

    // sensor result

    public long TIMEOUT = 30000;        // Timeout in msec to shut off listener

    int status;                         // status of listener
    long timeStamp;                     // time of most recent value
    long lastAccessTime;                // time the value was last retrieved

    JSONArray value;

    private SensorManager sensorManager;// Sensor manager
    Sensor mSensor;                     // Compass sensor returned by sensor manager

    private CallbackContext callbackContext;

    /**
     * Constructor.
     */
    public Proximity() {
        this.value = new JSONArray();
        this.timeStamp = 0;
        this.setStatus(Proximity.STOPPED);
    }

    /**
     * Sets the context of the Command. This can then be used to do things like
     * get file paths associated with the Activity.
     *
     * @param cordova The context of the main Activity.
     * @param webView The CordovaWebView Cordova is running in.
     */
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        this.sensorManager = (SensorManager) cordova.getActivity().getSystemService(Context.SENSOR_SERVICE);
    }

    /**
     * Executes the request and returns PluginResult.
     *
     * @param action                The action to execute.
     * @param args                  JSONArry of arguments for the plugin.
     * @param callbackS=Context     The callback id used when calling back into JavaScript.
     * @return                      True if the action was valid.
     * @throws JSONException
     */
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {

        if (action.equals("start")) {
            cordova.getActivity().runOnUiThread(new Runnable(){
                public void run() {
                    start();
                }
            });
        }
        else if (action.equals("stop")) {
            this.stop();
        }
        else if (action.equals("getState")) {

            // If its stopped then user needs to enable sensor using "start" method
            if (this.status == Proximity.STOPPED) {
                callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, "Proximity disabled, run start method before getState"));
            }
            // If not running, then this is an async call, so don't worry about waiting
            else if (this.status != Proximity.RUNNING) {
                int r = this.start();
                if (r == Proximity.ERROR_FAILED_TO_START) {
                    callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.IO_EXCEPTION, Proximity.ERROR_FAILED_TO_START));
                    return true;
                }
                // Set a timeout callback on the main thread.
                Handler handler = new Handler(Looper.getMainLooper());
                handler.postDelayed(new Runnable() {
                    public void run() {
                        Proximity.this.timeout();
                    }
                }, 2000);
            }
            else {
                callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, getValue()));
            }
        } else {
            // Unsupported action
            return false;
        }
        return true;
    }

    /**
     * Called when listener is to be shut down and object is being destroyed.
     */
    public void onDestroy() {
        this.stop();
    }

    /**
     * Called when app has navigated and JS listeners have been destroyed.
     */
    public void onReset() {
        this.stop();
    }

    //--------------------------------------------------------------------------
    // LOCAL METHODS
    //--------------------------------------------------------------------------

    /**
     * Start listening for compass sensor.
     *
     * @return          status of listener
     */
    public int start() {

        // If already starting or running, then just return
        if ((this.status == Proximity.RUNNING) || (this.status == Proximity.STARTING)) {
            return this.status;
        }
        // Get sensor from sensor manager
        @SuppressWarnings("deprecation")
        List<Sensor> list = new ArrayList<Sensor>();
        list = this.sensorManager.getSensorList(Sensor.TYPE_PROXIMITY);
        this.mSensor = list.get(0);
        this.sensorManager.registerListener(this, this.mSensor, SensorManager.SENSOR_DELAY_NORMAL);
        this.lastAccessTime = System.currentTimeMillis();
        this.setStatus(Proximity.STARTING);
        this.setStatus(Proximity.ERROR_FAILED_TO_START);
        return this.status;
    }

    /**
     * Stop listening to compass sensor.
     */
    public void stop() {
        if (this.status != Proximity.STOPPED) {
            this.sensorManager.unregisterListener(this);
        }
        this.setStatus(Proximity.STOPPED);
        this.value = new JSONArray();
    }

    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // TODO Auto-generated method stub
    }

    /**
     * Called after a delay to time out if the listener has not attached fast enough.
     */
    private void timeout() {
        if (this.status == Proximity.STARTING) {
            this.setStatus(Proximity.ERROR_FAILED_TO_START);
            if (this.callbackContext != null) {
                this.callbackContext.error("Compass listener failed to start.");
            }
        }
    }

    /**
     * Sensor listener event.
     *
     * @param SensorEvent event
     */
    public void onSensorChanged(SensorEvent event) {
        try {
            JSONArray value = new JSONArray();
            for(int i=0;i<event.values.length;i++){
                value.put(Float.parseFloat(event.values[i]+""));
            }

            this.timeStamp = System.currentTimeMillis();
            this.value = value;
            this.setStatus(Proximity.RUNNING);

            // If proximity hasn't been read for TIMEOUT time, then turn off sensor to save power
            if ((this.timeStamp - this.lastAccessTime) > this.TIMEOUT) {
                this.stop();
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /**
     * Get status of sensor.
     *
     * @return          status
     */
    public int getStatus() {
        return this.status;
    }

    /**
     * Get the most recent distance.
     *
     * @return          distance
     */
    public JSONArray getValue() {
        this.lastAccessTime = System.currentTimeMillis();
        return this.value;
    }


    /**
     * Set the timeout to turn off sensor if getValue() hasn't been called.
     *
     * @param timeout       Timeout in msec.
     */
    public void setTimeout(long timeout) {
        this.TIMEOUT = timeout;
    }

    /**
     * Get the timeout to turn off sensor if getValue() hasn't been called.
     *
     * @return timeout in msec
     */
    public long getTimeout() {
        return this.TIMEOUT;
    }

    /**
     * Set the status and send it to JavaScript.
     * @param status
     */
    private void setStatus(int status) {
        this.status = status;
    }
}
