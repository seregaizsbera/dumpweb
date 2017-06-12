package ru.sergeyb.dump;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

/**
 * Класс, который обеспечивает мониторинг дампов потоков.
 *
 * @author sergeyb
 */
class ThreadDumpImpl {
    private static final int MINIMAL_ROTATE_PERIOD_SEC = 3600;
    private static final String THREAD_NAME = "ThreadDumpMonitorDh1e5j8r";
    private static final int MINIMAL_PERIOD_SEC = 60; // не меньше 1 минуты
    private static final AtomicBoolean monitoringStarted = new AtomicBoolean(false);
    private static final Runnable worker;
    private static final DumpType defaultDumpType = DumpType.HOTSPOT;
    private static final AtomicReference<ScheduledFuture<?>> timer = new AtomicReference<>();
    private static final AtomicReference<ScheduledExecutorService> pool = new AtomicReference<>();
    private static final AtomicReference<Path> dumpParentDir = new AtomicReference<>();
    private static final AtomicInteger periodSec = new AtomicInteger(-1);
    private static final AtomicReference<DumpType> dumpType = new AtomicReference<>();
    private static final AtomicReference<Preferences> preferences = new AtomicReference<>();
    private static final AtomicInteger rotatePeriodSec = new AtomicInteger(-1);
    private static final AtomicInteger rotateDays = new AtomicInteger(-1);
    private static final AtomicLong lastRotate = new AtomicLong(0);
    static final String JMX_NAME = "ru.sergeyb:type=ThreadDump";
    static final int DEFAULT_PERIOD_SEC = 300;
    static final int DEFAULT_ROTATE_PERIOD_SEC = 86_400_000;
    static final int DEFAULT_ROTATE_DAYS = 7;
    static final Logger logger = Logger.getLogger(ThreadDumpImpl.class.getName());

    static {
        worker = new Runnable() {
            @Override
            public void run() {
                threadDump();
            }
        };
    }

    private static Preferences getPreferences() {
        if (preferences.get() == null) {
            preferences.compareAndSet(null, Preferences.userRoot().node("/ru/sergeyb/dump/thread-dump"));
        }
        return preferences.get();
    }

    static int getRotatePeriodSec() {
        return rotatePeriodSec.get();
    }

    static void setRotatePeriodSec(int rotatePeriodSec) {
        int newRotatePeriodSec = rotatePeriodSec;
        if (newRotatePeriodSec != 0 && newRotatePeriodSec < MINIMAL_ROTATE_PERIOD_SEC) {
            logger.log(Level.WARNING, "Invalid rotation period {0} sec. Minimum period allowed is {1} sec.", new Object[] {newRotatePeriodSec, MINIMAL_ROTATE_PERIOD_SEC});
            newRotatePeriodSec = MINIMAL_ROTATE_PERIOD_SEC;
        }
        if (newRotatePeriodSec == ThreadDumpImpl.rotatePeriodSec.get()) {
            return;
        }
        ThreadDumpImpl.rotatePeriodSec.set(newRotatePeriodSec);
        if (newRotatePeriodSec == 0) {
            logger.log(Level.INFO, "File rotation is disabled because period is 0.");
        } else {
            logger.log(Level.INFO, "Rotation period set to {0} sec.", new  Object[] {newRotatePeriodSec});
        }
        getPreferences().putInt("rotatePeriodSec", newRotatePeriodSec);
    }

    static DumpType getDefaultDumpType() {
        String pref = getPreferences().get("dumpType", null);
        if (pref == null) {
            return defaultDumpType;
        }
        try {
            return DumpType.valueOf(pref);
        } catch (IllegalArgumentException e) {
            getPreferences().put("dumpType", defaultDumpType.name());
            return defaultDumpType;
        }
    }

    static int getDefaultPeriodSec() {
        return getPreferences().getInt("periodSec", DEFAULT_PERIOD_SEC);
    }

    static Path getDumpParentDir() {
        return dumpParentDir.get();
    }

    static void setDumpType(DumpType dumpType) {
        ThreadDumpImpl.dumpType.set(dumpType);
        getPreferences().put("dumpType", dumpType.name());
    }

    static DumpType getDumpType() {
        return dumpType.get();
    }

    static void setDumpParentDir(Path dumpParentDir) {
        Path path = dumpParentDir.toAbsolutePath().normalize();
        try {
            Files.createDirectories(path);
        } catch (IOException e) {
            logger.log(Level.SEVERE, e.getMessage(), e);
            return;
        }
        if (!Files.exists(path)) {
            logger.log(Level.SEVERE, "{0} doesn't exist", new Object[] {path});
            return;
        }
        if (!Files.isDirectory(path)) {
            logger.log(Level.SEVERE, "{0} is not a directory", new Object[] {path});
            return;
        }
        ThreadDumpImpl.dumpParentDir.set(dumpParentDir);
        getPreferences().put("dumpDir", path.toString());
    }

