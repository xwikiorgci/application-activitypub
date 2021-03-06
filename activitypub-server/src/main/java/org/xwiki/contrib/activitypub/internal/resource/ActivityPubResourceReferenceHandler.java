/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.xwiki.contrib.activitypub.internal.resource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.http.HttpHeaders;
import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.component.util.DefaultParameterizedType;
import org.xwiki.container.Container;
import org.xwiki.container.servlet.ServletRequest;
import org.xwiki.container.servlet.ServletResponse;
import org.xwiki.contrib.activitypub.ActivityHandler;
import org.xwiki.contrib.activitypub.ActivityPubClient;
import org.xwiki.contrib.activitypub.ActivityPubException;
import org.xwiki.contrib.activitypub.ActivityPubJsonParser;
import org.xwiki.contrib.activitypub.ActivityPubJsonSerializer;
import org.xwiki.contrib.activitypub.ActivityPubObjectReferenceResolver;
import org.xwiki.contrib.activitypub.ActivityPubResourceReference;
import org.xwiki.contrib.activitypub.ActivityPubStorage;
import org.xwiki.contrib.activitypub.ActivityRequest;
import org.xwiki.contrib.activitypub.ActorHandler;
import org.xwiki.contrib.activitypub.entities.AbstractActivity;
import org.xwiki.contrib.activitypub.entities.AbstractActor;
import org.xwiki.contrib.activitypub.entities.AbstractBox;
import org.xwiki.contrib.activitypub.entities.ActivityPubObject;
import org.xwiki.contrib.activitypub.entities.Inbox;
import org.xwiki.contrib.activitypub.entities.OrderedCollection;
import org.xwiki.contrib.activitypub.entities.Outbox;
import org.xwiki.contrib.activitypub.internal.XWikiUserBridge;
import org.xwiki.contrib.activitypub.internal.filters.CollectionFilter;
import org.xwiki.contrib.activitypub.webfinger.WebfingerException;
import org.xwiki.contrib.activitypub.webfinger.WebfingerService;
import org.xwiki.resource.AbstractResourceReferenceHandler;
import org.xwiki.resource.ResourceReference;
import org.xwiki.resource.ResourceReferenceHandlerChain;
import org.xwiki.resource.ResourceReferenceHandlerException;
import org.xwiki.resource.ResourceType;
import org.xwiki.resource.annotations.Authenticate;
import org.xwiki.user.UserReference;

/**
 * Main handler for ActivityPub.
 * This handler receives requests on the form /activitypub/entitytype/identifier with entitytype being one of the
 * concrete type of {@link ActivityPubObject}. In case of GET request, the resource is looked for in the storage and
 * immediately returned if found.
 *
 * In case of a GET request for an Actor, the actor is created if not find in the storage: this allows to create lazily
 * the actors.
 *
 * In case of POST request some checks are performed to ensure the user is authorized to do it, and then the activity
 * is sent to the right {@link ActivityHandler}.
 *
 * @version $Id$
 */
@Component
@Named("activitypub")
@Singleton
@Authenticate
public class ActivityPubResourceReferenceHandler extends AbstractResourceReferenceHandler<ResourceType>
{
    private static final ResourceType TYPE = new ResourceType("activitypub");

    private static final String TEXTPLAIN_CONTENTTYPE = "text/plain";

    @Inject
    private Logger logger;

    @Inject
    @Named("context")
    private ComponentManager componentManager;

    @Inject
    private ActivityPubJsonParser activityPubJsonParser;

    @Inject
    private ActivityPubJsonSerializer activityPubJsonSerializer;

    @Inject
    private Container container;

    @Inject
    private ActorHandler actorHandler;

    @Inject
    private ActivityPubStorage activityPubStorage;

    @Inject
    private ActivityPubObjectReferenceResolver objectReferenceResolver;

    @Inject
    private CollectionFilter<OrderedCollection<AbstractActivity>> publicActivityCollectionFilter;

    @Inject
    private XWikiUserBridge xWikiUserBridge;

