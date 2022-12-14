package org.ssssssss.magicapi.spring.boot.starter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistration;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.ssssssss.magicapi.backup.service.MagicBackupService;
import org.ssssssss.magicapi.backup.service.MagicDatabaseBackupService;
import org.ssssssss.magicapi.backup.web.MagicBackupController;
import org.ssssssss.magicapi.core.annotation.MagicModule;
import org.ssssssss.magicapi.core.config.Backup;
import org.ssssssss.magicapi.core.config.Constants;
import org.ssssssss.magicapi.core.config.MagicAPIProperties;
import org.ssssssss.magicapi.core.config.MagicConfiguration;
import org.ssssssss.magicapi.core.config.MagicCorsFilter;
import org.ssssssss.magicapi.core.config.MagicFunction;
import org.ssssssss.magicapi.core.config.MagicPluginConfiguration;
import org.ssssssss.magicapi.core.config.Resource;
import org.ssssssss.magicapi.core.config.ResponseCode;
import org.ssssssss.magicapi.core.config.Security;
import org.ssssssss.magicapi.core.config.WebSocketSessionManager;
import org.ssssssss.magicapi.core.exception.MagicAPIException;
import org.ssssssss.magicapi.core.handler.MagicCoordinationHandler;
import org.ssssssss.magicapi.core.handler.MagicDebugHandler;
import org.ssssssss.magicapi.core.handler.MagicWebSocketDispatcher;
import org.ssssssss.magicapi.core.handler.MagicWorkbenchHandler;
import org.ssssssss.magicapi.core.interceptor.AuthorizationInterceptor;
import org.ssssssss.magicapi.core.interceptor.CustomAuthorizationInterceptor;
import org.ssssssss.magicapi.core.interceptor.MagicWebRequestInterceptor;
import org.ssssssss.magicapi.core.interceptor.RequestInterceptor;
import org.ssssssss.magicapi.core.interceptor.ResultProvider;
import org.ssssssss.magicapi.core.logging.LoggerManager;
import org.ssssssss.magicapi.core.model.DataType;
import org.ssssssss.magicapi.core.model.MagicEntity;
import org.ssssssss.magicapi.core.model.Plugin;
import org.ssssssss.magicapi.core.resource.DatabaseResource;
import org.ssssssss.magicapi.core.resource.ResourceAdapter;
import org.ssssssss.magicapi.core.service.MagicAPIService;
import org.ssssssss.magicapi.core.service.MagicDynamicRegistry;
import org.ssssssss.magicapi.core.service.MagicNotifyService;
import org.ssssssss.magicapi.core.service.MagicResourceService;
import org.ssssssss.magicapi.core.service.MagicResourceStorage;
import org.ssssssss.magicapi.core.service.impl.DefaultMagicAPIService;
import org.ssssssss.magicapi.core.service.impl.DefaultMagicResourceService;
import org.ssssssss.magicapi.core.service.impl.RequestMagicDynamicRegistry;
import org.ssssssss.magicapi.core.web.MagicResourceController;
import org.ssssssss.magicapi.core.web.MagicWorkbenchController;
import org.ssssssss.magicapi.core.web.RequestHandler;
import org.ssssssss.magicapi.datasource.model.MagicDynamicDataSource;
import org.ssssssss.magicapi.datasource.service.DataSourceEncryptProvider;
import org.ssssssss.magicapi.datasource.web.MagicDataSourceController;
import org.ssssssss.magicapi.function.service.FunctionMagicDynamicRegistry;
import org.ssssssss.magicapi.jsr223.LanguageProvider;
import org.ssssssss.magicapi.modules.DynamicModule;
import org.ssssssss.magicapi.utils.Mapping;
import org.ssssssss.script.MagicResourceLoader;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptEngine;
import org.ssssssss.script.exception.MagicScriptRuntimeException;
import org.ssssssss.script.functions.DynamicModuleImport;
import org.ssssssss.script.functions.ExtensionMethod;
import org.ssssssss.script.parsing.ast.statement.AsyncCall;
import org.ssssssss.script.reflection.JavaReflection;

/**
 * magic-api???????????????
 *
 * @author mxd
 */
@Configuration
@ConditionalOnClass({RequestMappingHandlerMapping.class})
@EnableConfigurationProperties(MagicAPIProperties.class)
@Import({MagicJsonAutoConfiguration.class, ApplicationUriPrinter.class,
    MagicModuleConfiguration.class, MagicDynamicRegistryConfiguration.class})
