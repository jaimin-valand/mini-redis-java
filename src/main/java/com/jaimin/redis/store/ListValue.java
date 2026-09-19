package com.jaimin.redis.store;

import java.util.*;

public final class ListValue implements Value {
    private final LinkedList<String> values = new LinkedList<>();
    public synchronized void pushLeft(List<String> items) { for (String item : items) values.addFirst(item); }
    public synchronized void pushRight(List<String> items) { values.addAll(items); }
    public synchronized String popLeft() { return values.isEmpty() ? null : values.removeFirst(); }
    public synchronized String popRight() { return values.isEmpty() ? null : values.removeLast(); }
    public synchronized List<String> range(long start, long stop) {
        int size = values.size();
        int from = normalize(start, size), to = normalize(stop, size);
        if (from > to || from >= size || to < 0) return List.of();
        from = Math.max(from, 0); to = Math.min(to, size - 1);
        return new ArrayList<>(values.subList(from, to + 1));
    }
    public synchronized boolean isEmpty() { return values.isEmpty(); }
    private static int normalize(long index, int size) {
        if (index < 0) return (int)Math.max(Integer.MIN_VALUE, size + index);
        return (int)Math.min(Integer.MAX_VALUE, index);
    }
    @Override public String type() { return "list"; }
}