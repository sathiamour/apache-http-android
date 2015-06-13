/*
 * ====================================================================
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */

package original.apache.http.impl.cookie;

import java.util.ArrayList;
import java.util.List;

import original.apache.http.HeaderElement;
import original.apache.http.NameValuePair;
import original.apache.http.ParseException;
import original.apache.http.annotation.Immutable;
import original.apache.http.message.BasicHeaderElement;
import original.apache.http.message.BasicNameValuePair;
import original.apache.http.message.ParserCursor;
import original.apache.http.protocol.HTTP;
import original.apache.http.util.Args;
import original.apache.http.util.CharArrayBuffer;

/**
 *
 * @since 4.0
 */
@Immutable
public class NetscapeDraftHeaderParser {

    public final static NetscapeDraftHeaderParser DEFAULT = new NetscapeDraftHeaderParser();

    public NetscapeDraftHeaderParser() {
        super();
    }

    public HeaderElement parseHeader(
            final CharArrayBuffer buffer,
            final ParserCursor cursor) throws ParseException {
        Args.notNull(buffer, "Char array buffer");
        Args.notNull(cursor, "Parser cursor");
        final NameValuePair nvp = parseNameValuePair(buffer, cursor);
        final List<NameValuePair> params = new ArrayList<NameValuePair>();
        while (!cursor.atEnd()) {
            final NameValuePair param = parseNameValuePair(buffer, cursor);
            params.add(param);
        }
        return new BasicHeaderElement(
                nvp.getName(),
                nvp.getValue(), params.toArray(new NameValuePair[params.size()]));
    }

    private NameValuePair parseNameValuePair(
            final CharArrayBuffer buffer, final ParserCursor cursor) {
        boolean terminated = false;

        int pos = cursor.getPos();
        final int indexFrom = cursor.getPos();
        final int indexTo = cursor.getUpperBound();

        // Find name
        String name = null;
        while (pos < indexTo) {
            final char ch = buffer.charAt(pos);
            if (ch == '=') {
                break;
            }
            if (ch == ';') {
                terminated = true;
                break;
            }
            pos++;
        }

        if (pos == indexTo) {
            terminated = true;
            name = buffer.substringTrimmed(indexFrom, indexTo);
        } else {
            name = buffer.substringTrimmed(indexFrom, pos);
            pos++;
        }

        if (terminated) {
            cursor.updatePos(pos);
            return new BasicNameValuePair(name, null);
        }

        // Find value
        String value = null;
        int i1 = pos;

        while (pos < indexTo) {
            final char ch = buffer.charAt(pos);
            if (ch == ';') {
                terminated = true;
                break;
            }
            pos++;
        }

        int i2 = pos;
        // Trim leading white spaces
        while (i1 < i2 && (HTTP.isWhitespace(buffer.charAt(i1)))) {
            i1++;
        }
        // Trim trailing white spaces
        while ((i2 > i1) && (HTTP.isWhitespace(buffer.charAt(i2 - 1)))) {
            i2--;
        }
        value = buffer.substring(i1, i2);
        if (terminated) {
            pos++;
        }
        cursor.updatePos(pos);
        return new BasicNameValuePair(name, value);
    }

}