package crane.juc.demo;

import java.util.Random;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * ConditionDemo
 */
public class ConditionDemo {

    private final ReentrantLock lock = new ReentrantLock();

    // 条件：队列不为空
    private final Condition notEmpty = lock.newCondition();

    // 条件：队列未满
    private final Condition notFull = lock.newCondition();

    private final int[] queue = new int[5];
    private int count = 0;

    public void add(int value) {
        lock.lock();
        try {
            if (count == queue.length) {
                notFull.await();
            }
            queue[count++] = value;
            notEmpty.signal();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            lock.unlock();
        }
    }

    public int take() {
        lock.lock();
        try {
            if (count == 0) {
                notEmpty.await();
            }
            int value = queue[--count];
            notFull.signal();
            return value;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            lock.unlock();
        }
    }

    public static void main(String[] args) {
        ConditionDemo demo = new ConditionDemo();

        Thread producer = new Thread(() -> {
            Random random = new Random();
            try {
                for (int i = 1; i <= 10; i++) {
                   // System.out.printf("%s, add: %d\n", Thread.currentThread().getName(), i);
                    demo.add(i);

                    Thread.sleep(random.nextInt(1000));
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });

        Thread consumer = new Thread(() -> {
            Random random = new Random();
            try {
                for (int i = 1; i <= 10; i++) {
                    Thread.sleep(100 + random.nextInt(1000));
                    int value = demo.take();
                    System.out.printf("%s, take: %d\n", Thread.currentThread().getName(), value);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        consumer.start();
        producer.start();
    }
}
