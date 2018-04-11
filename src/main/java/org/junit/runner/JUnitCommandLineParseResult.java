package org.junit.runner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.internal.Classes;
import org.junit.runner.FilterFactory.FilterNotCreatedException;
import org.junit.runner.manipulation.Filter;
import org.junit.runners.model.InitializationError;

class JUnitCommandLineParseResult {
    private final List<String> filterSpecs = new ArrayList<String>();
    private final List<Class<?>> classes = new ArrayList<Class<?>>();
    private final List<Throwable> parserErrors = new ArrayList<Throwable>();

    /**
     * Do not use. Testing purposes only.
     */
    JUnitCommandLineParseResult() {}

    /**
     * Returns filter specs parsed from command line.
     */
    public List<String> getFilterSpecs() {
        return Collections.unmodifiableList(filterSpecs);
    }

    /**
     * Returns test classes parsed from command line.
     */
    public List<Class<?>> getClasses() {
        return Collections.unmodifiableList(classes);
    }

    /**
     * Parses the arguments.
     *
     * @param args Arguments
     */
    public static JUnitCommandLineParseResult parse(String[] args) {
        JUnitCommandLineParseResult result = new JUnitCommandLineParseResult();

        result.parseArgs(args);

        return result;
    }

    //解析命令行参数，提取参数中的filter和class并保存下来
    private void parseArgs(String[] args) {
        parseParameters(parseOptions(args));
    }

    //提取filterSpec，返回参数中的class数组
    String[] parseOptions(String... args) {
        for (int i = 0; i != args.length; ++i) {
            String arg = args[i];

            if (arg.equals("--")) {
                return copyArray(args, i + 1, args.length);
            } else if (arg.startsWith("--")) {
                //提取参数中的--filter=xxx或--filter xxx的xxx到filterSpec
                if (arg.startsWith("--filter=") || arg.equals("--filter")) {
                    String filterSpec;
                    if (arg.equals("--filter")) {
                        ++i;

                        if (i < args.length) {
                            filterSpec = args[i];
                        } else {
                            parserErrors.add(new CommandLineParserError(arg + " value not specified"));
                            break;
                        }
                    } else {
                        filterSpec = arg.substring(arg.indexOf('=') + 1);
                    }

                    filterSpecs.add(filterSpec);
                } else {
                    parserErrors.add(new CommandLineParserError("JUnit knows nothing about the " + arg + " option"));
                }
            } else {
                return copyArray(args, i, args.length);
            }
        }

        return new String[]{};
    }

    private String[] copyArray(String[] args, int from, int to) {
        String[] result = new String[to - from];
        for (int j = from; j != to; ++j) {
            result[j - from] = args[j];
        }
        return result;
    }

    void parseParameters(String[] args) {
        for (String arg : args) {
            try {
                classes.add(Classes.getClass(arg));
            } catch (ClassNotFoundException e) {
                parserErrors.add(new IllegalArgumentException("Could not find class [" + arg + "]", e));
            }
        }
    }

    private Request errorReport(Throwable cause) {
        return Request.errorReport(JUnitCommandLineParseResult.class, cause);
    }

    /**
     * Creates a {@link Request}.
     *
     * @param computer {@link Computer} to be used.
     */
    public Request createRequest(Computer computer) {
        if (parserErrors.isEmpty()) {
            Request request = Request.classes(
                    computer, classes.toArray(new Class<?>[classes.size()]));
            //暂时不考虑存在filter的情况，没有filter时这里相当于直接返回request
            return applyFilterSpecs(request);
        } else {
            return errorReport(new InitializationError(parserErrors));
        }
    }

    //如果存在filter则返回包含当前的request和指定的filter的FilterRequest实例，filterWith方法每次调用都返回一个新的FilterRequest
    //所以相当于即使指定了多个filter，也只有最后一个会生效，这一规则只对使用JUnitCommandLineParseResult生成的Request有效，是否有其他地方
    //存在生成Request的方法还不确定
    private Request applyFilterSpecs(Request request) {
        try {
            for (String filterSpec : filterSpecs) {
                Filter filter = FilterFactories.createFilterFromFilterSpec(
                        request, filterSpec);
                request = request.filterWith(filter);
            }
            return request;
        } catch (FilterNotCreatedException e) {
            return errorReport(e);
        }
    }

    /**
     * Exception used if there's a problem parsing the command line.
     */
    public static class CommandLineParserError extends Exception {
        private static final long serialVersionUID= 1L;

        public CommandLineParserError(String message) {
            super(message);
        }
    }
}
