package jp.shogihome.app;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // super.onCreate の前に登録を行う（重要）
        registerPlugin(USIEnginePlugin.class);
        super.onCreate(savedInstanceState);
        android.util.Log.i("MainActivity", "SHOGI HOME STARTING");
    }
}
