/*
 * Copyright (C) 2019 Authlete, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific
 * language governing permissions and limitations under the
 * License.
 */
package com.authlete.jaxrs.server.api.backchannel;


import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import com.authlete.common.api.AuthleteApiFactory;
import com.authlete.common.dto.Scope;
import com.authlete.common.dto.BackchannelAuthenticationCompleteRequest.Result;
import com.authlete.common.types.User;
import com.authlete.jaxrs.BackchannelAuthenticationCompleteRequestHandler;


/**
 * A base class for processors that communicate with <a href="https://cibasim.authlete.com">
 * Authlete CIBA authentication device simulator</a> for end-user authentication
 * and authorization.
 *
 * @see <a href="https://cibasim.authlete.com">Authlete CIBA authentication device
 *      simulator</a>
 *
 * @see <a href="https://app.swaggerhub.com/apis-docs/Authlete/cibasim">Authlete
 *      CIBA authentication device simulator API</a>
 *
 * @see com.authlete.jaxrs.server.api.backchannel.BackchannelAuthenticationCallbackEndpoint
 * BackchannelAuthenticationCallbackEndpoint
 *
 * @author Hideki Ikeda
 */
public abstract class BaseAuthenticationDeviceProcessor implements AuthenticationDeviceProcessor
{
    protected final String mTicket;
    protected final User mUser;
    protected final String mClientName;
    protected final String[] mAcrs;
    protected final Scope[] mScopes;
    protected final String[] mClaimNames;
    protected final String mBindingMessage;


    /**
     * The constructor of this class.
     *
     * @param ticket
     *         A ticket that was issued by Authlete's {@code /api/backchannel/authentication}
     *         API.
     *
     * @param user
     *         An end-user to be authenticated and asked to authorize the client
     *         application.
     *
     * @param clientName
     *         The name of the client application.
     *
     * @param acrs
     *         The requested ACRs.
     *
     * @param scopes
     *         The requested scopes.
     *
     * @param claimNames
     *         The names of the requested claims.
     *
     * @param bindingMessage
     *         The binding message to be shown to the end-user on the authentication
     *         device.
     *
     * @return
     *         An instance of this class.
     */
    public BaseAuthenticationDeviceProcessor(String ticket, User user, String clientName, String[] acrs, Scope[] scopes, String[] claimNames, String bindingMessage)
    {
        mTicket         = ticket;
        mUser           = user;
        mClientName     = clientName;
        mAcrs           = acrs;
        mScopes         = scopes;
        mClaimNames     = claimNames;
        mBindingMessage = bindingMessage;
    }


    /**
     * Delegate the process to {@link com.authlete.jaxrs.BackchannelAuthenticationCompleteRequestHandler
     * BackchannelAuthenticationCompleteRequestHandler} with a result of {@link
     * Result#AUTHORIZED AUTHORIZED}. This method is equivalent to {@link #complete(Result, Date)
     * complete}({@link Result}.{@link Result#AUTHORIZED AUTHORIZED}, {@code null}).
     *
     * @param authTime
     *         The time when end-user authentication occurred. The number of
     *         seconds since Unix epoch (1970-01-01). This value is used as
     *         the value of {@code auth_time} claim in an ID token that may
     *         be issued. Pass 0 if the time is unknown.
     */
    protected void completeWithAuthorized(Date authTime)
    {
        complete(Result.AUTHORIZED, authTime);
    }


    /**
     * Delegate the process to {@link com.authlete.jaxrs.BackchannelAuthenticationCompleteRequestHandler
     * BackchannelAuthenticationCompleteRequestHandler} with a result of {@link
     * Result#ACCESS_DENIED ACCESS_DENIED}. This method is equivalent to {@link
     * #complete(Result, Date) complete}({@link Result}.{@link Result#ACCESS_DENIED ACCESS_DENIED}, {@code null}).
     */
    protected void completeWithAccessDenied()
    {
        complete(Result.ACCESS_DENIED, null);
    }


    /**
     * Delegate the process to {@link com.authlete.jaxrs.BackchannelAuthenticationCompleteRequestHandler
     * BackchannelAuthenticationCompleteRequestHandler} with a result of {@link
     * Result#ERROR ERROR}. This method is equivalent to {@link #complete(Result, Date)
     * complete}({@link Result}.{@link Result#ERROR ERROR}, {@code null}).
     */
    protected void completeWithError()
    {
        complete(Result.ERROR, null);
    }


    /**
     * Delegate the process to {@link com.authlete.jaxrs.BackchannelAuthenticationCompleteRequestHandler
     * BackchannelAuthenticationCompleteRequestHandler}.
     *
     * @param result
     *         A result of the end-user authentication and authorization.
     *
     * @param authTime
     *         The time when end-user authentication occurred. The number of
     *         seconds since Unix epoch (1970-01-01). This value is used as
     *         the value of {@code auth_time} claim in an ID token that may
     *         be issued. Pass 0 if the time is unknown.
     */
    protected void complete(Result result, Date authTime)
    {
        new BackchannelAuthenticationCompleteRequestHandler(
                AuthleteApiFactory.getDefaultApi(),
                new BackchannelAuthenticationCompleteHandlerSpiImpl(result, mUser, authTime, mAcrs)
            )
        .handle(mTicket, mClaimNames);
    }


    /**
     * Build a simple message to be shown to the end-user on the authentication
     * device.
     *
     * @return
     *         A message to be shown to the end-user on the authentication device.
     */
    protected String buildMessage()
    {
        StringBuilder messageFormatBuilder = new StringBuilder();
        List<String> messageArgs = new ArrayList<String>();

        // Add client information to the message.
        messageFormatBuilder.append("Client App (%s) is requesting the following permissions.");
        messageArgs.add(mClientName);

        // Add scope information to the message.
        messageFormatBuilder.append("[Requested scopes]: %s");
        messageArgs.add(extractScopeNames());

        // Add binding message to the message if available.
        if (mBindingMessage != null)
        {
            messageFormatBuilder.append("[Binding message]: %s");
            messageArgs.add(mBindingMessage);
        }

        // Build a message to be shown to the end-user.
        return String.format(messageFormatBuilder.toString(), messageArgs.toArray());
    }


    private String extractScopeNames()
    {
        if (mScopes == null || mScopes.length == 0)
        {
            return null;
        }

        List<String> scopeNames = new ArrayList<String>();

        for (Scope scope : mScopes)
        {
            scopeNames.add(scope.getName());
        }

        return String.join(",", scopeNames);
    }
}
