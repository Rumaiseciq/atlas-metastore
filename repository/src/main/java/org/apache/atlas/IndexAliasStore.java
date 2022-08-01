/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.atlas;

import org.apache.atlas.exception.AtlasBaseException;
import org.apache.atlas.persona.PersonaContext;
import org.apache.atlas.repository.graphdb.AtlasGraph;
import org.codehaus.jettison.json.JSONException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.io.IOException;

@Component
public interface IndexAliasStore {

    public boolean createAlias(PersonaContext personaContext) throws JSONException, IOException, AtlasException, AtlasBaseException;

    public boolean updateAlias(PersonaContext personaContext) throws JSONException, IOException, AtlasException, AtlasBaseException;

    public boolean deleteAlias(PersonaContext personaContext) throws JSONException, IOException, AtlasException, AtlasBaseException;
}
