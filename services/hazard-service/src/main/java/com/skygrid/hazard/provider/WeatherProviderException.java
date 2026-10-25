package com.skygrid.hazard.provider;

public class WeatherProviderException extends RuntimeException {
    private final int status;
    public WeatherProviderException(int status, String message) { super(message); this.status = status; }
    public WeatherProviderException(int status, String message, Throwable cause) { super(message, cause); this.status = status; }
    public int status() { return status; }
}
