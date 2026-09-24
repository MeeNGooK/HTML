package com.meengook.instapocket;

import com.getcapacitor.BridgeActivity;
import android.os.Bundle;

public class MainActivity extends BridgeActivity {
    @Override public void onCreate(Bundle state) {
        registerPlugin(PocketMediaPlugin.class);
        super.onCreate(state);
    }
}
