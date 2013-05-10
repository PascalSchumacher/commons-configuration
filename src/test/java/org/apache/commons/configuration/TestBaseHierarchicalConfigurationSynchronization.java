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
package org.apache.commons.configuration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.List;

import org.apache.commons.configuration.SynchronizerTestImpl.Methods;
import org.apache.commons.configuration.io.FileHandler;
import org.apache.commons.configuration.tree.DefaultConfigurationNode;
import org.junit.Before;
import org.junit.Test;

/**
 * A test class for {@code BaseHierarchicalConfiguration} which checks whether
 * the Synchronizer is called correctly by the methods specific for hierarchical
 * configurations.
 *
 * @version $Id: $
 */
public class TestBaseHierarchicalConfigurationSynchronization
{
    /** The test synchronizer. */
    private SynchronizerTestImpl sync;

    /** The test configuration. */
    private BaseHierarchicalConfiguration config;

    @Before
    public void setUp() throws Exception
    {
        XMLConfiguration c = new XMLConfiguration();
        new FileHandler(c).load(ConfigurationAssert.getTestFile("test.xml"));
        sync = new SynchronizerTestImpl();
        c.setSynchronizer(sync);
        config = c;
    }

    /**
     * Tests whether getMaxIndex() is correctly synchronized.
     */
    @Test
    public void testGetMaxIndexSynchronized()
    {
        assertTrue("Wrong max index", config.getMaxIndex("list.item") > 0);
        sync.verify(Methods.BEGIN_READ, Methods.END_READ);
    }

    /**
     * Tests whether getRootElementName() is correctly synchronized.
     */
    @Test
    public void testGetRootElementNameSynchronized()
    {
        assertEquals("Wrong root element name", "testconfig",
                config.getRootElementName());
        sync.verify(Methods.BEGIN_READ, Methods.END_READ);
    }

    /**
     * Tests whether clone() is correctly synchronized.
     */
    @Test
    public void testCloneSynchronized()
    {
        BaseHierarchicalConfiguration clone =
                (BaseHierarchicalConfiguration) config.clone();
        sync.verify(Methods.BEGIN_READ, Methods.END_READ);
        assertNotSame("Synchronizer was not cloned", config.getSynchronizer(),
                clone.getSynchronizer());
    }

    /**
     * Tests whether addNodes() is correctly synchronized.
     */
    @Test
    public void testAddNodesSynchronized()
    {
        DefaultConfigurationNode node =
                new DefaultConfigurationNode("newNode", "true");
        config.addNodes("test.addNodes", Collections.singleton(node));
        sync.verify(Methods.BEGIN_WRITE, Methods.END_WRITE);
    }

    /**
     * Tests whether clearTree() is correctly synchronized.
     */
    @Test
    public void testClearTreeSynchronized()
    {
        config.clearTree("clear");
        sync.verify(Methods.BEGIN_WRITE, Methods.END_WRITE);
    }

    /**
     * Tests whether setRootNode() is correctly synchronized.
     */
    @Test
    public void testSetRootNodeSynchronized()
    {
        config.setRootNode(new DefaultConfigurationNode("testRoot"));
        sync.verify(Methods.BEGIN_WRITE, Methods.END_WRITE);
    }

    /**
     * Tests whether synchronization is performed when copying a configuration.
     */
    @Test
    public void testCopyConstructorSynchronized()
    {
        BaseHierarchicalConfiguration copy =
                new BaseHierarchicalConfiguration(config);
        sync.verify(Methods.BEGIN_READ, Methods.END_READ);
        assertNotSame("Synchronizer was copied", sync, copy.getSynchronizer());
    }

    /**
     * Tests whether synchronization is performed when constructing a
     * SubnodeConfiguration.
     */
    @Test
    public void testConfigurationAtSynchronized()
    {
        SubnodeConfiguration sub = config.configurationAt("element2");
        assertEquals("Wrong property", "I'm complex!",
                sub.getString("subelement.subsubelement"));
        sync.verify(Methods.BEGIN_WRITE, Methods.END_WRITE, Methods.BEGIN_READ,
                Methods.END_READ);
    }

