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

import java.util.Collections;
import java.util.List;

import original.apache.http.Header;
import original.apache.http.annotation.NotThreadSafe;
import original.apache.http.cookie.Cookie;
import original.apache.http.cookie.CookieOrigin;
import original.apache.http.cookie.MalformedCookieException;

/**
 * CookieSpec that ignores all cookies
 *
 * @since 4.1
 */
@NotThreadSafe // superclass is @NotThreadSafe
public class IgnoreSpec extends CookieSpecBase {

    public int getVersion() {
        return 0;
    }

    public List<Cookie> parse(final Header header, final CookieOrigin origin)
            throws MalformedCookieException {
        return Collections.emptyList();
    }

    public List<Header> formatCookies(final List<Cookie> cookies) {
        return Collections.emptyList();
    }

    public Header getVersionHeader() {
        return null;
    }
}
