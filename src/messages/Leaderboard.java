package messages;

import java.util.Map;

public record Leaderboard(String lobby, Map<String, Long> leaderboard) {
}
