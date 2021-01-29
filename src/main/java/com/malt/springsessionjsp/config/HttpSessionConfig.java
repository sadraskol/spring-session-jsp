package com.malt.springsessionjsp.config;


import com.malt.springsessionjsp.filter.SessionCreatingFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.session.data.redis.config.ConfigureRedisAction;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;

@EnableRedisHttpSession
@Configuration
public class HttpSessionConfig {

    @Bean
    public static ConfigureRedisAction configureRedisAction() {
        return ConfigureRedisAction.NO_OP;
    }

    @Bean
    public FilterRegistrationBean<SessionCreatingFilter> sessionCreatingFilterFilterRegistrationBean() {
        FilterRegistrationBean<SessionCreatingFilter> rb = new FilterRegistrationBean<>(new SessionCreatingFilter());
        rb.setOrder(Ordered.HIGHEST_PRECEDENCE + 90);
        return rb;
    }
}