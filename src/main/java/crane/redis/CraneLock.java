package crane.redis;

import org.redisson.Redisson;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;


/**
 * CraneLock
 */
public class CraneLock {

    private static RedissonClient redissonClient;

    static {
        Config config = new Config();
        // Redis 单节点
        config.useSingleServer()
            .setAddress("redis://172.16.0.104:8888");
        redissonClient = Redisson.create(config);
    }

    public static RedissonClient getClient() {
        return redissonClient;
    }

    public static void main(String[] args)
        throws Exception {
        RedissonClient client = getClient();
        RLock lock = client.getLock("order:create:lock");

        try {


            /*
             * 获取锁
             *
             * 默认：
             * watchdog自动续期
             */
            lock.lock();

            System.out.println(
                Thread.currentThread().getName()
                    + " 获取锁"
            );

            //模拟业务
            Thread.sleep(5000);

            System.out.println(
                "执行订单创建"
            );


        } finally {

            if (lock.isHeldByCurrentThread()) {

                lock.unlock();

                System.out.println("释放锁");

            }

        }
    }

}
