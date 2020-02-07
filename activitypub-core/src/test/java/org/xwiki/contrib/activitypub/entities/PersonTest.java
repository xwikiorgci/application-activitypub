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
package org.xwiki.contrib.activitypub.entities;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PersonTest extends AbstractEntityTest
{
    @Test
    public void serializePerson1() throws URISyntaxException, IOException
    {
        Person person = new Person()
            .setPreferredUsername("Foo bar")
            .setId(new URI("http://www.xwiki.org/wiki/activitypub/Foo"))
            .setName("XWiki.Foo");
        String expectedPerson = this.readResource("person/person1.json");
        String serializedPerson = this.serializer.serialize(person);
        assertEquals(expectedPerson, serializedPerson);
    }

    @Test
    public void parsePerson1() throws FileNotFoundException, URISyntaxException
    {
        Person person = new Person()
            .setPreferredUsername("Foo bar")
            .setId(new URI("http://www.xwiki.org/wiki/activitypub/Foo"))
            .setName("XWiki.Foo");

        String personJson = this.readResource("person/person1.json");
        Person obtainedPerson = this.parser.parseRequest(personJson, Person.class);
        assertEquals(person, obtainedPerson);

        Actor obtainedActor = this.parser.parseRequest(personJson, Actor.class);
        assertEquals(person, obtainedActor);

        obtainedPerson = this.parser.parseRequest(personJson);
        assertEquals(person, obtainedPerson);
    }

    @Test
    public void parsePerson2() throws URISyntaxException, IOException
    {
        Person person = new Person()
            .setPreferredUsername("alyssa")
            .setInbox(new ActivityPubObjectReference<Inbox>()
                .setLink(true)
                .setLink(new URI("https://social.example/alyssa/inbox/")))
            .setOutbox(new ActivityPubObjectReference<Outbox>()
                .setLink(true)
                .setLink(new URI("https://social.example/alyssa/outbox/")))
            .setFollowers(new ActivityPubObjectReference<OrderedCollection>()
                .setLink(true)
                .setLink(new URI("https://social.example/alyssa/followers/")))
            .setFollowing(new ActivityPubObjectReference<OrderedCollection>()
                .setLink(true)
                .setLink(new URI("https://social.example/alyssa/following/")))
            .setId(new URI("https://social.example/alyssa/"))
            .setName("Alyssa P. Hacker")
            .setSummary("Lisp enthusiast hailing from MIT");

        String personJson = this.readResource("person/person2.json");
        Person obtainedPerson = this.parser.parseRequest(personJson, Person.class);
        assertEquals(person, obtainedPerson);

        Actor obtainedActor = this.parser.parseRequest(personJson, Actor.class);
        assertEquals(person, obtainedActor);

        obtainedPerson = this.parser.parseRequest(personJson);
        assertEquals(person, obtainedPerson);
    }
}