    @Inject
    private WebfingerService webfingerService;

    @Override
    public List<ResourceType> getSupportedResourceReferences()
    {
        return Arrays.asList(TYPE);
    }

    @Override
    public void handle(ResourceReference reference, ResourceReferenceHandlerChain chain)
        throws ResourceReferenceHandlerException
    {
        ActivityPubResourceReference resourceReference = (ActivityPubResourceReference) reference;
        HttpServletRequest request = ((ServletRequest) this.container.getRequest()).getHttpServletRequest();
        HttpServletResponse response = ((ServletResponse) this.container.getResponse()).getHttpServletResponse();
        try {
            ActivityPubObject entity =
                this.activityPubStorage.retrieveEntity(new URI(request.getRequestURL().toString()));

            this.issueMissingPublicKey(entity);

            // We didn't manage to retrieve the entity from storage, but it's about an Actor: we lazily create it.
            if (entity == null && isAboutActor(resourceReference)) {
                entity = this.actorHandler.getActor(resourceReference);
            }

            // if the entity is still null, then it's a 404: we don't know about it.
            if (entity == null) {
                this.sendErrorResponse(HttpServletResponse.SC_NOT_FOUND,
                        String.format("The entity of type [%s] and uid [%s] cannot be found.",
                                resourceReference.getEntityType(), resourceReference.getUuid()));

            // FIXME: we should check the Content-Type and Accept headers
            // See: https://www.w3.org/TR/activitypub/#client-to-server-interactions for POST and
            // https://www.w3.org/TR/activitypub/#retrieving-objects for GET
            // We are in a GET request with an entity: we just serve it.
            } else if (isGet(request)) {
                this.handleGetOnExistingEntity(request, response, entity);

            // We are in a POST request but not in a box: we don't accept those requests.
            } else if (!isAboutBox(resourceReference)) {
                this.sendErrorResponse(HttpServletResponse.SC_BAD_REQUEST,
                    "POST requests are only allowed on inbox or outbox.");

            // We are in a POST request, in a box, but the attributedTo entity is empty: this shouldn't happen
            // we cannot identify who the box belongs to, so we have to report an error.
            } else if (!isAttributedTo(entity)) {
                this.sendErrorResponse(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "This box is not attributed. Please report the error to the administrator.");

            // We are finally in a POST request to a box and we can handle it.
            } else {
                this.handleBox((AbstractBox) entity);
            }
        } catch (ActivityPubException | IOException | URISyntaxException e) {
            try {
                this.handleException(response, e);
            } catch (IOException ex) {
                logger.error("Cannot handle exception properly", ex);
                logger.error("Root exception to handle", e);
            }
        }
        // Be a good citizen, continue the chain, in case some lower-priority Handler has something to do for this
        // Resource Reference.
        chain.handleNext(reference);
    }

    /**
     * Generates and store a public key for an {@link AbstractActor} if she/it does not already hvae one.
     *
     * @param entity The entity to possibly update.
     * @throws ActivityPubException In case of error during the public key generation or the entity storage.
     */
    private void issueMissingPublicKey(ActivityPubObject entity) throws ActivityPubException
    {
        if (entity instanceof AbstractActor) {
            AbstractActor abstractActor = (AbstractActor) entity;
            if (abstractActor.getPublicKey() == null) {
                abstractActor.setPublicKey(this.actorHandler.initPublicKey(abstractActor));
                this.activityPubStorage.storeEntity(abstractActor);
            }
        }
    }

