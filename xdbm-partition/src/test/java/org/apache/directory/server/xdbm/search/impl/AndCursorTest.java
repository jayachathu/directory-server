/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *  
 *    http://www.apache.org/licenses/LICENSE-2.0
 *  
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License. 
 *  
 */
package org.apache.directory.server.xdbm.search.impl;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.directory.server.core.api.filtering.BaseEntryFilteringCursor;
import org.apache.directory.server.core.api.interceptor.context.SearchOperationContext;
import org.apache.directory.server.core.api.interceptor.context.SearchingOperationContext;
import org.apache.directory.server.core.api.partition.Partition;
import org.apache.directory.server.core.partition.impl.avl.AvlPartition;
import org.apache.directory.server.core.partition.impl.btree.AbstractBTreePartition;
import org.apache.directory.server.core.partition.impl.btree.EntryCursorAdaptor;
import org.apache.directory.server.xdbm.ForwardIndexEntry;
import org.apache.directory.server.xdbm.IndexEntry;
import org.apache.directory.server.xdbm.Store;
import org.apache.directory.server.xdbm.StoreUtils;
import org.apache.directory.server.xdbm.impl.avl.AvlIndex;
import org.apache.directory.server.xdbm.search.Evaluator;
import org.apache.directory.server.xdbm.search.PartitionSearchResult;
import org.apache.directory.shared.ldap.model.constants.SchemaConstants;
import org.apache.directory.shared.ldap.model.cursor.Cursor;
import org.apache.directory.shared.ldap.model.cursor.InvalidCursorPositionException;
import org.apache.directory.shared.ldap.model.entry.Entry;
import org.apache.directory.shared.ldap.model.filter.ExprNode;
import org.apache.directory.shared.ldap.model.filter.FilterParser;
import org.apache.directory.shared.ldap.model.name.Dn;
import org.apache.directory.shared.ldap.model.schema.SchemaManager;
import org.apache.directory.shared.ldap.schemaextractor.SchemaLdifExtractor;
import org.apache.directory.shared.ldap.schemaextractor.impl.DefaultSchemaLdifExtractor;
import org.apache.directory.shared.ldap.schemaloader.LdifSchemaLoader;
import org.apache.directory.shared.ldap.schemamanager.impl.DefaultSchemaManager;
import org.apache.directory.shared.util.Strings;
import org.apache.directory.shared.util.exception.Exceptions;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * 
 * Test class for AndCursor.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 */
public class AndCursorTest
{
    private static final Logger LOG = LoggerFactory.getLogger( AndCursorTest.class.getSimpleName() );

    File wkdir;
    Store store;
    EvaluatorBuilder evaluatorBuilder;
    CursorBuilder cursorBuilder;
    private static SchemaManager schemaManager;


    @BeforeClass
    public static void setup() throws Exception
    {
        // setup the standard registries
        String workingDirectory = System.getProperty( "workingDirectory" );

        if ( workingDirectory == null )
        {
            String path = AndCursorTest.class.getResource( "" ).getPath();
            int targetPos = path.indexOf( "target" );
            workingDirectory = path.substring( 0, targetPos + 6 );
        }

        File schemaRepository = new File( workingDirectory, "schema" );
        SchemaLdifExtractor extractor = new DefaultSchemaLdifExtractor( new File( workingDirectory ) );
        extractor.extractOrCopy( true );
        LdifSchemaLoader loader = new LdifSchemaLoader( schemaRepository );
        schemaManager = new DefaultSchemaManager( loader );

        boolean loaded = schemaManager.loadAllEnabled();

        if ( !loaded )
        {
            fail( "Schema load failed : " + Exceptions.printErrors( schemaManager.getErrors() ) );
        }

        loaded = schemaManager.loadWithDeps( "collective" );

        if ( !loaded )
        {
            fail( "Schema load failed : " + Exceptions.printErrors( schemaManager.getErrors() ) );
        }
    }


    public AndCursorTest() throws Exception
    {
    }


    @Before
    public void createStore() throws Exception
    {
        // setup the working directory for the store
        wkdir = File.createTempFile( getClass().getSimpleName(), "db" );
        wkdir.delete();
        wkdir = new File( wkdir.getParentFile(), getClass().getSimpleName() );
        wkdir.mkdirs();

        // initialize the store
        store = new AvlPartition( schemaManager );
        ( ( Partition ) store ).setId( "example" );
        store.setCacheSize( 10 );
        store.setPartitionPath( wkdir.toURI() );
        store.setSyncOnWrite( false );

        store.addIndex( new AvlIndex( SchemaConstants.OU_AT_OID ) );
        store.addIndex( new AvlIndex( SchemaConstants.CN_AT_OID ) );
        ( ( Partition ) store ).setSuffixDn( new Dn( schemaManager, "o=Good Times Co." ) );
        ( ( Partition ) store ).initialize();

        ( ( Partition ) store ).initialize();

        StoreUtils.loadExampleData( store, schemaManager );

        evaluatorBuilder = new EvaluatorBuilder( store, schemaManager );
        cursorBuilder = new CursorBuilder( store, evaluatorBuilder );

        LOG.debug( "Created new store" );
    }


