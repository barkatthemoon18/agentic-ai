package com.fuad.audio.output.windows;

import com.fuad.audio.output.AudioOutputProvider;
import com.fuad.audio.output.AudioOutputSnapshot;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.platform.win32.COM.COMUtils;
import com.sun.jna.platform.win32.COM.Unknown;
import com.sun.jna.platform.win32.Guid.REFIID;
import com.sun.jna.platform.win32.Guid.GUID;
import com.sun.jna.platform.win32.Guid.IID;
import com.sun.jna.platform.win32.Guid.CLSID;
import com.sun.jna.platform.win32.Ole32;
import com.sun.jna.platform.win32.Variant;
import com.sun.jna.platform.win32.WTypes;
import com.sun.jna.platform.win32.WinNT.HRESULT;
import com.sun.jna.ptr.FloatByReference;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;

import java.util.OptionalDouble;

public final class WindowsAudioOutputProvider implements AudioOutputProvider {
    private static final CLSID CLSID_MMDEVICE_ENUMERATOR = new CLSID("BCDE0395-E52F-467C-8E3D-C4579291692E");
    private static final IID IID_IMMDEVICE_ENUMERATOR = new IID("A95664D2-9614-4F35-A746-DE8DB63617E6");
    private static final IID IID_IAUDIO_ENDPOINT_VOLUME = new IID("5CDF2C82-841E-4546-9722-0CF74078229A");
    private static final GUID DEVICE_PROPERTY_SET = new GUID("A45C254E-DF1C-4EFD-8020-67D146A850E0");
    private static final int PKEY_DEVICE_FRIENDLY_NAME = 14;
    private static final int PKEY_DEVICE_DESCRIPTION = 2;
    private static final int E_RENDER = 0;
    private static final int E_MULTIMEDIA = 1;
    private static final int STGM_READ = 0;
    private static final int ENDPOINT_HARDWARE_SUPPORT_VOLUME = 0x00000001;
    private static final int PROPVARIANT_SIZE = Native.POINTER_SIZE == 8 ? 24 : 16;
    private static final long PROPVARIANT_VALUE_OFFSET = 8L;

    @Override
    public AudioOutputSnapshot current() {
        boolean initialized;

        HRESULT initialization = Ole32.INSTANCE.CoInitializeEx(Pointer.NULL, Ole32.COINIT_MULTITHREADED);

        initialized = COMUtils.FAILED(initialization);
        if (!initialized) {
            COMUtils.checkRC(initialization);
        }
        IMMDeviceEnumerator enumerator = null;
        IMMDevice device = null;
        try {
            enumerator = createEnumerator();
            device = defaultOutputDevice(enumerator);
            String endpointId = readEndpointId(device);
            String deviceName = readFriendlyName(device);
            if (deviceName.isBlank()) {
                deviceName = endpointId;
            }
            VolumeInfo volume = readVolumeInfo(device);
            System.out.println("[AUDIO OUTPUT] device=" + deviceName + " | endpoint=" + endpointId + " | hardwareVolume="
                            + volume.hardwareVolumeSupported() + " | volume=" + (volume.volume().isPresent() ?
                    volume.volume().getAsDouble() : "N/A"));
            return new AudioOutputSnapshot(true, endpointId, deviceName, volume.volume(), volume.hardwareVolumeSupported());
        }
        catch (RuntimeException | LinkageError e) {
            System.err.println("WINDOWS AUDIO OUTPUT -> unavailable" + e.getMessage());
            return AudioOutputSnapshot.unavailable();
        }
        finally {
            release(device);
            release(enumerator);
            if (initialized) {
                Ole32.INSTANCE.CoUninitialize();
            }
            Ole32.INSTANCE.CoUninitialize();
        }
    }

    private static IMMDeviceEnumerator createEnumerator() {
        PointerByReference reference = new PointerByReference();
        HRESULT result = Ole32.INSTANCE.CoCreateInstance(CLSID_MMDEVICE_ENUMERATOR, Pointer.NULL, WTypes.CLSCTX_ALL, IID_IMMDEVICE_ENUMERATOR, reference);
        COMUtils.checkRC(result);
        return new IMMDeviceEnumerator(reference.getValue());
    }

    private static IMMDevice defaultOutputDevice(IMMDeviceEnumerator enumerator) {
        PointerByReference reference = new PointerByReference();
        HRESULT result = enumerator.getDefaultAudioEndpoint(E_RENDER, E_MULTIMEDIA, reference);
        COMUtils.checkRC(result);
        return new IMMDevice(reference.getValue());
    }

    private static String readEndpointId(IMMDevice device) {
        PointerByReference reference = new PointerByReference();
        HRESULT result = device.getId(reference);
        COMUtils.checkRC(result);
        Pointer pointer = reference.getValue();
        if (pointer == null) {
            return "";
        }
        try {
            return pointer.getWideString(0);
        }
        finally {
            Ole32.INSTANCE.CoTaskMemFree(pointer);
        }
    }

