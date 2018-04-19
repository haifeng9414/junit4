package org.junit.runner;

import junit.runner.Version;
import org.junit.internal.JUnitSystem;
import org.junit.internal.RealSystem;
import org.junit.internal.TextListener;
import org.junit.internal.runners.JUnit38ClassRunner;
import org.junit.runner.notification.RunListener;
import org.junit.runner.notification.RunNotifier;

/**
 * <code>JUnitCore</code> is a facade for running tests. It supports running JUnit 4 tests,
 * JUnit 3.8.x tests, and mixtures. To run tests from the command line, run
 * <code>java org.junit.runner.JUnitCore TestClass1 TestClass2 ...</code>.
 * For one-shot test runs, use the static method {@link #runClasses(Class[])}.
 * If you want to add special listeners,
 * create an instance of {@link org.junit.runner.JUnitCore} first and use it to run the tests.
 *
 * @see org.junit.runner.Result
 * @see org.junit.runner.notification.RunListener
 * @see org.junit.runner.Request
 * @since 4.0
 */
/*
JUnit运行过程：
1.如何执行测试方法？
将待测试的Class对象传入JUnitCore后，使用Request.classes方法创建Runner对象，创建过程是利用传入Request.classes方法的Compute
对象获取Runner，Junit4中获取的Runner对象是Suite类，该类继承自ParentRunner，使用指定的RunnerBuilder创建Runner，默认使用的是AllDefaultPossibilitiesBuilder，该builder内部维护了所有
可用的builder，并依次调用builder尝试创建Runner直到创建成功。在成功获取到Runner后JUnitCore开始执行该Runner，执行前创建Result对象，用于保存
运行过程中的状态，如成功运行失败的个数，保存的实现方式是添加一个listener到RunNotifier中，以此监听运行过程。对于JUnit4版本的Test，Runner的实现
是BlockJUnit4ClassRunner，该Runner首先在构造函数中将Class对象封装成TestClass类，该类可用来获取测试类的信息，如待测试的方法、Before注解的方法
After注解的方法、获取测试类的注解、按Before和After的顺序放回待运行的测试方法等。构造函数中创建完TestClass对象后还会进行验证工作，如验证
测试方法是否是public、是否是无参数的。之后在Runner的run方法中创建一个基础的Statement类，该类执行真正的测试方法运行。基础的Statement类
将会利用getChildren方法获取待测试的方法并逐个运行（如BlockJUnit4ClassRunner的实现就是通过TestClass类获取有Test注解的方法），由于还存在
Before、After、BeforeClass、AfterClass、Test注解指定expected来设置期望的异常等机制，简单的逐个运行测试方法是不够的，所以Runner在获取到
基础的Statement后，又在该Statement的基础上新建了多个Statement，每个Statement在执行完自己的任务后调用上一个Statement，如ParentRunner
使用如下方式支持BeforeClass和AfterClass
Statement statement = childrenInvoker(notifier) //基础Statement
statement = withBeforeClasses(statement); //在调用Statement之前调用BeforeClass注解的方法
statement = withAfterClasses(statement);  //在最后调用AfterClass注解的方法
而对于Before、After和excepted的支持是在ParentRunner的子类BlockJUnit4ClassRunner中做的，查看ParentRunner的run方法可以发现，生成的
基础Statement执行时实际上是调用子类的runChild方法，子类实现该方法时如BlockJUnit4ClassRunner，会和ParentRunner一样封装自己的Statement
所以ParentRunner的childrenInvoker(notifier)返回的Statement已经是子类处理好的支持了Before和After等特性的Statement，BlockJUnit4ClassRunner
中对Statement的方式如下：
Statement statement = methodInvoker(method, test); //这只是简单的调用测试方法
statement = possiblyExpectingExceptions(method, test, statement); //在调用测试方法后判断是否抛出了指定的异常
statement = withPotentialTimeout(method, test, statement); //支持Test支持设置timeout，主要是利用FutureTask.get(timeout, timeUnit)方法，实现在期望的时间内返回结果
statement = withBefores(method, test, statement); //运行传入的statement之前先运行Before注解注定的方法
statement = withAfters(method, test, statement); //运行传入的statement后运行After注解的方法
statement = withRules(method, test, statement); //支持自定义的statement

2.如何支持方法的排序
JUnit4支持在测试类上使用FixMethodOrder注解指定测试方法运行是的顺序，但是只支持简单的执行顺序，查看FixMethodOrder定义可以发现，只能指定MethodSorters枚举
下的3中执行顺序，枚举的元素定义了各自的Comparator。该Comparator使用方式如下：
方法的执行只在ParentRunner的runChildren(final RunNotifier notifier)方法中执行的，该方法会遍历由getFilteredChildren()方法返回的元素并顺序执行，getFilteredChildren()的
返回结果由ParentRunner的实现类实现的getChildren实现，ParentRunner的实现类在创建Runner时如创建BlockJUnit4ClassRunner时，会将Class对象封装成TestClass，而测试类的测试方法运行顺序由具体Runner的getChildren返回的child顺序实现
如BlockJUnit4ClassRunner中getChildren方法返回的是TestClass保存的private final Map<Class<? extends Annotation>, List<FrameworkMethod>> methodsForAnnotations
中Test注解对应的List的方法，所以想要指定方法的运行顺序，只要设置好TestClass中private final Map<Class<? extends Annotation>, List<FrameworkMethod>> methodsForAnnotations
保存的List的元素顺序即可，而顺序可以在创建TestClass对象的过程中实现，而JUnit的实现方式也是在构建TestClass的时候指定好运行顺序的，查看TestClass构造函数调用的scanAnnotatedMembers方法，
该方法在遍历测试类的时对方法进行了过滤，而过滤器就是由测试类的注解FixMethodOrder指定的

3.如何支持过滤
filter是使用JUnitCore.main方法运行时由传入的参数指定的，如在命令行运行
java org.junit.runner.JUnitCore \
    --filter=org.junit.experimental.categories.IncludeCategories=testutils.SlowTests \
    com.example.ExampleTestSuite
JUnitCore使用jUnitCommandLineParseResult帮助解析传入的参数得到测试类名称和指定的过滤器名称，并由jUnitCommandLineParseResult创建Request对象
在返回创建出来的Request之前调用applyFilterSpecs(Request)方法，该方法遍历提取到的filter并使用FilterFactories创建filter，之后调用request.filterWith(Filter)
方法应该filter，filterWith方法实际上使用的是装饰器模式，继承自Request类，构造函数传入指定的request和filter并重写Request的getRunner方法，在获取Request的Runner时
对从原Request获取到的Runner执行filter.apply(Runner)方法。
filter.apply(Runner)方法的实现方式也值得参考，Request返回的Runner是Suite，Suite继承自ParentRunner，ParentRunner实现了Filterable接口，下面的实现方法使得filter的实现类实现起来很简单，filter只需要关注自己如何进行filter，
而各个Filterable则关注应该如何使用filter到自己的child，这样也不需要一个第三方的类接受Filter和Filterable并调用Filterable.filter(Filter)
public void apply(Object child) throws NoTestsRemainException {
        if (!(child instanceof Filterable)) {
            return;
        }
        Filterable filterable = (Filterable) child;
        filterable.filter(this);
    }
*/
public class JUnitCore {
    private final RunNotifier notifier = new RunNotifier();

