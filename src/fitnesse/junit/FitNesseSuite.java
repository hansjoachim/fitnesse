package fitnesse.junit;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

import fitnesse.FitNesseContext;
import fitnesse.FitNesseContext.Builder;
import fitnesse.authentication.PromiscuousAuthenticator;
import fitnesse.testrunner.MultipleTestsRunner;
import fitnesse.testrunner.PagesByTestSystem;
import fitnesse.testrunner.SuiteContentsFinder;
import fitnesse.testrunner.WikiPageDescriptor;
import fitnesse.testsystems.Descriptor;
import fitnesse.testsystems.TestSummary;
import fitnesse.wiki.ClassPathBuilder;
import fitnesse.wiki.PageCrawler;
import fitnesse.wiki.PathParser;
import fitnesse.wiki.WikiPage;
import fitnesse.wiki.WikiPageFactory;
import fitnesse.wiki.WikiPagePath;
import fitnesse.wiki.fs.FileSystemPageFactory;
import junit.framework.AssertionFailedError;
import org.junit.runner.Description;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.ParentRunner;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.RunnerBuilder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FitNesseSuite extends ParentRunner<WikiPage> {

  /**
   * The <code>Name</code> annotation specifies the name of the Fitnesse suite
   * to be run, e.g.: MySuite.MySubSuite
   */
  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.TYPE)
  public @interface Name {
    public String value();
  }

  /**
   * The <code>DebugMode</code> annotation specifies whether the test is run
   * with the REST debug option. Default is true
   */
  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.TYPE)
  public @interface DebugMode {
    public boolean value();
  }

  /**
   * The <code>SuiteFilter</code> annotation specifies the suite filter of the Fitnesse suite
   * to be run, e.g.: fasttests
   */
  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.TYPE)
  public @interface SuiteFilter {
    public String value();
  }

  /**
   * The <code>ExcludeSuiteFilter</code> annotation specifies a filter for excluding tests from the Fitnesse suite
   * to be run, e.g.: slowtests
   */
  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.TYPE)
  public @interface ExcludeSuiteFilter {
    public String value();
  }

  /**
   * The <code>FitnesseDir</code> annotation specifies the absolute or relative
   * path to the directory in which FitNesseRoot can be found
   */
  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.TYPE)
  public @interface FitnesseDir {
    public String value();
  }

  /**
   * The <code>OutputDir</code> annotation specifies where the html reports of
   * run suites and tests will be found after running them. You can either
   * specify a relative or absolute path directly, e.g.: <code>@OutputDir("/tmp/trinidad-results")</code>, or you can
   * specify a
   * system property the content of which will be taken as base dir and
   * optionally give a path extension, e.g.:
   * <code>@OutputDir(systemProperty = "java.io.tmpdir", pathExtension = "trinidad-results")</code>
   */
  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.TYPE)
  public @interface OutputDir {
    public String value() default "";

    public String systemProperty() default "";

    public String pathExtension() default "";
  }

  /**
   * The <code>Port</code> annotation specifies the port used by the FitNesse
   * server. Default is the standard FitNesse port.
   */
  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.TYPE)
  public @interface Port {
    public int value() default 0;

    public String systemProperty() default "";
  }

  private final Class<?> suiteClass;
  private final String suiteName;
  private String fitNesseDir;
  private String outputDir;
  private String suiteFilter;
  private String excludeSuiteFilter;
  private boolean debugMode = false;
  private int port = 0;
  private final FitNesseContext context;
  private List<WikiPage> children;

  public FitNesseSuite(Class<?> suiteClass, RunnerBuilder builder) throws InitializationError {
    super(suiteClass);
    this.suiteClass = suiteClass;
    this.suiteName = getSuiteName(suiteClass);
    this.fitNesseDir = getFitnesseDir(suiteClass);
    this.outputDir = getOutputDir(suiteClass);
    this.suiteFilter = getSuiteFilter(suiteClass);
    this.excludeSuiteFilter = getExcludeSuiteFilter(suiteClass);
    this.debugMode = useDebugMode(suiteClass);
    this.port = getPort(suiteClass);
    this.context = initContext(this.fitNesseDir, port);

    try {
      this.children = initChildren(context);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private List<WikiPage> initChildren(FitNesseContext context) {
    WikiPagePath path = PathParser.parse(this.suiteName);
    PageCrawler crawler = context.root.getPageCrawler();
    WikiPage suiteRoot = crawler.getPage(path);
    if (!suiteRoot.getData().hasAttribute("Suite")) {
      throw new IllegalArgumentException("page " + this.suiteName + " is not a suite");
    }
    return new SuiteContentsFinder(suiteRoot, new fitnesse.testrunner.SuiteFilter(suiteFilter, excludeSuiteFilter), context.root).getAllPagesToRunForThisSuite();
  }

  @Override
  protected Description describeChild(WikiPage child) {
    return Description.createTestDescription(suiteClass, child.getPageCrawler().getFullPath().toString());
  }

  @Override
  protected List<WikiPage> getChildren() {
    return this.children;
  }

  static String getFitnesseDir(Class<?> klass)
          throws InitializationError {
    FitnesseDir fitnesseDirAnnotation = klass.getAnnotation(FitnesseDir.class);
    if (fitnesseDirAnnotation == null) {
      throw new InitializationError("There must be a @FitnesseDir annotation");
    }
    return fitnesseDirAnnotation.value();
  }

  static String getSuiteFilter(Class<?> klass)
          throws InitializationError {
    SuiteFilter suiteFilterAnnotation = klass.getAnnotation(SuiteFilter.class);
    if (suiteFilterAnnotation == null) {
      return null;
    }
    return suiteFilterAnnotation.value();
  }

  static String getExcludeSuiteFilter(Class<?> klass)
          throws InitializationError {
    ExcludeSuiteFilter excludeSuiteFilterAnnotation = klass.getAnnotation(ExcludeSuiteFilter.class);
    if (excludeSuiteFilterAnnotation == null) {
      return null;
    }
    return excludeSuiteFilterAnnotation.value();
  }

  static String getSuiteName(Class<?> klass) throws InitializationError {
    Name nameAnnotation = klass.getAnnotation(Name.class);
    if (nameAnnotation == null) {
      throw new InitializationError("There must be a @Name annotation");
    }
    return nameAnnotation.value();
  }

  static String getOutputDir(Class<?> klass) throws InitializationError {
    OutputDir outputDirAnnotation = klass.getAnnotation(OutputDir.class);
    if (outputDirAnnotation == null) {
      throw new InitializationError("There must be a @OutputDir annotation");
    }
    if (!"".equals(outputDirAnnotation.value())) {
      return outputDirAnnotation.value();
    }
    if (!"".equals(outputDirAnnotation.systemProperty())) {
      String baseDir = System.getProperty(outputDirAnnotation.systemProperty());
      File outputDir = new File(baseDir, outputDirAnnotation.pathExtension());
      return outputDir.getAbsolutePath();
    }
    throw new InitializationError(
            "In annotation @OutputDir you have to specify either 'value' or 'systemProperty'");
  }

  public static boolean useDebugMode(Class<?> klass) {
    DebugMode debugModeAnnotation = klass.getAnnotation(DebugMode.class);
    if (null == debugModeAnnotation) {
      return true;
    }
    return debugModeAnnotation.value();
  }

  public static int getPort(Class<?> klass) {
    Port portAnnotation = klass.getAnnotation(Port.class);
    if (null == portAnnotation) {
      return 0;
    }
    int lport = portAnnotation.value();
    if (!"".equals(portAnnotation.systemProperty())) {
      lport = Integer.getInteger(portAnnotation.systemProperty(), lport);
    }
    return lport;
  }

  @Override
  public void run(final RunNotifier notifier) {
    if (isFilteredForChildTest()) {
      super.run(notifier);
    } else {
      runPages(children, notifier);
    }
  }

  @Override
  protected void runChild(WikiPage page, RunNotifier notifier) {
    runPages(listOf(page), notifier);
  }

  protected void runPages(List<WikiPage>pages, final RunNotifier notifier) {
    MultipleTestsRunner testRunner = createTestRunner(pages);
    testRunner.addTestSystemListener(new JUnitRunNotifierResultsListener(notifier, suiteClass));
    try {
      executeTests(testRunner);
    } catch (AssertionError e) {
      notifier.fireTestFailure(new Failure(Description.createSuiteDescription(suiteClass), e));
    } catch (Exception e) {
      notifier.fireTestFailure(new Failure(Description.createSuiteDescription(suiteClass), e));
    }
  }

  private boolean isFilteredForChildTest() {
    return getDescription().getChildren().size() < getChildren().size();
  }

  private List<WikiPage> listOf(WikiPage page) {
    List<WikiPage> list = new ArrayList<WikiPage>(1);
    list.add(page);
    return list;
  }

  private FitNesseContext initContext(String rootPath, int port) {
    Builder builder = new Builder();
    WikiPageFactory wikiPageFactory = new FileSystemPageFactory();

    builder.port = port;
    builder.rootPath = rootPath;
    builder.rootDirectoryName = "FitNesseRoot";

    builder.root = wikiPageFactory.makeRootPage(builder.rootPath,
        builder.rootDirectoryName);

    builder.logger = null;
    builder.authenticator = new PromiscuousAuthenticator();

    return builder.createFitNesseContext();
  }

  private MultipleTestsRunner createTestRunner(List<WikiPage> pages) {
    final String classPath = new ClassPathBuilder().buildClassPath(pages);

    final PagesByTestSystem pagesByTestSystem = new PagesByTestSystem(pages, context.root, new PagesByTestSystem.DescriptorFactory() {
      @Override
      public Descriptor create(WikiPage page) {
        return new WikiPageDescriptor(page.readOnlyData(), debugMode, false, classPath);
      }
    });

    return new MultipleTestsRunner(pagesByTestSystem, context.runningTestingTracker, context.testSystemFactory);
  }

  private void executeTests(MultipleTestsRunner testRunner) throws IOException, InterruptedException {
    JavaFormatter testFormatter = new JavaFormatter(suiteName);
    testFormatter.setResultsRepository(new JavaFormatter.FolderResultsRepository(outputDir));
    testRunner.addTestSystemListener(testFormatter);

    testRunner.executeTestPages();
    TestSummary summary = testFormatter.getTotalSummary();

    assertEquals("wrong", 0, summary.wrong);
    assertEquals("exceptions", 0, summary.exceptions);
    assertTrue(msgAtLeastOneTest(suiteName, summary), summary.right > 0);
  }

  private String msgAtLeastOneTest(String pageName, TestSummary summary) {
    return MessageFormat.format("at least one test executed in {0}\n{1}",
            pageName, summary.toString());
  }
}
