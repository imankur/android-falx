package com.life360.falx.monitor;

import android.support.annotation.VisibleForTesting;

import com.life360.falx.dagger.DaggerUtilComponent;
import com.life360.falx.dagger.DateTimeModule;
import com.life360.falx.dagger.LoggerModule;
import com.life360.falx.dagger.UtilComponent;
import com.life360.falx.model.FalxMonitorEvent;
import com.life360.falx.model.RealtimeMessagingActivity;
import com.life360.falx.util.Clock;
import com.life360.falx.util.Logger;

import java.util.HashMap;

import javax.inject.Inject;

import io.reactivex.Observable;
import io.reactivex.annotations.NonNull;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;

/**
 * Created by remon on 7/19/17.
 */

public class RealtimeMessagingMonitor extends Monitor {

    private static final String TAG = "RealTimeDataMonitor";

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    protected Disposable rtMessagingDisposable;

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    @Inject
    Clock clock;

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    @Inject
    Logger logger;

    public static UtilComponent createDaggerComponent() {
        return DaggerUtilComponent.builder()
                // list of modules that are part of this component need to be created here too
                .dateTimeModule(new DateTimeModule())
                .loggerModule(new LoggerModule())
                .build();
    }

    public RealtimeMessagingMonitor(@NonNull Observable<RealtimeMessagingActivity> rtDataObservable) {
        this(createDaggerComponent(), rtDataObservable);

    }

    public RealtimeMessagingMonitor(@NonNull UtilComponent utilComponent, @NonNull Observable<RealtimeMessagingActivity> rtMessagingObservable) {
        utilComponent.inject(this);

        rtMessagingDisposable = rtMessagingObservable
                .observeOn(Schedulers.single())
                .subscribe(new Consumer<RealtimeMessagingActivity>() {

                    @Override
                    public void accept(RealtimeMessagingActivity rtMessagingActivity) throws Exception {
                        logger.d(Logger.TAG, "Real time data Activity: " + rtMessagingActivity.toString());

                        saveToDataStore(rtMessagingActivity);
                    }
                });
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    protected void saveToDataStore(RealtimeMessagingActivity activity) {
        HashMap<String, Double> args = new HashMap<>();
        args.put(FalxConstants.PROP_COUNT, new Double(activity.getCount()));
        args.put(FalxConstants.PROP_BYTES_RECEIVED, new Double(activity.getBytesReceived()));

        eventPublishSubject.onNext(new FalxMonitorEvent(FalxConstants.EVENT_REALTIME_MESSAGING, args));
    }

    @Override
    public void stop() {
        super.stop();

        if (!rtMessagingDisposable.isDisposed()) {
            rtMessagingDisposable.dispose();
            logger.d(TAG, "stop: disposed rtMessagingDisposable");
        }
    }

}
