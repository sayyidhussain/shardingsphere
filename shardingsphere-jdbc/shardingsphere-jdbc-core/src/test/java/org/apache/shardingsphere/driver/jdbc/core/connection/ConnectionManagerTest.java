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

package org.apache.shardingsphere.driver.jdbc.core.connection;

import com.google.common.collect.Sets;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.shardingsphere.infra.config.datasource.DataSourceProperties;
import org.apache.shardingsphere.infra.config.datasource.creator.DataSourcePoolCreatorUtil;
import org.apache.shardingsphere.infra.database.DefaultSchema;
import org.apache.shardingsphere.infra.executor.sql.execute.engine.ConnectionMode;
import org.apache.shardingsphere.infra.instance.ComputeNodeInstance;
import org.apache.shardingsphere.infra.instance.InstanceDefinition;
import org.apache.shardingsphere.infra.instance.InstanceType;
import org.apache.shardingsphere.infra.metadata.user.ShardingSphereUser;
import org.apache.shardingsphere.mode.manager.ContextManager;
import org.apache.shardingsphere.mode.metadata.persist.MetaDataPersistService;
import org.apache.shardingsphere.traffic.rule.TrafficRule;
import org.apache.shardingsphere.transaction.rule.TransactionRule;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

public final class ConnectionManagerTest {
    
    private ConnectionManager connectionManager;
    
    private MockedStatic<DataSourcePoolCreatorUtil> dataSourcePoolCreatorUtil;
    
    @Before
    public void setUp() throws SQLException {
        connectionManager = new ConnectionManager(DefaultSchema.LOGIC_NAME, mockContextManager());
    }
    
    @After
    public void cleanUp() {
        dataSourcePoolCreatorUtil.close();
    }
    
    private ContextManager mockContextManager() throws SQLException {
        ContextManager result = mock(ContextManager.class, RETURNS_DEEP_STUBS);
        Map<String, DataSource> dataSourceMap = mockDataSourceMap();
        TrafficRule trafficRule = mockTrafficRule();
        MetaDataPersistService metaDataPersistService = mockMetaDataPersistService();
        when(result.getDataSourceMap(DefaultSchema.LOGIC_NAME)).thenReturn(dataSourceMap);
        when(result.getMetaDataContexts().getMetaDataPersistService()).thenReturn(Optional.of(metaDataPersistService));
        when(result.getMetaDataContexts().getGlobalRuleMetaData().findSingleRule(TransactionRule.class)).thenReturn(Optional.empty());
        when(result.getMetaDataContexts().getGlobalRuleMetaData().findSingleRule(TrafficRule.class)).thenReturn(Optional.of(trafficRule));
        dataSourcePoolCreatorUtil = mockStatic(DataSourcePoolCreatorUtil.class);
        Map<String, DataSource> trafficDataSourceMap = mockTrafficDataSourceMap();
        when(DataSourcePoolCreatorUtil.getDataSourceMap(any())).thenReturn(trafficDataSourceMap);
        return result;
    }
    
    private Map<String, DataSource> mockTrafficDataSourceMap() {
        Map<String, DataSource> trafficDataSourceMap = new LinkedHashMap<>();
        trafficDataSourceMap.put("127.0.0.1@3307", mock(DataSource.class));
        return trafficDataSourceMap;
    }
    
    private MetaDataPersistService mockMetaDataPersistService() {
        MetaDataPersistService result = mock(MetaDataPersistService.class, RETURNS_DEEP_STUBS);
        when(result.getDataSourceService().load(DefaultSchema.LOGIC_NAME)).thenReturn(createDataSourcePropertiesMap());
        when(result.getComputeNodePersistService().loadComputeNodeInstances(InstanceType.PROXY, Arrays.asList("OLTP", "OLAP"))).thenReturn(Collections.singletonList(mockComputeNodeInstance()));
        when(result.getGlobalRuleService().loadUsers()).thenReturn(Collections.singletonList(new ShardingSphereUser("root", "root", "localhost")));
        return result;
    }
    
    private Map<String, DataSourceProperties> createDataSourcePropertiesMap() {
        Map<String, DataSourceProperties> result = new LinkedHashMap<>();
        DataSourceProperties dataSourceProps = new DataSourceProperties(HikariDataSource.class.getName());
        result.put(DefaultSchema.LOGIC_NAME, dataSourceProps);
        dataSourceProps.getProps().put("jdbcUrl", "jdbc:mysql://127.0.0.1:3306/demo_ds_0?serverTimezone=UTC&useSSL=false");
        dataSourceProps.getProps().put("username", "root");
        dataSourceProps.getProps().put("password", "123456");
        return result;
    }
    
