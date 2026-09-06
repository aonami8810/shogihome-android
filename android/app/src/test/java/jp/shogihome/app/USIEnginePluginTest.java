package jp.shogihome.app;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;

import com.getcapacitor.Bridge;
import com.getcapacitor.JSObject;
import com.getcapacitor.PluginCall;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class USIEnginePluginTest {

    private USIEnginePlugin plugin;

    @Mock
    private Context context;

    @Mock
    private PackageManager packageManager;

    @Mock
    private Bridge bridge;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        plugin = new USIEnginePlugin();

        // Use reflection to set the bridge field in the Plugin class
        java.lang.reflect.Field bridgeField = com.getcapacitor.Plugin.class.getDeclaredField("bridge");
        bridgeField.setAccessible(true);
        bridgeField.set(plugin, bridge);

        when(bridge.getContext()).thenReturn(context);
        when(context.getPackageManager()).thenReturn(packageManager);
    }

    @Test
    public void testGetEnginesEmpty() throws JSONException {
        PluginCall call = mock(PluginCall.class);

        when(packageManager.queryIntentServices(any(Intent.class), anyInt())).thenReturn(new ArrayList<>());

        plugin.getEngines(call);

        ArgumentCaptor<JSObject> captor = ArgumentCaptor.forClass(JSObject.class);
        verify(call).resolve(captor.capture());

        JSObject result = captor.getValue();
        JSONArray engines = result.getJSONArray("engines");
        assertEquals(0, engines.length());
    }

    @Test
    public void testGetEnginesFoundAIDL() throws JSONException {
        PluginCall call = mock(PluginCall.class);

        List<ResolveInfo> services = new ArrayList<>();
        ResolveInfo ri = new ResolveInfo();
        ri.serviceInfo = new ServiceInfo();
        ri.serviceInfo.packageName = "com.test.engine";
        ri.nonLocalizedLabel = "Test Engine";
        services.add(ri);

        when(packageManager.queryIntentServices(any(Intent.class), anyInt())).thenReturn(services);

        plugin.getEngines(call);

        ArgumentCaptor<JSObject> captor = ArgumentCaptor.forClass(JSObject.class);
        verify(call).resolve(captor.capture());

        JSObject result = captor.getValue();
        JSONArray engines = result.getJSONArray("engines");
        assertEquals(1, engines.length());

        JSONObject engine = engines.getJSONObject(0);
        assertEquals("com.test.engine", engine.getString("packageName"));
        assertEquals("aidl", engine.getString("type"));
    }

    @Test
    public void testQuit() {
        PluginCall call = mock(PluginCall.class);
        when(call.getInt("sessionId")).thenReturn(1);

        // This should not crash even if session 1 doesn't exist
        plugin.quit(call);

        verify(call).resolve();
    }

    @Test
    public void testSendCommandNoSession() {
        PluginCall call = mock(PluginCall.class);
        when(call.getInt("sessionId")).thenReturn(999);
        when(call.getString("command")).thenReturn("isready");

        plugin.sendCommand(call);

        verify(call).reject("Session not found");
    }
}
