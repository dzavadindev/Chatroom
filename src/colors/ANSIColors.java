package colors;

public abstract class ANSIColors {
    private static final String ANSI_RESET = "\u001B[0m";
    public static final String ANSI_GREEN = "\u001B[32m";
    public static final String ANSI_CYAN = "\u001B[36m";
    public static final String ANSI_MAGENTA = "\u001B[35m";
    public static final String ANSI_YELLOW = "\u001B[33m";

    public static void coloredPrint(String color, String message) {
        System.out.println(color + message + ANSI_RESET);
    }
}
