package crane.juc;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class ConditionWait {

    public static void main(String[] args) throws InterruptedException {
        ReentrantLock lock = new ReentrantLock();
        Condition queueA = lock.newCondition();
        Condition queueB = lock.newCondition();

        Thread waiterA = new Thread(() -> {
            lock.lock();
            try {
                System.out.println(Thread.currentThread().getName() + ": await on queueA");
                queueA.await();
                System.out.println(Thread.currentThread().getName() + ": after await on queueA");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                lock.unlock();
            }
        }, "waiterA");

        Thread waiterB = new Thread(() -> {
            lock.lock();
            try {
                System.out.println(Thread.currentThread().getName() + ": await on queueB");
                queueB.await();
                System.out.println(Thread.currentThread().getName() + ": after await on queueB");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                lock.unlock();
            }
        }, "waiterB");

        Thread notifierA = new Thread(() -> {
            lock.lock();
            try {
                Thread.sleep(300);
                System.out.println(Thread.currentThread().getName() + ": signal queueA");
                queueA.signal();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                lock.unlock();
            }
        }, "notifierA");

        Thread notifierB = new Thread(() -> {
            lock.lock();
            try {
                Thread.sleep(500);
                System.out.println(Thread.currentThread().getName() + ": signal queueB");
                queueB.signal();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                lock.unlock();
            }
        }, "notifierB");

        waiterA.start();
        waiterB.start();
        notifierA.start();
        notifierB.start();
        waiterA.join();
        waiterB.join();
        notifierA.join();
        notifierB.join();
    }
}
