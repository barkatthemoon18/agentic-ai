package com.fuad.media.enrichment.tidal;

import com.fuad.media.enrichment.MediaEnrichmentProvider;
import com.fuad.media.enrichment.MediaEnrichmentSnapshot;
import com.fuad.media.enrichment.MediaEnrichmentSnapshot.MediaQueueItem;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.COM.COMException;
import com.sun.jna.platform.win32.COM.COMUtils;
import com.sun.jna.platform.win32.COM.Unknown;
import com.sun.jna.platform.win32.Guid.CLSID;
import com.sun.jna.platform.win32.Guid.IID;
import com.sun.jna.platform.win32.Ole32;
import com.sun.jna.platform.win32.OleAuto;
import com.sun.jna.platform.win32.WTypes;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.RECT;
import com.sun.jna.platform.win32.WinNT.HRESULT;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

public class TidalMediaEnrichmentProvider implements MediaEnrichmentProvider {
    private static final CLSID CLSID_CUI_AUTOMATION = new CLSID("FF48DBA4-60EF-4201-AA87-54103EEF594E");
    private static final IID IID_IUI_AUTOMATION = new IID("30CBE57D-D9D0-452A-AB13-7AC5AC4825EE");
    private static final Pattern QUALITY_PATTERN = Pattern.compile("\\b\\d{1,2}-bit\\s+\\d+(?:\\.\\d+)?\\s*kHz\\b",
            Pattern.CASE_INSENSITIVE);
    private static final int UIA_HYPERLINK_CONTROL_TYPE_ID = 50005;
    private static final int UIA_TEXT_CONTROL_TYPE_ID = 50020;
    private static final int UIA_DATA_ITEM_CONTROL_TYPE_ID = 50029;
    private static final int MAX_QUEUE_ITEMS = 3;
    private static final int MAX_TREE_DEPTH = 80;
    private static final int MAX_VISITED_ELEMENTS = 15_000;
    private static final long REFRESH_NANOS = TimeUnit.SECONDS.toNanos(1);
    private final AtomicBoolean showHwnd = new AtomicBoolean(false);
    private final AtomicBoolean showTreeWalk = new AtomicBoolean(false);
    private volatile MediaEnrichmentSnapshot cached = MediaEnrichmentSnapshot.unavailable();
    private volatile long nextRefreshNanos;

    @Override
    public synchronized MediaEnrichmentSnapshot current() {
        long now = System.nanoTime();

        if (now < nextRefreshNanos) {
            return cached;
        }
        nextRefreshNanos = now + REFRESH_NANOS;
        try {
            cached = readSnapshot();
        }
        catch (RuntimeException | LinkageError e) {
            System.err.println("TIDAL ENRICHMENT -> unavailable: " + e.getMessage());
            cached = MediaEnrichmentSnapshot.unavailable();
        }
        return cached;
    }

    @Override
    public void close() {
        MediaEnrichmentProvider.super.close();
    }

    private MediaEnrichmentSnapshot readSnapshot() {
        HWND tidalWindow = findTidalWindow().orElse(null);

        if (tidalWindow == null) {
            System.out.println("[TIDAL UIA] TIDAL window not found");
            return MediaEnrichmentSnapshot.unavailable();
        }
        if (showHwnd.compareAndSet(false, true)) {
            System.out.println("[TIDAL UIA] hwnd=" + Pointer.nativeValue(tidalWindow.getPointer()));
        }
        HRESULT initialization = Ole32.INSTANCE.CoInitializeEx(Pointer.NULL, Ole32.COINIT_MULTITHREADED);
        boolean initialized = !COMUtils.FAILED(initialization);
        if (!initialized) {
            COMUtils.checkRC(initialization);
        }

        UiAutomation automation = null;
        UiAutomationTreeWalker walker = null;
        UiAutomationElement root = null;

        try {
            automation = createAutomation();
            walker = automation.rawViewWalker();
            root = automation.elementFromHandle(tidalWindow);
            if (root == null || walker == null) {
                return MediaEnrichmentSnapshot.unavailable();
            }
            ScanState state = new ScanState();
            traverse(root, walker, state, 0);
            if (showTreeWalk.compareAndSet(false, true)) {
                System.out.println("[TIDAL UIA] visited=" + state.visited + " | quality='" + state.quality + "' | nextSection="
                        + state.nextSectionSeen + " | queue=" + state.queue.size());
            }
            return new MediaEnrichmentSnapshot(state.quality, state.queue);
        }
        finally {
            release(root);
            release(walker);
            release(automation);
            if (initialized) {
                Ole32.INSTANCE.CoUninitialize();
            }
        }
    }

