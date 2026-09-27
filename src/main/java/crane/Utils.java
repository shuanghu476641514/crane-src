package crane;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Utils
 */
public class Utils {
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    public static void printLog(String msg) {
        System.out.println("["+LocalTime.now().format(formatter)+" "+Thread.currentThread().getName()+"] " + msg);
    }
}
