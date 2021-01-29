package com.malt.springsessionjsp.config;

import com.malt.springsessionjsp.filter.CustomSessionRepositoryFilter;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.session.config.annotation.web.http.SpringHttpSessionConfiguration;
import org.springframework.session.security.web.authentication.SpringSessionRememberMeServices;
import org.springframework.session.web.http.*;
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;

import javax.annotation.PostConstruct;
import javax.servlet.ServletContext;
import javax.servlet.SessionCookieConfig;
import javax.servlet.http.HttpSessionListener;
import java.util.ArrayList;
import java.util.List;

@Configuration
public class CustomHttpSessionConfiguration extends SpringHttpSessionConfiguration {
    private final Log logger = LogFactory.getLog(getClass());

    private CookieHttpSessionIdResolver defaultHttpSessionIdResolver = new CookieHttpSessionIdResolver();

    private boolean usesSpringSessionRememberMeServices;

    private ServletContext servletContext;

    private CookieSerializer cookieSerializer;

    private HttpSessionIdResolver httpSessionIdResolver = this.defaultHttpSessionIdResolver;

    private List<HttpSessionListener> httpSessionListeners = new ArrayList<>();

    @PostConstruct
    public void init() {
        CookieSerializer cookieSerializer = (this.cookieSerializer != null) ? this.cookieSerializer
                : createDefaultCookieSerializer();
        this.defaultHttpSessionIdResolver.setCookieSerializer(cookieSerializer);
    }

    @Bean
    public SessionEventHttpSessionListenerAdapter sessionEventHttpSessionListenerAdapter() {
        return new SessionEventHttpSessionListenerAdapter(this.httpSessionListeners);
    }

    @Bean
    public <S extends Session> SessionRepositoryFilter<? extends Session> springSessionRepositoryFilter(
            SessionRepository<S> sessionRepository
    ) {
        SessionRepositoryFilter<S> sessionRepositoryFilter = new CustomSessionRepositoryFilter<>(sessionRepository);
        sessionRepositoryFilter.setHttpSessionIdResolver(this.httpSessionIdResolver);
        return sessionRepositoryFilter;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        if (ClassUtils.isPresent("org.springframework.security.web.authentication.RememberMeServices", null)) {
            this.usesSpringSessionRememberMeServices = !ObjectUtils
                    .isEmpty(applicationContext.getBeanNamesForType(SpringSessionRememberMeServices.class));
        }
    }

    @Autowired(required = false)
    public void setServletContext(ServletContext servletContext) {
        this.servletContext = servletContext;
    }

    @Autowired(required = false)
    public void setCookieSerializer(CookieSerializer cookieSerializer) {
        this.cookieSerializer = cookieSerializer;
    }

    @Autowired(required = false)
    public void setHttpSessionIdResolver(HttpSessionIdResolver httpSessionIdResolver) {
        this.httpSessionIdResolver = httpSessionIdResolver;
    }

    @Autowired(required = false)
    public void setHttpSessionListeners(List<HttpSessionListener> listeners) {
        this.httpSessionListeners = listeners;
    }

    private CookieSerializer createDefaultCookieSerializer() {
        DefaultCookieSerializer cookieSerializer = new DefaultCookieSerializer();
        if (this.servletContext != null) {
            SessionCookieConfig sessionCookieConfig = null;
            try {
                sessionCookieConfig = this.servletContext.getSessionCookieConfig();
            }
            catch (UnsupportedOperationException ex) {
                this.logger.warn("Unable to obtain SessionCookieConfig: " + ex.getMessage());
            }
            if (sessionCookieConfig != null) {
                if (sessionCookieConfig.getName() != null) {
                    cookieSerializer.setCookieName(sessionCookieConfig.getName());
                }
                if (sessionCookieConfig.getDomain() != null) {
                    cookieSerializer.setDomainName(sessionCookieConfig.getDomain());
                }
                if (sessionCookieConfig.getPath() != null) {
                    cookieSerializer.setCookiePath(sessionCookieConfig.getPath());
                }
                if (sessionCookieConfig.getMaxAge() != -1) {
                    cookieSerializer.setCookieMaxAge(sessionCookieConfig.getMaxAge());
                }
            }
        }
        if (this.usesSpringSessionRememberMeServices) {
            cookieSerializer.setRememberMeRequestAttribute(SpringSessionRememberMeServices.REMEMBER_ME_LOGIN_ATTR);
        }
        return cookieSerializer;
    }
}