    /**
     * Run the tests contained in the classes named in the <code>args</code>.
     * If all tests run successfully, exit with a status of 0. Otherwise exit with a status of 1.
     * Write feedback while tests are running and write
     * stack traces for all failed tests after the tests all complete.
     *
     * @param args names of classes in which to find tests to run
     */
    public static void main(String... args) {
        Result result = new JUnitCore().runMain(new RealSystem(), args);
        System.exit(result.wasSuccessful() ? 0 : 1);
    }

    /**
     * Run the tests contained in <code>classes</code>. Write feedback while the tests
     * are running and write stack traces for all failed tests after all tests complete. This is
     * similar to {@link #main(String[])}, but intended to be used programmatically.
     *
     * @param classes Classes in which to find tests
     * @return a {@link Result} describing the details of the test run and the failed tests.
     */
    public static Result runClasses(Class<?>... classes) {
        return runClasses(defaultComputer(), classes);
    }

    /**
     * Run the tests contained in <code>classes</code>. Write feedback while the tests
     * are running and write stack traces for all failed tests after all tests complete. This is
     * similar to {@link #main(String[])}, but intended to be used programmatically.
     *
     * @param computer Helps construct Runners from classes
     * @param classes  Classes in which to find tests
     * @return a {@link Result} describing the details of the test run and the failed tests.
     */
    public static Result runClasses(Computer computer, Class<?>... classes) {
        return new JUnitCore().run(computer, classes);
    }