    private ComputeNodeInstance mockComputeNodeInstance() {
        ComputeNodeInstance result = new ComputeNodeInstance();
        result.setLabels(Collections.singletonList("OLTP"));
        result.setInstanceDefinition(new InstanceDefinition(InstanceType.PROXY, "127.0.0.1@3307"));
        return result;
    }
    
    private TrafficRule mockTrafficRule() {
        TrafficRule result = mock(TrafficRule.class);
        when(result.getLabels()).thenReturn(Arrays.asList("OLTP", "OLAP"));
        return result;
    }
    
    private Map<String, DataSource> mockDataSourceMap() throws SQLException {
        Map<String, DataSource> result = new HashMap<>(2, 1);
        result.put("ds", mock(DataSource.class, RETURNS_DEEP_STUBS));
        DataSource invalidDataSource = mock(DataSource.class);
        when(invalidDataSource.getConnection()).thenThrow(new SQLException());
        result.put("invalid_ds", invalidDataSource);
        return result;
    }
    
    @Test
    public void assertGetRandomPhysicalDataSourceNameFromContextManager() {
        String actual = connectionManager.getRandomPhysicalDataSourceName();
        assertTrue(Sets.newHashSet("ds", "invalid_ds").contains(actual));
    }
    
    @Test
    public void assertGetRandomPhysicalDataSourceNameFromCache() throws SQLException {
        connectionManager.getConnections("ds", 1, ConnectionMode.MEMORY_STRICTLY);
        String actual = connectionManager.getRandomPhysicalDataSourceName();
        assertThat(actual, is("ds"));
    }
    
    @Test
    public void assertGetConnection() throws SQLException {
        assertThat(connectionManager.getConnections("ds", 1, ConnectionMode.MEMORY_STRICTLY),
                is(connectionManager.getConnections("ds", 1, ConnectionMode.MEMORY_STRICTLY)));
    }
    
    @Test
    public void assertGetConnectionWhenConfigTrafficRule() throws SQLException {
        assertThat(connectionManager.getConnections("127.0.0.1@3307", 1, ConnectionMode.MEMORY_STRICTLY),
                is(connectionManager.getConnections("127.0.0.1@3307", 1, ConnectionMode.MEMORY_STRICTLY)));
    }
    
    @Test
    public void assertGetConnectionsWhenAllInCache() throws SQLException {
        Connection expected = connectionManager.getConnections("ds", 1, ConnectionMode.MEMORY_STRICTLY).get(0);
        List<Connection> actual = connectionManager.getConnections("ds", 1, ConnectionMode.CONNECTION_STRICTLY);
        assertThat(actual.size(), is(1));
        assertThat(actual.get(0), is(expected));
    }
    
    @Test
    public void assertGetConnectionsWhenConfigTrafficRuleAndAllInCache() throws SQLException {
        Connection expected = connectionManager.getConnections("127.0.0.1@3307", 1, ConnectionMode.MEMORY_STRICTLY).get(0);
        List<Connection> actual = connectionManager.getConnections("127.0.0.1@3307", 1, ConnectionMode.CONNECTION_STRICTLY);
        assertThat(actual.size(), is(1));
        assertThat(actual.get(0), is(expected));
    }
    
    @Test
    public void assertGetConnectionsWhenEmptyCache() throws SQLException {
        List<Connection> actual = connectionManager.getConnections("ds", 1, ConnectionMode.MEMORY_STRICTLY);
        assertThat(actual.size(), is(1));
    }
    
    @Test
    public void assertGetConnectionsWhenConfigTrafficRuleAndEmptyCache() throws SQLException {
        List<Connection> actual = connectionManager.getConnections("127.0.0.1@3307", 1, ConnectionMode.MEMORY_STRICTLY);
        assertThat(actual.size(), is(1));
    }
    
    @Test
    public void assertGetConnectionsWhenPartInCacheWithMemoryStrictlyMode() throws SQLException {
        connectionManager.getConnections("ds", 1, ConnectionMode.MEMORY_STRICTLY);
        List<Connection> actual = connectionManager.getConnections("ds", 3, ConnectionMode.MEMORY_STRICTLY);
        assertThat(actual.size(), is(3));
    }
    
    @Test
    public void assertGetConnectionsWhenPartInCacheWithConnectionStrictlyMode() throws SQLException {
        connectionManager.getConnections("ds", 1, ConnectionMode.MEMORY_STRICTLY);
        List<Connection> actual = connectionManager.getConnections("ds", 3, ConnectionMode.CONNECTION_STRICTLY);
        assertThat(actual.size(), is(3));
    }
    
    @Test
    public void assertGetConnectionsWhenConnectionCreateFailed() {
        try {
            connectionManager.getConnections("invalid_ds", 3, ConnectionMode.CONNECTION_STRICTLY);
        } catch (final SQLException ex) {
            assertThat(ex.getMessage(), is("Can not get 3 connections one time, partition succeed connection(0) have released!"));
        }
    }
}
