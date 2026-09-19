package com.jaimin.redis.protocol;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class RespDecoder {
    private final BufferedInputStream in;

    public RespDecoder(InputStream input) { this.in = new BufferedInputStream(input); }

    public List<String> readCommand() throws IOException, RespException {
        int first = in.read();
        if (first == -1) return null;
        if (first != '*') throw new RespException("Expected RESP array");

        int count;
        try {
            count = Integer.parseInt(readLine());
        } catch (NumberFormatException e) {
            throw new RespException("Invalid array length");
        }

        if (count <= 0 || count > 1024) throw new RespException("Invalid array length");

        List<String> args = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int marker = in.read();
            if (marker != '$') throw new RespException("Expected bulk string");

            int length;
            try {
                length = Integer.parseInt(readLine());
            } catch (NumberFormatException e) {
                throw new RespException("Invalid bulk length");
            }

            if (length < 0 || length > 16 * 1024 * 1024) {
                throw new RespException("Invalid bulk length");
            }

            byte[] bytes = in.readNBytes(length);
            if (bytes.length != length || in.read() != '\r' || in.read() != '\n') {
                throw new EOFException("Incomplete bulk string");
            }
            args.add(new String(bytes, StandardCharsets.UTF_8));
        }
        return args;
    }

    private String readLine() throws IOException, RespException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int prev = -1;
        while (true) {
            int b = in.read();
            if (b == -1) throw new EOFException("Unexpected end of stream");

            if (prev == '\r' && b == '\n') {
                byte[] data = buffer.toByteArray();
                return new String(data, 0, Math.max(0, data.length - 1), StandardCharsets.UTF_8);
            }

            buffer.write(b);
            prev = b;
            if (buffer.size() > 1024 * 1024) throw new RespException("Line too long");
        }
    }
}