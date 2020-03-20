var argscheck = require('cordova/argscheck');
var utils = require('cordova/utils');
var exec = require('cordova/exec');
var ProximitySensor = require('./ProximitySensor');

var running = false;

var timers = {};

var listeners = [];

var proximity = null;

var eventTimerId = null;

// Tells native to start.
function start() {
    exec(function (a) {
        var tempListeners = listeners.slice(0);
        proximity = new ProximitySensor(a.distance, a.timestamp);
        for (var i = 0, l = tempListeners.length; i < l; i++) {
            tempListeners[i].win(proximity);
        }
    }, function (e) {
        var tempListeners = listeners.slice(0);
        for (var i = 0, l = tempListeners.length; i < l; i++) {
            tempListeners[i].fail(e);
        }
    }, "Proximity", "start", []);
    running = true;
}

// Tells native to stop.
function stop() {
    exec(null, null, "Proximity", "stop", []);
    proximity = null;
    running = false;
}

// Adds a callback pair to the listeners array
function createCallbackPair(win, fail) {
    return { win: win, fail: fail };
}

// Removes a win/fail listener pair from the listeners array
function removeListeners(l) {
    var idx = listeners.indexOf(l);
    if (idx > -1) {
        listeners.splice(idx, 1);
        if (listeners.length === 0) {
            stop();
        }
    }
}

var proximitysensor = {
	/**
     * Asynchronously acquires the current acceleration.
     *
     * @param {Function} successCallback    The function to call when the acceleration data is available
     * @param {Function} errorCallback      The function to call when there is an error getting the acceleration data. (OPTIONAL)
     * @param {Options} options The options for getting the accelerometer data such as timeout. (OPTIONAL)
     */
    getCurrentProximity: function (successCallback, errorCallback, options) {
        argscheck.checkArgs('fFO', 'proximitysensor.getCurrentProximity', arguments);

        if (cordova.platformId !== "android") {
            return;
        }

        var p;
        var win = function (a) {
            removeListeners(p);
            successCallback(a);
        };
        var fail = function (e) {
            removeListeners(p);
            if (errorCallback) {
                errorCallback(e);
            }
        };

        p = createCallbackPair(win, fail);
        listeners.push(p);

        if (!running) {
            start();
        }
    },

    /**
     * Asynchronously acquires the proximity repeatedly at a given interval.
     *
     * @param {Function} successCallback    The function to call each time the proximity data is available
     * @param {Function} errorCallback      The function to call when there is an error getting the acceleration data. (OPTIONAL)
     * @param {Options} options The options for getting the proximity data such as timeout. (OPTIONAL)
     * @return String                       The watch id that must be passed to #clearWatch to stop watching.
     */
    watchProximity: function (successCallback, errorCallback, options) {
		argscheck.checkArgs('fFO', 'proximitysensor.watchProximity', arguments);
		
        // Default interval (10 sec)
        var frequency = (options && options.frequency && typeof options.frequency == 'number') ? options.frequency : 10000;

        // Keep reference to watch id, and report accel readings as often as defined in frequency
        var id = utils.createUUID();

        var p = createCallbackPair(function () { }, function (e) {
            removeListeners(p);
            if (errorCallback) {
                errorCallback(e);
            }
        });
        listeners.push(p);

        timers[id] = {
            timer: window.setInterval(function () {
                if (proximity) {
                    successCallback(proximity);
                }
            }, frequency),
            listeners: p
        };

        if (running) {
            // If we're already running then immediately invoke the success callback
            // but only if we have retrieved a value, sample code does not check for null ...
            if (proximity) {
                successCallback(proximity);
            }
        } else {
            start();
		}
		
        return id;
    },

    /**
     * Clears the specified proximity watch.
     *
     * @param {String} id       The id of the watch returned from #watchproximity.
     */
    clearWatch: function (id) {
        // Stop javascript timer & remove from timer list
        if (id && timers[id]) {
            window.clearInterval(timers[id].timer);
            removeListeners(timers[id].listeners);
            delete timers[id];

            if (eventTimerId && Object.keys(timers).length === 0) {
                // No more watchers, so stop firing 'devicemotion' events
                window.clearInterval(eventTimerId);
                eventTimerId = null;
            }
        }
    }
};

module.exports  = proximitysensor;
