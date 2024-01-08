package exceptions;

public class UserNotFoundException extends Exception{
    public UserNotFoundException(String username) {
        super("User " + username + " is not among the connected users");
    }
}
