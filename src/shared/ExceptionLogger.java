package shared;

public class ExceptionLogger {

    public static void log(Exception e){
        System.err.println("We have caught an exception:");
        System.err.println("\tCaused by: ");
        System.err.println("\t\t" + e.getLocalizedMessage());
        System.err.println("\tStack trace:");
        StackTraceElement[] stack = e.getStackTrace();
        for (StackTraceElement s : stack)
            System.err.println("\t\tAt " + s.toString());
    }

}
