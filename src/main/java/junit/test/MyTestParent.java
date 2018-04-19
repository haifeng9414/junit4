package junit.test;

import org.junit.Test;
import org.junit.runner.JUnitCore;

public class MyTestParent<T> {
    @Test
    public void testParent() {
        System.out.println("parent");
    }
}
