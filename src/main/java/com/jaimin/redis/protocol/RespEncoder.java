package com.jaimin.redis.protocol;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class RespEncoder {
    private RespEncoder() {}
    private static byte[] ascii(String s) { return s.getBytes(StandardCharsets.UTF_8); }
    public static void simple(OutputStream out, String value) throws IOException { out.write(ascii("+" + value + "
")); }
    public static void error(OutputStream out, String value) throws IOException { out.write(ascii("-ERR " + value + "
")); }
    public static void integer(OutputStream out, long value) throws IOException { out.write(ascii(":" + value + "
")); }
    public static void bulk(OutputStream out, String value) throws IOException {
        if (value == null) { out.write(ascii("$-1
")); return; }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        out.write(ascii("$" + bytes.length + "
")); out.write(bytes); out.write("
".getBytes(StandardCharsets.US_ASCII));
    }
    public static void array(OutputStream out, List<String> values) throws IOException {
        out.write(ascii("*" + values.size() + "
"));
        for (String value : values) bulk(out, value);
    }
}