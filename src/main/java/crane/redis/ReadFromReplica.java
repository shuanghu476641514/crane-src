package crane.redis;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;

/**
 * ReadFromReplica
 */
public class ReadFromReplica {

    public static void main(String[] args) {


        RedisClient redisClient =
            RedisClient.create(
                "redis://172.16.0.104:16380"
            );


        StatefulRedisConnection<String, String> connection =
        redisClient .connect();

        var commands =
            connection.sync();


        System.out.println(
            commands.get("k1")
        );


        connection.close();
        redisClient.shutdown();

    }

}
