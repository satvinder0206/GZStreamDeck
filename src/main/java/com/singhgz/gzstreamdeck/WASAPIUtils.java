package com.singhgz.gzstreamdeck;

import com.sun.jna.Function;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.*;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;

import java.util.ArrayList;
import java.util.List;

/**
 * Raw COM Invoker for Windows Core Audio API (WASAPI) via JNA.
 * Eliminates the need for 3rd-party DLLs.
 */
public class WASAPIUtils {

    // Custom PSAPI binding to bypass JNA versioning issues
    public interface CustomPsapi extends StdCallLibrary {
        CustomPsapi INSTANCE = Native.load("psapi", CustomPsapi.class, W32APIOptions.UNICODE_OPTIONS);
        int GetModuleBaseNameW(WinNT.HANDLE hProcess, Pointer hModule, char[] lpBaseName, int nSize);
    }

    private static final Guid.CLSID CLSID_MMDeviceEnumerator = new Guid.CLSID("{BCDE0395-E52F-467C-8E3D-C4579291692E}");
    private static final Guid.IID IID_IMMDeviceEnumerator = new Guid.IID("{A95664D2-9614-4F35-A746-DE8DB63617E6}");
    private static final Guid.IID IID_IAudioSessionManager2 = new Guid.IID("{77AA99A0-1BD6-484F-8BC7-2C654C9A9B6F}");
    private static final Guid.IID IID_IAudioSessionControl2 = new Guid.IID("{bfb7ff88-7239-4fc9-8fa2-07c950be9c6d}");
    private static final Guid.IID IID_ISimpleAudioVolume = new Guid.IID("{87CE5498-68D6-44E5-9215-6DA47EF883D8}");
    private static final Guid.IID IID_IAudioEndpointVolume = new Guid.IID("{5CDF2C82-841E-4546-9722-0CF74078229A}");
    
    private static final int CLSCTX_ALL = 23;

    public static List<String> getActiveAudioApps() {
        List<String> apps = new ArrayList<>();
        try {
            Ole32.INSTANCE.CoInitializeEx(Pointer.NULL, Ole32.COINIT_MULTITHREADED);

            PointerByReference ppv = new PointerByReference();
            WinNT.HRESULT hr = Ole32.INSTANCE.CoCreateInstance(
                    CLSID_MMDeviceEnumerator, null, CLSCTX_ALL, IID_IMMDeviceEnumerator, ppv
            );
            if (!hr.equals(W32Errors.S_OK)) return apps;
            Pointer pEnumerator = ppv.getValue();

            PointerByReference ppEndpoint = new PointerByReference();
            invoke(pEnumerator, 4, 0, 1, ppEndpoint);
            Pointer pEndpoint = ppEndpoint.getValue();

            PointerByReference ppSessionManager = new PointerByReference();
            invoke(pEndpoint, 3, IID_IAudioSessionManager2, CLSCTX_ALL, null, ppSessionManager);
            Pointer pSessionManager = ppSessionManager.getValue();

            PointerByReference ppSessionEnum = new PointerByReference();
            invoke(pSessionManager, 5, ppSessionEnum);
            Pointer pSessionEnum = ppSessionEnum.getValue();

            IntByReference pCount = new IntByReference();
            invoke(pSessionEnum, 3, pCount);
            int count = pCount.getValue();

            for (int i = 0; i < count; i++) {
                PointerByReference ppSessionCtrl = new PointerByReference();
                invoke(pSessionEnum, 4, i, ppSessionCtrl);
                Pointer pSessionCtrl = ppSessionCtrl.getValue();

                PointerByReference ppSessionCtrl2 = new PointerByReference();
                invoke(pSessionCtrl, 0, IID_IAudioSessionControl2, ppSessionCtrl2);
                Pointer pSessionCtrl2 = ppSessionCtrl2.getValue();

                if (pSessionCtrl2 != null) {
                    IntByReference pPid = new IntByReference();
                    invoke(pSessionCtrl2, 14, pPid); // GetProcessId
                    int pid = pPid.getValue();

                    if (pid > 0) {
                        String name = getProcessName(pid);
                        if (name != null && !name.isEmpty() && !apps.contains(name)) {
                            apps.add(name);
                        }
                    }
                    invoke(pSessionCtrl2, 2); // Release
                }
                invoke(pSessionCtrl, 2); // Release
            }

            invoke(pSessionEnum, 2);
            invoke(pSessionManager, 2);
            invoke(pEndpoint, 2);
            invoke(pEnumerator, 2);

        } catch (Exception e) {
            e.printStackTrace();
        }
        return apps;
    }

