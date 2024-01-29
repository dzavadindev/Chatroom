package util;

import java.util.HashMap;
import java.util.Map;

import static java.util.Map.entry;

public class Codes {

    public static Map<Integer, String> codeToMessage = new HashMap<>(Map.ofEntries(
            // 810-819 reserved for login codes,
            entry(810, "You can't log in twice"),
            entry(811, "Invalid username format"),
            entry(812, "User with this username already exists"),
            // 820-829 reserved for message related codes
            entry(822, "Cannot send a private message to yourself"),
            // 830-839 reserved for heartbeat codes
            entry(830, "Pong without ping"),
            // 840-849 reserved for user list related errors
            // 850-859 reserved for game related errors
            entry(850, "You can't create a lobby with name %s. Use only latin letters and numbers"),
            entry(851, "Cannot make a guess before the game starts!"),
            entry(852, "You are not in a game to send your guesses to"),
            entry(853, "Invalid guess provided. Only numbers are acceptable"),
            entry(854, "Your guess in not in the games range: %s"), // response.content(is the game range)
            entry(855, "Can't join two games at the same time"),
            entry(856, "You have already joined this game"),
            // 860-869 reserved for file transfer related errors,
            entry(860, "You have not received a file transfer request to accept or deny"),
            entry(861, "You cannot send a file to yourself"),
            // 700-710 reserved for disconnection reasons,
            entry(700, "Pong timeout"),
            entry(701, "Unterminated message"),
            entry(702, "Server was shut down"),
            // 710-720 reserved for general codes
            entry(710, "You are not logged in"),
            entry(711, "%s %s was not found")
    ));
}
