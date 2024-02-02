package exceptions;

public class InputArgumentMismatchException extends Exception {
    public InputArgumentMismatchException(int argsNum) {
        super("Expected 2 arguments provided for that command, received " + argsNum);
    }
}
