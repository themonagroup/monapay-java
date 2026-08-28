package com.themona.monapay;

/** An API, transport, or response decoding error. */
public final class MonaPayException extends RuntimeException {
    private final int status;
    private final Object body;

    public MonaPayException(String message) {
        this(message, 0, null, null);
    }

    public MonaPayException(String message, Throwable cause) {
        this(message, 0, null, cause);
    }

    public MonaPayException(String message, int status, Object body) {
        this(message, status, body, null);
    }

    private MonaPayException(String message, int status, Object body, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.body = body;
    }

    public int getStatus() { return status; }
    public Object getBody() { return body; }
}
