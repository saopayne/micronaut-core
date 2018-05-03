/*
 * Copyright 2017-2018 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.micronaut.security.endpoints;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.security.Secured;
import io.micronaut.security.authentication.*;
import io.micronaut.security.event.LoginFailedEvent;
import io.micronaut.security.event.LoginSuccessfulEvent;
import io.micronaut.security.handlers.LoginHandler;
import io.micronaut.security.rules.SecurityRule;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.reactivex.functions.Function;
import org.reactivestreams.Publisher;

import java.util.Optional;

/**
 *
 * Handles login requests
 *
 * @author Sergio del Amo
 * @author Graeme Rocher
 *
 * @since 1.0
 */
@Controller("/")
@Requires(property = SecurityEndpointsConfigurationProperties.PREFIX + ".login")
@Secured(SecurityRule.IS_ANONYMOUS)
public class LoginController {

    protected final Authenticator authenticator;
    protected final LoginHandler loginHandler;
    protected final ApplicationEventPublisher eventPublisher;

    /**
     *
     * @param authenticator {@link Authenticator} collaborator
     * @param loginHandler A collaborator which helps to build HTTP response depending on success or failure.
     * @param eventPublisher The application event publisher
     */
    public LoginController(Authenticator authenticator,
                           LoginHandler loginHandler,
                           ApplicationEventPublisher eventPublisher) {
        this.authenticator = authenticator;
        this.loginHandler = loginHandler;
        this.eventPublisher = eventPublisher;
    }

    /**
     *
     * @param usernamePasswordCredentials An instance of {@link UsernamePasswordCredentials} in the body payload
     * @param request The {@link HttpRequest} being executed
     * @return An AccessRefreshToken encapsulated in the HttpResponse or a failure indicated by the HTTP status
     */
    @Consumes({MediaType.APPLICATION_FORM_URLENCODED, MediaType.APPLICATION_JSON})
    @Post
    public Maybe<HttpResponse> login(@Body UsernamePasswordCredentials usernamePasswordCredentials, HttpRequest<?> request) {
        Maybe<AuthenticationResponse> authenticationResponse = Single.fromPublisher(authenticator.authenticate(usernamePasswordCredentials)).toMaybe();

        return authenticationResponse.map(auth -> {
            if (auth.isAuthenticated()) {
                UserDetails userDetails = (UserDetails) auth;
                eventPublisher.publishEvent(new LoginSuccessfulEvent(userDetails));
                return loginHandler.loginSuccess(userDetails, request);
            } else {
                AuthenticationFailed authenticationFailed = (AuthenticationFailed) auth;
                eventPublisher.publishEvent(new LoginFailedEvent(authenticationFailed));
                return loginHandler.loginFailed(authenticationFailed);
            }
        }).switchIfEmpty(Maybe.error(
                new AuthenticationException((String)null)
        ));
    }
}