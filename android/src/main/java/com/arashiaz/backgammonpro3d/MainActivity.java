package com.arashiaz.backgammonpro3d;

import android.os.Bundle;
import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;
import com.arashiaz.backgammonpro3d.core.BackgammonGame;

public class MainActivity extends AndroidApplication {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        AndroidApplicationConfiguration config = new AndroidApplicationConfiguration();
        config.useImmersiveMode = true;
        config.useAccelerometer = false;
        config.useCompass = false;
        config.numSamples = 2;
        initialize(new BackgammonGame(), config);
    }
}
