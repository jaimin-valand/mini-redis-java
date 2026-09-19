package com.jaimin.redis.protocol;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class RespEncoder {
    private RespEncoder() {}

    private static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    public static void simple(OutputStream out, String value) throws IOException {
        out.write(utf8("+" + value + "\r\n"));
    }

    public static void error(OutputStream out, String value) throws IOException {
        out.write(utf8("-ERR " + value + "\r\n"));
    }

    public static void integer(OutputStream out, long value) throws IOException {
        out.write(utf8(":" + value + "\r\n"));
    }

    public static void bulk(OutputStream out, String value) throws IOException {
        if (value == null) {
            out.write(utf8("$-1\r\n"));
            return;
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        out.write(utf8("$" + bytes.length + "\r\n"));
        out.write(bytes);
        out.write("\r\n".getBytes(StandardCharsets.US_ASCII));
    }

    public static void array(OutputStream out, List<String> values) throws IOException {
        out.write(utf8("*" + values.size() + "\r\n"));
        for (String value : values) bulk(out, value);
    }
}