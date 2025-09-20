package org.avium.systemui.lockscreen.di;

import org.avium.systemui.lockscreen.CustomLockscreenClockManager;
import org.avium.systemui.lockscreen.NativeLockscreenViewHider;
import org.avium.systemui.lockscreen.util.SystemPropertiesWatcher;
import dagger.Module;
import dagger.Provides;
import javax.inject.Singleton;
import android.content.Context;
import com.android.systemui.dagger.SysUISingleton;
import org.avium.systemui.lockscreen.sections.CustomClockSection;
import org.avium.systemui.lockscreen.CustomLockscreenRepository;

@Module
public class AviumLockscreenModule {

    @Provides
    @SysUISingleton
    public NativeLockscreenViewHider provideNativeLockscreenViewHider(Context context) {
        return new NativeLockscreenViewHider(context);
    }
    
    @Provides
    @SysUISingleton
    public CustomLockscreenClockManager provideCustomLockscreenClockManager(
            Context context, 
            NativeLockscreenViewHider nativeLockscreenViewHider,
            SystemPropertiesWatcher propertiesWatcher
    ) {
        return new CustomLockscreenClockManager(context, nativeLockscreenViewHider, propertiesWatcher);
    }

    @Provides
    @SysUISingleton
    public CustomClockSection provideCustomClockSection(CustomLockscreenClockManager manager) {
        return new CustomClockSection(manager);
    }

    @Provides
    @SysUISingleton
    public CustomLockscreenRepository provideCustomLockscreenRepository(Context context) {
        return new CustomLockscreenRepository(context);
    }
    
    @Provides
    @SysUISingleton
    public SystemPropertiesWatcher provideSystemPropertiesWatcher(Context context) {
        return new SystemPropertiesWatcher(context);
    }
}