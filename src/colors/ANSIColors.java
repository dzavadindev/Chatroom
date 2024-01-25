package colors;

import java.util.LinkedList;
import java.util.List;
import java.util.Random;

public abstract class ANSIColors {
    private static final String ANSI_RESET = "\u001B[0m";
    public static final String ANSI_RED = "\u001B[31m";
    public static final String ANSI_GREEN = "\u001B[32m";
    public static final String ANSI_YELLOW = "\u001B[33m";
    public static final String ANSI_BLUE = "\u001B[34m";
    public static final String ANSI_MAGENTA = "\u001B[35m";
    public static final String ANSI_CYAN = "\u001B[36m";
    public static final String ANSI_GRAY = "\\e[38;5;248m";

    private static final List<String> allColors = new LinkedList<>(List.of(ANSI_RED, ANSI_GREEN, ANSI_YELLOW, ANSI_BLUE, ANSI_MAGENTA, ANSI_CYAN));

    public static void coloredPrint(String color, String message) {
        System.out.println(color + message + ANSI_RESET);
    }

    // Im having too much fun with this shit tbh.
    public static void rainbowPrint(String message) {
        StringBuilder sb = new StringBuilder();
        Random rand = new Random();
        for (char c : message.toCharArray()) {
            int color = rand.nextInt(0, allColors.size() - 1);
            sb.append(allColors.get(color)).append(c);
        }
        System.out.println(sb + ANSI_RESET);
    }
}
