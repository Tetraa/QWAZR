/**
 * Copyright 2014-2016 Emmanuel Keller / QWAZR
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 **/
package com.qwazr.webapps.transaction;

import javax.servlet.http.HttpSession;
import java.util.Map;

public interface WebappHttpSession extends HttpSession {

	long getCreationTime();

	String getId();

	Object getAttribute(String name);

	void setAttribute(String name, Object value);

	boolean isAttribute(String name);

	void removeAttribute(String name);

	void invalidate();

	Map<String, Object> getAttributes();

}
