package crane.redis;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;

/**
 * RedissonClientFactory
 */
public class RedissonClientFactory {

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

}
