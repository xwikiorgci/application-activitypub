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
package org.xwiki.contrib.activitypub.internal.async.jobs;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;

import javax.inject.Inject;

import org.xwiki.contrib.activitypub.ActivityPubConfiguration;
import org.xwiki.contrib.activitypub.ActivityPubException;
import org.xwiki.contrib.activitypub.ActivityPubObjectReferenceResolver;
import org.xwiki.contrib.activitypub.ActorHandler;
import org.xwiki.contrib.activitypub.entities.AbstractActor;
import org.xwiki.contrib.activitypub.entities.ActivityPubObjectReference;
import org.xwiki.contrib.activitypub.entities.OrderedCollection;
import org.xwiki.contrib.activitypub.entities.Service;
import org.xwiki.contrib.activitypub.internal.XWikiUserBridge;
import org.xwiki.contrib.activitypub.internal.async.PageChangedRequest;
import org.xwiki.contrib.activitypub.internal.async.PageChangedStatus;
import org.xwiki.job.AbstractJob;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.security.authorization.AuthorizationManager;
import org.xwiki.security.authorization.Right;
import org.xwiki.user.UserReference;

import com.xpn.xwiki.user.api.XWikiRightService;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.xwiki.contrib.activitypub.ActivityPubConfiguration.PageNotificationPolicy.WIKIANDUSER;

/**
 * Please document me.
 *
 * @version $Id$
 * @since 1.2
 */
public abstract class AbstractPageNotificationJob extends AbstractJob<PageChangedRequest, PageChangedStatus>
{
    private static final DocumentReference GUEST_USER =
        new DocumentReference("xwiki", "XWiki", XWikiRightService.GUEST_USER);

    @Inject
    protected EntityReferenceSerializer<String> stringEntityReferenceSerializer;

    @Inject
    private XWikiUserBridge xWikiUserBridge;

    @Inject
    private ActivityPubObjectReferenceResolver objectReferenceResolver;

    @Inject
    private AuthorizationManager authorizationManager;

    @Inject
    private ActorHandler actorHandler;

    @Inject
    private ActivityPubConfiguration configuration;

    @Override
    protected void runInternal()
    {
        PageChangedRequest request = this.getRequest();
        try {
            UserReference userReference =
                this.xWikiUserBridge.resolveDocumentReference(request.getAuthorReference());
            AbstractActor author = this.actorHandler.getActor(userReference);
            OrderedCollection<AbstractActor> authorFollowers =
                this.objectReferenceResolver.resolveReference(author.getFollowers());
            Service wikiActor = this.actorHandler.getActor(request.getDocumentReference().getWikiReference());
            OrderedCollection<AbstractActor> wikiFollowers =
                this.objectReferenceResolver.resolveReference(wikiActor.getFollowers());

            // ensure the page can be viewed with guest user to not disclose private stuff in a notif
            boolean guestAccess = this.authorizationManager
                .hasAccess(Right.VIEW, GUEST_USER, request.getDocumentReference());

            // We trigger notifications only if the page is available in guest and there is wiki followers
            // but also if there's no wiki followers but author followers and the configuration allows to trigger on it.
            boolean shouldTriggerNotifications = guestAccess
                && (!wikiFollowers.isEmpty()
                || (Objects.equals(this.configuration.getPageNotificationPolicy(), WIKIANDUSER) && !authorFollowers
                .isEmpty()));

            if (shouldTriggerNotifications) {
                this.proceed(author);
            }
        } catch (URISyntaxException | ActivityPubException | IOException e) {
            // FIXME: we have a special handling of errors coming from user reference resolution,
            // since we have regular stacktraces related to Scheduler listener and AP resolution issue with
            // CurrentUserReference. This should be removed after fixing XAP-28.
            String errorMessage = "Error while trying to handle notifications for document [{}]";
            if (e instanceof ActivityPubException && e.getMessage().contains("Cannot find any user with reference")) {
                this.logger.debug(errorMessage, request.getDocumentReference(), e);
            } else {
                this.logger.error(errorMessage, request.getDocumentReference(), e);
            }
        }
    }

    protected abstract void proceed(AbstractActor author) throws URISyntaxException, IOException, ActivityPubException;

    protected List<ActivityPubObjectReference<AbstractActor>> emitters(AbstractActor author) throws ActivityPubException
    {
        PageChangedRequest request = this.getRequest();
        DocumentReference dr = request.getDocumentReference();
        Service wikiActor = this.actorHandler.getActor(dr.getWikiReference());
        ActivityPubObjectReference<AbstractActor> wiki = new ActivityPubObjectReference<AbstractActor>()
            .setObject(wikiActor);

        List<ActivityPubObjectReference<AbstractActor>> ret;
        if (Objects.equals(this.configuration.getPageNotificationPolicy(), WIKIANDUSER)) {
            ret = asList(wiki, new ActivityPubObjectReference<AbstractActor>().setObject(author));
        } else {
            ret = singletonList(wiki);
        }
        return ret;
    }
}
