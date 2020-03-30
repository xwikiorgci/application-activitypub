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
package org.xwiki.contrib.activitypub.internal;

import java.net.URI;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.Collections;

import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.methods.GetMethod;
import org.jsoup.helper.HttpConnection;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.xwiki.contrib.activitypub.ActivityPubClient;
import org.xwiki.contrib.activitypub.ActivityPubException;
import org.xwiki.contrib.activitypub.ActivityPubJsonParser;
import org.xwiki.contrib.activitypub.ActivityPubStorage;
import org.xwiki.contrib.activitypub.SignatureService;
import org.xwiki.contrib.activitypub.entities.AbstractActor;
import org.xwiki.contrib.activitypub.entities.ActivityPubObjectReference;
import org.xwiki.contrib.activitypub.entities.Inbox;
import org.xwiki.contrib.activitypub.entities.OrderedCollection;
import org.xwiki.contrib.activitypub.entities.Outbox;
import org.xwiki.contrib.activitypub.entities.Person;
import org.xwiki.contrib.activitypub.webfinger.WebfingerClient;
import org.xwiki.contrib.activitypub.webfinger.entities.JSONResourceDescriptor;
import org.xwiki.contrib.activitypub.webfinger.entities.Link;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;
import org.xwiki.user.CurrentUserReference;
import org.xwiki.user.UserProperties;
import org.xwiki.user.UserReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test for {@link DefaultActorHandler}.
 *
 * @version $Id$
 */
@ComponentTest
public class DefaultActorHandlerTest
{
    @InjectMockComponents
    private DefaultActorHandler actorHandler;

    @MockComponent
    private ActivityPubStorage activityPubStorage;

    @MockComponent
    private ActivityPubClient activityPubClient;

    @MockComponent
    private ActivityPubJsonParser jsonParser;

    @MockComponent
    private XWikiUserBridge xWikiUserBridge;

    @MockComponent
    private SignatureService signatureService;

    @MockComponent
    private WebfingerClient webfingerClient;

    @Mock
    private UserReference fooUserReference;

    @Mock
    private UserReference barUserReference;

    @BeforeEach
    public void setup() throws Exception
    {
        // Foo is an existing user, named Foo Foo.
        when(this.xWikiUserBridge.resolveUser("XWiki.Foo")).thenReturn(this.fooUserReference);
        when(this.xWikiUserBridge.resolveUser("Foo")).thenReturn(this.fooUserReference);
        when(this.xWikiUserBridge.isExistingUser("XWiki.Foo")).thenReturn(true);
        when(this.xWikiUserBridge.isExistingUser("Foo")).thenReturn(true);
        when(this.xWikiUserBridge.isExistingUser(this.fooUserReference)).thenReturn(true);
        UserProperties fooUser = mock(UserProperties.class);
        when(this.xWikiUserBridge.resolveUser(this.fooUserReference)).thenReturn(fooUser);
        when(fooUser.getFirstName()).thenReturn("Foo");
        when(fooUser.getLastName()).thenReturn("Foo");
        when(this.xWikiUserBridge.getUserLogin(this.fooUserReference)).thenReturn("XWiki.Foo");

        // Bar does not exist.
        when(this.xWikiUserBridge.resolveUser("XWiki.Bar")).thenReturn(this.barUserReference);
        when(this.xWikiUserBridge.isExistingUser("XWiki.Bar")).thenReturn(false);
        when(this.xWikiUserBridge.isExistingUser(this.barUserReference)).thenReturn(false);
        when(this.xWikiUserBridge.resolveUser("Bar")).thenReturn(this.barUserReference);
        when(this.xWikiUserBridge.isExistingUser("Bar")).thenReturn(false);
    }

    @Test
    public void getActorWithAlreadyExistingActor() throws Exception
    {
        AbstractActor expectedActor = new Person().setPreferredUsername("XWiki.Foo");
        when(this.activityPubStorage.retrieveEntity("XWiki.Foo")).thenReturn(expectedActor);

        assertSame(expectedActor, this.actorHandler.getActor(this.fooUserReference));
    }

