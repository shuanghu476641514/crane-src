package crane.redis;

import org.redisson.api.RPatternTopic;
import org.redisson.api.RedissonClient;

/**
 * RedissonSubscriber
 */
public class RedissonSubscriber {

    public static void main(String[] args)
        throws Exception {

        RedissonClient client =
            RedissonClientFactory
                .getClient();

        RPatternTopic topic =
            client.getPatternTopic(
                "order*"
            );

        topic.addListener(
            String.class,
            (pattern, channel, message) -> {


                System.out.println(
                    "匹配规则:"
                        + pattern
                );


                System.out.println(
                    "实际频道:"
                        + channel
                );


                System.out.println(
                    "消息:"
                        + message
                );

            }
        );


        System.out.println(
            "Pattern监听启动"
        );


        Thread.currentThread()
            .join();

    }

}