    /**
     * Handle the POST made on the given box: this methods parse the body of the request, perform some checks on it,
     * build an {@link ActivityRequest}, retrieve the right {@link ActivityHandler} and delegates to it the request.
     *
     * @param box the box where the POST was performed
     * @throws ActivityPubException in case of error during the checks on the body
     * @throws IOException          in case of error during an HTTP response.
     */
    private void handleBox(AbstractBox box) throws ActivityPubException, IOException
    {
        HttpServletRequest request = ((ServletRequest) this.container.getRequest()).getHttpServletRequest();
        HttpServletResponse response = ((ServletResponse) this.container.getResponse()).getHttpServletResponse();

        // resolve the actor with the attributed to reference
        AbstractActor actor = this.objectReferenceResolver.resolveReference(box.getAttributedTo().get(0));

        // Parse the body of the request to retrieve the activity
        ActivityPubObject object = this.activityPubJsonParser.parse(
            new BufferedReader(new InputStreamReader(request.getInputStream(), StandardCharsets.UTF_8)));
        AbstractActivity activity = getActivity(object);

        // Create the ActivityRequest and retrieve the handler for it
        ActivityRequest<AbstractActivity> activityRequest = new ActivityRequest<>(actor, activity, request, response);
        ActivityHandler<AbstractActivity> handler = this.getHandler(activity);

        if (box instanceof Inbox) {
            handler.handleInboxRequest(activityRequest);
        } else {
            // Perform some authorization checks
            UserReference userReference = this.xWikiUserBridge.getCurrentUserReference();
            if (this.actorHandler.isAuthorizedToActFor(userReference, actor)) {
                handler.handleOutboxRequest(activityRequest);
            } else {
                this.sendErrorResponse(HttpServletResponse.SC_FORBIDDEN,
                    String.format("The session user [%s] cannot post to [%s] outbox.",
                        userReference, actor.getPreferredUsername()));
            }
        }
    }

    /**
     * Ensure that the given {@link ActivityPubObject} has an attributedTo parameter filled.
     * @param entity the object that needs an attributedTo parameter.
     * @return {@code true} iff {@link ActivityPubObject#getAttributedTo()} returns a filled collection.
     */
    private boolean isAttributedTo(ActivityPubObject entity)
    {
        return entity.getAttributedTo() != null && !entity.getAttributedTo().isEmpty();
    }

    /**
     * Ensure that the given {@link ActivityPubResourceReference} is about an inbox or an outbox.
     * @param resourceReference the reference to check
     * @return {@code true} iff the type of the reference is inbox or outbox.
     */
    private boolean isAboutBox(ActivityPubResourceReference resourceReference)
    {
        return "inbox".equalsIgnoreCase(resourceReference.getEntityType())
            || "outbox".equalsIgnoreCase(resourceReference.getEntityType());
    }

    /**
     * Ensure that the request method is a GET.
     * @param request the request to test
     * @return {@code true} iff the method of the request is GET.
     */
    private boolean isGet(HttpServletRequest request)
    {
        return "get".equalsIgnoreCase(request.getMethod());
    }

    /**
     * Serialize the given entity in the response and set the headers.
     *
     * @param request the request.
     * @param response the response servlet to use.
     * @param entity the entity to serialize.
     * @throws IOException in case of error during the HTTP response.
     * @throws ActivityPubException in case of error during the serialization.
     */
    private void handleGetOnExistingEntity(HttpServletRequest request, HttpServletResponse response,
        ActivityPubObject entity)
        throws IOException, ActivityPubException
    {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(ActivityPubClient.CONTENT_TYPE_STRICT);
        response.setCharacterEncoding(StandardCharsets.UTF_8.toString());

        // FIXME: check if the entity is an actor and redirect if the content type is not activitypub compliant
        // if the entity is an inbox or an outbox we filtered out some content
        if (entity instanceof Inbox) {
            this.handleGetOnBox(response, (Inbox) entity);
        } else if (entity instanceof Outbox) {
            this.handleGetOnBox(response, (Outbox) entity);
            // else we directly serialize the entity
        } else if (entity instanceof AbstractActor) {
            this.handleGetOnAbstractActor(request, response, (AbstractActor) entity);
        } else {
            this.activityPubJsonSerializer.serialize(response.getOutputStream(), entity);
        }
    }

