package junit.test;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.JUnitCore;

public class MyTest {
    public static void main(String... args) {
        JUnitCore.runClasses(MyTest.class);
    }

    @Before
    public void before() {
        System.out.println("before");
    }

    @Test
    public void test() {
        System.out.println("test");
    }
    @Test
    public void test2() {
        System.out.println("test2");
    }
}
