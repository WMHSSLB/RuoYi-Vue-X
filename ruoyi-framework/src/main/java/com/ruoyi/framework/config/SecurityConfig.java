package com.ruoyi.framework.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.logout.LogoutFilter;
import org.springframework.web.filter.CorsFilter;
import com.ruoyi.framework.config.properties.PermitAllUrlProperties;
import com.ruoyi.framework.config.properties.StorageFileSecurityProperties;
import com.ruoyi.framework.security.filter.JwtAuthenticationTokenFilter;
import com.ruoyi.framework.security.handle.AuthenticationEntryPointImpl;
import com.ruoyi.framework.security.handle.LogoutSuccessHandlerImpl;

/**
 * Spring Security 配置
 *
 * @author ruoyi
 */
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true)
@Configuration
public class SecurityConfig
{
    /**
     * 认证失败处理类
     */
    @Autowired
    private AuthenticationEntryPointImpl unauthorizedHandler;

    /**
     * 退出处理类
     */
    @Autowired
    private LogoutSuccessHandlerImpl logoutSuccessHandler;

    /**
     * token 认证过滤器
     */
    @Autowired
    private JwtAuthenticationTokenFilter authenticationTokenFilter;

    /**
     * 跨域过滤器
     */
    @Autowired
    private CorsFilter corsFilter;

    /**
     * 允许匿名访问的地址
     */
    @Autowired
    private PermitAllUrlProperties permitAllUrl;

    /**
     * 公开文件访问路径
     * <p>
     * 只放行配置中的公开文件路径，私有路径继续进入登录认证
     */
    @Autowired
    private StorageFileSecurityProperties storageFileSecurityProperties;

    /**
     * 身份认证实现
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration)
            throws Exception
    {
        return authenticationConfiguration.getAuthenticationManager();
    }

    /**
     * anyRequest          | 匹配所有请求路径
     * access              | SpringEl 表达式结果为 true 时可以访问
     * anonymous           | 匿名可以访问
     * denyAll             | 用户不能访问
     * fullyAuthenticated  | 用户完全认证可以访问，非 remember-me 自动登录
     * hasAnyAuthority     | 如果有参数，参数表示权限，则其中任一权限可以访问
     * hasAnyRole          | 如果有参数，参数表示角色，则其中任一角色可以访问
     * hasAuthority        | 如果有参数，参数表示权限，则其权限可以访问
     * hasIpAddress        | 如果有参数，参数表示 IP 地址，如果用户 IP 和参数匹配，则可以访问
     * hasRole             | 如果有参数，参数表示角色，则其角色可以访问
     * permitAll           | 用户可以任意访问
     * rememberMe          | 允许通过 remember-me 登录的用户访问
     * authenticated       | 用户登录后可以访问
     */
    @Bean
    protected SecurityFilterChain filterChain(HttpSecurity httpSecurity) throws Exception
    {
        return httpSecurity
                // 禁用 CSRF，因为不使用 session
                .csrf(csrf -> csrf.disable())
                // 禁用 HTTP 响应头缓存控制
                .headers(headersCustomizer -> {
                    headersCustomizer.cacheControl(cache -> cache.disable()).frameOptions(options -> options.sameOrigin());
                })
                // 认证失败处理类
                .exceptionHandling(exception -> exception.authenticationEntryPoint(unauthorizedHandler))
                // 基于 token，所以不需要 session
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 注解标记允许匿名访问的 url
                .authorizeHttpRequests((requests) -> {
                    permitAllUrl.getUrls().forEach(url -> requests.requestMatchers(url).permitAll());
                    // 登录、注册、验证码允许匿名访问
                    requests.requestMatchers("/login", "/register", "/captchaImage").permitAll();
                    // 静态资源、若依历史 /profile 本地资源和文件代理入口允许进入，私有文件在控制器内继续鉴权
                    requests.requestMatchers(HttpMethod.GET, "/", "/*.html", "/**.html", "/**.css", "/**.js",
                            "/profile/**", "/common/file/preview/**", "/common/file/download/**").permitAll();
                    String[] publicPathPatterns = getStorageFilePublicPathPatterns();
                    if (publicPathPatterns.length > 0)
                    {
                        // 只放行公开文件路径，例如 /file/upload/public/**
                        requests.requestMatchers(HttpMethod.GET, publicPathPatterns).permitAll();
                    }
                    // Knife4j 依赖 /doc.html、webjars、springdoc、swagger-ui 和 druid 地址，放行后由接口注解继续控制
                    requests.requestMatchers("/doc.html", "/webjars/**", "/swagger-ui.html", "/v3/api-docs/**",
                            "/swagger-ui/**", "/druid/**").permitAll();
                    // 除上面外的所有请求全部需要鉴权认证
                    requests.anyRequest().authenticated();
                })
                // 添加 Logout filter
                .logout(logout -> logout.logoutUrl("/logout").logoutSuccessHandler(logoutSuccessHandler))
                // 添加 JWT filter
                .addFilterBefore(authenticationTokenFilter, UsernamePasswordAuthenticationFilter.class)
                // 添加 CORS filter
                .addFilterBefore(corsFilter, JwtAuthenticationTokenFilter.class)
                .addFilterBefore(corsFilter, LogoutFilter.class)
                .build();
    }

    /**
     * 强散列哈希加密实现
     */
    @Bean
    public BCryptPasswordEncoder bCryptPasswordEncoder()
    {
        return new BCryptPasswordEncoder();
    }

    /**
     * 获取公开文件访问路径
     *
     * @return 已清洗的公开文件访问路径
     */
    private String[] getStorageFilePublicPathPatterns()
    {
        // 过滤空配置，允许通过空列表关闭所有 X File Storage 匿名访问
        if (storageFileSecurityProperties.getPublicPathPatterns() == null)
        {
            return new String[0];
        }
        return storageFileSecurityProperties.getPublicPathPatterns().stream()
                .map(String::trim)
                .filter(pattern -> !pattern.isEmpty())
                .toArray(String[]::new);
    }
}
