package com.jaimin.redis;

import com.jaimin.redis.command.*;
import com.jaimin.redis.store.*;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RedisStoreTest {
    @Test void coreCommandsAndExpiryWork() throws Exception {
        try (RedisStore store = new RedisStore()) {
            Path p = Files.createTempFile("mini-redis", ".aof");
            try (AofLog aof = new AofLog(p, true)) {
                CommandDispatcher d = new CommandDispatcher(store, aof);
                assertEquals("PONG", d.execute(List.of("PING"), false).value());
                assertTrue(d.execute(List.of("SET", "n", "10"), false).ok());
                assertEquals(11L, d.execute(List.of("INCR", "n"), false).value());
                assertEquals(2L, d.execute(List.of("LPUSH", "l", "a", "b"), false).value());
                assertEquals(List.of("b", "a"), d.execute(List.of("LRANGE", "l", "0", "-1"), false).value());
                assertEquals(1L, d.execute(List.of("EXPIRE", "n", "1"), false).value());
                assertTrue((Long)d.execute(List.of("TTL", "n"), false).value() >= 0);
                Thread.sleep(1100);
                assertNull(d.execute(List.of("GET", "n"), false).value());
            } finally { Files.deleteIfExists(p); }
        }
    }

    @Test void wrongTypesReturnErrors() throws Exception {
        try (RedisStore store = new RedisStore()) {
            Path p = Files.createTempFile("mini-redis", ".aof");
            try (AofLog aof = new AofLog(p, false)) {
                CommandDispatcher d = new CommandDispatcher(store, aof);
                d.execute(List.of("SET", "x", "value"), false);
                assertFalse(d.execute(List.of("INCR", "x"), false).ok());
            } finally { Files.deleteIfExists(p); }
        }
    }
}