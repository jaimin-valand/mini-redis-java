#!/usr/bin/env bash
set -euo pipefail

rm -rf out
mkdir out

javac --release 21 -Xlint:all -d out $(find src/main/java -name "*.java")

cat > /tmp/MiniRedisSmoke.java <<'JAVA'
import com.jaimin.redis.command.*;
import com.jaimin.redis.store.*;
import java.nio.file.*;
import java.util.*;

public class MiniRedisSmoke {
    static void ok(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
    }

    public static void main(String[] args) throws Exception {
        try (RedisStore store = new RedisStore()) {
            Path p = Files.createTempFile("mr", ".aof");
            try (AofLog aof = new AofLog(p, true)) {
                CommandDispatcher d = new CommandDispatcher(store, aof);
                ok(d.execute(List.of("PING"), false).value().equals("PONG"), "PING");
                ok(d.execute(List.of("SET", "x", "41"), false).ok(), "SET");
                ok(d.execute(List.of("INCR", "x"), false).value().equals(42L), "INCR");
                ok(d.execute(List.of("LPUSH", "l", "a", "b"), false).value().equals(2L), "LPUSH");
                ok(d.execute(List.of("LRANGE", "l", "0", "-1"), false).value().equals(List.of("b", "a")), "LRANGE");
            }
            Files.deleteIfExists(p);
        }
        System.out.println("PASS: dependency-free smoke tests");
    }
}
JAVA

javac -cp out -d out /tmp/MiniRedisSmoke.java
java -cp out MiniRedisSmoke
rm -rf out
