package messages;

import java.util.HashMap;

public record Leaderboard(String lobby, HashMap<String, Integer> leaderboard) {
}
