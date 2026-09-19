package com.jaimin.redis.protocol;

public final class RespException extends Exception {
    private static final long serialVersionUID = 1L;
    public RespException(String message) { super(message); }
}