package ru.sergeyb.dump;

import java.lang.management.ManagementFactory;

class HostHelper {

    private HostHelper() {
        // hidden constructor
    }

    static String getHost() {
        String vmName = ManagementFactory.getRuntimeMXBean().getName();
        return vmName.substring(vmName.indexOf('@') + 1);
    }

}