    public static void setAppVolume(String targetAppName, float volume) {
        if ("Master Volume".equals(targetAppName)) {
            setMasterVolume(volume);
            return;
        }

        try {
            Ole32.INSTANCE.CoInitializeEx(Pointer.NULL, Ole32.COINIT_MULTITHREADED);
            PointerByReference ppv = new PointerByReference();
            if (!Ole32.INSTANCE.CoCreateInstance(CLSID_MMDeviceEnumerator, null, CLSCTX_ALL, IID_IMMDeviceEnumerator, ppv).equals(W32Errors.S_OK)) return;
            
            Pointer pEnumerator = ppv.getValue();
            PointerByReference ppEndpoint = new PointerByReference();
            invoke(pEnumerator, 4, 0, 1, ppEndpoint);
            Pointer pEndpoint = ppEndpoint.getValue();

            PointerByReference ppSessionManager = new PointerByReference();
            invoke(pEndpoint, 3, IID_IAudioSessionManager2, CLSCTX_ALL, null, ppSessionManager);
            Pointer pSessionManager = ppSessionManager.getValue();

            PointerByReference ppSessionEnum = new PointerByReference();
            invoke(pSessionManager, 5, ppSessionEnum);
            Pointer pSessionEnum = ppSessionEnum.getValue();

            IntByReference pCount = new IntByReference();
            invoke(pSessionEnum, 3, pCount);

            for (int i = 0; i < pCount.getValue(); i++) {
                PointerByReference ppSessionCtrl = new PointerByReference();
                invoke(pSessionEnum, 4, i, ppSessionCtrl);
                Pointer pSessionCtrl = ppSessionCtrl.getValue();

                PointerByReference ppSessionCtrl2 = new PointerByReference();
                invoke(pSessionCtrl, 0, IID_IAudioSessionControl2, ppSessionCtrl2);
                Pointer pSessionCtrl2 = ppSessionCtrl2.getValue();

                if (pSessionCtrl2 != null) {
                    IntByReference pPid = new IntByReference();
                    invoke(pSessionCtrl2, 14, pPid);
                    int pid = pPid.getValue();

                    if (pid > 0 && targetAppName.equals(getProcessName(pid))) {
                        PointerByReference ppSimpleVol = new PointerByReference();
                        invoke(pSessionCtrl, 0, IID_ISimpleAudioVolume, ppSimpleVol);
                        Pointer pSimpleVol = ppSimpleVol.getValue();
                        
                        if (pSimpleVol != null) {
                            invoke(pSimpleVol, 3, volume, null); // SetMasterVolume
                            invoke(pSimpleVol, 2);
                        }
                    }
                    invoke(pSessionCtrl2, 2);
                }
                invoke(pSessionCtrl, 2);
            }
            invoke(pSessionEnum, 2);
            invoke(pSessionManager, 2);
            invoke(pEndpoint, 2);
            invoke(pEnumerator, 2);

        } catch (Exception e) { e.printStackTrace(); }
    }

    private static void setMasterVolume(float volume) {
        try {
            Ole32.INSTANCE.CoInitializeEx(Pointer.NULL, Ole32.COINIT_MULTITHREADED);
            PointerByReference ppv = new PointerByReference();
            if (!Ole32.INSTANCE.CoCreateInstance(CLSID_MMDeviceEnumerator, null, CLSCTX_ALL, IID_IMMDeviceEnumerator, ppv).equals(W32Errors.S_OK)) return;
            
            Pointer pEnumerator = ppv.getValue();
            PointerByReference ppEndpoint = new PointerByReference();
            invoke(pEnumerator, 4, 0, 1, ppEndpoint);
            Pointer pEndpoint = ppEndpoint.getValue();

            PointerByReference ppEndpointVol = new PointerByReference();
            invoke(pEndpoint, 3, IID_IAudioEndpointVolume, CLSCTX_ALL, null, ppEndpointVol);
            Pointer pEndpointVol = ppEndpointVol.getValue();

            if (pEndpointVol != null) {
                invoke(pEndpointVol, 7, volume, null); // SetMasterVolumeLevelScalar
                invoke(pEndpointVol, 2);
            }
            
            invoke(pEndpoint, 2);
            invoke(pEnumerator, 2);
        } catch (Exception e) { e.printStackTrace(); }
    }

    // --- Core Architecture Engine ---
    
    private static void invoke(Pointer p, int vtableIndex, Object... args) {
        Pointer vtable = p.getPointer(0);
        Pointer funcPtr = vtable.getPointer((long) vtableIndex * Native.POINTER_SIZE);
        Function func = Function.getFunction(funcPtr);
        Object[] allArgs = new Object[args.length + 1];
        allArgs[0] = p; // 'this' pointer in C++
        System.arraycopy(args, 0, allArgs, 1, args.length);
        func.invoke(WinNT.HRESULT.class, allArgs);
    }

    private static String getProcessName(int pid) {
        WinNT.HANDLE hProcess = Kernel32.INSTANCE.OpenProcess(
                0x0400 | 0x0010, // PROCESS_QUERY_INFORMATION | PROCESS_VM_READ
                false, pid
        );
        if (hProcess != null) {
            char[] name = new char[512];
            // Use our CustomPsapi to safely pass Pointer.NULL
            CustomPsapi.INSTANCE.GetModuleBaseNameW(hProcess, Pointer.NULL, name, name.length);
            Kernel32.INSTANCE.CloseHandle(hProcess);
            return Native.toString(name).trim();
        }
        return null;
    }
}