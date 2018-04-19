package org.junit.validator;

import static java.util.Collections.singletonList;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.runners.model.Annotatable;
import org.junit.runners.model.FrameworkField;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.TestClass;

/**
 * An {@code AnnotationsValidator} validates all annotations of a test class,
 * including its annotated fields and methods.
 * 
 * @since 4.12
 */
/*
该Validator中使用内部类定义了一个AnnotatableValidator用于验证，因为理论上只需要Class、Method、Field
这3中验证器，所以使用内部类更方便，减少了源码文件数量
 */
public final class AnnotationsValidator implements TestClassValidator {
    private static final List<AnnotatableValidator<?>> VALIDATORS = Arrays.<AnnotatableValidator<?>>asList(
            new ClassValidator(), new MethodValidator(), new FieldValidator());

    /**
     * Validate all annotations of the specified test class that are be
     * annotated with {@link ValidateWith}.
     * 
     * @param testClass
     *            the {@link TestClass} that is validated.
     * @return the errors found by the validator.
     */
    public List<Exception> validateTestClass(TestClass testClass) {
        List<Exception> validationErrors= new ArrayList<Exception>();
        for (AnnotatableValidator<?> validator : VALIDATORS) {
            List<Exception> additionalErrors= validator
                    .validateTestClass(testClass);
            validationErrors.addAll(additionalErrors);
        }
        return validationErrors;
    }

    //模版方法模式，validateTestClass方法定义了验证TestClass的过程，子类实现过程中的步骤
    //需要实现的两个方法一个是根据TestClass获取待验证的Annotatable，一个是使用指定的AnnotationValidator
    //进行验证，AnnotationValidator抽象类中定义了3个方法，分别对Class、Field、Method进行验证，AnnotatableValidator的实现类
    //根据自己的责任调用对应的方法如ClassValidator调用validateAnnotatedClass方法，而查看validateAnnotatable可知ClassValidator
    //使用的AnnotationValidator是用ValidateWith注解指定的某个具体的AnnotationValidator，在validateAnnotatable方法中获取这个
    //注解对应的AnnotationValidator实例并传给相应的AnnotatableValidator实现类进行验证（如果不存在ValidateWith注解则跳过验证）
    //AnnotationValidatorFactory是用于保存已实例化过的ValidateWith
    private static abstract class AnnotatableValidator<T extends Annotatable> {
        private static final AnnotationValidatorFactory ANNOTATION_VALIDATOR_FACTORY = new AnnotationValidatorFactory();

        abstract Iterable<T> getAnnotatablesForTestClass(TestClass testClass);

        abstract List<Exception> validateAnnotatable(
                AnnotationValidator validator, T annotatable);

        public List<Exception> validateTestClass(TestClass testClass) {
            List<Exception> validationErrors= new ArrayList<Exception>();
            for (T annotatable : getAnnotatablesForTestClass(testClass)) {
                List<Exception> additionalErrors= validateAnnotatable(annotatable);
                validationErrors.addAll(additionalErrors);
            }
            return validationErrors;
        }

        private List<Exception> validateAnnotatable(T annotatable) {
            List<Exception> validationErrors= new ArrayList<Exception>();
            for (Annotation annotation : annotatable.getAnnotations()) {
                Class<? extends Annotation> annotationType = annotation
                        .annotationType();
                ValidateWith validateWith = annotationType
                        .getAnnotation(ValidateWith.class);
                if (validateWith != null) {
                    AnnotationValidator annotationValidator = ANNOTATION_VALIDATOR_FACTORY
                            .createAnnotationValidator(validateWith);
                    List<Exception> errors= validateAnnotatable(
                            annotationValidator, annotatable);
                    validationErrors.addAll(errors);
                }
            }
            return validationErrors;
        }
    }

    private static class ClassValidator extends AnnotatableValidator<TestClass> {
        @Override
        Iterable<TestClass> getAnnotatablesForTestClass(TestClass testClass) {
            return singletonList(testClass);
        }

        @Override
        List<Exception> validateAnnotatable(
                AnnotationValidator validator, TestClass testClass) {
            return validator.validateAnnotatedClass(testClass);
        }
    }

    private static class MethodValidator extends
            AnnotatableValidator<FrameworkMethod> {
        @Override
        Iterable<FrameworkMethod> getAnnotatablesForTestClass(
                TestClass testClass) {
            return testClass.getAnnotatedMethods();
        }

        @Override
        List<Exception> validateAnnotatable(
                AnnotationValidator validator, FrameworkMethod method) {
            return validator.validateAnnotatedMethod(method);
        }
    }

    private static class FieldValidator extends
            AnnotatableValidator<FrameworkField> {
        @Override
        Iterable<FrameworkField> getAnnotatablesForTestClass(TestClass testClass) {
            return testClass.getAnnotatedFields();
        }

        @Override
        List<Exception> validateAnnotatable(
                AnnotationValidator validator, FrameworkField field) {
            return validator.validateAnnotatedField(field);
        }
    }
}
