package ru.sergeyb.dump;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Calendar;

public class ThreadDump implements ThreadDumpMBean {

    @Override
    public int getPeriodSec() {
        return ThreadDumpImpl.getPeriodSec();
    }

    @Override
    public void setPeriodSec(int periodSec) {
        ThreadDumpImpl.setPeriodSec(periodSec);
    }

    @Override
    public String getDumpDir() {
        return ThreadDumpImpl.getDumpParentDir().toString();
    }

    @Override
    public void setDumpDir(String dumpDir) {
        ThreadDumpImpl.setDumpParentDir(Paths.get(dumpDir));
    }

    @Override
    public void resetDumpDir() {
        ThreadDumpImpl.setDumpParentDir(ThreadDumpImpl.getDefaultDumpParentDir());
    }

    @Override
    public void resetPeriod() {
        ThreadDumpImpl.setPeriodSec(ThreadDumpImpl.DEFAULT_PERIOD_SEC);
    }

    @Override
    public boolean isStarted() {
        return ThreadDumpImpl.isStarted();
    }

    @Override
    public void start() {
        ThreadDumpImpl.runThreadDumpMonitoring();
    }

    @Override
    public void stop() {
        ThreadDumpImpl.stopThreadDumpMonitoring();
    }

    @Override
    public void threadDump() {
        ThreadDumpImpl.threadDump();
    }

    @Override
    public void threadDump(String file) throws IOException {
        ThreadDumpImpl.threadDump(Paths.get(file), Calendar.getInstance().getTime());
    }

    @Override
    public String getDumpType() {
        return ThreadDumpImpl.getDumpType().name();
    }

    @Override
    public void setDumpType(String dumpType) {
        ThreadDumpImpl.setDumpType(DumpType.valueOf(dumpType));
    }

    @Override
    public String[] getAvailableDumpTypes() {
        DumpType[] values = DumpType.values();
        String result[] = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = values[i].name();
        }
        return result;
    }

    @Override
    public int getRotatePeriodSec() {
        return ThreadDumpImpl.getRotatePeriodSec();
    }

    @Override
    public void setRotatePeriodSec(int rotatePeriodSec) {
        ThreadDumpImpl.setRotatePeriodSec(rotatePeriodSec);
    }

    @Override
    public int getRotateDays() {
        return ThreadDumpImpl.getRotateDays();
    }

    @Override
    public void setRotateDays(int rotateDays) {
        ThreadDumpImpl.setRotateDays(rotateDays);
    }

    @Override
    public void resetRotatePeriod() {
        ThreadDumpImpl.setRotatePeriodSec(ThreadDumpImpl.DEFAULT_ROTATE_PERIOD_SEC);
    }

    @Override
    public void resetRotateDays() {
        ThreadDumpImpl.setRotateDays(ThreadDumpImpl.DEFAULT_ROTATE_DAYS);
    }

    @Override
    public void rotate() {
        ThreadDumpImpl.rotateInternal(Calendar.getInstance().getTime());
    }
}
