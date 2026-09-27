package crane.lang;

import static crane.Utils.printLog;

/**
 * WaitNotifyDemo
 */
public class WaitNotifyDemo {
    private static final Object lock = new Object();

    private static void demo1(){
        printLog("Get lock");
        synchronized (lock) {
            try {
                printLog("准备等待");

                // 释放 lock，并进入等待状态
                lock.wait();

                printLog("被唤醒，继续执行");

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static void demo2(){
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        printLog("Get lock");
        synchronized (lock) {
            printLog("唤醒线程1");

            lock.notify();
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Thread thread1 = new Thread(WaitNotifyDemo::demo1);
        Thread thread2 = new Thread(WaitNotifyDemo::demo2);

        thread1.start();
        thread2.start();

        printLog("线程等待");
        thread1.join();
        thread2.join();
        printLog("线程结束");
    }
}
