package ru.sergeyb.profile;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;

import javax.management.MBeanServer;

import ru.sergeyb.profile.impl.JVMTIDirectDumperImpl;
import ru.sergeyb.profile.impl.JVMTIIndirectDumperImpl;
import ru.sergeyb.dump.Dumper;

public class JVMTIDumper implements AutoCloseable {
    private static final String TEMP_DIR_PREFIX = "U9pqzyxw";
    private static final String TOOLS_JAR = "/tools.jar";
    private static final String JVMTI_DUMPER_IMPL_JAR = "/DumperImpl.jar";
    private static final String JVMTI_DUMPER_IMPL_CLASS = "ru.sergeyb.profile.impl.JVMTIDumperImpl";
    private static final String NULL_DEVICE = "nul";
    private URLClassLoader classLoader;
    private Dumper hotSpotVMDumper;
    private Path tempDir;

    public JVMTIDumper() {
        this.classLoader = null;
        this.hotSpotVMDumper = null;
    }

    public void init() {
        try {
            try {
                initDirect();
            } catch (Throwable e) {
                initIndirect();
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (InvocationTargetException e) {
            Throwable target = e.getTargetException();
            if (target == null) {
                throw new IllegalStateException(e);
            } else if (target instanceof RuntimeException) {
                throw (RuntimeException) target;
            } else {
                throw new IllegalStateException(e);
            }
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
    }

    private void initIndirect() throws IOException, ReflectiveOperationException {
        this.tempDir = Files.createTempDirectory(TEMP_DIR_PREFIX);
        Collection<URL> jars = new ArrayList<>();
        URL url = JVMTIDumper.class.getResource(TOOLS_JAR);
        if (url == null) {
            throw new IllegalStateException(String.format("%s not found", TOOLS_JAR));
        }
        jars.add(url);
        url = JVMTIDumper.class.getResource(JVMTI_DUMPER_IMPL_JAR);
        if (url == null) {
            throw new IllegalStateException(String.format("%s not found", JVMTI_DUMPER_IMPL_JAR));
        }
        jars.add(url);
        URL[] cp = jars.toArray(new URL[jars.size()]);
        for (int i = 0; i < cp.length; i++) {
            url = cp[i];
            if (url.toString().contains("!")) {
                try (InputStream input = url.openStream()) {
                    Path target = tempDir.resolve(String.format("lib-%d.jar", i));
                    Files.copy(input, target);
                    URL newUrl = target.toUri().toURL();
                    cp[i] = newUrl;
                }
            }
        }
        String attachDll = System.mapLibraryName("attach");
        String dll = String.format("%s/%s", System.getProperty("os.arch", "x86"), attachDll);
        url = JVMTIDumper.class.getResource(dll);
        if (url == null) {
            throw new IllegalStateException(String.format("%s not found", dll));
        }
        try (InputStream input = url.openStream()) {
            Path target = tempDir.resolve(attachDll);
            Files.copy(input, target);
        }
        addLibraryPath(tempDir.toString());
        ClassLoader parent  = JVMTIDumper.class.getClassLoader();
        URLClassLoader cl = new URLClassLoader(cp, parent);
        this.classLoader = cl;
        Class<?> clazz = cl.loadClass(JVMTI_DUMPER_IMPL_CLASS);
        this.hotSpotVMDumper = new JVMTIIndirectDumperImpl(clazz);
    }

    private void initDirect() throws ReflectiveOperationException {
        Class<?> vmClass = Class.forName("com.sun.tools.attach.VirtualMachine");
        Method attach = vmClass.getMethod("attach", String.class);
        Method detach = vmClass.getMethod("detach");
        String vmName = ManagementFactory.getRuntimeMXBean().getName();
        int i = vmName.indexOf('@');
        String pid = vmName.substring(0, i);
        String host = vmName.substring(i + 1);
        detach.invoke(attach.invoke(null, pid));
        this.classLoader = null;
        this.hotSpotVMDumper = new JVMTIDirectDumperImpl(pid, host, attach, detach);
    }

    @Override
    public void close() {
        this.hotSpotVMDumper = null;
        URLClassLoader cl = classLoader;
        if (cl != null) {
            try {
                cl.close();
            } catch (IOException e) {
                // ignore
            }
            cl = null;
            this.classLoader = null;
        }
        if (this.tempDir != null) {
            System.gc();
            System.runFinalization();
            System.gc();
            System.runFinalization();
            if (!delete(tempDir)) {
                dumpHeap();
                delete(tempDir);
            }
            this.tempDir = null;
        }
    }

    public void dump(PrintWriter out, Date date) throws IOException {
        try {
            this.hotSpotVMDumper.threadDump(out, date);
        } catch (Throwable e) {
            IOException exception = new IOException(e.toString());
            exception.setStackTrace(e.getStackTrace());
            throw exception;
        }
    }

    private static void addLibraryPath(String pathToAdd) throws ReflectiveOperationException {
        Field usrPathsField = ClassLoader.class.getDeclaredField("usr_paths");
        usrPathsField.setAccessible(true);
        String paths[] = (String[]) usrPathsField.get(null);
        for (int i = 0; i < paths.length; i++) {
            String path = paths[i];
            if (path.equals(pathToAdd)) {
                return;
            }
            Path p = Paths.get(path);
            if (p.getFileName().startsWith(TEMP_DIR_PREFIX)) {
                paths[i] = pathToAdd;
                delete(p);
                return;
            }
        }
        String newPaths[] = Arrays.copyOf(paths, paths.length + 1);
        newPaths[newPaths.length - 1] = pathToAdd;
        usrPathsField.set(null, newPaths);
    }

    private static boolean delete(Path path) {
        if (path == null || !Files.exists(path)) {
            return true;
        }
        try {
            Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    try {
                        Files.delete(file);
                    } catch (IOException e) {
                        // skip
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    try {
                        Files.delete(dir);
                    } catch (IOException e) {
                        // ignore
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            // ignore
        }
        return !Files.exists(path);
    }

    private static void dumpHeap() {
        try {
            Class<?> clazz = Class.forName("com.sun.management.HotSpotDiagnosticMXBean");
            MBeanServer server = ManagementFactory.getPlatformMBeanServer();
            Object hotspotMBean = ManagementFactory.newPlatformMXBeanProxy(server, "com.sun.management:type=HotSpotDiagnostic", clazz);
            Method m = clazz.getMethod("dumpHeap", String.class, boolean.class);
            m.invoke(hotspotMBean, NULL_DEVICE, true);
        } catch (Throwable e) {
            // continue
        }
    }
}