    @After
    public void destroyStore() throws Exception
    {
        if ( store != null )
        {
            ( ( Partition ) store ).destroy();
        }

        store = null;
        if ( wkdir != null )
        {
            FileUtils.deleteDirectory( wkdir );
        }

        wkdir = null;
    }


    private Cursor<Entry> buildCursor( ExprNode root ) throws Exception
    {
        Evaluator<? extends ExprNode> evaluator = evaluatorBuilder.build( root );

        PartitionSearchResult searchResult = new PartitionSearchResult();
        Set<IndexEntry<String, String>> resultSet = new HashSet<IndexEntry<String, String>>();

        Set<String> uuids = new HashSet<String>();

        long candidates = cursorBuilder.build( root, uuids );

        for ( String uuid : uuids )
        {
            ForwardIndexEntry<String, String> indexEntry = new ForwardIndexEntry<String, String>();
            indexEntry.setId( uuid );
            resultSet.add( indexEntry );
        }

        searchResult.setResultSet( resultSet );
        searchResult.setEvaluator( evaluator );

        SearchingOperationContext operationContext = new SearchOperationContext( null );

        return new BaseEntryFilteringCursor( new EntryCursorAdaptor( ( AbstractBTreePartition ) store, searchResult ),
            operationContext );
    }


    @Test
    public void testAndCursorWithCursorBuilder() throws Exception
    {
        String filter = "(&(cn=J*)(sn=*))";

        ExprNode exprNode = FilterParser.parse( schemaManager, filter );

        Cursor<Entry> cursor = buildCursor( exprNode );

        cursor.beforeFirst();

        assertTrue( cursor.next() );
        assertTrue( cursor.available() );
        Entry entry = cursor.get();
        assertEquals( Strings.getUUID( 5 ), entry.get( "entryUUID" ).getString() );
        assertEquals( "JOhnny WAlkeR", entry.get( "cn" ).getString() );

        assertTrue( cursor.next() );
        assertTrue( cursor.available() );
        entry = cursor.get();
        assertEquals( Strings.getUUID( 6 ), entry.get( "entryUUID" ).getString() );
        assertEquals( "JIM BEAN", entry.get( "cn" ).getString() );

        assertTrue( cursor.next() );
        assertTrue( cursor.available() );
        entry = cursor.get();
        assertEquals( Strings.getUUID( 8 ), entry.get( "entryUUID" ).getString() );
        assertEquals( "Jack Daniels", entry.get( "cn" ).getString() );

        assertFalse( cursor.next() );
        assertFalse( cursor.available() );

        cursor.close();
        assertTrue( cursor.isClosed() );
    }


    @Test
    public void testAndCursorWithManualFilter() throws Exception
    {
        ExprNode exprNode = FilterParser.parse( schemaManager, "(&(cn=J*)(sn=*))" );

        Cursor<Entry> cursor = buildCursor( exprNode );

        cursor.beforeFirst();

        assertTrue( cursor.next() );
        assertTrue( cursor.available() );
        Entry entry = cursor.get();
        assertEquals( Strings.getUUID( 5 ), entry.get( "entryUUID" ).getString() );
        assertEquals( "JOhnny WAlkeR", entry.get( "cn" ).getString() );

        cursor.first();

        assertTrue( cursor.next() );
        assertTrue( cursor.available() );
        entry = cursor.get();
        assertEquals( Strings.getUUID( 6 ), entry.get( "entryUUID" ).getString() );
        assertEquals( "JIM BEAN", entry.get( "cn" ).getString() );

        assertTrue( cursor.next() );
        assertTrue( cursor.available() );
        entry = cursor.get();
        assertEquals( Strings.getUUID( 8 ), entry.get( "entryUUID" ).getString() );
        assertEquals( "Jack Daniels", entry.get( "cn" ).getString() );

        assertFalse( cursor.next() );
        assertFalse( cursor.available() );

        cursor.afterLast();

        assertTrue( cursor.previous() );
        assertTrue( cursor.available() );
        entry = cursor.get();
        assertEquals( Strings.getUUID( 8 ), entry.get( "entryUUID" ).getString() );
        assertEquals( "Jack Daniels", entry.get( "cn" ).getString() );

        cursor.last();

        assertTrue( cursor.previous() );
        assertTrue( cursor.available() );
        entry = cursor.get();
        assertEquals( Strings.getUUID( 6 ), entry.get( "entryUUID" ).getString() );
        assertEquals( "JIM BEAN", entry.get( "cn" ).getString() );

        assertTrue( cursor.previous() );
        assertTrue( cursor.available() );
        entry = cursor.get();
        assertEquals( Strings.getUUID( 5 ), entry.get( "entryUUID" ).getString() );
        assertEquals( "JOhnny WAlkeR", entry.get( "cn" ).getString() );

        assertFalse( cursor.previous() );
        assertFalse( cursor.available() );

        try
        {
            cursor.get();
            fail( "should fail with InvalidCursorPositionException" );
        }
        catch ( InvalidCursorPositionException ice )
        {
        }

        cursor.close();
        assertTrue( cursor.isClosed() );
    }
}
