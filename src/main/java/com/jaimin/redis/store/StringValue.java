package com.jaimin.redis.store;

public final class StringValue implements Value {
    private String value;
    public StringValue(String value) { this.value = value; }
    public synchronized String get() { return value; }
    public synchronized void set(String value) { this.value = value; }
    @Override public String type() { return "string"; }
}