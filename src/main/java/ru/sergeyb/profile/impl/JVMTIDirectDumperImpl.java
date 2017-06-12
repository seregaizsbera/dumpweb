package ru.sergeyb.profile.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.util.Date;

import ru.sergeyb.dump.Dumper;

public class JVMTIDirectDumperImpl implements Dumper {
    private final String pid;
    private final Method attach;
    private final Method detach;
    private final Method remoteDataDump;
    private final String host;

    public JVMTIDirectDumperImpl(String pid, String host, Method attach, Method detach) throws ReflectiveOperationException {
        this.pid = pid;
        Class<?> hsClass = Class.forName("sun.tools.attach.HotSpotVirtualMachine");
        this.attach = attach;
        this.detach = detach;
        this.remoteDataDump = hsClass.getMethod("remoteDataDump", Object[].class);
        this.host = host;
    }

    @Override
    public void threadDump(PrintWriter out, Date date) throws IOException {
        out.printf("[%1$tY-%1$tm-%1$td %1$tT%1$tz] Thread dump at %2$s:%n%n", date, host);
        try {
            Object vm = attach.invoke(null, pid);
            try (
                InputStream in = (InputStream) remoteDataDump.invoke(vm, new Object[]{new Object[]{"-l"}});
                Reader reader = new InputStreamReader(in, Charset.defaultCharset());
            ) {
                char buf[] = new char[10240];
                for (int i = reader.read(buf); i >= 0; i = reader.read(buf)) {
                    out.write(buf, 0, i);
                }
            } finally {
                detach.invoke(vm);
            }
        } catch (InvocationTargetException e) {
            Throwable target = e.getTargetException();
            if (target == null) {
                throw new IOException(e);
            } else if (target instanceof IOException) {
                throw (IOException) target;
            } else {
                throw new IOException(target);
            }
        } catch (RuntimeException | ReflectiveOperationException e) {
            throw new IOException(e);
        }
    }

    @Override
    public void destroy() {
        // do nothing
    }
}
