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
package com.qwazr.tools.test;

import com.qwazr.tools.ToolsManager;
import com.qwazr.tools.ToolsManagerImpl;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Executors;

public abstract class AbstractToolsTest {

	final protected ToolsManager getToolManager() throws IOException {
		final ToolsManager toolsManager = ToolsManagerImpl.getInstance();
		if (toolsManager != null)
			return toolsManager;
		ToolsManagerImpl.load(Executors.newCachedThreadPool(), new File("src/test/resources"));
		return ToolsManagerImpl.getInstance();
	}

}
