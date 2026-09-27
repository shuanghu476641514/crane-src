package crane.lang;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * InterruptDemo
 */
public class InterruptDemo {
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private static void printLog(String msg) {
        System.out.println(LocalTime.now().format(formatter)+Thread.currentThread().getName() + msg);
    }

    private static void demo() {
        while (true) {
            printLog("线程正在等待中断...");
            if (Thread.currentThread().isInterrupted()) {
                printLog("线程被中断");
                break;
            }
        }
        printLog("interrupt 状态："+Thread.currentThread().isInterrupted());
        boolean ret = Thread.interrupted();
        printLog("interrupt 状态："
            + Thread.currentThread().isInterrupted()+", Ret:"+ret+", interrupted:"+Thread.interrupted());
        try {
            while (true) {
                printLog("线程开始sleep...");

                Thread.sleep(10000);
            }
        } catch (InterruptedException e) {
            printLog("线程再次被中断了！interrupt 状态："+Thread.currentThread().isInterrupted());
        }

        try {
            printLog("线程join test...");
            Thread.sleep(10000);
        } catch (InterruptedException e) {
        }
        printLog(" 线程结束");
    }

    public static void main(String[] args) throws InterruptedException {
        Thread thread = new Thread(InterruptDemo::demo);

        thread.start();

        thread.interrupt();

        // 主线程等待 3 秒
        Thread.sleep(10000);

        printLog("通知子线程中断");

        // 设置中断标志
        thread.interrupt();
        printLog("等待子线程结束");
        thread.join();
        printLog("子线程结束");
    }
}
