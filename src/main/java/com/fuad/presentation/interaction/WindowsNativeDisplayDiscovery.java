package com.fuad.presentation.interaction;

import com.sun.jna.Native;
import com.sun.jna.Structure;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;

import java.util.ArrayList;
import java.util.List;

final class WindowsNativeDisplayDiscovery implements NativeDisplayDiscovery {
    private static final int MONITORINFOF_PRIMARY = 1;
    private static final int EDD_GET_DEVICE_INTERFACE_NAME = 1;
    private final User32 user32;
    private final DisplayApi displayApi;

    WindowsNativeDisplayDiscovery() {
        this(User32.INSTANCE, DisplayApi.INSTANCE);
    }

    WindowsNativeDisplayDiscovery(User32 user32, DisplayApi displayApi) {
        this.user32 = user32;
        this.displayApi = displayApi;
    }

    @Override
    public List<NativeDisplay> discover() {
        List<NativeDisplay> displays = new ArrayList<>();
        user32.EnumDisplayMonitors(null, null, (monitor, hdc, rect, data) -> {
            WinUser.MONITORINFOEX info = new WinUser.MONITORINFOEX();
            if (!user32.GetMonitorInfo(monitor, info).booleanValue()) {
                return 1;
            }
            String nativeName = Native.toString(info.szDevice);
            DisplayDevice device = new DisplayDevice();
            String persistentId = nativeName;
            if (displayApi.EnumDisplayDevices(nativeName, 0, device,
                    EDD_GET_DEVICE_INTERFACE_NAME)) {
                String deviceId = Native.toString(device.deviceId);
                if (!deviceId.isBlank()) {
                    persistentId = deviceId;
                }
            }
            int width = info.rcMonitor.right - info.rcMonitor.left;
            int height = info.rcMonitor.bottom - info.rcMonitor.top;
            displays.add(new NativeDisplay(persistentId, nativeName,
                    info.rcMonitor.left, info.rcMonitor.top, width, height,
                    (info.dwFlags & MONITORINFOF_PRIMARY) != 0));
            return 1;
        }, null);
        return List.copyOf(displays);
    }

    interface DisplayApi extends StdCallLibrary {
        DisplayApi INSTANCE = Native.load("user32", DisplayApi.class,
                W32APIOptions.DEFAULT_OPTIONS);

        boolean EnumDisplayDevices(String deviceName, int deviceNumber,
                                   DisplayDevice displayDevice, int flags);
    }

    @Structure.FieldOrder({"cb", "deviceName", "deviceString", "stateFlags",
            "deviceId", "deviceKey"})
    public static class DisplayDevice extends Structure {
        public int cb;
        public char[] deviceName = new char[32];
        public char[] deviceString = new char[128];
        public int stateFlags;
        public char[] deviceId = new char[128];
        public char[] deviceKey = new char[128];

        public DisplayDevice() {
            cb = size();
        }
    }
}
