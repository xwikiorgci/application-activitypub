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
package org.xwiki.contrib.activitypub.internal.json;

import java.io.IOException;
import java.net.URI;

import javax.inject.Inject;

import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.activitypub.ActivityPubException;
import org.xwiki.contrib.activitypub.ActivityPubStore;
import org.xwiki.contrib.activitypub.entities.ActivityPubObject;
import org.xwiki.contrib.activitypub.entities.ActivityPubObjectReference;
import org.xwiki.contrib.activitypub.ActivityPubResourceReference;
import org.xwiki.resource.ResourceReferenceSerializer;
import org.xwiki.resource.SerializeResourceReferenceException;
import org.xwiki.resource.UnsupportedResourceReferenceException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

@Component(roles = ActivityPubObjectReferenceSerializer.class)
public class ActivityPubObjectReferenceSerializer extends JsonSerializer<ActivityPubObjectReference>
{
    @Inject
    private ActivityPubStore activityPubStore;

    @Inject
    private ResourceReferenceSerializer<ActivityPubResourceReference, URI> activityPubResourceReferenceSerializer;

    @Override
    public void serialize(ActivityPubObjectReference objectReference, JsonGenerator jsonGenerator,
        SerializerProvider serializerProvider) throws IOException
    {
        if (objectReference.isLink()) {
            jsonGenerator.writeString(objectReference.getLink().toASCIIString());
        } else {
            ActivityPubObject object = objectReference.getObject();

            // the ID wasn't null, but for some reason it wasn't a link, we kept it as an object in the serialization.
            if (object.getId() != null) {
                jsonGenerator.writeString(object.getId().toString());
            // it doesn't have an ID: we need to store it and we serialize it as a link to avoid big JSON answers.
            } else {
                try {
                    String uuid = this.activityPubStore.storeEntity(object);
                    ActivityPubResourceReference resourceReference =
                        new ActivityPubResourceReference(object.getType(), uuid);
                    URI uri = this.activityPubResourceReferenceSerializer.serialize(resourceReference);
                    jsonGenerator.writeString(uri.toString());
                } catch (SerializeResourceReferenceException|
                    UnsupportedResourceReferenceException|ActivityPubException e) {
                    throw new IOException(String.format("Error when serializing [%s]", object.toString()), e);
                }
            }
        }
    }
}