    private static String readFriendlyName(IMMDevice device) {
        PointerByReference reference = new PointerByReference();
        HRESULT result = device.openPropertyStore(STGM_READ, reference);
        COMUtils.checkRC(result);
        IPropertyStore properties = new IPropertyStore(reference.getValue());
        try {
            String friendlyName = readStringProperty(properties, PKEY_DEVICE_FRIENDLY_NAME);
            if (!friendlyName.isBlank()) {
                return friendlyName;
            }
            return readStringProperty(properties, PKEY_DEVICE_DESCRIPTION);
        }
        finally {
            release(properties);
        }
    }

    private static String readStringProperty(IPropertyStore properties, int propertyId) {
        PropertyKey key = new PropertyKey(DEVICE_PROPERTY_SET, propertyId);
        Memory value = new Memory(PROPVARIANT_SIZE);
        value.clear();
        HRESULT result = properties.getValue(key, value);
        COMUtils.checkRC(result);
        try {
            int variantType = Short.toUnsignedInt(value.getShort(0));
            if (variantType != Variant.VT_LPWSTR) {
                return "";
            }
            Pointer stringPointer = value.getPointer(PROPVARIANT_VALUE_OFFSET);
            if (stringPointer == null) {
                return "";
            }
            return stringPointer.getWideString(0);
        }
        finally {
            PropVariantApi.INSTANCE.PropVariantClear(value);
        }
    }

    private static VolumeInfo readVolumeInfo(IMMDevice device) {
        PointerByReference reference = new PointerByReference();
        HRESULT result = device.activate(new REFIID(IID_IAUDIO_ENDPOINT_VOLUME), WTypes.CLSCTX_ALL, Pointer.NULL, reference);
        COMUtils.checkRC(result);
        IAudioEndpointVolume endpointVolume = new IAudioEndpointVolume(reference.getValue());
        try {
            IntByReference supportMask = new IntByReference();
            COMUtils.checkRC(endpointVolume.queryHardwareSupport(supportMask));
            boolean hardwareVolume = (supportMask.getValue() & ENDPOINT_HARDWARE_SUPPORT_VOLUME) != 0;
            if (!hardwareVolume) {
                return new VolumeInfo(OptionalDouble.empty(), false);
            }
            FloatByReference level = new FloatByReference();
            COMUtils.checkRC(endpointVolume.getMasterVolumeLevelScalar(level));
            double normalized = Math.clamp(level.getValue(), 0.0, 1.0);
            return new VolumeInfo(OptionalDouble.of(normalized), true);
        }
        finally {
            release(endpointVolume);
        }
    }

    private static void release(Unknown unknown) {
        if (unknown == null || unknown.getPointer() == null) {
            return;
        }
        unknown.Release();
    }

    private record VolumeInfo(
            OptionalDouble volume,
            boolean hardwareVolumeSupported) {
        /* Empty intentionally */
    }

    private static final class IMMDeviceEnumerator extends Unknown {
        IMMDeviceEnumerator(Pointer pointer) {
            super(pointer);
        }

        HRESULT getDefaultAudioEndpoint(int dataFlow, int role, PointerByReference endpoint) {
            return (HRESULT) _invokeNativeObject(4, new Object[] { getPointer(), dataFlow, role, endpoint }, HRESULT.class);
        }
    }

    private static final class IMMDevice extends Unknown {
        IMMDevice(Pointer pointer) {
            super(pointer);
        }

        HRESULT activate(REFIID iid, int context, Pointer activationParameters, PointerByReference result) {
            return (HRESULT) _invokeNativeObject(3, new Object[] { getPointer(), iid, context, activationParameters, result }, HRESULT.class);
        }

        HRESULT openPropertyStore(int access, PointerByReference properties) {
            return (HRESULT) _invokeNativeObject(4, new Object[] { getPointer(), access, properties }, HRESULT.class);
        }

        HRESULT getId(PointerByReference endpointId) {
            return (HRESULT)  _invokeNativeObject(5, new Object[] { getPointer(), endpointId }, HRESULT.class);
        }
    }

    private static final class IPropertyStore extends Unknown {
        IPropertyStore(Pointer pointer) {
            super(pointer);
        }

        HRESULT getValue(PropertyKey key, Pointer value) {
            return (HRESULT) _invokeNativeObject(5, new Object[] { getPointer(), key, value }, HRESULT.class);
        }
    }

    private static final class IAudioEndpointVolume extends Unknown {
        IAudioEndpointVolume(Pointer pointer) {
            super(pointer);
        }

        HRESULT getMasterVolumeLevelScalar(FloatByReference level) {
            return (HRESULT) _invokeNativeObject(9, new Object[] { getPointer(), level }, HRESULT.class);
        }

        HRESULT queryHardwareSupport(IntByReference supportMask) {
            return (HRESULT) _invokeNativeObject(19, new Object[]  { getPointer(), supportMask }, HRESULT.class);
        }
    }

    @Structure.FieldOrder({
            "fmtid",
            "pid"
    })
    public static final class PropertyKey extends Structure {
        public GUID fmtid;
        public int pid;

        public PropertyKey(GUID fmtid, int pid) {
            this.fmtid = new GUID(fmtid);
            this.pid = pid;
        }
    }

    private interface PropVariantApi extends StdCallLibrary {
        PropVariantApi INSTANCE = Native.load("Ole32", PropVariantApi.class, W32APIOptions.DEFAULT_OPTIONS);
        HRESULT PropVariantClear(Pointer value);
    }
}
