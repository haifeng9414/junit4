package org.junit.runners.model;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.List;

import org.junit.internal.runners.model.ReflectiveCallable;

/**
 * Represents a method on a test class to be invoked at the appropriate point in
 * test execution. These methods are usually marked with an annotation (such as
 * {@code @Test}, {@code @Before}, {@code @After}, {@code @BeforeClass},
 * {@code @AfterClass}, etc.)
 *
 * @since 4.5
 */
/*
对测试类中的方法的封装，提供了验证及获取方法信息等功能
 */
public class FrameworkMethod extends FrameworkMember<FrameworkMethod> {
    private final Method method;

    /**
     * Returns a new {@code FrameworkMethod} for {@code method}
     */
    public FrameworkMethod(Method method) {
        if (method == null) {
            throw new NullPointerException(
                    "FrameworkMethod cannot be created without an underlying method.");
        }
        this.method = method;

        if (isPublic()) {
            // This method could be a public method in a package-scope base class
            try {
                method.setAccessible(true);
            } catch (SecurityException  e) {
                // We may get an IllegalAccessException when we try to call the method
            }
        }
    }

    /**
     * Returns the underlying Java method
     */
    public Method getMethod() {
        return method;
    }

    /**
     * Returns the result of invoking this method on {@code target} with
     * parameters {@code params}. {@link InvocationTargetException}s thrown are
     * unwrapped, and their causes rethrown.
     */
    //这里对调用method时可能抛出的InvocationTargetException异常做了包装，当发生异常时将cause抛出而不是InvocationTargetException
    public Object invokeExplosively(final Object target, final Object... params)
            throws Throwable {
        return new ReflectiveCallable() {
            @Override
            protected Object runReflectiveCall() throws Throwable {
                return method.invoke(target, params);
            }
        }.run();
    }

    /**
     * Returns the method's name
     */
    @Override
    public String getName() {
        return method.getName();
    }

    /**
     * Adds to {@code errors} if this method:
     * <ul>
     * <li>is not public, or
     * <li>takes parameters, or
     * <li>returns something other than void, or
     * <li>is static (given {@code isStatic is false}), or
     * <li>is not static (given {@code isStatic is true}).
     * </ul>
     */
    public void validatePublicVoidNoArg(boolean isStatic, List<Throwable> errors) {
        validatePublicVoid(isStatic, errors);
        if (method.getParameterTypes().length != 0) {
            errors.add(new Exception("Method " + method.getName() + " should have no parameters"));
        }
    }


    /**
     * Adds to {@code errors} if this method:
     * <ul>
     * <li>is not public, or
     * <li>returns something other than void, or
     * <li>is static (given {@code isStatic is false}), or
     * <li>is not static (given {@code isStatic is true}).
     * </ul>
     */
    public void validatePublicVoid(boolean isStatic, List<Throwable> errors) {
        if (isStatic() != isStatic) {
            String state = isStatic ? "should" : "should not";
            errors.add(new Exception("Method " + method.getName() + "() " + state + " be static"));
        }
        if (!isPublic()) {
            errors.add(new Exception("Method " + method.getName() + "() should be public"));
        }
        if (method.getReturnType() != Void.TYPE) {
            errors.add(new Exception("Method " + method.getName() + "() should be void"));
        }
    }

    @Override
    protected int getModifiers() {
        return method.getModifiers();
    }

    /**
     * Returns the return type of the method
     */
    public Class<?> getReturnType() {
        return method.getReturnType();
    }

    /**
     * Returns the return type of the method
     */
    @Override
    public Class<?> getType() {
        return getReturnType();
    }

    /**
     * Returns the class where the method is actually declared
     */
    @Override
    public Class<?> getDeclaringClass() {
        return method.getDeclaringClass();
    }

    public void validateNoTypeParametersOnArgs(List<Throwable> errors) {
        new NoGenericTypeParametersValidator(method).validate(errors);
    }

    //TestClass中调用handlePossibleBridgeMethod的地方是addToAnnotationLists，该方法传入的参数是当前的FrameworkMethod
    //和已经保存在map中的List<FrameworkMethod>，而查看调用addToAnnotationLists方法的scanAnnotatedMembers方法的for循环
    //可以发现测试类的遍历是从当前测试类开始，逐个循环父类，所以是子类的方法先进入map，然后是父类的，而bridge方法(bridge的定义查看
    // 笔记：bridge方法)是在子类中才有的，所以如果测试类的整个继承结构中存在bridge方法，则bridge方法肯定比其对应的父类方法先进入map
    //所以就有了下面的判断方式，而在发现bridge方法后返回otherMethod就如下面注释的那样
    @Override
    FrameworkMethod handlePossibleBridgeMethod(List<FrameworkMethod> methods) {
        for (int i = methods.size() - 1; i >=0; i--) {
            FrameworkMethod otherMethod = methods.get(i);
            if (isShadowedBy(otherMethod)) {
                if (otherMethod.isBridgeMethod()) {
                    /*
                     *  We need to return the previously-encountered bridge method
                     *  because JUnit won't be able to call the parent method,
                     *  because the parent class isn't public.
                     */
                    methods.remove(i);
                    return otherMethod;
                }
                // We found a shadowed member that isn't a bridge method. Ignore it.
                return null;
            }
        }
        // No shadow or bridge method found. The caller should add *this* member.
        return this;
    }

    @Override
    public boolean isShadowedBy(FrameworkMethod other) {
        if (isStatic()) {
            return false;
        }
        if (!other.getName().equals(getName())) {
            return false;
        }
        if (other.getParameterTypes().length != getParameterTypes().length) {
            return false;
        }
        for (int i = 0; i < other.getParameterTypes().length; i++) {
            if (!other.getParameterTypes()[i].equals(getParameterTypes()[i])) {
                return false;
            }
        }
        return true;
    }

    @Override
    boolean isBridgeMethod() {
        return method.isBridge();
    }

    @Override
    public boolean equals(Object obj) {
        if (!FrameworkMethod.class.isInstance(obj)) {
            return false;
        }
        return ((FrameworkMethod) obj).method.equals(method);
    }

    @Override
    public int hashCode() {
        return method.hashCode();
    }

    /**
     * Returns true if this is a no-arg method that returns a value assignable
     * to {@code type}
     *
     * @deprecated This is used only by the Theories runner, and does not
     *             use all the generic type info that it ought to. It will be replaced
     *             with a forthcoming ParameterSignature#canAcceptResultOf(FrameworkMethod)
     *             once Theories moves to junit-contrib.
     */
    @Deprecated
    public boolean producesType(Type type) {
        return getParameterTypes().length == 0 && type instanceof Class<?>
                && ((Class<?>) type).isAssignableFrom(method.getReturnType());
    }

    private Class<?>[] getParameterTypes() {
        return method.getParameterTypes();
    }

    /**
     * Returns the annotations on this method
     */
    public Annotation[] getAnnotations() {
        return method.getAnnotations();
    }

    /**
     * Returns the annotation of type {@code annotationType} on this method, if
     * one exists.
     */
    public <T extends Annotation> T getAnnotation(Class<T> annotationType) {
        return method.getAnnotation(annotationType);
    }

    @Override
    public String toString() {
        return method.toString();
    }
}