    /**
     * @param system
     * @param args from main()
     */
    Result runMain(JUnitSystem system, String... args) {
        system.out().println("JUnit version " + Version.id());

        JUnitCommandLineParseResult jUnitCommandLineParseResult = JUnitCommandLineParseResult.parse(args);

        RunListener listener = new TextListener(system);
        addListener(listener);

        return run(jUnitCommandLineParseResult.createRequest(defaultComputer()));
    }

    /**
     * @return the version number of this release
     */
    public String getVersion() {
        return Version.id();
    }

    /**
     * Run all the tests in <code>classes</code>.
     *
     * @param classes the classes containing tests
     * @return a {@link Result} describing the details of the test run and the failed tests.
     */
    public Result run(Class<?>... classes) {
        return run(defaultComputer(), classes);
    }

    /**
     * Run all the tests in <code>classes</code>.
     *
     * @param computer Helps construct Runners from classes
     * @param classes the classes containing tests
     * @return a {@link Result} describing the details of the test run and the failed tests.
     */
    //run函数最后都会调用到public Result run(Runner runner)方法，其他的run函数的不同之处在于获取Runner的方式
    //如在junit4中支持执行测试方法时的过滤和测试，这里用Request类实现对Runner的过滤和排序功能，用Compute类实现
    //获取junit4风格的Runner功能
    public Result run(Computer computer, Class<?>... classes) {
        return run(Request.classes(computer, classes));
    }

    /**
     * Run all the tests contained in <code>request</code>.
     *
     * @param request the request describing tests
     * @return a {@link Result} describing the details of the test run and the failed tests.
     */
    public Result run(Request request) {
        return run(request.getRunner());
    }

    /**
     * Run all the tests contained in JUnit 3.8.x <code>test</code>. Here for backward compatibility.
     *
     * @param test the old-style test
     * @return a {@link Result} describing the details of the test run and the failed tests.
     */
    public Result run(junit.framework.Test test) {
        return run(new JUnit38ClassRunner(test));
    }

    /**
     * Do not use. Testing purposes only.
     */
    public Result run(Runner runner) {
        Result result = new Result();
        //利用Result类中的内部类创建一个listener来获取执行结果，result只关心runner的执行状态
        //而不关心runner执行的具体任务及任务结果，所以这里直接使用listener解耦了runner的执行和创建执行结果的过程
        RunListener listener = result.createListener();
        notifier.addFirstListener(listener);
        try {
            notifier.fireTestRunStarted(runner.getDescription());
            runner.run(notifier);
            notifier.fireTestRunFinished(result);
        } finally {
            removeListener(listener);
        }
        return result;
    }

    /**
     * Add a listener to be notified as the tests run.
     *
     * @param listener the listener to add
     * @see org.junit.runner.notification.RunListener
     */
    public void addListener(RunListener listener) {
        notifier.addListener(listener);
    }

    /**
     * Remove a listener.
     *
     * @param listener the listener to remove
     */
    public void removeListener(RunListener listener) {
        notifier.removeListener(listener);
    }

    static Computer defaultComputer() {
        return new Computer();
    }
}
