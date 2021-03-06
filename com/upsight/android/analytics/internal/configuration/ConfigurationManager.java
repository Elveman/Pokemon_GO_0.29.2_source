package com.upsight.android.analytics.internal.configuration;

import com.upsight.android.UpsightContext;
import com.upsight.android.UpsightException;
import com.upsight.android.analytics.C0863R;
import com.upsight.android.analytics.configuration.UpsightConfiguration;
import com.upsight.android.analytics.dispatcher.EndpointResponse;
import com.upsight.android.analytics.event.config.UpsightConfigExpiredEvent;
import com.upsight.android.logger.UpsightLogger;
import com.upsight.android.persistence.UpsightDataStore;
import com.upsight.android.persistence.UpsightDataStoreListener;
import com.upsight.android.persistence.UpsightSubscription;
import com.upsight.android.persistence.annotation.Created;
import java.io.IOException;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.apache.commons.io.IOUtils;
import rx.Scheduler;
import rx.Scheduler.Worker;
import rx.Subscription;
import rx.functions.Action0;

public final class ConfigurationManager {
    public static final String CONFIGURATION_RESPONSE_SUBTYPE = "upsight.configuration";
    public static final String CONFIGURATION_SUBTYPE = "upsight.configuration.configurationManager";
    private static final String LOG_TAG = "Configurator";
    private final ManagerConfigParser mConfigParser;
    private Config mCurrentConfig;
    private final UpsightDataStore mDataStore;
    private UpsightSubscription mDataStoreSubscription;
    private boolean mIsLaunched;
    private boolean mIsOutOfSync;
    private final UpsightLogger mLogger;
    private final ConfigurationResponseParser mResponseParser;
    private Action0 mSyncAction;
    private final UpsightContext mUpsight;
    private final Worker mWorker;
    private Subscription mWorkerSubscription;

    /* renamed from: com.upsight.android.analytics.internal.configuration.ConfigurationManager.1 */
    class C08731 implements UpsightDataStoreListener<Set<UpsightConfiguration>> {
        C08731() {
        }

        public void onSuccess(Set<UpsightConfiguration> result) {
            if (ConfigurationManager.this.mCurrentConfig == null) {
                boolean hasApplied = false;
                if (result.size() > 0) {
                    for (UpsightConfiguration config : result) {
                        if (config.getScope().equals(ConfigurationManager.CONFIGURATION_SUBTYPE)) {
                            hasApplied = ConfigurationManager.this.applyConfiguration(config.getConfiguration());
                        }
                    }
                }
                if (!hasApplied) {
                    ConfigurationManager.this.applyDefaultConfiguration();
                }
            }
        }

        public void onFailure(UpsightException exception) {
            ConfigurationManager.this.mLogger.m199e(ConfigurationManager.LOG_TAG, "Could not fetch existing configs from datastore", exception);
            if (ConfigurationManager.this.mCurrentConfig == null) {
                ConfigurationManager.this.applyDefaultConfiguration();
            }
        }
    }

    /* renamed from: com.upsight.android.analytics.internal.configuration.ConfigurationManager.2 */
    class C08742 implements Action0 {
        C08742() {
        }

        public void call() {
            UpsightConfigExpiredEvent.createBuilder().record(ConfigurationManager.this.mUpsight);
        }
    }

    /* renamed from: com.upsight.android.analytics.internal.configuration.ConfigurationManager.3 */
    class C08753 implements UpsightDataStoreListener<Set<UpsightConfiguration>> {
        C08753() {
        }

        public void onSuccess(Set<UpsightConfiguration> result) {
            for (UpsightConfiguration configuration : result) {
                ConfigurationManager.this.mDataStore.remove(configuration);
            }
        }

        public void onFailure(UpsightException exception) {
        }
    }

    public static final class Config {
        public final long requestInterval;

        Config(long requestInterval) {
            this.requestInterval = requestInterval;
        }

        public boolean isValid() {
            return this.requestInterval > 0;
        }

        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            if (((Config) o).requestInterval != this.requestInterval) {
                return false;
            }
            return true;
        }
    }

    ConfigurationManager(UpsightContext upsight, UpsightDataStore dataStore, ConfigurationResponseParser responseParser, ManagerConfigParser managerConfigParser, Scheduler scheduler, UpsightLogger logger) {
        this.mIsLaunched = false;
        this.mSyncAction = new C08742();
        this.mUpsight = upsight;
        this.mDataStore = dataStore;
        this.mResponseParser = responseParser;
        this.mConfigParser = managerConfigParser;
        this.mLogger = logger;
        this.mWorker = scheduler.createWorker();
    }

    public void launch() {
        if (!this.mIsLaunched) {
            this.mIsLaunched = true;
            this.mIsOutOfSync = true;
            this.mCurrentConfig = null;
            this.mDataStoreSubscription = this.mDataStore.subscribe(this);
            this.mWorkerSubscription = null;
            fetchCurrentConfig();
        }
    }

    private void fetchCurrentConfig() {
        this.mDataStore.fetch(UpsightConfiguration.class, new C08731());
    }

    private void applyDefaultConfiguration() {
        try {
            applyConfiguration(IOUtils.toString(this.mUpsight.getResources().openRawResource(C0863R.raw.configurator_config)));
        } catch (IOException e) {
            this.mLogger.m199e(LOG_TAG, "Could not read default config", e);
        }
    }

    private boolean applyConfiguration(String jsonConfiguration) {
        try {
            Config config = this.mConfigParser.parse(jsonConfiguration);
            if (config == null || !config.isValid()) {
                this.mLogger.m205w(LOG_TAG, "Incoming config is invalid", new Object[0]);
                return false;
            } else if (config.equals(this.mCurrentConfig)) {
                this.mLogger.m205w(LOG_TAG, "Current config is equals to incoming config, rejecting", new Object[0]);
                return true;
            } else {
                if (!(this.mWorkerSubscription == null || this.mWorkerSubscription.isUnsubscribed())) {
                    this.mWorkerSubscription.unsubscribe();
                }
                this.mWorkerSubscription = this.mWorker.schedulePeriodically(this.mSyncAction, this.mIsOutOfSync ? 0 : config.requestInterval, config.requestInterval, TimeUnit.MILLISECONDS);
                this.mIsOutOfSync = false;
                this.mCurrentConfig = config;
                return true;
            }
        } catch (IOException e) {
            this.mLogger.m199e(LOG_TAG, "Could not parse incoming configuration", e);
            return false;
        }
    }

    @Created
    public void onEndpointResponse(EndpointResponse response) {
        if (CONFIGURATION_RESPONSE_SUBTYPE.equals(response.getType())) {
            try {
                Collection<UpsightConfiguration> configs = this.mResponseParser.parse(response.getContent());
                this.mDataStore.fetch(UpsightConfiguration.class, new C08753());
                for (UpsightConfiguration config : configs) {
                    if (!config.getScope().equals(CONFIGURATION_SUBTYPE)) {
                        this.mDataStore.store(config);
                    } else if (applyConfiguration(config.getConfiguration())) {
                        this.mDataStore.store(config);
                    }
                }
            } catch (IOException e) {
                this.mLogger.m199e(LOG_TAG, "Could not parse incoming configurations", e);
            }
        }
    }

    public void terminate() {
        if (this.mDataStoreSubscription != null) {
            this.mDataStoreSubscription.unsubscribe();
            this.mDataStoreSubscription = null;
        }
        if (this.mWorkerSubscription != null) {
            this.mWorkerSubscription.unsubscribe();
            this.mWorkerSubscription = null;
        }
        this.mCurrentConfig = null;
        this.mIsLaunched = false;
    }
}
