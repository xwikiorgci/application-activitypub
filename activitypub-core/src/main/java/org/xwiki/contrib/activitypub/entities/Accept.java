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

import org.xwiki.stability.Unstable;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/**
 * Represents the Accept activity as defined by ActivityStream.
 *
 * @see <a href="https://www.w3.org/TR/activitystreams-vocabulary/#dfn-accept">ActivityStream Accept definition</a>
 * @version $Id$
 * @since 1.0
 */
@Unstable
@JsonDeserialize(as = Accept.class)
public class Accept extends AbstractActivity
{
}
