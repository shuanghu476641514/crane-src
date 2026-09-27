package crane.collection.demo;

import java.util.ArrayList;
import java.util.List;

/**
 * ArrayListDemo
 */
public class ArrayListDemo {

    public static void main(String[] args) {
        retainAll();
    }

    static void retainAll(){
        List<String> demo = new ArrayList<>();
        demo.add("a");
        demo.add("1");
        demo.add("2");
        demo.add("b");
        demo.add("c");
        List<String> demo2 = new ArrayList<>();
        demo2.add("a");
        demo2.add("d");
        demo2.add("c");
        //求交集
        demo.retainAll(demo2);

        System.out.println(demo);
    }

    static void containNull(){
        List<String> demo = new ArrayList<>();
        demo.add(null);
        demo.add("A");
        System.out.println(demo.contains(null));
        System.out.println(demo.contains("A"));
        System.out.println(demo.contains("B"));
    }
    static void toArray(){
        List<String> demo = new ArrayList<>();
        demo.add("A");
        demo.add("B");

        Object[] a1 = demo.toArray();
        Object[] a2 = demo.toArray();

        System.out.println(a1 == a2);
        System.out.println(a1.toString());
        System.out.println(a2.toString());
    }
}
