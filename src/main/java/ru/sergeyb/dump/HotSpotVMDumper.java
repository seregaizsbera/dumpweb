package ru.sergeyb.dump;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import ru.sergeyb.profile.JVMTIDumper;

public class HotSpotVMDumper implements Dumper {
    private final AtomicBoolean initialized;
    private final Lock initLock;
    private final AtomicBoolean good;
    private final JVMTIDumper jvmtiDumper;

    public HotSpotVMDumper() {
        this.initialized = new AtomicBoolean(false);
        this.initLock = new ReentrantLock();
        this.good = new AtomicBoolean(true);
        this.jvmtiDumper = new JVMTIDumper();
    }

    @Override
    public void threadDump(PrintWriter out, Date date) throws IOException {
        if (!initialized.get()) {
            init();
        }
        if (!good.get()) {
            DumpType.JMX.dumper().threadDump(out, date);
            return;
        }
        jvmtiDumper.dump(out, date);
    }

    private void init() {
        boolean ok = false;
        initLock.lock();
        try {
            if (initialized.get()) {
                return;
            }
            this.jvmtiDumper.init();
            ok = true;
        } catch (Throwable e) {
            e.printStackTrace();
        } finally {
            initialized.set(true);
            good.set(ok);
            initLock.unlock();
        }
    }

    @Override
    public void destroy() {
        this.jvmtiDumper.close();
    }
}
