package crane.juc;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

public class ObjectWait {

   static private Object lock = new Object();

    private static void waitDemo(){
        synchronized (lock) {
            try {
                System.out.println(Thread.currentThread().getName() + ": before wait"+ LocalDateTime.now());
                lock.wait();
                Thread.sleep(3000);
                System.out.println(Thread.currentThread().getName() + ": after wait" + LocalDateTime.now());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static void startAndJoin(List<Thread> threadList){
        threadList.forEach(Thread::start);
        threadList.forEach(thread -> {
            try {
                thread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
    }

    public static void main(String[] args) throws InterruptedException {
        List<Thread> threadList = new ArrayList<>(IntStream.range(0, 3)
            .mapToObj(i -> new Thread(ObjectWait::waitDemo, "waiter" + i))
            .toList());


        Thread notifier = new Thread(() -> {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            synchronized (lock) {
                System.out.println(Thread.currentThread().getName() + ": notify"+LocalDateTime.now());
                lock.notify();
            }

            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            synchronized (lock){

                System.out.println(Thread.currentThread().getName() + ": notifyAll"+LocalDateTime.now());
                lock.notifyAll();
            }
        }, "notifier");

        threadList.add(notifier);

        startAndJoin(threadList);
    }
}
