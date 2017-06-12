package ru.sergeyb.dump;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Date;

public interface Dumper {

    void threadDump(PrintWriter out, Date date) throws IOException;

    void destroy();

}
