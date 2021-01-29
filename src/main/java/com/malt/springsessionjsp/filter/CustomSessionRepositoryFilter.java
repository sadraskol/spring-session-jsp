package com.malt.springsessionjsp.filter;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.security.web.util.OnCommittedResponseWrapper;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.session.web.http.CookieHttpSessionIdResolver;
import org.springframework.session.web.http.HttpSessionIdResolver;
import org.springframework.session.web.http.SessionRepositoryFilter;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Instant;
import java.util.List;

/**
 * Replacement for the SessionRepositoryFilter to demonstrate redis being call multiple time during includes
 * @param <S>
 */
public class CustomSessionRepositoryFilter<S extends Session> extends SessionRepositoryFilter<S> {
    private static final String SESSION_LOGGER_NAME = CustomSessionRepositoryFilter.class.getName().concat(".SESSION_LOGGER");

    private static final Log SESSION_LOGGER = LogFactory.getLog(SESSION_LOGGER_NAME);

    public static final String SESSION_REPOSITORY_ATTR = SessionRepository.class.getName();

    private static final String CURRENT_SESSION_ATTR = SESSION_REPOSITORY_ATTR + ".CURRENT_SESSION";

    private final SessionRepository<S> sessionRepository;

    private HttpSessionIdResolver httpSessionIdResolver = new CookieHttpSessionIdResolver();

    public CustomSessionRepositoryFilter(SessionRepository<S> sessionRepository) {
        super(sessionRepository);
        this.sessionRepository = sessionRepository;
    }

    @Override
    public void setHttpSessionIdResolver(HttpSessionIdResolver httpSessionIdResolver) {
        super.setHttpSessionIdResolver(httpSessionIdResolver);
        this.httpSessionIdResolver = httpSessionIdResolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        request.setAttribute(SESSION_REPOSITORY_ATTR, this.sessionRepository);

        SessionRepositoryRequestWrapper wrappedRequest = new SessionRepositoryRequestWrapper(request, response);
        SessionRepositoryResponseWrapper wrappedResponse = new SessionRepositoryResponseWrapper(wrappedRequest,
                response);

        try {
            filterChain.doFilter(wrappedRequest, wrappedResponse);
        }
        finally {
            wrappedRequest.commitSession();
        }
    }

    private final class SessionRepositoryResponseWrapper extends OnCommittedResponseWrapper {
        private final SessionRepositoryRequestWrapper request;

        /**
         * Create a new {@link CustomSessionRepositoryFilter.SessionRepositoryResponseWrapper}.
         * @param request the request to be wrapped
         * @param response the response to be wrapped
         */
        SessionRepositoryResponseWrapper(SessionRepositoryRequestWrapper request, HttpServletResponse response) {
            super(response);
            if (request == null) {
                throw new IllegalArgumentException("request cannot be null");
            }
            this.request = request;
        }

        @Override
        protected void onResponseCommitted() {
            this.request.commitSession();
        }
    }

    private final class SessionRepositoryRequestWrapper extends HttpServletRequestWrapper {

        private final HttpServletResponse response;

        private S requestedSession;

        private boolean requestedSessionCached;

        private String requestedSessionId;

        private Boolean requestedSessionIdValid;

        private boolean requestedSessionInvalidated;

        private SessionRepositoryRequestWrapper(HttpServletRequest request, HttpServletResponse response) {
            super(request);
            this.response = response;
        }

        private void commitSession() {
            HttpSessionWrapper wrappedSession = getCurrentSession();
            if (wrappedSession == null) {
                if (isInvalidateClientSession()) {
                    CustomSessionRepositoryFilter.this.httpSessionIdResolver.expireSession(this, this.response);
                }
            }
            else {
                S session = wrappedSession.getSession();
                clearRequestedSessionCache();
                CustomSessionRepositoryFilter.this.sessionRepository.save(session);
                String sessionId = session.getId();
                if (!(isRequestedSessionIdValid() && sessionId.equals(getRequestedSessionId()))) {
                    CustomSessionRepositoryFilter.this.httpSessionIdResolver.setSessionId(this, this.response, sessionId);
                }
            }
        }

        private HttpSessionWrapper getCurrentSession() {
            return (HttpSessionWrapper) getAttribute(CURRENT_SESSION_ATTR);
        }

        private void setCurrentSession(HttpSessionWrapper currentSession) {
            SESSION_LOGGER.info("getCurrentSession");
            if (currentSession == null) {
                removeAttribute(CURRENT_SESSION_ATTR);
            }
            else {
                setAttribute(CURRENT_SESSION_ATTR, currentSession);
            }
        }

        @Override
        @SuppressWarnings("unused")
        public String changeSessionId() {
            HttpSession session = getSession(false);

            if (session == null) {
                throw new IllegalStateException(
                        "Cannot change session ID. There is no session associated with this request.");
            }

            return getCurrentSession().getSession().changeSessionId();
        }

