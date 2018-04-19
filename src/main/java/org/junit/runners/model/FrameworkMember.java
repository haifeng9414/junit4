package org.junit.runners.model;

import java.lang.reflect.Modifier;
import java.util.List;

/**
 * Parent class for {@link FrameworkField} and {@link FrameworkMethod}
 *
 * @since 4.7
 */
/*
T要求是FrameworkMember类型的，所以这里写成T extends FrameworkMember<T>，同时还限定了extends FrameworkMember<T>
表示T继承自FrameworkMember<T>，这样FrameworkMember的子类在实现时只能写成A extends<FrameworkMember<A>>的形式，并且子类中
的T类型就被限定成A，如子类实现isShadowedBy方法时参数类型只能是A

FrameworkMember用于指定封装测试类的属性和方法的类的接口
 */
public abstract class FrameworkMember<T extends FrameworkMember<T>> implements
        Annotatable {
    abstract boolean isShadowedBy(T otherMember);

    /**
     * Check if this member is shadowed by any of the given members. If it
     * is, the other member is removed.
     * 
     * @return member that should be used, or {@code null} if no member should be used.
     */
    abstract T handlePossibleBridgeMethod(List<T> members);

    abstract boolean isBridgeMethod();

    protected abstract int getModifiers();

    /**
     * Returns true if this member is static, false if not.
     */
    public boolean isStatic() {
        return Modifier.isStatic(getModifiers());
    }

    /**
     * Returns true if this member is public, false if not.
     */
    public boolean isPublic() {
        return Modifier.isPublic(getModifiers());
    }

    public abstract String getName();

    public abstract Class<?> getType();

    public abstract Class<?> getDeclaringClass();
}
