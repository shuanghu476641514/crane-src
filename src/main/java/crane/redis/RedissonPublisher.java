package crane.redis;

import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;


public class RedissonPublisher {


    public static void main(String[] args)
        throws Exception {

        RedissonClient client =
            RedissonClientFactory
                .getClient();

        RTopic topic =
            client.getTopic("order-topic");

        System.out.println("Sub:"+topic.countSubscribers());
        System.out.println("Name:"+ topic.getChannelNames());

        long count =
            topic.publish("订单1001创建成功");

        System.out.println(
            System.currentTimeMillis()+"发送完成，接收人数:"
                + count);

        client.shutdown();

    }

}