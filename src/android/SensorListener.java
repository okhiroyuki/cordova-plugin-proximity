package org.apache.cordova.proximity;

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
import org.json.JSONObject;

public class SensorListener extends CordovaPlugin implements SensorEventListener {

    public static int STOPPED = 0;
    public static int STARTING = 1;
    public static int RUNNING = 2;
    public static int ERROR_FAILED_TO_START = 3;

    public static int NEAR = 1;
    public static int FAR = 0;

    private float distance;		//distance value
    private long timestamp;		//time of most recent value
    private int status;			//running status listner
	private int accuracy = SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM;

    private SensorManager mSensorManager; 	//sensor manager
    private Sensor mSensor;					//proximity sensor

    private CallbackContext callbackContext;

	private Handler mainHandler = null;
	private Runnable mainRunnable = new Runnable() {
		public void run() {
			SensorListener.this.timeout();
		}
	};

    /**
     * Constructor.
     */
    public SensorListener() {
        this.distance = Proximity.FAR;
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
            if((this.status == SensorListener.RUNNING) || (this.status == SensorListener.STARTING)){
                startTimeout();
                return this.status;
            }
        }
        this.setStatus(SensorListener.STARTING);

        // if sensor found, register as listner
		if ((list != null) && (list.size() > 0)){
			this.mSensor = list.get(0);
			if(this.mSensorManager.registerListener(this, this.mSensor, SensorManager.SENSOR_DELAY_UI)){
				this.setStatus(SensorListener.STARTING);
				this.accuracy = SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM;
			}else{
				this.setStatus(SensorListener.ERROR_FAILED_TO_START);
				this.fail(SensorListener.ERROR_FAILED_TO_START, "Device sensor returned an error.");
				return this.status;
			};
		}
		else{
			this.setStatus(SensorListener.ERROR_FAILED_TO_START);
			this.fail(SensorListener.ERROR_FAILED_TO_START, "Device sensor returned an error.");
			return this.status;
		}
		startTimeout();
		return this.status;
    }
    
    private void startTimeout(){
		// Set a timeout callback on the main thread.
        stopTimeout();
        mainHandler = new Handler(Looper.getMainLooper());
        mainHandler.postDelayed(mainRunnable, 2000);
	}

	private void stopTimeout(){
		if(mainHandler!=null){
            mainHandler.removeCallbacks(mainRunnable);
        }
    }
    
    /**
     * Stop listening to ambient light sensor.
     */
	public void stop(){
		stopTimeout();
		if (this.status != SensorListener.STOPPED){
			this.mSensorManager.unregisterListener(this);
		}
		this.setStatus(SensorListener.STOPPED);
		this.accuracy = SensorManager.SENSOR_STATUS_UNRELIABLE;
	}

    /**
     * Returns latest cached position if the sensor hasn't returned newer value.
     *
     * Called two seconds after starting the listener.
     */
	private void timeout() {
		if (this.status == SensorListener.STARTING && 
			this.accuracy >= SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM) {				
				this.timestamp = System.currentTimeMillis();
				this.win();
        }
    }
    
    /**
     * Called when the accuracy of the sensor has changed.
     *
     * @param sensor
     * @param accuracy
     */
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
		// Only look at gyroscope events
        if (sensor.getType() != Sensor.TYPE_PROXIMITY) {
            return;
        }

        // If not running, then just return
        if (this.status == SensorListener.STOPPED) {
            return;
        }
        this.accuracy = accuracy;
    }

        /**
     * Sensor listener event.
	 * 
     * @param SensorEvent event
     */
	public void onSensorChanged(SensorEvent event){
		if(event.sensor.getType() != Sensor.TYPE_PROXIMITY){
			return;
		}

		if (this.status == SensorListener.STOPPED){
			return;
		}

		this.setStatus(SensorListener.RUNNING);

		if (this.accuracy >= SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM){
			this.timestamp = System.currentTimeMillis();
			this.intensity = event.values[0];
			
			this.win();
		}
	}

	/**
     * Called when the view navigates.
     */
    @Override
    public void onReset() {
        if (this.status == SensorListener.RUNNING) {
            this.stop();
        }
	}	
	
	// Sends an error back to JS
    private void fail(int code, String message) {
        // Error object
        JSONObject errorObj = new JSONObject();
        try {
            errorObj.put("code", code);
            errorObj.put("message", message);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        PluginResult err = new PluginResult(PluginResult.Status.ERROR, errorObj);
        err.setKeepCallback(true);
        callbackContext.sendPluginResult(err);
    }

    private void win() {
        // Success return object
        PluginResult result = new PluginResult(PluginResult.Status.OK, this.getProximityJSON());
        result.setKeepCallback(true);
        callbackContext.sendPluginResult(result);
    }

    private void setStatus(int status) {
        this.status = status;
    }

    private JSONObject getProximityJSON() {
        JSONObject r = new JSONObject();
        try {
			r.put("distance", this.distance);
            r.put("timestamp", this.timestamp);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return r;
    }
}
