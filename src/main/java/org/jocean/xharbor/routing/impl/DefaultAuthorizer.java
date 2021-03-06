package org.jocean.xharbor.routing.impl;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jocean.idiom.Pair;
import org.jocean.xharbor.api.RoutingInfo;
import org.jocean.xharbor.routing.AuthorizationRule;
import org.jocean.xharbor.routing.RuleSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Charsets;
import com.google.common.io.BaseEncoding;

import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import rx.functions.Func1;

public class DefaultAuthorizer implements AuthorizationRule {
    
    private static final Logger LOG = LoggerFactory
            .getLogger(DefaultAuthorizer.class);
    
    public DefaultAuthorizer(
            final RuleSet rules,
            final String pathPattern, 
            final String user, 
            final String password) {
        this._rules = rules;
        this._pathPattern = safeCompilePattern(pathPattern);
        this._user = user;
        this._password = password;
        
        this._rules.addAuthorization(this);
    }
    
    public void stop() {
        this._rules.removeAuthorization(this);
    }
    
    @Override
    public Func1<HttpRequest, Boolean> genAuthorization(final RoutingInfo info) {
        final Matcher matcher = this._pathPattern.matcher(info.getPath());
        if ( matcher.find() ) {
            return new Func1<HttpRequest, Boolean>() {
                @Override
                public Boolean call(final HttpRequest request) {
                    return !isAuthorizeSuccess(request, _user, _password);
                }
                
                @Override
                public String toString() {
                    return "[" + _pathPattern.toString() + ":" + _user + "/" + _password + "]";
                }};
        } else {
            return null;
        }
    }
    
    private boolean isAuthorizeSuccess(
            final HttpRequest httpRequest, 
            final String authUser, 
            final String authPassword) {
        final String authorization = HttpHeaders.getHeader(httpRequest, HttpHeaders.Names.AUTHORIZATION);
        if ( null != authorization) {
            final String userAndPassBase64Encoded = validateBasicAuth(authorization);
            if ( null != userAndPassBase64Encoded ) {
                final Pair<String, String> userAndPass = getUserAndPassForBasicAuth(userAndPassBase64Encoded);
                if (null != userAndPass) {
                    final boolean ret = (userAndPass.getFirst().equals(authUser)
                            && userAndPass.getSecond().equals(authPassword));
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("httpRequest [{}] basic authorization {}, input:{}/{}, setted auth:{}/{}", 
                                httpRequest, (ret ? "success" : "failure"), 
                                userAndPass.getFirst(), userAndPass.getSecond(),
                                authUser, authPassword);
                    }
                    return ret;
                }
            }
        }
        return false;
    }

    private static String validateBasicAuth(final String authorization) {
        if (authorization.startsWith("Basic")) {
            final String[] authFields = authorization.split(" ");
            if (authFields.length>=2) {
                return authFields[1];
            }
        }
        return null;
    }
    
    private static Pair<String, String> getUserAndPassForBasicAuth(
            final String userAndPassBase64Encoded) {
        final String userAndPass = new String(BaseEncoding.base64().decode(userAndPassBase64Encoded), 
                Charsets.UTF_8);
        final String[] fields = userAndPass.split(":");
        return fields.length == 2 ? Pair.of(fields[0], fields[1]) : null;
    }
    
    private static Pattern safeCompilePattern(final String regex) {
        return null != regex && !"".equals(regex) ? Pattern.compile(regex) : null;
    }
    
    private final RuleSet _rules;
    private final Pattern _pathPattern;
    private final String _user;
    private final String _password;
}
