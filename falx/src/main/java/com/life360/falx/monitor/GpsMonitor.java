package com.life360.falx.monitor;

import android.support.annotation.VisibleForTesting;

import com.life360.falx.dagger.DaggerUtilComponent;
import com.life360.falx.dagger.DateTimeModule;
import com.life360.falx.dagger.LoggerModule;
import com.life360.falx.dagger.UtilComponent;
import com.life360.falx.model.FalxMonitorEvent;
import com.life360.falx.model.GpsSessionData;
import com.life360.falx.util.Clock;
import com.life360.falx.util.Logger;

import java.util.TimerTask;

import javax.inject.Inject;

import io.reactivex.Observable;
import io.reactivex.annotations.NonNull;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;

/**
 * Created by sudheer on 10/30/17.
 */

public class GpsMonitor extends Monitor {

    private final String TAG = "GpsMonitor";

    private Disposable gpsStateDisposable;
    private UtilComponent utilComponent;
    private long startTime;

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    GpsSessionData lastSessionData;

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    @Inject
    Clock clock;

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    @Inject
    Logger logger;

    /**
     * Subscribes to a Observable to receive GPS state data changes.
     *
     * @param gpsStateObservable
     */
    public GpsMonitor(@NonNull Observable<GpsState> gpsStateObservable) {
        // Create our UtilComponent module, since it will be only used by FalxApi
        UtilComponent utilComponent = DaggerUtilComponent.builder()
                // list of modules that are part of this component need to be created here too
                .dateTimeModule(new DateTimeModule())
                .loggerModule(new LoggerModule())
                .build();

        init(utilComponent, gpsStateObservable);
    }

    /**
     * Test code can pass in a TestUtilsComponent to this special constructor to inject
     * fake objects instead of what is provided by UtilComponent
     *
     * @param utilComponent
     */
    public GpsMonitor(@NonNull UtilComponent utilComponent, @NonNull Observable<GpsState> gpsStateObservable) {
        init(utilComponent, gpsStateObservable);
    }


    private void init(UtilComponent utilComponent, Observable<GpsState> gpsStateObservable) {
        // Create our UtilComponent module, since it will be only used by FalxApi
        this.utilComponent = utilComponent;
        this.utilComponent.inject(this);

        gpsStateDisposable = gpsStateObservable
                .observeOn(Schedulers.single())                         // Use a single background thread to sequentially process the received data
                .subscribe(new Consumer<GpsState>() {
                    @Override
                    public void accept(GpsState GpsState) throws Exception {
                        logger.d(TAG, "accept: gpsState = " + GpsState);

                        switch (GpsState) {
                            case ON:
                                onGpsOn();
                                break;

                            case OFF:
                                onGpsOff();
                                break;
                        }
                    }
                });
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    boolean onGpsOn() {
        // Is a session in progress?
        if (startTime == 0) {
            startTime = clock.currentTimeMillis();

            logger.d(TAG, "Session started... at: " + startTime);
        } else {
            logger.d(TAG, "Session already in progress, started at: " + startTime);
        }

        return true;
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    boolean onGpsOff() {
        long endTime = clock.currentTimeMillis();
        if (endTime < startTime) {
            logger.e(TAG, "Likely error in timer schedule to mark end of a GPS session");

            endTime = startTime;
        }

        logger.d(Logger.TAG, "GPS Session completed, duration (seconds): " + ((endTime - startTime) / 1000));

        lastSessionData = new GpsSessionData(GpsState.ON, startTime, endTime);

        // Save to local data store
        saveToDataStore();

        startTime = 0;

        return true;
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    protected void saveToDataStore() {
        eventPublishSubject.onNext(new FalxMonitorEvent(lastSessionData.getName(), lastSessionData.getArgumentMap()));
    }

    @Override
    public void stop() {
        super.stop();

        if (!gpsStateDisposable.isDisposed()) {
            gpsStateDisposable.dispose();
            logger.d(TAG, "stop: disposed gpsStateDisposable");
        }
    }
}