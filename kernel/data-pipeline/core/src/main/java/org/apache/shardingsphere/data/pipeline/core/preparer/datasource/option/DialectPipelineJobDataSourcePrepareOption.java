/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.shardingsphere.data.pipeline.core.preparer.datasource.option;

import org.apache.shardingsphere.infra.database.core.spi.DatabaseTypedSPI;
import org.apache.shardingsphere.infra.spi.annotation.SingletonSPI;

import java.util.Collection;

/**
 * Dialect pipeline job data source prepare option.
 */
@SingletonSPI
public interface DialectPipelineJobDataSourcePrepareOption extends DatabaseTypedSPI {
    
    /**
     * Is support if not exists on create schema SQL.
     * 
     * @return supported or not
     */
    boolean isSupportIfNotExistsOnCreateSchema();
    
    /**
     * Get ignored exception messages.
     *
     * @return ignored exception messages
     */
    Collection<String> getIgnoredExceptionMessages();
}
