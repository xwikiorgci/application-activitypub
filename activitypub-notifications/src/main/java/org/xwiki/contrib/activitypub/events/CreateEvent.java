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
package org.xwiki.contrib.activitypub.events;

import java.util.Set;

import org.xwiki.contrib.activitypub.entities.Create;
import org.xwiki.stability.Unstable;

/**
 * A specific event type for Create activities that concerns every other type than Notes.
 * Create activities about Notes are handled by a {@link MessageEvent}.
 *
 * @since 1.1
 * @version $Id$
 */
@Unstable
public class CreateEvent extends AbstractActivityPubEvent<Create>
{
    /**
     * Default name for those events.
     */
    public static final String EVENT_TYPE = "activitypub.create";

    /**
     * Default constructor.
     *
     * @param activity the activity to notify about
     * @param target the serialized references of users to notify to
     */
    public CreateEvent(Create activity, Set<String> target)
    {
        super(activity, target);
    }

    @Override
    public String getType()
    {
        return EVENT_TYPE;
    }

    @Override
    public boolean matches(Object otherEvent)
    {
        return otherEvent instanceof CreateEvent;
    }
}