    private void traverse(UiAutomationElement element, UiAutomationTreeWalker walker, ScanState state, int depth) {
        if (element == null || depth > MAX_TREE_DEPTH || state.visited++ >= MAX_VISITED_ELEMENTS || state.complete()) {
            return;
        }
        String name = safeName(element);
        int controlType = safeControlType(element);
        String ariaRole = safeAriaRole(element);
        if (state.quality.isBlank() && controlType == UIA_TEXT_CONTROL_TYPE_ID && QUALITY_PATTERN.matcher(name).find()) {
            state.quality = normalizeQuality(name);
        }
        if (startsNextSection(name)) {
            state.nextSectionSeen = true;
        }
        boolean queueRow = state.nextSectionSeen && isRow(controlType, ariaRole);
        if (queueRow) {
            parseQueueRow(element, walker).ifPresent(state.queue::add);
            return;
        }
        UiAutomationElement child = safeFirstChild(walker, element);
        while (child != null) {
            UiAutomationElement next = null;
            try {
                traverse(child, walker, state, depth + 1);
                if (!state.complete()) {
                    next = safeNextSibling(walker, child);
                }
            }
            finally {
                release(child);
            }
            child = next;
        }
    }

    private Optional<MediaQueueItem> parseQueueRow(UiAutomationElement row, UiAutomationTreeWalker walker) {
        List<String> links = new ArrayList<>();
        collectLinks(row, walker, links, 0);
        if (links.size() < 2) {
            return Optional.empty();
        }
        String title = links.getFirst();
        LinkedHashSet<String> artists = new LinkedHashSet<>();
        for (int i = 1; i < links.size(); i++) {
            String artist = links.get(i);
            if (!artist.isBlank() && !artist.equalsIgnoreCase(title)) {
                artists.add(artist);
            }
        }
        if (title.isBlank() || artists.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new MediaQueueItem(title, List.copyOf(artists)));
    }

    private void collectLinks(UiAutomationElement element, UiAutomationTreeWalker walker, List<String> links, int depth) {
        if (element == null || depth > 16) {
            return;
        }
        int controlType = safeControlType(element);
        if (controlType == UIA_HYPERLINK_CONTROL_TYPE_ID) {
            String name = safeName(element);
            if (!name.isBlank()) {
                links.add(name.trim());
            }
            return;
        }
        UiAutomationElement child = safeFirstChild(walker, element);
        while (child != null) {
            UiAutomationElement next = null;
            try {
                collectLinks(child, walker, links, depth + 1);
                next = safeNextSibling(walker, child);
            }
            finally {
                release(child);
            }
            child = next;
        }
    }

    private static boolean startsNextSection(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        return name.strip().toLowerCase(Locale.ROOT).startsWith("a continuación");
    }

    private static boolean isRow(int controlType, String ariaRole) {
        return controlType == UIA_DATA_ITEM_CONTROL_TYPE_ID || "row".equalsIgnoreCase(ariaRole);
    }

    private static String normalizeQuality(String quality) {
        return quality == null ? "" : quality.trim().replaceAll("\\s+", " ");
    }

    private static UiAutomation createAutomation() {
        PointerByReference ref = new PointerByReference();
        HRESULT result = Ole32.INSTANCE.CoCreateInstance(CLSID_CUI_AUTOMATION, Pointer.NULL, WTypes.CLSCTX_INPROC_SERVER,
                IID_IUI_AUTOMATION, ref);
        COMUtils.checkRC(result);
        Pointer pointer = ref.getValue();
        if (pointer == null) {
            throw new IllegalStateException("IUIAutomation pointer is null");
        }
        return new UiAutomation(pointer);
    }

    private static Optional<HWND> findTidalWindow() {
        Set<Long> tidalPids = findTidalProcessIds();
        if (tidalPids.isEmpty()) {
            return Optional.empty();
        }
        HWND[] bestWindow = new HWND[1];
        long[] bestArea = new long[] { -1L };
        User32.INSTANCE.EnumWindows((window, data) -> {
            if (!User32.INSTANCE.IsWindowVisible(window)) {
                return true;
            }
            IntByReference processId = new IntByReference();
            User32.INSTANCE.GetWindowThreadProcessId(window, processId);
            if (!tidalPids.contains(Integer.toUnsignedLong(processId.getValue()))) {
                return true;
            }
            char[] className = new char[256];
            User32.INSTANCE.GetClassName(window, className, className.length);
            String windowClass = Native.toString(className);
            if (!windowClass.startsWith("Chrome_WidgetWin_")) {
                return true;
            }
            RECT rectangle = new RECT();
            if (!User32.INSTANCE.GetWindowRect(window, rectangle)) {
                return true;
            }
            long width = Math.max(0, rectangle.right - rectangle.left);
            long height = Math.max(0, rectangle.bottom - rectangle.top);
            long area = width * height;
            if (area > bestArea[0]) {
                bestArea[0] = area;
                bestWindow[0] = window;
            }
            return true;
        }, null);
        return Optional.ofNullable(bestWindow[0]);
    }

