package ru.sergeyb.dump;

import java.lang.management.ManagementFactory;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

@WebListener
public class ContextListener implements ServletContextListener {
    private final Logger logger;
    private final MBeanServer mBeanServer;
    private final ObjectName name;

    public ContextListener() {
        this.logger = Logger.getLogger(ContextListener.class.getName());
        this.mBeanServer = ManagementFactory.getPlatformMBeanServer();
        try {
            this.name = new ObjectName(ThreadDumpImpl.JMX_NAME);
        } catch (MalformedObjectNameException e) {
            logger.log(Level.SEVERE, "ContextListener", e);
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public void contextInitialized(ServletContextEvent event) {
        logger.log(Level.INFO, "Starting thread dump monitoring...");
        ThreadDumpImpl.runThreadDumpMonitoring();
        try {
            ThreadDumpMBean mBean = new ThreadDump();
            mBeanServer.registerMBean(mBean, name);
        } catch (InstanceAlreadyExistsException e) {
            logger.log(Level.SEVERE, "contextInitialized", e);
        } catch (MBeanRegistrationException e) {
            logger.log(Level.SEVERE, "contextInitialized", e);
        } catch (NotCompliantMBeanException e) {
            logger.log(Level.SEVERE, "contextInitialized", e);
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent event) {
        logger.log(Level.INFO, "Stopping thread dump monitoring...");
        try {
            mBeanServer.unregisterMBean(name);
        } catch (MBeanRegistrationException e) {
            logger.log(Level.SEVERE, "contextDestroyed", e);
        } catch (InstanceNotFoundException e) {
            logger.log(Level.SEVERE, "contextDestroyed", e);
        }
        ThreadDumpImpl.stopThreadDumpMonitoring();
        DumpType.destroyAll();
    }
}
