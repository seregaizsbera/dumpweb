package ru.sergeyb.profile.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.lang.management.ManagementFactory;
import java.nio.charset.Charset;
import java.util.Date;

import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.VirtualMachine;

import sun.tools.attach.HotSpotVirtualMachine;

public class JVMTIDumperImpl {
    private final String pid;
    private final String host;

    public JVMTIDumperImpl() {
        String vmName = ManagementFactory.getRuntimeMXBean().getName();
        int i = vmName.indexOf('@');
        this.pid = vmName.substring(0, i);
        this.host = vmName.substring(i + 1);
    }

    public void dump(PrintWriter out, Date date) throws IOException {
        out.printf("[%1$tY-%1$tm-%1$td %1$tT%1$tz] Thread dump at %2$s:%n%n", date, host);
        try {
            HotSpotVirtualMachine vm = (HotSpotVirtualMachine) VirtualMachine.attach(pid);
            try (
                InputStream in = vm.remoteDataDump("-l");
                Reader reader = new InputStreamReader(in, Charset.defaultCharset());
            ) {
                char buf[] = new char[10240];
                for (int i = reader.read(buf); i >= 0; i = reader.read(buf)) {
                    out.write(buf, 0, i);
                }
            } finally {
                vm.detach();
            }
        } catch (AttachNotSupportedException e) {
            throw new IOException(e);
        }
    }
}
