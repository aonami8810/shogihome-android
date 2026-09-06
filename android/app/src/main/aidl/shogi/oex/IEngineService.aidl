package shogi.oex;

import shogi.oex.IEngineServiceCallback;

interface IEngineService {
    void registerCallback(IEngineServiceCallback callback);
    void unregisterCallback(IEngineServiceCallback callback);
    void sendCommand(String command);
}
