package ru.sergeyb.dump;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.Thread.State;
import java.lang.management.LockInfo;
import java.lang.management.ManagementFactory;
import java.lang.management.MonitorInfo;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.Date;

public class JMXDumper implements Dumper {
    private final String host;

    public JMXDumper() {
        this.host = HostHelper.getHost();
    }

    @Override
    public void threadDump(PrintWriter out, Date date) throws IOException {
        out.printf("[%1$tY-%1$tm-%1$td %1$tT%1$tz] Thread dump at %2$s:%n%n", date, host);
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        ThreadInfo[] result = threadMXBean.dumpAllThreads(true, true);
        for (ThreadInfo threadInfo: result) {
            out.printf("%s%n", toString(threadInfo));
        }
    }

    private static String toString(ThreadInfo threadInfo) {
        StringBuilder buf = new StringBuilder();

        String threadName = threadInfo.getThreadName();
        long threadId = threadInfo.getThreadId();
        State threadState = threadInfo.getThreadState();
        String lockName = threadInfo.getLockName();
        String lockOwnerName = threadInfo.getLockOwnerName();
        LockInfo lockInfo = threadInfo.getLockInfo();

        buf.append('"').append(threadName).append('"').append(" Id=").append(threadId).append(' ').append(threadState);
        if (lockName != null) {
            buf.append(" on ").append(lockName);
        }
        if (lockOwnerName != null) {
            long lockOwnerId = threadInfo.getLockOwnerId();
            buf.append(" owned by \"").append(lockOwnerName).append("\" Id=").append(lockOwnerId);
        }
        if (threadInfo.isSuspended()) {
            buf.append(" (suspended)");
        }
        if (threadInfo.isInNative()) {
            buf.append(" (in native)");
        }
        buf.append('\n');

        StackTraceElement[] stackTrace = threadInfo.getStackTrace();
        for (int i = 0; i < stackTrace.length; i++) {
            StackTraceElement element = stackTrace[i];
            buf.append("    at " + element.toString());
            buf.append('\n');
            if (i == 0 && lockInfo != null) {
                switch (threadState) {
                    case BLOCKED:
                        buf.append("    -  blocked on ").append(lockInfo).append('\n');
                        break;
                    case WAITING:
                    case TIMED_WAITING:
                        buf.append("    -  waiting on ").append(lockInfo).append('\n');
                        break;
                    default:
                        break;
                }
            }
            for (MonitorInfo monitorInfo: threadInfo.getLockedMonitors()) {
                if (monitorInfo.getLockedStackDepth() == i) {
                    buf.append("    -  locked ").append(monitorInfo).append('\n');
                }
            }
        }
        LockInfo[] locks = threadInfo.getLockedSynchronizers();
        if (locks.length > 0) {
            buf.append("\n    Number of locked synchronizers = ").append(locks.length).append('\n');
            for (LockInfo lock: locks) {
                buf.append("    - ").append(lock).append('\n');
            }
        }
        buf.append('\n');
        return buf.toString();
    }

    @Override
    public void destroy() {
        // do nothing
    }
}