    private void handleGetOnAbstractActor(HttpServletRequest request, HttpServletResponse response,
        AbstractActor entity) throws IOException, ActivityPubException
    {
        if (request.getHeader(HttpHeaders.ACCEPT).contains("text/html")) {
            try {
                URI uri = this.webfingerService.resolveXWikiUserUrl(entity);
                response.sendRedirect(uri.toASCIIString());
            } catch (WebfingerException e) {
                throw new ActivityPubException("Error during the redirection of [" + entity + "]", e);
            }
        } else {
            this.activityPubJsonSerializer.serialize(response.getOutputStream(), entity);
        }
    }

    /**
     * Filter to keep only public activities if the logged-in users is not an owner of the inbox/outbox.
     */
    private void handleGetOnBox(HttpServletResponse response, OrderedCollection<AbstractActivity> box)
        throws ActivityPubException, IOException
    {
        OrderedCollection<AbstractActivity> filteredBox;
        // resolve the actor with the attributed to reference
        AbstractActor actor = this.objectReferenceResolver.resolveReference(box.getAttributedTo().get(0));
        UserReference userReference = this.xWikiUserBridge.getCurrentUserReference();

        if (this.actorHandler.isAuthorizedToActFor(userReference, actor)) {
            filteredBox = box;
        } else {
            filteredBox = this.publicActivityCollectionFilter.filter(box);
        }
        this.activityPubJsonSerializer.serialize(response.getOutputStream(), filteredBox);
    }

    /**
     * Utility method to send an error message in case of exception.
     * @param response the servlet response to use
     * @param e the exception to handle.
     * @throws IOException in case of error during the HTTP response
     */
    private void handleException(HttpServletResponse response, Exception e) throws IOException
    {
        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        response.setContentType(TEXTPLAIN_CONTENTTYPE);
        e.printStackTrace(response.getWriter());
    }

    /**
     * Check that the given resource reference is a request about an actor.
     * @param resourceReference the reference to check
     * @return {@code true} if the request is about an actor (person or service).
     */
    private boolean isAboutActor(ActivityPubResourceReference resourceReference)
    {
        return ("person".equalsIgnoreCase(resourceReference.getEntityType())
            || "service".equalsIgnoreCase(resourceReference.getEntityType()));
    }

    /**
     * Send an error message as plain text.
     * @param statusCode the HTTP status to send
     * @param message the error message to send
     * @throws IOException in case of error during the HTTP response
     */
    private void sendErrorResponse(int statusCode, String message) throws IOException
    {
        HttpServletResponse response = ((ServletResponse) this.container.getResponse()).getHttpServletResponse();
        response.setStatus(statusCode);
        response.setContentType(TEXTPLAIN_CONTENTTYPE);
        response.getOutputStream().write(message.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Ensure that the actual object class inherits from AbstractActivity and returns it.
     * TODO: in case it's not the case, it should return a Create wrapper around the object.
     * @param object the object to check
     * @param <T> the type of activity
     * @throws ActivityPubException in case the object is not an activity
     * @return the activity
     */
    private <T extends AbstractActivity> T getActivity(ActivityPubObject object) throws ActivityPubException
    {
        if (AbstractActivity.class.isAssignableFrom(object.getClass())) {
            return (T) object;
        } else {
            // TODO: handle wrapping object in a create activity
            throw new ActivityPubException("The body does not contain an activity, "
                + "the wrapping of objects in a Create activity is not yet supported. "
                + "Please report it if you have this issue.");
        }
    }

    /**
     * Retrieve the {@link ActivityHandler} concrete component based on the given activity class.
     * @param activity the activity for which we need a handler
     * @param <T> the type of the activity
     * @return an activity handler for this activity
     * @throws ActivityPubException in case no component for this activity can be found.
     */
    private <T extends AbstractActivity> ActivityHandler<T> getHandler(T activity)
        throws ActivityPubException
    {
        try {
            Type activityHandlerType = new DefaultParameterizedType(null, ActivityHandler.class,
                activity.getClass());
            return this.componentManager.getInstance(activityHandlerType);
        } catch (ComponentLookupException e) {
            throw new ActivityPubException(
                String.format("Error while getting the ActivityHandler for activity [%s]",
                    activity.getType()), e);
        }
    }
}
