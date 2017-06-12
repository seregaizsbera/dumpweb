package ru.sergeyb.profile.impl;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Date;

import ru.sergeyb.dump.Dumper;

public class JVMTIIndirectDumperImpl implements Dumper {
    private final Object impl;
    private final Method dump;

    public JVMTIIndirectDumperImpl(Class<?> clazz) throws ReflectiveOperationException {
        this.dump = clazz.getMethod("dump", PrintWriter.class, Date.class);
        this.impl = clazz.newInstance();
    }

    @Override
    public void threadDump(PrintWriter out, Date date) throws IOException {
        try {
            dump.invoke(impl, out, date);
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
