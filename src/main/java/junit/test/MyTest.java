package junit.test;

import org.junit.Test;
import org.junit.runner.JUnitCore;

public class MyTest {
    public static void main(String... args) {
        JUnitCore.main(MyTest.class.getName());
    }

    @Test
    public void test() {
        System.out.println("my test run");
    }
}
