using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Runtime.InteropServices;
using Microsoft.Diagnostics.Tracing.Session;
using Microsoft.Diagnostics.Tracing.Parsers;
using Microsoft.Diagnostics.Tracing.Parsers.Kernel;

static class Boost
{
    [DllImport("kernel32.dll", SetLastError = true)]
    static extern IntPtr OpenProcess(uint access, bool inherit, int pid);
    [DllImport("kernel32.dll", SetLastError = true)]
    static extern bool SetPriorityClass(IntPtr h, uint cls);
    [DllImport("kernel32.dll", SetLastError = true)]
    static extern bool CloseHandle(IntPtr h);

    const uint PROCESS_SET_INFORMATION = 0x0200;
    const uint HIGH_PRIORITY_CLASS = 0x00000080;
    const uint ABOVE_NORMAL_PRIORITY_CLASS = 0x00008000;

    static readonly HashSet<int> ForkPids = new();
    static readonly HashSet<int> ForkGitPids = new();

    static void SetPriority(int pid, uint cls)
    {
        IntPtr h = OpenProcess(PROCESS_SET_INFORMATION, false, pid);
        if (h == IntPtr.Zero) return;
        try { SetPriorityClass(h, cls); } finally { CloseHandle(h); }
    }

    static string BaseName(string image)
    {
        if (string.IsNullOrEmpty(image)) return image;
        int slash = Math.Max(image.LastIndexOf('\\'), image.LastIndexOf('/'));
        if (slash >= 0 && slash + 1 < image.Length) image = image.Substring(slash + 1);
        return image.EndsWith(".exe", StringComparison.OrdinalIgnoreCase) ? image.Substring(0, image.Length - 4) : image;
    }

    static int Main()
    {
        if (!(TraceEventSession.IsElevated() ?? false))
        {
            Console.Error.WriteLine("claude-boost-native: must run elevated (kernel ETW).");
            return 2;
        }

        foreach (var p in Process.GetProcessesByName("Fork"))
            try { ForkPids.Add(p.Id); SetPriority(p.Id, HIGH_PRIORITY_CLASS); } catch { }
        foreach (var name in new[] { "git", "claude" })
            foreach (var p in Process.GetProcessesByName(name))
                try { SetPriority(p.Id, ABOVE_NORMAL_PRIORITY_CLASS); } catch { }

        const string SessionName = "ClaudeBoostNativeKernel";
        foreach (string active in TraceEventSession.GetActiveSessionNames())
            if (string.Equals(active, SessionName, StringComparison.OrdinalIgnoreCase))
                try { using (var stale = new TraceEventSession(SessionName)) { stale.Stop(); } } catch { }

        using var session = new TraceEventSession(SessionName);
        Console.CancelKeyPress += (_, e) => { e.Cancel = true; session.Dispose(); };
        session.EnableKernelProvider(KernelTraceEventParser.Keywords.Process);

        session.Source.Kernel.ProcessStart += (ProcessTraceData d) =>
        {
            string name = BaseName(d.ImageFileName);
            if (string.Equals(name, "Fork", StringComparison.OrdinalIgnoreCase))
            {
                ForkPids.Add(d.ProcessID);
                SetPriority(d.ProcessID, HIGH_PRIORITY_CLASS);
            }
            else if (name.Equals("git", StringComparison.OrdinalIgnoreCase) || name.Equals("node", StringComparison.OrdinalIgnoreCase))
            {
                bool forkLineage = ForkPids.Contains(d.ParentID) || ForkGitPids.Contains(d.ParentID);
                SetPriority(d.ProcessID, forkLineage ? HIGH_PRIORITY_CLASS : ABOVE_NORMAL_PRIORITY_CLASS);
                if (forkLineage) ForkGitPids.Add(d.ProcessID);
            }
            else if (name.Equals("claude", StringComparison.OrdinalIgnoreCase))
            {
                SetPriority(d.ProcessID, ABOVE_NORMAL_PRIORITY_CLASS);
            }
        };
        session.Source.Kernel.ProcessStop += (ProcessTraceData d) => { ForkPids.Remove(d.ProcessID); ForkGitPids.Remove(d.ProcessID); };
        session.Source.Process();
        return 0;
    }
}