    /**
     * Tests whether synchronization is performed when constructing multiple
     * SubnodeConfiguration objects.
     */
    @Test
    public void testConfigurationsAtSynchronized()
    {
        List<SubnodeConfiguration> subs = config.configurationsAt("list.item");
        assertFalse("No subnode configurations", subs.isEmpty());
        sync.verify(Methods.BEGIN_WRITE, Methods.END_WRITE);
    }

    /**
     * Tests whether childConfigurationsAt() is correctly synchronized.
     */
    @Test
    public void testChildConfigurationsAtSynchronized()
    {
        List<SubnodeConfiguration> subs = config.childConfigurationsAt("clear");
        assertFalse("No subnode configurations", subs.isEmpty());
        sync.verify(Methods.BEGIN_WRITE, Methods.END_WRITE);
    }

    /**
     * Tests whether synchronization is performed when setting the key of a
     * SubnodeConfiguration.
     */
    @Test
    public void testSetSubnodeKeySynchronized()
    {
        SubnodeConfiguration sub = config.configurationAt("element2");
        assertNull("Got a subnode key", sub.getSubnodeKey());
        sub.setSubnodeKey("element2");
        // 1 x configurationAt(), 1 x getSubnodeKey(), 1 x setSubnodeKey()
        sync.verify(Methods.BEGIN_WRITE, Methods.END_WRITE, Methods.BEGIN_READ,
                Methods.END_READ, Methods.BEGIN_WRITE, Methods.END_WRITE);
    }

    /**
     * Tests whether synchronization is performed when querying the key of a
     * SubnodeConfiguration.
     */
    @Test
    public void testGetSubnodeKeySynchronized()
    {
        SubnodeConfiguration sub = config.configurationAt("element2", true);
        assertEquals("Wrong subnode key", "element2", sub.getSubnodeKey());
        // 1 x configurationAt(), 1 x getSubnodeKey()
        sync.verify(Methods.BEGIN_WRITE, Methods.END_WRITE,
                Methods.BEGIN_READ, Methods.END_READ);
    }

    /**
     * Tests whether updates on nodes are communicated to all
     * SubnodeConfigurations of a configuration.
     */
    @Test
    public void testSubnodeUpdate()
    {
        config.addProperty("element2.test", Boolean.TRUE);
        SubnodeConfiguration sub = config.configurationAt("element2", true);
        SubnodeConfiguration subsub = sub.configurationAt("subelement", true);
        config.clearTree("element2.subelement");
        assertNotNull("Sub1 detached", sub.getSubnodeKey());
        assertNull("Sub2 still attached", subsub.getSubnodeKey());
    }

    /**
     * Tests whether updates caused by a SubnodeConfiguration are communicated
     * to all other SubnodeConfigurations.
     */
    @Test
    public void testSubnodeUpdateBySubnode()
    {
        SubnodeConfiguration sub = config.configurationAt("element2", true);
        SubnodeConfiguration subsub = sub.configurationAt("subelement", true);
        SubnodeConfiguration sub2 =
                config.configurationAt("element2.subelement", true);
        sub.clearTree("subelement");
        // 3 x configurationAt(), 1 x clearTree()
        sync.verify(Methods.BEGIN_WRITE, Methods.END_WRITE,
                Methods.BEGIN_WRITE, Methods.END_WRITE, Methods.BEGIN_WRITE,
                Methods.END_WRITE, Methods.BEGIN_WRITE, Methods.END_WRITE);
        assertNull("Sub2 still attached", sub2.getSubnodeKey());
        assertNull("Subsub still attached", subsub.getSubnodeKey());
    }

    /**
     * Tests whether a SubnodeConfiguration's clearAndDetachFromParent() method
     * is correctly synchronized.
     */
    @Test
    public void testSubnodeClearAndDetachFromParentSynchronized()
    {
        SubnodeConfiguration sub = config.configurationAt("element2", true);
        sub.clearAndDetachFromParent();
        assertFalse("Node not removed", config.containsKey("element2"));
        // configurationAt() + clearTree() + containsKey()
        sync.verify(Methods.BEGIN_WRITE, Methods.END_WRITE,
                Methods.BEGIN_WRITE, Methods.END_WRITE, Methods.BEGIN_READ,
                Methods.END_READ);
    }
}
