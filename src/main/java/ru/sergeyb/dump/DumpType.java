package ru.sergeyb.dump;

enum DumpType {
    SIMPLE(new SimpleDumper()),
    JMX(new JMXDumper()),
    HOTSPOT(new HotSpotVMDumper());

    private final Dumper dumper;

    private DumpType(Dumper dumper) {
        this.dumper = dumper;
    }

    Dumper dumper() {
        return dumper;
    }

    void destroy() {
        dumper.destroy();
    }

    static void destroyAll() {
        for (DumpType dumpType: DumpType.values()) {
            dumpType.destroy();
        }
    }
}
