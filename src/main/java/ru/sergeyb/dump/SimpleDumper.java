package ru.sergeyb.dump;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Date;
import java.util.Map;

public class SimpleDumper implements Dumper {
    private final String host;

    public SimpleDumper() {
        this.host = HostHelper.getHost();
    }

    @Override
    public void threadDump(PrintWriter out, Date date) throws IOException {
        out.printf("[%1$tY-%1$tm-%1$td %1$tT%1$tz] Thread dump at %2$s:%n%n", date, host);
        Map<Thread, StackTraceElement[]> stackTraces = Thread.getAllStackTraces();
        for (Map.Entry<Thread, StackTraceElement[]> stackTrace: stackTraces.entrySet()) {
            Thread thread = stackTrace.getKey();
            out.printf("Thread %s: (state = %s)%n", thread.getName(), thread.getState().toString());
            for (StackTraceElement element: stackTrace.getValue()) {
                out.printf(" - %s%n", element.toString());
            }
            out.printf("%n");
        }
    }

    @Override
    public void destroy() {
        // do nothing
    }
}
