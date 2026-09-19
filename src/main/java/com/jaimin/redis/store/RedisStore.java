package com.jaimin.redis.store;

import java.util.*;
import java.util.concurrent.*;

public final class RedisStore implements AutoCloseable {
    private final ConcurrentHashMap<String, Value> data = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> expiryNanos = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleaner = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "redis-expiry-cleaner"); t.setDaemon(true); return t;
    });

    public RedisStore() { cleaner.scheduleAtFixedRate(this::removeExpiredKeys, 1, 1, TimeUnit.SECONDS); }

    public Value get(String key) { expireIfNeeded(key); return data.get(key); }
    public void put(String key, Value value) { data.put(key, value); expiryNanos.remove(key); }
    public Value remove(String key) { expiryNanos.remove(key); return data.remove(key); }
    public boolean exists(String key) { return get(key) != null; }
    public int size() { removeExpiredKeys(); return data.size(); }

    public boolean expire(String key, long seconds) {
        if (get(key) == null) return false;
        if (seconds <= 0) { remove(key); return true; }
        expiryNanos.put(key, System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds)); return true;
    }

    public long ttl(String key) {
        if (get(key) == null) return -2;
        Long deadline = expiryNanos.get(key);
        if (deadline == null) return -1;
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0) { remove(key); return -2; }
        return TimeUnit.NANOSECONDS.toSeconds(remaining);
    }

    public long increment(String key, long delta) {
        synchronized (lockFor(key)) {
            Value value = get(key);
            if (value == null) { put(key, new StringValue(Long.toString(delta))); return delta; }
            if (!(value instanceof StringValue sv)) throw new IllegalArgumentException("WRONGTYPE key is not a string");
            long current;
            try { current = Long.parseLong(sv.get()); } catch (NumberFormatException e) { throw new IllegalArgumentException("value is not an integer"); }
            long next;
            try { next = Math.addExact(current, delta); } catch (ArithmeticException e) { throw new IllegalArgumentException("increment or decrement would overflow"); }
            sv.set(Long.toString(next)); return next;
        }
    }

    public long pushLeft(String key, List<String> items) {
        synchronized (lockFor(key)) {
            ListValue list = listForMutation(key); list.pushLeft(items); return list.range(0, Long.MAX_VALUE).size();
        }
    }
    public long pushRight(String key, List<String> items) {
        synchronized (lockFor(key)) {
            ListValue list = listForMutation(key); list.pushRight(items); return list.range(0, Long.MAX_VALUE).size();
        }
    }
    public String popLeft(String key) { synchronized(lockFor(key)) { ListValue l = listForMutation(key); String v=l.popLeft(); if(l.isEmpty()) remove(key); return v; } }
    public String popRight(String key) { synchronized(lockFor(key)) { ListValue l = listForMutation(key); String v=l.popRight(); if(l.isEmpty()) remove(key); return v; } }
    public List<String> range(String key,long start,long stop) { Value v=get(key); if(v==null)return List.of(); if(!(v instanceof ListValue l))throw new IllegalArgumentException("WRONGTYPE key is not a list"); return l.range(start,stop); }

    private ListValue listForMutation(String key) {
        Value existing = get(key);
        if (existing == null) { ListValue list = new ListValue(); put(key,list); return list; }
        if (!(existing instanceof ListValue list)) throw new IllegalArgumentException("WRONGTYPE key is not a list");
        return list;
    }
    private Object lockFor(String key) { return key.intern(); }
    private void expireIfNeeded(String key) { Long d=expiryNanos.get(key); if(d!=null && d-System.nanoTime()<=0) remove(key); }
    private void removeExpiredKeys() { for(String key: expiryNanos.keySet()) expireIfNeeded(key); }
    @Override public void close() { cleaner.shutdownNow(); data.clear(); expiryNanos.clear(); }
}