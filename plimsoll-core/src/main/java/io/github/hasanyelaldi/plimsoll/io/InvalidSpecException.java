package io.github.hasanyelaldi.plimsoll.io;

/** The spec cannot be read or is not valid. The CLI maps this to exit code 2. */
public final class InvalidSpecException extends RuntimeException {

    public InvalidSpecException(String message) {
        super(message);
    }

    public InvalidSpecException(String message, Throwable cause) {
        super(message, cause);
    }
}