    @Test
    public void getActorWithExistingUser() throws Exception
    {
        when(this.signatureService.getPublicKeyPEM(any())).thenReturn("...");
        org.xwiki.contrib.activitypub.entities.PublicKey publicKey =
            new org.xwiki.contrib.activitypub.entities.PublicKey().setPublicKeyPem("...")
                .setId("null#main-key");
        AbstractActor expectedActor = new Person();
        expectedActor.setPreferredUsername("XWiki.Foo")
            .setPublicKey(publicKey)
            .setInbox(new ActivityPubObjectReference<Inbox>().setObject(
                new Inbox().setAttributedTo(Collections.singletonList(
                    new ActivityPubObjectReference<AbstractActor>().setObject(expectedActor)))))
            .setOutbox(new ActivityPubObjectReference<Outbox>().setObject(
                new Outbox().setAttributedTo(Collections.singletonList(
                    new ActivityPubObjectReference<AbstractActor>().setObject(expectedActor)))))
            .setFollowers(
                new ActivityPubObjectReference<OrderedCollection<AbstractActor>>().setObject(new OrderedCollection<>()))
            .setFollowing(
                new ActivityPubObjectReference<OrderedCollection<AbstractActor>>().setObject(new OrderedCollection<>()))
            .setName("Foo Foo");

        AbstractActor obtainedActor = this.actorHandler.getActor(this.fooUserReference);
        assertEquals(expectedActor, obtainedActor);
    }

    @Test
    public void getActorNotExisting()
    {
        ActivityPubException activityPubException = assertThrows(ActivityPubException.class, () -> {
            actorHandler.getActor(this.barUserReference);
        });
        assertEquals("Cannot find any user with reference [barUserReference]", activityPubException.getMessage());
    }

    @Test
    public void isExistingUser()
    {
        assertTrue(this.actorHandler.isExistingUser("Foo"));
        assertTrue(this.actorHandler.isExistingUser("XWiki.Foo"));

        assertFalse(this.actorHandler.isExistingUser("Bar"));
        assertFalse(this.actorHandler.isExistingUser("XWiki.Bar"));
    }

    @Test
    public void getXWikiUserReference() throws Exception
    {
        assertSame(this.fooUserReference,
            this.actorHandler.getXWikiUserReference(new Person().setPreferredUsername("Foo")));
        assertSame(this.fooUserReference,
            this.actorHandler.getXWikiUserReference(new Person().setPreferredUsername("XWiki.Foo")));

        assertNull(this.actorHandler.getXWikiUserReference(new Person().setPreferredUsername("Bar")));
        assertNull(this.actorHandler.getXWikiUserReference(new Person().setPreferredUsername("XWiki.Bar")));
    }

    @Test
    public void isLocalActor() throws Exception
    {
        assertTrue(this.actorHandler.isLocalActor(new Person().setPreferredUsername("Foo")));
        assertTrue(this.actorHandler.isLocalActor(new Person().setPreferredUsername("XWiki.Foo")));

        assertFalse(this.actorHandler.isLocalActor(new Person().setPreferredUsername("Bar")));
        assertFalse(this.actorHandler.isLocalActor(new Person().setPreferredUsername("XWiki.Bar")));

        URI actorId = new URI("http://myId");
        when(this.activityPubStorage.belongsToCurrentInstance(actorId)).thenReturn(true);
        assertTrue(this.actorHandler.isLocalActor(new Person().setId(actorId)));
        verify(this.activityPubStorage).belongsToCurrentInstance(actorId);
    }

    @Test
    public void getLocalActor() throws ActivityPubException
    {
        when(this.signatureService.getPublicKeyPEM(any())).thenReturn("...");
        org.xwiki.contrib.activitypub.entities.PublicKey publicKey =
            new org.xwiki.contrib.activitypub.entities.PublicKey().setPublicKeyPem("...")
                .setId("null#main-key");

        AbstractActor expectedActor = new Person();
        expectedActor.setPreferredUsername("XWiki.Foo")
            .setPublicKey(publicKey)
            .setInbox(new ActivityPubObjectReference<Inbox>().setObject(
                new Inbox().setAttributedTo(Collections.singletonList(
                    new ActivityPubObjectReference<AbstractActor>().setObject(expectedActor)))))
            .setOutbox(new ActivityPubObjectReference<Outbox>().setObject(
                new Outbox().setAttributedTo(Collections.singletonList(
                    new ActivityPubObjectReference<AbstractActor>().setObject(expectedActor)))))
            .setFollowers(
                new ActivityPubObjectReference<OrderedCollection<AbstractActor>>().setObject(new OrderedCollection<>()))
            .setFollowing(
                new ActivityPubObjectReference<OrderedCollection<AbstractActor>>().setObject(new OrderedCollection<>()))
            .setName("Foo Foo");

        assertEquals(expectedActor, this.actorHandler.getLocalActor("Foo"));
        assertEquals(expectedActor, this.actorHandler.getLocalActor("XWiki.Foo"));

        ActivityPubException activityPubException = assertThrows(ActivityPubException.class, () -> {
            actorHandler.getLocalActor("XWiki.Bar");
        });
        assertEquals("Cannot find any user with reference [barUserReference]", activityPubException.getMessage());

        activityPubException = assertThrows(ActivityPubException.class, () -> {
            actorHandler.getLocalActor("Bar");
        });
        assertEquals("Cannot find any user with reference [barUserReference]", activityPubException.getMessage());
    }

