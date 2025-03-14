/**
 * OSHI (https://github.com/oshi/oshi)
 *
 * Copyright (c) 2010 - 2019 The OSHI Project Team:
 * https://github.com/oshi/oshi/graphs/contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package oshi.software.os.windows;

import static oshi.software.os.OSService.State.OTHER;
import static oshi.software.os.OSService.State.RUNNING;
import static oshi.software.os.OSService.State.STOPPED;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.jna.Native; // NOSONAR squid:S1191
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Advapi32;
import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.Advapi32Util.Account;
import com.sun.jna.platform.win32.Advapi32Util.EventLogIterator;
import com.sun.jna.platform.win32.Advapi32Util.EventLogRecord;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.Kernel32Util;
import com.sun.jna.platform.win32.Psapi;
import com.sun.jna.platform.win32.Psapi.PERFORMANCE_INFORMATION;
import com.sun.jna.platform.win32.Tlhelp32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.VersionHelpers;
import com.sun.jna.platform.win32.W32ServiceManager;
import com.sun.jna.platform.win32.Win32Exception;
import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.platform.win32.WinDef.DWORD;
import com.sun.jna.platform.win32.WinError;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.platform.win32.WinNT.HANDLEByReference;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.platform.win32.Winsvc;
import com.sun.jna.platform.win32.Wtsapi32;
import com.sun.jna.platform.win32.Wtsapi32.WTS_PROCESS_INFO_EX;
import com.sun.jna.platform.win32.COM.WbemcliUtil.WmiQuery;
import com.sun.jna.platform.win32.COM.WbemcliUtil.WmiResult;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;

import oshi.software.common.AbstractOperatingSystem;
import oshi.software.os.FileSystem;
import oshi.software.os.NetworkParams;
import oshi.software.os.OSProcess;
import oshi.software.os.OSService;
import oshi.software.os.OSService.State;
import oshi.util.ParseUtil;
import oshi.util.platform.windows.PerfCounterQuery;
import oshi.util.platform.windows.PerfCounterWildcardQuery;
import oshi.util.platform.windows.PerfCounterWildcardQuery.PdhCounterWildcardProperty;
import oshi.util.platform.windows.WmiQueryHandler;
import oshi.util.platform.windows.WmiUtil;

public class WindowsOperatingSystem extends AbstractOperatingSystem {

    private static final Logger LOG = LoggerFactory.getLogger(WindowsOperatingSystem.class);

    private static final long BOOTTIME = querySystemBootTime();

    enum OSVersionProperty {
        Version, ProductType, BuildNumber, CSDVersion, SuiteMask;
    }

    enum BitnessProperty {
        AddressWidth;
    }

    enum ProcessProperty {
        ProcessId, CommandLine;
    }

    private static final String PROCESS_BASE_CLASS = "Win32_Process";

    // Properties to get from WMI if WTSEnumerateProcesses doesn't work
    enum ProcessXPProperty {
        ProcessId, Name, KernelModeTime, UserModeTime, ThreadCount, PageFileUsage, HandleCount, ExecutablePath;
    }

    enum ProcessPerformanceProperty implements PdhCounterWildcardProperty {
        // First element defines WMI instance name field and PDH instance filter
        Name(PerfCounterQuery.NOT_TOTAL_INSTANCES),
        // Remaining elements define counters
        Priority("Priority Base"), //
        CreationDate("Elapsed Time"), //
        ProcessId("ID Process"), //
        ParentProcessId("Creating Process ID"), //
        ReadTransferCount("IO Read Bytes/sec"), //
        WriteTransferCount("IO Write Bytes/sec"), //
        PrivatePageCount("Working Set - Private");

        private final String counter;

        ProcessPerformanceProperty(String counter) {
            this.counter = counter;
        }

        @Override
        public String getCounter() {
            return counter;
        }
    }

    private final PerfCounterWildcardQuery<ProcessPerformanceProperty> processPerformancePerfCounters = new PerfCounterWildcardQuery<>(
            ProcessPerformanceProperty.class, "Process", "Win32_Process WHERE NOT Name LIKE\"%_Total\"",
            "Process Information");

    private static final boolean IS_VISTA_OR_GREATER = VersionHelpers.IsWindowsVistaOrGreater();
    private static final boolean IS_WINDOWS7_OR_GREATER = VersionHelpers.IsWindows7OrGreater();

    static {
        enableDebugPrivilege();
    }

    private final WmiQueryHandler wmiQueryHandler = WmiQueryHandler.createInstance();

    /**
     * <p>
     * Constructor for WindowsOperatingSystem.
     * </p>
     */
    @SuppressWarnings("deprecation")
    public WindowsOperatingSystem() {
        this.version = new WindowsOSVersionInfoEx();
    }

    @Override
    public String queryManufacturer() {
        return "Microsoft";
    }

    @Override
    public FamilyVersionInfo queryFamilyVersionInfo() {
        WmiQuery<OSVersionProperty> osVersionQuery = new WmiQuery<>("Win32_OperatingSystem", OSVersionProperty.class);
        WmiResult<OSVersionProperty> versionInfo = WmiQueryHandler.createInstance().queryWMI(osVersionQuery);
        if (versionInfo.getResultCount() < 1) {
            return new FamilyVersionInfo("Windows", new OSVersionInfo(System.getProperty("os.version"), null, null));
        }
        // Guaranteed that versionInfo is not null and lists non-empty
        // before calling the parse*() methods
        int suiteMask = WmiUtil.getUint32(versionInfo, OSVersionProperty.SuiteMask, 0);
        String buildNumber = WmiUtil.getString(versionInfo, OSVersionProperty.BuildNumber, 0);
        String version = parseVersion(versionInfo, suiteMask, buildNumber);
        String codeName = parseCodeName(suiteMask);
        return new FamilyVersionInfo("Windows", new OSVersionInfo(version, codeName, buildNumber));
    }

    private static String parseVersion(WmiResult<OSVersionProperty> versionInfo, int suiteMask, String buildNumber) {

        // Initialize a default, sane value
        String version = System.getProperty("os.version");

        // Version is major.minor.build. Parse the version string for
        // major/minor and get the build number separately
        String[] verSplit = WmiUtil.getString(versionInfo, OSVersionProperty.Version, 0).split("\\D");
        int major = verSplit.length > 0 ? ParseUtil.parseIntOrDefault(verSplit[0], 0) : 0;
        int minor = verSplit.length > 1 ? ParseUtil.parseIntOrDefault(verSplit[1], 0) : 0;

        // see
        // http://msdn.microsoft.com/en-us/library/windows/desktop/ms724833%28v=vs.85%29.aspx
        boolean ntWorkstation = WmiUtil.getUint32(versionInfo, OSVersionProperty.ProductType,
                0) == WinNT.VER_NT_WORKSTATION;
        switch (major) {
        case 10:
            if (minor == 0) {
                if (ntWorkstation) {
                    version = "10";
                } else {
                    // Build numbers greater than 17762 is Server 2019 for OS
                    // Version 10.0
                    version = (ParseUtil.parseLongOrDefault(buildNumber, 0L) > 17762) ? "Server 2019" : "Server 2016";
                }
            }
            break;
        case 6:
            if (minor == 3) {
                version = ntWorkstation ? "8.1" : "Server 2012 R2";
            } else if (minor == 2) {
                version = ntWorkstation ? "8" : "Server 2012";
            } else if (minor == 1) {
                version = ntWorkstation ? "7" : "Server 2008 R2";
            } else if (minor == 0) {
                version = ntWorkstation ? "Vista" : "Server 2008";
            }
            break;
        case 5:
            if (minor == 2) {
                if ((suiteMask & 0x00008000) != 0) {// VER_SUITE_WH_SERVER
                    version = "Home Server";
                } else if (ntWorkstation) {
                    version = "XP"; // 64 bits
                } else {
                    version = User32.INSTANCE.GetSystemMetrics(WinUser.SM_SERVERR2) != 0 ? "Server 2003"
                            : "Server 2003 R2";
                }
            } else if (minor == 1) {
                version = "XP"; // 32 bits
            } else if (minor == 0) {
                version = "2000";
            }
            break;
        default:
            break;
        }

        String sp = WmiUtil.getString(versionInfo, OSVersionProperty.CSDVersion, 0);
        if (!sp.isEmpty() && !"unknown".equals(sp)) {
            version = version + " " + sp.replace("Service Pack ", "SP");
        }

        return version;
    }

    /**
     * Gets suites available on the system and return as a codename
     *
     * @param suiteMask
     *
     * @return Suites
     */
    private static String parseCodeName(int suiteMask) {
        List<String> suites = new ArrayList<>();
        if ((suiteMask & 0x00000002) != 0) {
            suites.add("Enterprise");
        }
        if ((suiteMask & 0x00000004) != 0) {
            suites.add("BackOffice");
        }
        if ((suiteMask & 0x00000008) != 0) {
            suites.add("Communication Server");
        }
        if ((suiteMask & 0x00000080) != 0) {
            suites.add("Datacenter");
        }
        if ((suiteMask & 0x00000200) != 0) {
            suites.add("Home");
        }
        if ((suiteMask & 0x00000400) != 0) {
            suites.add("Web Server");
        }
        if ((suiteMask & 0x00002000) != 0) {
            suites.add("Storage Server");
        }
        if ((suiteMask & 0x00004000) != 0) {
            suites.add("Compute Cluster");
        }
        // 0x8000, Home Server, is included in main version name
        return String.join(",", suites);
    }

    @Override
    protected int queryBitness() {
        if (this.jvmBitness < 64 && System.getenv("ProgramFiles(x86)") != null && IS_VISTA_OR_GREATER) {
            WmiQuery<BitnessProperty> bitnessQuery = new WmiQuery<>("Win32_Processor", BitnessProperty.class);
            WmiResult<BitnessProperty> bitnessMap = wmiQueryHandler.queryWMI(bitnessQuery);
            if (bitnessMap.getResultCount() > 0) {
                return WmiUtil.getUint16(bitnessMap, BitnessProperty.AddressWidth, 0);
            }
        }
        return this.jvmBitness;
    }

    @Override
    public boolean queryElevated() {
        try {
            File dir = new File(System.getenv("windir") + "\\system32\\config\\systemprofile");
            return dir.isDirectory();
        } catch (SecurityException e) {
            return false;
        }
    }

    @Override
    public FileSystem getFileSystem() {
        return new WindowsFileSystem();
    }

    @Override
    public OSProcess[] getProcesses(int limit, ProcessSort sort, boolean slowFields) {
        List<OSProcess> procList = processMapToList(null, slowFields);
        List<OSProcess> sorted = processSort(procList, limit, sort);
        return sorted.toArray(new OSProcess[0]);
    }

    @Override
    public List<OSProcess> getProcesses(Collection<Integer> pids) {
        return processMapToList(pids, true);
    }

    @Override
    public OSProcess[] getChildProcesses(int parentPid, int limit, ProcessSort sort) {
        Set<Integer> childPids = new HashSet<>();
        // Get processes from ToolHelp API for parent PID
        Tlhelp32.PROCESSENTRY32.ByReference processEntry = new Tlhelp32.PROCESSENTRY32.ByReference();
        WinNT.HANDLE snapshot = Kernel32.INSTANCE.CreateToolhelp32Snapshot(Tlhelp32.TH32CS_SNAPPROCESS, new DWORD(0));
        try {
            while (Kernel32.INSTANCE.Process32Next(snapshot, processEntry)) {
                if (processEntry.th32ParentProcessID.intValue() == parentPid) {
                    childPids.add(processEntry.th32ProcessID.intValue());
                }
            }
        } finally {
            Kernel32.INSTANCE.CloseHandle(snapshot);
        }
        List<OSProcess> procList = getProcesses(childPids);
        List<OSProcess> sorted = processSort(procList, limit, sort);
        return sorted.toArray(new OSProcess[0]);
    }

    @Override
    public OSProcess getProcess(int pid) {
        return getProcess(pid, true);
    }

    private OSProcess getProcess(int pid, boolean slowFields) {
        List<OSProcess> procList = processMapToList(Arrays.asList(pid), slowFields);
        return procList.isEmpty() ? null : procList.get(0);
    }

    /**
     * Private method to do the heavy lifting for all the getProcess functions.
     *
     * @param pids
     *            A collection of pids to query. If null, the entire process list
     *            will be queried.
     * @param slowFields
     *            Whether to include fields that incur processor latency
     * @return A corresponding list of processes
     */
    private List<OSProcess> processMapToList(Collection<Integer> pids, boolean slowFields) {
        // Get data from the registry
        Map<Integer, OSProcess> processMap = buildProcessMapFromPerfCounters(pids);

        // define here to avoid object repeated creation overhead later
        List<String> groupList = new ArrayList<>();
        List<String> groupIDList = new ArrayList<>();
        int myPid = getProcessId();

        // Structure we'll fill from native memory pointer for Vista+
        Pointer pProcessInfo = null;
        WTS_PROCESS_INFO_EX[] processInfo = null;
        IntByReference pCount = new IntByReference(0);

        // WMI result we'll use for pre-Vista
        WmiResult<ProcessXPProperty> processWmiResult = null;

        // Get processes from WTS (post-XP)
        if (IS_WINDOWS7_OR_GREATER) {
            final PointerByReference ppProcessInfo = new PointerByReference();
            if (!Wtsapi32.INSTANCE.WTSEnumerateProcessesEx(Wtsapi32.WTS_CURRENT_SERVER_HANDLE,
                    new IntByReference(Wtsapi32.WTS_PROCESS_INFO_LEVEL_1), Wtsapi32.WTS_ANY_SESSION, ppProcessInfo,
                    pCount)) {
                LOG.error("Failed to enumerate Processes. Error code: {}", Kernel32.INSTANCE.GetLastError());
                return new ArrayList<>(0);
            }
            // extract the pointed-to pointer and create array
            pProcessInfo = ppProcessInfo.getValue();
            final WTS_PROCESS_INFO_EX processInfoRef = new WTS_PROCESS_INFO_EX(pProcessInfo);
            processInfo = (WTS_PROCESS_INFO_EX[]) processInfoRef.toArray(pCount.getValue());
        } else {
            // Pre-Vista we can't use WTSEnumerateProcessesEx so we'll grab the
            // same info from WMI and fake the array
            StringBuilder sb = new StringBuilder(PROCESS_BASE_CLASS);
            if (pids != null) {
                boolean first = true;
                for (Integer pid : pids) {
                    if (first) {
                        sb.append(" WHERE ProcessID=");
                        first = false;
                    } else {
                        sb.append(" OR ProcessID=");
                    }
                    sb.append(pid);
                }
            }
            WmiQuery<ProcessXPProperty> processQueryXP = new WmiQuery<>(sb.toString(), ProcessXPProperty.class);
            processWmiResult = wmiQueryHandler.queryWMI(processQueryXP);
        }

        // Store a subset of processes in a list to later return.
        List<OSProcess> processList = new ArrayList<>();

        int procCount = IS_WINDOWS7_OR_GREATER ? processInfo.length : processWmiResult.getResultCount();
        for (int i = 0; i < procCount; i++) {
            int pid = IS_WINDOWS7_OR_GREATER ? processInfo[i].ProcessId
                    : WmiUtil.getUint32(processWmiResult, ProcessXPProperty.ProcessId, i);
            OSProcess proc = null;
            // If the cache is empty, there was a problem with
            // filling the cache using performance information.
            if (processMap.isEmpty()) {
                if (pids != null && !pids.contains(pid)) {
                    continue;
                }
                proc = new OSProcess(this);
                proc.setProcessID(pid);
                proc.setName(IS_WINDOWS7_OR_GREATER ? processInfo[i].pProcessName
                        : WmiUtil.getString(processWmiResult, ProcessXPProperty.Name, i));
            } else {
                proc = processMap.get(pid);
                if (proc == null || pids != null && !pids.contains(pid)) {
                    continue;
                }
            }
            // For my own process, set CWD
            if (pid == myPid) {
                String cwd = new File(".").getAbsolutePath();
                // trim off trailing "."
                proc.setCurrentWorkingDirectory(cwd.isEmpty() ? "" : cwd.substring(0, cwd.length() - 1));
            }

            if (IS_WINDOWS7_OR_GREATER) {
                WTS_PROCESS_INFO_EX procInfo = processInfo[i];
                proc.setKernelTime(procInfo.KernelTime.getValue() / 10000L);
                proc.setUserTime(procInfo.UserTime.getValue() / 10000L);
                proc.setThreadCount(procInfo.NumberOfThreads);
                proc.setVirtualSize(procInfo.PagefileUsage & 0xffff_ffffL);
                proc.setOpenFiles(procInfo.HandleCount);
            } else {
                proc.setKernelTime(WmiUtil.getUint64(processWmiResult, ProcessXPProperty.KernelModeTime, i) / 10000L);
                proc.setUserTime(WmiUtil.getUint64(processWmiResult, ProcessXPProperty.UserModeTime, i) / 10000L);
                proc.setThreadCount(WmiUtil.getUint32(processWmiResult, ProcessXPProperty.ThreadCount, i));
                // WMI Pagefile usage is in KB
                proc.setVirtualSize(1024
                        * (WmiUtil.getUint32(processWmiResult, ProcessXPProperty.PageFileUsage, i) & 0xffff_ffffL));
                proc.setOpenFiles(WmiUtil.getUint32(processWmiResult, ProcessXPProperty.HandleCount, i));
            }

            // Get a handle to the process for various extended info. Only gets
            // current user unless running as administrator
            final HANDLE pHandle = Kernel32.INSTANCE.OpenProcess(WinNT.PROCESS_QUERY_INFORMATION, false,
                    proc.getProcessID());
            if (pHandle != null) {
                proc.setBitness(this.getBitness());
                // Only test for 32-bit process on 64-bit windows
                if (IS_VISTA_OR_GREATER && this.getBitness() == 64) {
                    IntByReference wow64 = new IntByReference(0);
                    if (Kernel32.INSTANCE.IsWow64Process(pHandle, wow64)) {
                        proc.setBitness(wow64.getValue() > 0 ? 32 : 64);
                    }
                }
                // Full path
                final HANDLEByReference phToken = new HANDLEByReference();
                try {// EXECUTABLEPATH
                    proc.setPath(IS_WINDOWS7_OR_GREATER ? Kernel32Util.QueryFullProcessImageName(pHandle, 0)
                            : WmiUtil.getString(processWmiResult, ProcessXPProperty.ExecutablePath, i));
                    if (Advapi32.INSTANCE.OpenProcessToken(pHandle, WinNT.TOKEN_DUPLICATE | WinNT.TOKEN_QUERY,
                            phToken)) {
                        Account account = Advapi32Util.getTokenAccount(phToken.getValue());
                        proc.setUser(account.name);
                        proc.setUserID(account.sidString);
                        // Fetching group information incurs ~10ms per process.
                        if (slowFields) {
                            Account[] accounts = Advapi32Util.getTokenGroups(phToken.getValue());
                            // get groups
                            groupList.clear();
                            groupIDList.clear();
                            for (Account a : accounts) {
                                groupList.add(a.name);
                                groupIDList.add(a.sidString);
                            }
                            proc.setGroup(String.join(",", groupList));
                            proc.setGroupID(String.join(",", groupIDList));
                        }
                    } else {
                        int error = Kernel32.INSTANCE.GetLastError();
                        // Access denied errors are common. Fail silently.
                        if (error != WinError.ERROR_ACCESS_DENIED) {
                            LOG.error("Failed to get process token for process {}: {}", proc.getProcessID(),
                                    Kernel32.INSTANCE.GetLastError());
                        }
                    }
                } catch (Win32Exception e) {
                    handleWin32ExceptionOnGetProcessInfo(proc, e);
                } finally {
                    final HANDLE token = phToken.getValue();
                    if (token != null) {
                        Kernel32.INSTANCE.CloseHandle(token);
                    }
                }
                Kernel32.INSTANCE.CloseHandle(pHandle);
            }

            // There is no easy way to get ExecutuionState for a process.
            // The WMI value is null. It's possible to get thread Execution
            // State and possibly roll up.
            proc.setState(OSProcess.State.RUNNING);

            processList.add(proc);
        }
        // Clean up memory allocated in C (only Vista+ but null pointer
        // effectively tests)
        if (pProcessInfo != null && !Wtsapi32.INSTANCE.WTSFreeMemoryEx(Wtsapi32.WTS_PROCESS_INFO_LEVEL_1, pProcessInfo,
                pCount.getValue())) {
            LOG.error("Failed to Free Memory for Processes. Error code: {}", Kernel32.INSTANCE.GetLastError());
            return new ArrayList<>(0);
        }

        // Command Line only accessible via WMI.
        StringBuilder sb = new StringBuilder(PROCESS_BASE_CLASS);
        if (pids != null) {
            Set<Integer> pidsToQuery = new HashSet<>();
            for (OSProcess process : processList) {
                pidsToQuery.add(process.getProcessID());
            }
            boolean first = true;
            for (Integer pid : pidsToQuery) {
                if (first) {
                    sb.append(" WHERE ProcessID=");
                    first = false;
                } else {
                    sb.append(" OR ProcessID=");
                }
                sb.append(pid);
            }
        }
        WmiQuery<ProcessProperty> processQuery = new WmiQuery<>(sb.toString(), ProcessProperty.class);
        WmiResult<ProcessProperty> commandLineProcs = wmiQueryHandler.queryWMI(processQuery);

        for (int p = 0; p < commandLineProcs.getResultCount(); p++) {
            int pid = WmiUtil.getUint32(commandLineProcs, ProcessProperty.ProcessId, p);
            // This should always be true because pidsToQuery was
            // built from the map, but just in case, protect against
            // dereferencing null
            if (processMap.containsKey(pid)) {
                OSProcess proc = processMap.get(pid);
                proc.setCommandLine(WmiUtil.getString(commandLineProcs, ProcessProperty.CommandLine, p));
            }
        }
        return processList;
    }

    /**
     * <p>
     * handleWin32ExceptionOnGetProcessInfo.
     * </p>
     *
     * @param proc
     *            a {@link oshi.software.os.OSProcess} object.
     * @param ex
     *            a {@link com.sun.jna.platform.win32.Win32Exception} object.
     */
    protected void handleWin32ExceptionOnGetProcessInfo(OSProcess proc, Win32Exception ex) {
        LOG.warn("Failed to set path or get user/group on PID {}. It may have terminated. {}", proc.getProcessID(),
                ex.getMessage());
    }

    private Map<Integer, OSProcess> buildProcessMapFromPerfCounters(Collection<Integer> pids) {
        Map<Integer, OSProcess> processMap = new HashMap<>();
        Map<ProcessPerformanceProperty, List<Long>> valueMap = this.processPerformancePerfCounters
                .queryValuesWildcard();
        long now = System.currentTimeMillis(); // 1970 epoch
        List<String> instances = this.processPerformancePerfCounters.getInstancesFromLastQuery();
        List<Long> pidList = valueMap.get(ProcessPerformanceProperty.ProcessId);
        List<Long> ppidList = valueMap.get(ProcessPerformanceProperty.ParentProcessId);
        List<Long> priorityList = valueMap.get(ProcessPerformanceProperty.Priority);
        List<Long> ioReadList = valueMap.get(ProcessPerformanceProperty.ReadTransferCount);
        List<Long> ioWriteList = valueMap.get(ProcessPerformanceProperty.WriteTransferCount);
        List<Long> workingSetSizeList = valueMap.get(ProcessPerformanceProperty.PrivatePageCount);
        List<Long> creationTimeList = valueMap.get(ProcessPerformanceProperty.CreationDate);

        for (int inst = 0; inst < instances.size(); inst++) {
            int pid = pidList.get(inst).intValue();
            if (pids == null || pids.contains(pid)) {
                OSProcess proc = new OSProcess(this);
                processMap.put(pid, proc);

                proc.setProcessID(pid);
                proc.setName(instances.get(inst));
                proc.setParentProcessID(ppidList.get(inst).intValue());
                proc.setPriority(priorityList.get(inst).intValue());
                // if creation time value is less than current millis, it's in 1970 epoch,
                // otherwise it's 1601 epoch and we must convert
                long ctime = creationTimeList.get(inst);
                if (ctime > now) {
                    ctime = WinBase.FILETIME.filetimeToDate((int) (ctime >> 32), (int) (ctime & 0xffffffffL)).getTime();
                }
                proc.setUpTime(now - ctime);
                proc.setStartTime(ctime);
                proc.setBytesRead(ioReadList.get(inst));
                proc.setBytesWritten(ioWriteList.get(inst));
                proc.setResidentSetSize(workingSetSizeList.get(inst));
            }
        }

        return processMap;
    }

    @Override
    public int getProcessId() {
        return Kernel32.INSTANCE.GetCurrentProcessId();
    }

    @Override
    public int getProcessCount() {
        PERFORMANCE_INFORMATION perfInfo = new PERFORMANCE_INFORMATION();
        if (!Psapi.INSTANCE.GetPerformanceInfo(perfInfo, perfInfo.size())) {
            LOG.error("Failed to get Performance Info. Error code: {}", Kernel32.INSTANCE.GetLastError());
            return 0;
        }
        return perfInfo.ProcessCount.intValue();
    }

    @Override
    public int getThreadCount() {
        PERFORMANCE_INFORMATION perfInfo = new PERFORMANCE_INFORMATION();
        if (!Psapi.INSTANCE.GetPerformanceInfo(perfInfo, perfInfo.size())) {
            LOG.error("Failed to get Performance Info. Error code: {}", Kernel32.INSTANCE.GetLastError());
            return 0;
        }
        return perfInfo.ThreadCount.intValue();
    }

    @Override
    public long getSystemUptime() {
        return querySystemUptime();
    }

    private static long querySystemUptime() {
        // Uptime is in seconds so divide milliseconds
        // GetTickCount64 requires Vista (6.0) or later
        if (IS_VISTA_OR_GREATER) {
            return Kernel32.INSTANCE.GetTickCount64() / 1000L;
        } else {
            // 32 bit rolls over at ~ 49 days
            return Kernel32.INSTANCE.GetTickCount() / 1000L;
        }
    }

    @Override
    public long getSystemBootTime() {
        return BOOTTIME;
    }

    private static long querySystemBootTime() {
        // Get the most recent boot event (ID 12) from the Event log. If Windows "Fast
        // Startup" is enabled we may not see event 12, so also check for most recent ID
        // 6005 (Event log startup) as a reasonably close backup.
        long event6005Time = 0L;
        EventLogIterator iter = new EventLogIterator(null, "System", WinNT.EVENTLOG_BACKWARDS_READ);
        while (iter.hasNext()) {
            EventLogRecord record = iter.next();
            if (record.getStatusCode() == 12) {
                // Event 12 is system boot. We want this value unless we find two 6005 events
                // first (may occur with Fast Boot)
                return record.getRecord().TimeGenerated.longValue();
            } else if (record.getStatusCode() == 6005) {
                // If we already found one, this means we've found a second one without finding
                // an event 12. Return the latest one.
                if (event6005Time > 0) {
                    return event6005Time;
                }
                // First 6005; tentatively assign
                event6005Time = record.getRecord().TimeGenerated.longValue();
            }
        }
        // If we get this far, event log reading has failed. Subtract up time from
        // current time as a reasonable proxy.
        return event6005Time > 0 ? event6005Time : System.currentTimeMillis() / 1000L - querySystemUptime();
    }

    @Override
    public NetworkParams getNetworkParams() {
        return new WindowsNetworkParams();
    }

    /**
     * Enables debug privileges for this process, required for OpenProcess() to get
     * processes other than the current user
     */
    private static void enableDebugPrivilege() {
        HANDLEByReference hToken = new HANDLEByReference();
        boolean success = Advapi32.INSTANCE.OpenProcessToken(Kernel32.INSTANCE.GetCurrentProcess(),
                WinNT.TOKEN_QUERY | WinNT.TOKEN_ADJUST_PRIVILEGES, hToken);
        if (!success) {
            LOG.error("OpenProcessToken failed. Error: {}", Native.getLastError());
            return;
        }
        WinNT.LUID luid = new WinNT.LUID();
        success = Advapi32.INSTANCE.LookupPrivilegeValue(null, WinNT.SE_DEBUG_NAME, luid);
        if (!success) {
            LOG.error("LookupprivilegeValue failed. Error: {}", Native.getLastError());
            Kernel32.INSTANCE.CloseHandle(hToken.getValue());
            return;
        }
        WinNT.TOKEN_PRIVILEGES tkp = new WinNT.TOKEN_PRIVILEGES(1);
        tkp.Privileges[0] = new WinNT.LUID_AND_ATTRIBUTES(luid, new DWORD(WinNT.SE_PRIVILEGE_ENABLED));
        success = Advapi32.INSTANCE.AdjustTokenPrivileges(hToken.getValue(), false, tkp, 0, null, null);
        if (!success) {
            LOG.error("AdjustTokenPrivileges failed. Error: {}", Native.getLastError());
        }
        Kernel32.INSTANCE.CloseHandle(hToken.getValue());
    }

    @Override
    public OSService[] getServices() {
        try (W32ServiceManager sm = new W32ServiceManager()) {
            sm.open(Winsvc.SC_MANAGER_ENUMERATE_SERVICE);
            Winsvc.ENUM_SERVICE_STATUS_PROCESS[] services = sm.enumServicesStatusExProcess(WinNT.SERVICE_WIN32,
                    Winsvc.SERVICE_STATE_ALL, null);
            OSService[] svcArray = new OSService[services.length];
            for (int i = 0; i < services.length; i++) {
                State state;
                switch (services[i].ServiceStatusProcess.dwCurrentState) {
                case 1:
                    state = STOPPED;
                    break;
                case 4:
                    state = RUNNING;
                    break;
                default:
                    state = OTHER;
                    break;
                }
                svcArray[i] = new OSService(services[i].lpDisplayName, services[i].ServiceStatusProcess.dwProcessId,
                        state);
            }
            return svcArray;
        } catch (com.sun.jna.platform.win32.Win32Exception ex) {
            LOG.error("Win32Exception: {}", ex.getMessage());
            return new OSService[0];
        }
    }
}