@EnableWebSocket
@AutoConfigureAfter(MagicPluginConfiguration.class)
public class MagicAPIAutoConfiguration implements WebMvcConfigurer, WebSocketConfigurer {

  private static final Logger logger = LoggerFactory.getLogger(MagicAPIAutoConfiguration.class);

  /**
   * ???????????????
   */
  private final ObjectProvider<List<RequestInterceptor>> requestInterceptorsProvider;


  /**
   * ????????????????????????
   */
  private final ObjectProvider<List<ExtensionMethod>> extensionMethodsProvider;

  /**
   * ?????????????????????
   */
  private final ObjectProvider<List<HttpMessageConverter<?>>> httpMessageConvertersProvider;


  private final ObjectProvider<AuthorizationInterceptor> authorizationInterceptorProvider;

  /**
   * ??????????????????
   */
  private final ObjectProvider<List<MagicFunction>> magicFunctionsProvider;

  private final ObjectProvider<List<MagicPluginConfiguration>> magicPluginsProvider;

  private final ObjectProvider<MagicNotifyService> magicNotifyServiceProvider;

  private final ObjectProvider<List<MagicDynamicRegistry<? extends MagicEntity>>> magicDynamicRegistriesProvider;

  private final ObjectProvider<List<MagicResourceStorage<? extends MagicEntity>>> magicResourceStoragesProvider;

  private final ObjectProvider<DataSourceEncryptProvider> dataSourceEncryptProvider;

  private final MagicCorsFilter magicCorsFilter = new MagicCorsFilter();

  private final MagicAPIProperties properties;

  private final ApplicationContext applicationContext;

  private boolean registerMapping = false;

  private boolean registerInterceptor = false;

  private boolean registerWebsocket = false;

  @Autowired
  @Lazy
  private RequestMappingHandlerMapping requestMappingHandlerMapping;

  private CustomAuthorizationInterceptor defaultAuthorizationInterceptor;

  public MagicAPIAutoConfiguration(MagicAPIProperties properties,
      ObjectProvider<List<RequestInterceptor>> requestInterceptorsProvider,
      ObjectProvider<List<ExtensionMethod>> extensionMethodsProvider,
      ObjectProvider<List<HttpMessageConverter<?>>> httpMessageConvertersProvider,
      ObjectProvider<List<MagicFunction>> magicFunctionsProvider,
      ObjectProvider<List<MagicPluginConfiguration>> magicPluginsProvider,
      ObjectProvider<MagicNotifyService> magicNotifyServiceProvider,
      ObjectProvider<AuthorizationInterceptor> authorizationInterceptorProvider,
      ObjectProvider<DataSourceEncryptProvider> dataSourceEncryptProvider,
      ObjectProvider<List<MagicDynamicRegistry<? extends MagicEntity>>> magicDynamicRegistriesProvider,
      ObjectProvider<List<MagicResourceStorage<? extends MagicEntity>>> magicResourceStoragesProvider,
      ApplicationContext applicationContext
  ) {
    this.properties = properties;
    this.requestInterceptorsProvider = requestInterceptorsProvider;
    this.extensionMethodsProvider = extensionMethodsProvider;
    this.httpMessageConvertersProvider = httpMessageConvertersProvider;
    this.magicFunctionsProvider = magicFunctionsProvider;
    this.magicPluginsProvider = magicPluginsProvider;
    this.magicNotifyServiceProvider = magicNotifyServiceProvider;
    this.authorizationInterceptorProvider = authorizationInterceptorProvider;
    this.dataSourceEncryptProvider = dataSourceEncryptProvider;
    this.magicDynamicRegistriesProvider = magicDynamicRegistriesProvider;
    this.magicResourceStoragesProvider = magicResourceStoragesProvider;
    this.applicationContext = applicationContext;
  }

  @Bean
  @ConditionalOnMissingBean(org.ssssssss.magicapi.core.resource.Resource.class)
  @ConditionalOnProperty(prefix = "magic-api", name = "resource.type", havingValue = "database")
  public org.ssssssss.magicapi.core.resource.Resource magicDatabaseResource(
      MagicDynamicDataSource magicDynamicDataSource) {
    Resource resourceConfig = properties.getResource();
    if (magicDynamicDataSource.isEmpty()) {
      throw new MagicAPIException(
          "??????????????????????????????????????????????????? spring-boot-starter-jdbc ?????????!");
    }
    MagicDynamicDataSource.DataSourceNode dataSourceNode = magicDynamicDataSource.getDataSource(
        resourceConfig.getDatasource());
    return new DatabaseResource(new JdbcTemplate(dataSourceNode.getDataSource()),
        resourceConfig.getTableName(), resourceConfig.getPrefix(), resourceConfig.isReadonly());
  }

