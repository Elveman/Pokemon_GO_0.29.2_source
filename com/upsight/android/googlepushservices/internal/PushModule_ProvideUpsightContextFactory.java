package com.upsight.android.googlepushservices.internal;

import com.upsight.android.UpsightContext;
import dagger.internal.Factory;

public final class PushModule_ProvideUpsightContextFactory implements Factory<UpsightContext> {
    static final /* synthetic */ boolean $assertionsDisabled;
    private final PushModule module;

    static {
        $assertionsDisabled = !PushModule_ProvideUpsightContextFactory.class.desiredAssertionStatus();
    }

    public PushModule_ProvideUpsightContextFactory(PushModule module) {
        if ($assertionsDisabled || module != null) {
            this.module = module;
            return;
        }
        throw new AssertionError();
    }

    public UpsightContext get() {
        UpsightContext provided = this.module.provideUpsightContext();
        if (provided != null) {
            return provided;
        }
        throw new NullPointerException("Cannot return null from a non-@Nullable @Provides method");
    }

    public static Factory<UpsightContext> create(PushModule module) {
        return new PushModule_ProvideUpsightContextFactory(module);
    }
}
