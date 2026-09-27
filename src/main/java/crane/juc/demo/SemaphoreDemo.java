package crane.juc.demo;

import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;

/**
 * SemaphoreDemo
 */
public class SemaphoreDemo {
    private static void demo(Semaphore semaphore){
        try {
            // 获取许可证
            semaphore.acquire();

            System.out.println("线程 " + Thread.currentThread().getName() + " 获取许可证，开始执行");

            // 模拟执行 1~2 秒
            Thread.sleep(
                ThreadLocalRandom.current().nextLong(1, 1000)
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            // 释放许可证
            semaphore.release();
        }

    }

    public static void main(String[] args) {
        // 同时最多允许 2 个线程获取许可证
        Semaphore semaphore = new Semaphore(2);

        for (int i = 1; i <= 5; i++) {
            int id = i;

            new Thread(() -> {
                for (int j = 0; j < 10; j++) {
                    demo(semaphore);
                }

                System.out.println("线程 " + Thread.currentThread().getName() + " 执行结束");

            }).start();
        }
    }

}