  @Bean
  @ConditionalOnMissingBean(org.ssssssss.magicapi.core.resource.Resource.class)
  @ConditionalOnProperty(prefix = "magic-api", name = "resource.type", havingValue = "file", matchIfMissing = true)
  public org.ssssssss.magicapi.core.resource.Resource magicResource() throws IOException {
    Resource resourceConfig = properties.getResource();
    return ResourceAdapter.getResource(resourceConfig.getLocation(),
        resourceConfig.isReadonly());
  }

  @Bean
  @ConditionalOnMissingBean(MagicBackupService.class)
  @ConditionalOnProperty(prefix = "magic-api", name = "backup.enable", havingValue = "true")
  public MagicBackupService magicDatabaseBackupService(
      MagicDynamicDataSource magicDynamicDataSource) {
    Backup backupConfig = properties.getBackup();
    MagicDynamicDataSource.DataSourceNode dataSourceNode = magicDynamicDataSource.getDataSource(
        backupConfig.getDatasource());
    return new MagicDatabaseBackupService(new JdbcTemplate(dataSourceNode.getDataSource()),
        backupConfig.getTableName());
  }

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    String web = properties.getWeb();
    if (web != null && !registerMapping) {
      registerMapping = true;
      // ????????????UI????????????????????????
      LoggerManager.createMagicAppender();
      // ????????????????????????
      registry.addResourceHandler(web + "/**")
          .addResourceLocations("classpath:/magic-editor/");
    }
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    if (!registerInterceptor) {
      registerInterceptor = true;
      registry.addInterceptor(new MagicWebRequestInterceptor(
              properties.isSupportCrossDomain() ? magicCorsFilter : null,
              authorizationInterceptorProvider.getIfAvailable(
                  this::createAuthorizationInterceptor)))
          .addPathPatterns("/**");
    }
  }

  @Bean
  @ConditionalOnProperty(prefix = "magic-api", value = "support-cross-domain", havingValue = "true", matchIfMissing = true)
  public FilterRegistrationBean<MagicCorsFilter> magicCorsFilterRegistrationBean() {
    FilterRegistrationBean<MagicCorsFilter> registration = new FilterRegistrationBean<>(
        magicCorsFilter);
    registration.addUrlPatterns("/*");
    registration.setName("Magic Cors Filter");
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
    return registration;
  }

  @Bean
  @ConditionalOnMissingBean
  public MagicResourceService magicResourceService(
      org.ssssssss.magicapi.core.resource.Resource workspace) {
    return new DefaultMagicResourceService(workspace, magicResourceStoragesProvider.getObject(),
        applicationContext);
  }


  @Bean
  @ConditionalOnMissingBean(MagicNotifyService.class)
  public MagicNotifyService magicNotifyService() {
    logger.info(
        "????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????magic-api-plugin-cluster??????");
    return magicNotify -> {
    };
  }

  /**
   * ??????API??????Service
   */
  @Bean
  @ConditionalOnMissingBean
  public MagicAPIService magicAPIService(ResultProvider resultProvider,
      MagicResourceService magicResourceService,
      RequestMagicDynamicRegistry requestMagicDynamicRegistry,
      FunctionMagicDynamicRegistry functionMagicDynamicRegistry) {
    return new DefaultMagicAPIService(resultProvider, properties.getInstanceId(),
        magicResourceService, requestMagicDynamicRegistry, functionMagicDynamicRegistry,
        properties.isThrowException(), properties.getPrefix(), applicationContext);
  }

  /**
   * ???????????????????????????
   */
  private void setupMagicModules(List<ExtensionMethod> extensionMethods,
      List<LanguageProvider> languageProviders) {
    // ????????????import??? class????????????
    MagicResourceLoader.setClassLoader((className) -> {
      try {
        return applicationContext.getBean(className);
      } catch (Exception e) {
        Class<?> clazz = null;
        try {
          clazz = Class.forName(className);
          return applicationContext.getBean(clazz);
        } catch (Exception ex) {
          if (clazz == null) {
            throw new MagicScriptRuntimeException(
                new ClassNotFoundException(className));
          }
          return clazz;
        }
      }
    });
    MagicResourceLoader.addScriptLanguageLoader(language -> languageProviders.stream()
        .filter(it -> it.support(language))
        .findFirst().<BiFunction<Map<String, Object>, String, Object>>map(
            languageProvider -> (context, script) -> {
              try {
                return languageProvider.execute(language, script, context);
              } catch (Exception e) {
                throw new MagicAPIException(e.getMessage(), e);
              }
            }).orElse(null)
    );
    logger.info("????????????11:{} -> {}", "log", Logger.class);
    MagicResourceLoader.addModule("log", new DynamicModuleImport(Logger.class,
        context -> LoggerFactory.getLogger(
            Objects.toString(context.getScriptName(), "Unknown"))));
    List<String> importModules = properties.getAutoImportModuleList();
    applicationContext.getBeansWithAnnotation(MagicModule.class).values().forEach(module -> {
      String moduleName = AnnotationUtils.findAnnotation(module.getClass(), MagicModule.class)
          .value();
      logger.info("????????????22:{} -> {}", moduleName, module.getClass());

      if (module instanceof DynamicModule) {
        MagicResourceLoader.addModule(moduleName, new DynamicModuleImport(module.getClass(),
            ((DynamicModule<?>) module)::getDynamicModule));
      } else {
        MagicResourceLoader.addModule(moduleName, module);
      }
    });
    MagicResourceLoader.getModuleNames().stream().filter(importModules::contains)
        .forEach(moduleName -> {
          logger.info("??????????????????33???{}", moduleName);
          MagicScriptEngine.addDefaultImport(moduleName,
              MagicResourceLoader.loadModule(moduleName));
        });
    properties.getAutoImportPackageList().forEach(importPackage -> {
      logger.info("????????????44???{}", importPackage);
      MagicResourceLoader.addPackage(importPackage);
    });
    extensionMethods.forEach(extension -> extension.supports().forEach(support -> {
      logger.info("????????????55:{} -> {}", support, extension.getClass());
      JavaReflection.registerMethodExtension(support, extension);
    }));
  }

  @Bean
  public MagicConfiguration magicConfiguration(List<LanguageProvider> languageProviders,
      org.ssssssss.magicapi.core.resource.Resource magicResource,
      ResultProvider resultProvider,
      MagicResourceService magicResourceService,
      MagicAPIService magicAPIService,
      MagicNotifyService magicNotifyService,
      RequestMagicDynamicRegistry requestMagicDynamicRegistry,
      @Autowired(required = false) MagicBackupService magicBackupService)
      throws NoSuchMethodException {
    logger.info("magic-api????????????:{}", magicResource);
    AsyncCall.setThreadPoolExecutorSize(properties.getThreadPoolExecutorSize());
    DataType.DATE_PATTERNS = properties.getDatePattern();
    MagicScript.setCompileCache(properties.getCompileCacheSize());
    // ?????????????????????code???
    ResponseCode responseCodeConfig = properties.getResponseCode();
    Constants.RESPONSE_CODE_SUCCESS = responseCodeConfig.getSuccess();
    Constants.RESPONSE_CODE_INVALID = responseCodeConfig.getInvalid();
    Constants.RESPONSE_CODE_EXCEPTION = responseCodeConfig.getException();
    // ???????????????????????????
    setupMagicModules(extensionMethodsProvider.getIfAvailable(Collections::emptyList),
        languageProviders);
    MagicConfiguration configuration = new MagicConfiguration();
    configuration.setMagicAPIService(magicAPIService);
    configuration.setMagicNotifyService(magicNotifyService);
    configuration.setInstanceId(properties.getInstanceId());
    configuration.setMagicResourceService(magicResourceService);
    configuration.setMagicDynamicRegistries(magicDynamicRegistriesProvider.getObject());
    configuration.setMagicBackupService(magicBackupService);
    Security security = properties.getSecurity();
    configuration.setDebugTimeout(properties.getDebug().getTimeout());
    configuration.setHttpMessageConverters(
        httpMessageConvertersProvider.getIfAvailable(Collections::emptyList));
    configuration.setResultProvider(resultProvider);
    configuration.setThrowException(properties.isThrowException());
    configuration.setEditorConfig(properties.getEditorConfig());
    configuration.setWorkspace(magicResource);
    configuration.setAuthorizationInterceptor(
        authorizationInterceptorProvider.getIfAvailable(this::createAuthorizationInterceptor));
    // ????????????
    this.magicFunctionsProvider.getIfAvailable(Collections::emptyList)
        .forEach(JavaReflection::registerFunction);
    // ????????????????????????????????????????????????????????????????????????
    security.setUsername(null);
    security.setPassword(null);
    requestMagicDynamicRegistry.setHandler(
        new RequestHandler(configuration, requestMagicDynamicRegistry));
    List<MagicPluginConfiguration> pluginConfigurations = magicPluginsProvider.getIfAvailable(
        Collections::emptyList);
    List<Plugin> plugins = pluginConfigurations.stream().map(MagicPluginConfiguration::plugin)
        .collect(Collectors.toList());
    // ??????UI???????????????
    String base = properties.getWeb();
    Mapping mapping = Mapping.create(requestMappingHandlerMapping, base);
    MagicWorkbenchController magicWorkbenchController = new MagicWorkbenchController(
        configuration, properties, plugins);
    if (base != null) {
      configuration.setEnableWeb(true);
      mapping.registerController(magicWorkbenchController)
          .registerController(new MagicResourceController(configuration))
          .registerController(new MagicDataSourceController(configuration))
          .registerController(new MagicBackupController(configuration));
      pluginConfigurations.forEach(
          it -> it.controllerRegister().register(mapping, configuration));
    }
    // ???????????????????????????
    if (StringUtils.isNotBlank(properties.getSecretKey())) {
      mapping.register(
          mapping.paths(properties.getPushPath()).methods(RequestMethod.POST).build(),
          magicWorkbenchController,
          MagicWorkbenchController.class.getDeclaredMethod("receivePush", MultipartFile.class,
              String.class, Long.class, String.class));
    }
    // ?????????????????????
    this.requestInterceptorsProvider.getIfAvailable(Collections::emptyList)
        .forEach(interceptor -> {
          logger.info("????????????????????????{}", interceptor.getClass());
          configuration.addRequestInterceptor(interceptor);
        });
    // ??????banner
    if (this.properties.isBanner()) {
      configuration.printBanner(
          plugins.stream().map(Plugin::getName).collect(Collectors.toList()));
    }
    if (magicBackupService == null) {
      logger.error("????????????????????????????????????????????????????????????????????????????????????");
    }
    // ????????????
    if (properties.getBackup().isEnable() && properties.getBackup().getMaxHistory() > 0
        && magicBackupService != null) {
      long interval = properties.getBackup().getMaxHistory() * 86400000L;
      // 1????????????1???
      new ScheduledThreadPoolExecutor(1,
          r -> new Thread(r, "magic-api-clean-task")).scheduleAtFixedRate(() -> {
        try {
          long count = magicBackupService.removeBackupByTimestamp(
              System.currentTimeMillis() - interval);
          if (count > 0) {
            logger.info("?????????????????????{}???", count);
          }
        } catch (Exception e) {
          logger.error("???????????????????????????", e);
        }
      }, 1, 1, TimeUnit.HOURS);
    }
    return configuration;
  }

  public AuthorizationInterceptor createAuthorizationInterceptor() {
    if (defaultAuthorizationInterceptor != null) {
      return defaultAuthorizationInterceptor;
    }
    Security security = properties.getSecurity();
    defaultAuthorizationInterceptor = new CustomAuthorizationInterceptor();
    return defaultAuthorizationInterceptor;
  }

  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry webSocketHandlerRegistry) {
    String web = properties.getWeb();
    MagicNotifyService magicNotifyService = magicNotifyServiceProvider.getObject();
    WebSocketSessionManager.setMagicNotifyService(magicNotifyService);
    if (web != null && !registerWebsocket) {
      registerWebsocket = true;
      MagicWebSocketDispatcher dispatcher = new MagicWebSocketDispatcher(
          properties.getInstanceId(), magicNotifyService, Arrays.asList(
          new MagicDebugHandler(),
          new MagicCoordinationHandler(),
          new MagicWorkbenchHandler(authorizationInterceptorProvider.getIfAvailable(
              this::createAuthorizationInterceptor))
      ));
      WebSocketHandlerRegistration registration = webSocketHandlerRegistry.addHandler(
          dispatcher, web + "/console");
      if (properties.isSupportCrossDomain()) {
        registration.setAllowedOrigins("*");
      }
    }
  }
}
