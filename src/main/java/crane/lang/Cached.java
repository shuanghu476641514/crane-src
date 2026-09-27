package crane.lang;

/**
 * Caced
 */
public class Cached {

    public static void main(String[] args) {




        String a1 = "abc";
        String s1 = "abc";
        String s2 = s1.intern();
            //new String("abc");

        System.out.println(a1 == s1);
        System.out.println(s1 == s2);
    }
}
