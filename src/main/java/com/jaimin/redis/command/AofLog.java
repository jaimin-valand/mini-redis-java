package com.jaimin.redis.command;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class AofLog implements Closeable {
    private final Path path;
    private final OutputStream out;

    public AofLog(Path path, boolean enabled) throws IOException {
        this.path = path;
        if (enabled) {
            Path parent = path.getParent();
            if (parent != null) Files.createDirectories(parent);
            out = Files.newOutputStream(path, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } else {
            out = OutputStream.nullOutputStream();
        }
    }

    public synchronized void append(List<String> args) throws IOException {
        out.write(("*" + args.size() + "\r\n").getBytes(StandardCharsets.US_ASCII));
        for (String arg : args) {
            byte[] bytes = arg.getBytes(StandardCharsets.UTF_8);
            out.write(("$" + bytes.length + "\r\n").getBytes(StandardCharsets.US_ASCII));
            out.write(bytes);
            out.write("\r\n".getBytes(StandardCharsets.US_ASCII));
        }
        out.flush();
    }

    public boolean exists() { return Files.exists(path); }
    public Path path() { return path; }

    @Override
    public void close() throws IOException { out.close(); }
}