    static void setPeriodSec(int periodSec) {
        int newPeriodSec = periodSec;
        if (newPeriodSec > 0 && newPeriodSec < MINIMAL_PERIOD_SEC) {
            logger.log(Level.WARNING, "Invalid monitoring period {0} sec. Minimum period allowed is {1} sec.", new Object[] {newPeriodSec, MINIMAL_PERIOD_SEC});
            newPeriodSec = MINIMAL_PERIOD_SEC;
        }
        int oldPeriodSec = ThreadDumpImpl.periodSec.get();
        if (oldPeriodSec == newPeriodSec) {
            return;
        }
        ThreadDumpImpl.periodSec.set(newPeriodSec);
        if (monitoringStarted.get()) {
            if (newPeriodSec == 0) {
                logger.log(Level.INFO, "Thread dump monitoring is disabled because period is 0.");
                stopThreadDumpMonitoring();
            } else {
                reschedule(oldPeriodSec);
            }
        } else {
            logger.log(Level.INFO, "Thread monitoring period set to {0} sec.", new  Object[] {newPeriodSec});
        }
        getPreferences().putInt("periodSec", newPeriodSec);
    }

    private static void reschedule(int oldPeriodSec) {
        int newPeriodSec = periodSec.get();
        long oldPeriodMs = TimeUnit.MILLISECONDS.convert(oldPeriodSec, TimeUnit.SECONDS);
        long newPeriodMs = TimeUnit.MILLISECONDS.convert(newPeriodSec, TimeUnit.SECONDS);
        ScheduledFuture<?> theTimer = timer.get();
        long remainingMs = theTimer.getDelay(TimeUnit.MILLISECONDS);
        theTimer.cancel(false);
        long delayMs = Math.max(newPeriodMs - oldPeriodMs + remainingMs, 1000);
        logger.log(Level.FINE, "oldPeriodMs = {0}; remainingMs = {1}; periodMs = {2}; delayMs = {3}", new Object[] {oldPeriodMs, remainingMs, newPeriodMs, delayMs});
        timer.set(pool.get().scheduleAtFixedRate(worker, delayMs, newPeriodMs, TimeUnit.MILLISECONDS));
        logger.log(Level.INFO, "Thread dump monitoring was rescheduled.");
    }

    static int getPeriodSec() {
        return periodSec.get();
    }

    /**
     * Возвращает значение по умолчанию для каталога, куда
     * будут сохранятся дампы.
     *
     * @return
     */
    static Path getDefaultDumpParentDir() {
        String pref = getPreferences().get("dumpDir", null);
        if (pref != null) {
            return Paths.get(pref);
        }
        String home = System.getProperty("catalina.home");
        if (home == null) {
            home = ".";
        }
        Path result = Paths.get(home, "thread-dumps").toAbsolutePath().normalize();
        return result;
    }

    static boolean isStarted() {
        return monitoringStarted.get();
    }


    static int getRotateDays() {
        return rotateDays.get();
    }

    static void setRotateDays(int rotateDays) {
        ThreadDumpImpl.rotateDays.set(rotateDays);
        getPreferences().putInt("rotateDays", rotateDays);
    }

    private static int getDefaultRotateDays() {
        return getPreferences().getInt("rotateDays", DEFAULT_ROTATE_DAYS);
    }

    /**
     * Возвращает каталог для сохранения дампов с учетом ротации по датам.
     *
     * @return
     */
    private static Path getDumpDir(Date date) {
        Path result = dumpParentDir.get().resolve(String.format("%1$tY-%1$tm-%1$td", date));
        try {
            Files.createDirectories(result);
        } catch (IOException e) {
            logger.log(Level.SEVERE, e.getMessage(), e);
        }
        return result;
    }

    /**
     * Метод, который сохраняет дамп и обрабатывает ошибки.
     */
    static void threadDump() {
        try {
            Date date = Calendar.getInstance().getTime();
            Path file = getDumpDir(date).resolve(String.format("thread-dump-%1$tY-%1$tm-%1$td-%1$tH-%1$tM.txt", date));
            threadDump(file, date);
            rotate(date);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to generate thread dump. Error = {0}", new Object[] {e.toString()});
        } catch (RuntimeException e) {
            logger.log(Level.SEVERE, "Failed to generate thread dump. Error = {0}", new Object[] {e.toString()});
        }
    }

    private static void rotate(Date date) {
        int rp = rotatePeriodSec.get();
        int rd = rotateDays.get();
        if (rp == 0 || rd == 0) {
            return;
        }
        long lr = lastRotate.get();
        if (date.getTime() - lr < rp) {
            return;
        }
        rotateInternal(date);
    }

