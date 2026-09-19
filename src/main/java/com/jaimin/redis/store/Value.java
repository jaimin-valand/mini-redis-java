package com.jaimin.redis.store;

public sealed interface Value permits StringValue, ListValue {
    String type();
}