    private static Set<Long> findTidalProcessIds() {
        LinkedHashSet<Long> processIds = new LinkedHashSet<>();

        ProcessHandle.allProcesses().forEach(process -> {
            Optional<String> command = process.info().command();
            if (command.isEmpty()) {
                return;
            }
            String executable;

            try {
                executable = Path.of(command.get()).getFileName().toString();
            }
            catch (RuntimeException e) {
                return;
            }
            if ("TIDAL.exe".equalsIgnoreCase(executable)) {
                processIds.add(process.pid());
            }
        });
        return processIds;
    }

    private static String safeName(UiAutomationElement element) {
        try {
            return element.currentName();
        }
        catch (RuntimeException e) {
            return "";
        }
    }

    private static int safeControlType(UiAutomationElement element) {
        try {
            return element.currentControlType();
        }
        catch (RuntimeException e) {
            return -1;
        }
    }

    private static String safeAriaRole(UiAutomationElement element) {
        try {
            return element.currentAriaRole();
        }
        catch (RuntimeException e) {
            return "";
        }
    }

    private static UiAutomationElement safeFirstChild(UiAutomationTreeWalker walker, UiAutomationElement parent) {
        try {
            return walker.firstChild(parent);
        }
        catch (RuntimeException e) {
            return null;
        }
    }

    private static UiAutomationElement safeNextSibling(UiAutomationTreeWalker walker, UiAutomationElement element) {
        try {
            return walker.nextSibling(element);
        }
        catch (RuntimeException e) {
            return null;
        }
    }

    private static void release(Unknown unknown) {
        if (unknown == null || unknown.getPointer() == null) {
            return;
        }
        unknown.Release();
    }

    private static final class ScanState {
        private final List<MediaQueueItem> queue = new ArrayList<>();
        private String quality = "";
        private boolean nextSectionSeen;
        private int visited;
        private boolean complete() {
            return !quality.isBlank()
                    && queue.size()
                    >= MAX_QUEUE_ITEMS;
        }
    }

    private static final class UiAutomation extends Unknown {
        private UiAutomation(Pointer ptr) {
            super(ptr);
        }

        private UiAutomationElement elementFromHandle(HWND window) {
            PointerByReference reference = new PointerByReference();
            HRESULT result = (HRESULT) _invokeNativeObject(6, new Object[] { getPointer(), window, reference },
                            HRESULT.class);
            COMUtils.checkRC(result);
            Pointer pointer = reference.getValue();
            return pointer == null ? null : new UiAutomationElement(pointer);
        }

        private UiAutomationTreeWalker rawViewWalker() {
            PointerByReference reference = new PointerByReference();
            HRESULT result = (HRESULT) _invokeNativeObject(16, new Object[] { getPointer(), reference },
                            HRESULT.class);
            COMUtils.checkRC(result);
            Pointer pointer = reference.getValue();
            return pointer == null ? null : new UiAutomationTreeWalker(pointer);
        }
    }

    private static final class UiAutomationTreeWalker extends Unknown {
        private UiAutomationTreeWalker(Pointer ptr) {
            super(ptr);
        }

        private UiAutomationElement firstChild(UiAutomationElement element) {
            PointerByReference reference = new PointerByReference();
            HRESULT result = (HRESULT) _invokeNativeObject(4, new Object[] { getPointer(), element.getPointer() , reference },
                    HRESULT.class);
            COMUtils.checkRC(result);
            Pointer pointer = reference.getValue();
            return pointer == null ? null : new UiAutomationElement(pointer);
        }

        private UiAutomationElement nextSibling(UiAutomationElement element) {
            PointerByReference reference = new PointerByReference();
            HRESULT result = (HRESULT) _invokeNativeObject(6, new Object[] { getPointer(), element.getPointer(), reference }, HRESULT.class);
            COMUtils.checkRC(result);
            Pointer pointer = reference.getValue();
            return pointer == null ? null : new UiAutomationElement(pointer);
        }
    }

    private static final class UiAutomationElement extends Unknown {
        private UiAutomationElement(Pointer ptr) {
            super(ptr);
        }

        private int currentControlType() {
            IntByReference result = new IntByReference();
            HRESULT hr = (HRESULT) _invokeNativeObject(21, new Object[] { getPointer(), result }, HRESULT.class);
            COMUtils.checkRC(hr);
            return result.getValue();
        }

        private String currentName() {
            return readBstrProperty(23);
        }

        private String currentAriaRole() {
            return readBstrProperty(45);
        }

        private String readBstrProperty(int vtableIndex) {
            WTypes.BSTRByReference reference = new WTypes.BSTRByReference();
            HRESULT result = (HRESULT) _invokeNativeObject(vtableIndex, new Object[] { getPointer(), reference }, HRESULT.class);
            COMUtils.checkRC(result);
            WTypes.BSTR value = reference.getValue();
            if (value == null || value.getPointer() == null) {
                return "";
            }
            try {
                return value.getValue();
            }
            finally {
                OleAuto.INSTANCE.SysFreeString(value);
            }
        }
    }
}