    @Test
    public void getRemoteActor() throws Exception
    {
        Person person = new Person().setPreferredUsername("Foobar");
        String remoteActorUrl = "http://www.xwiki.org/xwiki/activitypub/Person/Foobar";
        GetMethod getMethod = mock(GetMethod.class);
        when(this.activityPubClient.get(new URI(remoteActorUrl))).thenReturn(getMethod);
        when(this.jsonParser.parse(getMethod.getResponseBodyAsStream())).thenReturn(person);
        assertSame(person, this.actorHandler.getRemoteActor(remoteActorUrl));
    }

    @Test
    public void getRemoteActorWebfinger() throws Exception
    {
        JSONResourceDescriptor jrd = mock(JSONResourceDescriptor.class);
        when(jrd.getLinks())
            .thenReturn(Arrays.asList(new Link().setRel("self").setHref("http://server.com/user/test")));
        when(this.activityPubClient.get(any())).thenReturn(mock(HttpMethod.class));
        when(this.webfingerClient.get("test@server.com")).thenReturn(jrd);
        this.actorHandler.getRemoteActor("test@server.com");
        verify(this.webfingerClient).get(eq("test@server.com"));
        verify(this.activityPubClient).get(new URI("http://server.com/user/test"));
    }

    @Test
    public void getRemoteActorWithFallback() throws Exception
    {
        Person person = new Person().setPreferredUsername("Foobar");
        String remoteProfileUrl = "http://www.xwiki.org/xwiki/view/bin/XWiki/Foobar";
        String remoteActorUrl = "http://www.xwiki.org/xwiki/activitypub/Person/XWiki.Foobar";

        GetMethod getMethodProfile = mock(GetMethod.class);
        when(this.activityPubClient.get(new URI(remoteProfileUrl))).thenReturn(getMethodProfile);
        doThrow(new ActivityPubException("Check issue")).when(this.activityPubClient).checkAnswer(getMethodProfile);

        HttpConnection jsoupConnection = mock(HttpConnection.class);
        this.actorHandler.setJsoupConnection(jsoupConnection);
        when(jsoupConnection.url(remoteProfileUrl)).thenReturn(jsoupConnection);
        Document document = mock(Document.class);
        when(jsoupConnection.get()).thenReturn(document);
        Element element = mock(Element.class);
        when(document.selectFirst("html")).thenReturn(element);
        when(element.attr("data-xwiki-document")).thenReturn("XWiki.Foobar");

        GetMethod getMethodActor = mock(GetMethod.class);
        when(this.activityPubClient.get(new URI(remoteActorUrl))).thenReturn(getMethodActor);
        when(this.jsonParser.parse(getMethodActor.getResponseBodyAsStream())).thenReturn(person);
        assertSame(person, this.actorHandler.getRemoteActor(remoteActorUrl));
    }

    @Test
    public void getCurrentActor() throws Exception
    {
        when(this.xWikiUserBridge.isExistingUser(CurrentUserReference.INSTANCE)).thenReturn(true);
        when(this.xWikiUserBridge.getUserLogin(CurrentUserReference.INSTANCE)).thenReturn("XWiki.Foo");
        AbstractActor expectedActor = new Person().setPreferredUsername("XWiki.Foo");
        when(this.activityPubStorage.retrieveEntity("XWiki.Foo")).thenReturn(expectedActor);
        assertSame(expectedActor, this.actorHandler.getCurrentActor());
    }
}
