package kurtome.etch.app.location;

import android.app.Activity;
import android.location.Location;
import com.squareup.otto.Bus;
import com.squareup.otto.Produce;
import com.squareup.otto.Subscribe;
import kurtome.etch.app.ObjectGraphUtils;
import kurtome.etch.app.location.event.LocationFoundEvent;
import kurtome.etch.app.util.Minutes;
import kurtome.etch.app.util.Seconds;

import javax.inject.Inject;
import javax.xml.datatype.Duration;

public class LocationProducer {
    @Inject Bus mEventBus;
    @Inject LocationFetchManager mLocationFetchManager;

    private LocationFoundEvent mLastEvent;

    public LocationProducer(Activity activity) {
        ObjectGraphUtils.inject(activity, this);
        mEventBus.register(this);
    }

    public void refreshLocation() {
        FetchLocationCommand actualLocationCommand = new FetchLocationCommand(new LocationFetchListener() {
            @Override
            public void onLocationAcquired(Location location) {
                LocationFoundEvent event = new LocationFoundEvent();
                event.setLocation(location);
                event.setFinal(true);
                mLastEvent = event;
                mEventBus.post(event);
            }

            @Override
            public void onLocationFailed(String message, Location bestUnacceptableLocation) {
                LocationFoundEvent event = new LocationFoundEvent();
                event.setRoughLocation(bestUnacceptableLocation);
                event.setFinal(true);
                mLastEvent = event;
                mEventBus.post(event);
            }
        });
        actualLocationCommand.setTimeoutMillis(Minutes.toMillis(2));
        actualLocationCommand.setMinFetchOptimizationMillis(Seconds.toMillis(5));
        actualLocationCommand.setMaxAgeMillis(Seconds.toMillis(5));
        actualLocationCommand.setMinAccuracyMeters(200);
        mLocationFetchManager.fetchLocation(actualLocationCommand);

        FetchLocationCommand roughCommand = new FetchLocationCommand(new LocationFetchListener() {
            @Override
            public void onLocationAcquired(Location location) {
                if (!locationAcquired()) {
                    LocationFoundEvent event = new LocationFoundEvent();
                    event.setRoughLocation(location);
                    mLastEvent = event;
                    mEventBus.post(event);
                }
            }

            @Override
            public void onLocationFailed(String message, Location bestUnacceptableLocation) {
                if (!locationAcquired()) {
                    LocationFoundEvent event = new LocationFoundEvent();
                    event.setRoughLocation(bestUnacceptableLocation);
                    mLastEvent = event;
                    mEventBus.post(event);
                }
            }
        });
        roughCommand.setMaxAgeMillis(Minutes.toMillis(10));
        roughCommand.setMinFetchOptimizationMillis(400);
        roughCommand.setMinAccuracyMeters(1000);
        mLocationFetchManager.fetchLocation(roughCommand);
    }

    private boolean locationAcquired() {
        return mLastEvent != null && mLastEvent.isFinal();
    }

    @Produce
    public LocationFoundEvent produce() {
        return mLastEvent;
    }

    @Subscribe
    public void refreshLocationRequested(RefreshLocationRequest requestEvent) {
        mLastEvent = null;
        refreshLocation();
    }

    public void onDestroy() {
        mEventBus.unregister(this);
    }
}