    static void rotateInternal(final Date date) {
        lastRotate.set(date.getTime());
        final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        Path dumpDir = ThreadDumpImpl.dumpParentDir.get();
        FileVisitor<Path> visitor = new FileVisitor<Path>() {
            private int depth = -1;

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (depth == 0) {
                    String name = dir.getFileName().toString();
                    try {
                        Date dt = dateFormat.parse(name);
                        Calendar calendar = Calendar.getInstance();
                        calendar.setTime(dt);
                        calendar.add(Calendar.DATE, getRotateDays());
                        Calendar today = Calendar.getInstance();
                        today.setTime(date);
                        if (today.compareTo(calendar) > 0) {
                            depth++;
                            return FileVisitResult.CONTINUE;
                        }
                    } catch (ParseException e) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.SKIP_SUBTREE;
                } else if (depth < 0) {
                    depth++;
                    return FileVisitResult.CONTINUE;
                } else {
                    return FileVisitResult.SKIP_SUBTREE;
                }
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (depth == 1) {
                    try {
                        Files.delete(file);
                    } catch (IOException e) {
                        logger.log(Level.SEVERE, e.getMessage(), e);
                    }
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                if (depth == 1) {
                    try {
                        Files.delete(dir);
                    } catch (IOException e) {
                        logger.log(Level.SEVERE, "postVisitDirectory", e);
                    }
                }
                depth--;
                return FileVisitResult.CONTINUE;
            }
        };
        try {
            Files.walkFileTree(dumpDir, visitor);
        } catch (IOException e) {
            logger.log(Level.SEVERE, e.getMessage(), e);
        }
    }

    /**
     * Метод, который сохраняет дамп в файл.
     * @param out
     * @param date
     */
    static void threadDump(Path file, Date date) throws IOException {
        try (
            OutputStream output = Files.newOutputStream(file);
            OutputStreamWriter writer = new OutputStreamWriter(output, "UTF-8");
            PrintWriter out = new PrintWriter(writer);
        ) {
            logger.log(Level.INFO, "Saving thread dump to {0}...", new Object[] {file});
            dumpType.get().dumper().threadDump(out, date);
            out.flush();
        }
    }

    /**
     * Запускает поток, который периодически выполняет сохранение дампов
     * и следит за изменением настроек.
     */
    static void runThreadDumpMonitoring() {
        logger.log(Level.INFO, "Starting thread dump monitoring...");
        if (!monitoringStarted.compareAndSet(false, true)) {
            logger.log(Level.WARNING, "Thread dump monitoring already started");
            return;
        }
        if (pool.get() != null) {
            logger.log(Level.WARNING, "Thread pool already initialized");
            monitoringStarted.compareAndSet(false, true);
            return;
        }
        if (thereIsAnotherMonitor()) {
            /*
             *  Уже существует поток мониторинга
             *  На всякий случай выходим
             */
            logger.log(Level.SEVERE, "Found unexpected thread with name {0}", new Object[] {THREAD_NAME});
            return;
        }
        if (dumpType.get() == null) {
            dumpType.set(getDefaultDumpType());
        }
        if (dumpParentDir.get() == null) {
            dumpParentDir.set(getDefaultDumpParentDir());
        }
        int thePeriodSec = periodSec.get();
        if (thePeriodSec < 0) {
            periodSec.set(getDefaultPeriodSec());
        }
        if (thePeriodSec == 0) {
            logger.log(Level.INFO, "Thread dump monitoring is disabled because period is 0.");
            return;
        }
        if (rotatePeriodSec.get() < 0) {
            rotatePeriodSec.set(getDefaultRotatePeriodSec());
        }
        if (rotateDays.get() < 0) {
            rotateDays.set(getDefaultRotateDays());
        }
        ScheduledExecutorService thePool = Executors.newScheduledThreadPool(1, new ThreadFactory() {
            @Override
            public Thread newThread(Runnable target) {
                Thread result = new Thread(target, THREAD_NAME);
                result.setDaemon(true);
                return result;
            }
        });
        pool.set(thePool);
        timer.set(thePool.scheduleAtFixedRate(worker, 1, periodSec.get(), TimeUnit.SECONDS));
        logger.log(Level.INFO, "Thread dump monitoring initialized.");
    }

    private static int getDefaultRotatePeriodSec() {
        return getPreferences().getInt("rotatePeriodSec", DEFAULT_ROTATE_PERIOD_SEC);
    }

    private static boolean thereIsAnotherMonitor() {
        Thread threads[] = new Thread[Thread.activeCount()];
        int cnt = Thread.enumerate(threads);
        for (int i = 0; i < cnt; i++) {
            Thread thread = threads[i];
            if (thread.getName().equals(THREAD_NAME)) {
                return true;
            }
        }
        return false;
    }

    static void stopThreadDumpMonitoring() {
        if (!monitoringStarted.compareAndSet(true, false)) {
            return;
        }
        ScheduledFuture<?> theTimer = timer.get();
        if (theTimer != null) {
            theTimer.cancel(false);
            timer.set(null);
        }
        ScheduledExecutorService thePool = pool.get();
        if (thePool != null) {
            thePool.shutdownNow();
            pool.set(null);
        }
        logger.log(Level.INFO, "Thread dump monitoring stopped.");
    }
}