        @Override
        public boolean isRequestedSessionIdValid() {
            if (this.requestedSessionIdValid == null) {
                S requestedSession = getRequestedSession();
                if (requestedSession != null) {
                    requestedSession.setLastAccessedTime(Instant.now());
                }
                return isRequestedSessionIdValid(requestedSession);
            }
            return this.requestedSessionIdValid;
        }

        private boolean isRequestedSessionIdValid(S session) {
            if (this.requestedSessionIdValid == null) {
                this.requestedSessionIdValid = session != null;
            }
            return this.requestedSessionIdValid;
        }

        private boolean isInvalidateClientSession() {
            return getCurrentSession() == null && this.requestedSessionInvalidated;
        }

        @Override
        public HttpSessionWrapper getSession(boolean create) {
            HttpSessionWrapper currentSession = getCurrentSession();
            if (currentSession != null) {
                return currentSession;
            }
            S requestedSession = getRequestedSession();
            if (requestedSession != null) {
                if (getAttribute(INVALID_SESSION_ID_ATTR) == null) {
                    requestedSession.setLastAccessedTime(Instant.now());
                    this.requestedSessionIdValid = true;
                    currentSession = new HttpSessionWrapper(requestedSession, getServletContext());
                    currentSession.markNotNew();
                    setCurrentSession(currentSession);
                    return currentSession;
                }
            }
            else {
                // This is an invalid session id. No need to ask again if
                // request.getSession is invoked for the duration of this request
                if (SESSION_LOGGER.isDebugEnabled()) {
                    SESSION_LOGGER.debug(
                            "No session found by id: Caching result for getSession(false) for this HttpServletRequest.");
                }
                setAttribute(INVALID_SESSION_ID_ATTR, "true");
            }
            if (!create) {
                return null;
            }
            if (SESSION_LOGGER.isDebugEnabled()) {
                SESSION_LOGGER.debug(
                        "A new session was created. To help you troubleshoot where the session was created we provided a StackTrace (this is not an error). You can prevent this from appearing by disabling DEBUG logging for "
                                + SESSION_LOGGER_NAME,
                        new RuntimeException("For debugging purposes only (not an error)"));
            }
            S session = CustomSessionRepositoryFilter.this.sessionRepository.createSession();
            session.setLastAccessedTime(Instant.now());
            currentSession = new HttpSessionWrapper(session, getServletContext());
            setCurrentSession(currentSession);
            return currentSession;
        }

        @Override
        public HttpSessionWrapper getSession() {
            return getSession(true);
        }

        @Override
        public String getRequestedSessionId() {
            if (this.requestedSessionId == null) {
                getRequestedSession();
            }
            return this.requestedSessionId;
        }

        @Override
        public RequestDispatcher getRequestDispatcher(String path) {
            RequestDispatcher requestDispatcher = super.getRequestDispatcher(path);
            return new SessionCommittingRequestDispatcher(requestDispatcher);
        }

        private S getRequestedSession() {
            if (!this.requestedSessionCached) {
                List<String> sessionIds = CustomSessionRepositoryFilter.this.httpSessionIdResolver.resolveSessionIds(this);
                for (String sessionId : sessionIds) {
                    if (this.requestedSessionId == null) {
                        this.requestedSessionId = sessionId;
                    }
                    SESSION_LOGGER.info("fetching session in cache (redis, etc.)");
                    S session = CustomSessionRepositoryFilter.this.sessionRepository.findById(sessionId);
                    if (session != null) {
                        this.requestedSession = session;
                        this.requestedSessionId = sessionId;
                        break;
                    }
                }
                this.requestedSessionCached = true;
            }
            return this.requestedSession;
        }

        private void clearRequestedSessionCache() {
            this.requestedSessionCached = false;
            this.requestedSession = null;
            this.requestedSessionId = null;
        }

        /**
         * Allows creating an HttpSession from a Session instance.
         *
         * @author Rob Winch
         * @since 1.0
         */
        private final class HttpSessionWrapper extends HttpSessionAdapter<S> {

            HttpSessionWrapper(S session, ServletContext servletContext) {
                super(session, servletContext);
            }

            @Override
            public void invalidate() {
                super.invalidate();
                SessionRepositoryRequestWrapper.this.requestedSessionInvalidated = true;
                setCurrentSession(null);
                clearRequestedSessionCache();
                CustomSessionRepositoryFilter.this.sessionRepository.deleteById(getId());
            }

        }

        /**
         * Ensures session is committed before issuing an include.
         *
         * @since 1.3.4
         */
        private final class SessionCommittingRequestDispatcher implements RequestDispatcher {

            private final RequestDispatcher delegate;

            SessionCommittingRequestDispatcher(RequestDispatcher delegate) {
                this.delegate = delegate;
            }

            @Override
            public void forward(ServletRequest request, ServletResponse response) throws ServletException, IOException {
                this.delegate.forward(request, response);
            }

            @Override
            public void include(ServletRequest request, ServletResponse response) throws ServletException, IOException {
                SessionRepositoryRequestWrapper.this.commitSession();
                this.delegate.include(request, response);
            }

        }

    }
}
