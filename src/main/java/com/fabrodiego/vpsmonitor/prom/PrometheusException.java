package com.fabrodiego.vpsmonitor.prom;

public class PrometheusException extends RuntimeException {

    public PrometheusException(String message) {
        super(message);
    }

    public PrometheusException(String message, Throwable cause) {
        super(message, cause);
    }
}
