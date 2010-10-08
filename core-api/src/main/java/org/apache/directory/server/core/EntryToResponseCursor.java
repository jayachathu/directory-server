/*
 *   Licensed to the Apache Software Foundation (ASF) under one
 *   or more contributor license agreements.  See the NOTICE file
 *   distributed with this work for additional information
 *   regarding copyright ownership.  The ASF licenses this file
 *   to you under the Apache License, Version 2.0 (the
 *   "License"); you may not use this file except in compliance
 *   with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing,
 *   software distributed under the License is distributed on an
 *   "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *   KIND, either express or implied.  See the License for the
 *   specific language governing permissions and limitations
 *   under the License.
 *
 */

package org.apache.directory.server.core;


import java.util.Iterator;

import org.apache.directory.server.core.entry.ClonedServerEntry;
import org.apache.directory.shared.i18n.I18n;
import org.apache.directory.shared.ldap.cursor.ClosureMonitor;
import org.apache.directory.shared.ldap.cursor.Cursor;
import org.apache.directory.shared.ldap.message.ResultCodeEnum;
import org.apache.directory.shared.ldap.message.SearchResultDone;
import org.apache.directory.shared.ldap.message.SearchResultDoneImpl;
import org.apache.directory.shared.ldap.message.SearchResultEntry;
import org.apache.directory.shared.ldap.message.SearchResultEntryImpl;


/**
 * A cursor to get SearchResponses after setting the underlying cursor's
 * ServerEntry object in SearchResultEnty object
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 */
public class EntryToResponseCursor<InternalResponse> implements Cursor<InternalResponse>
{
    /** the underlying cursor */
    private Cursor<InternalResponse> wrapped;

    /** a reference to hold the SearchResultDone response */
    private SearchResultDone searchDoneResp;

    private boolean done;

    private int messageId;


    public EntryToResponseCursor( int messageId, Cursor<InternalResponse> wrapped )
    {
        this.wrapped = wrapped;
        this.messageId = messageId;
    }


    public Iterator<InternalResponse> iterator()
    {
        throw new UnsupportedOperationException();
    }


    public void after( InternalResponse resp ) throws Exception
    {
        throw new UnsupportedOperationException();
    }


    public void afterLast() throws Exception
    {
        wrapped.afterLast();
    }


    public boolean available()
    {
        return wrapped.available();
    }


    public void before( InternalResponse resp ) throws Exception
    {
        throw new UnsupportedOperationException();
    }


    public void beforeFirst() throws Exception
    {
        wrapped.beforeFirst();
    }


    public void close() throws Exception
    {
        wrapped.close();
    }


    public void close( Exception e ) throws Exception
    {
        wrapped.close( e );
    }


    public boolean first() throws Exception
    {
        return wrapped.first();
    }


    public InternalResponse get() throws Exception
    {
        ClonedServerEntry entry = ( ClonedServerEntry ) wrapped.get();
        SearchResultEntry se = new SearchResultEntryImpl( messageId );
        se.setEntry( entry );

        return ( InternalResponse ) se;
    }


    /**
     * gives the SearchResultDone message received at the end of search results
     *
     * @return the SearchResultDone message, null if the search operation fails for any reason
     */
    public SearchResultDone getSearchDone()
    {
        return searchDoneResp;
    }


    public boolean isClosed() throws Exception
    {
        return wrapped.isClosed();
    }


    public boolean isElementReused()
    {
        return wrapped.isElementReused();
    }


    public boolean last() throws Exception
    {
        return wrapped.last();
    }


    public boolean next() throws Exception
    {
        done = wrapped.next();

        if ( !done )
        {
            searchDoneResp = new SearchResultDoneImpl( messageId );
            searchDoneResp.getLdapResult().setResultCode( ResultCodeEnum.SUCCESS );
        }

        return done;
    }


    public boolean previous() throws Exception
    {
        return wrapped.previous();
    }


    public void setClosureMonitor( ClosureMonitor monitor )
    {
        wrapped.setClosureMonitor( monitor );
    }


    /**
     * {@inheritDoc}
     */
    public boolean isAfterLast() throws Exception
    {
        throw new UnsupportedOperationException( I18n.err( I18n.ERR_02014_UNSUPPORTED_OPERATION, getClass().getName()
            .concat( "." ).concat( "isAfterLast()" ) ) );
    }


    /**
     * {@inheritDoc}
     */
    public boolean isBeforeFirst() throws Exception
    {
        throw new UnsupportedOperationException( I18n.err( I18n.ERR_02014_UNSUPPORTED_OPERATION, getClass().getName()
            .concat( "." ).concat( "isBeforeFirst()" ) ) );
    }


    /**
     * {@inheritDoc}
     */
    public boolean isFirst() throws Exception
    {
        throw new UnsupportedOperationException( I18n.err( I18n.ERR_02014_UNSUPPORTED_OPERATION, getClass().getName()
            .concat( "." ).concat( "isFirst()" ) ) );
    }


    /**
     * {@inheritDoc}
     */
    public boolean isLast() throws Exception
    {
        throw new UnsupportedOperationException( I18n.err( I18n.ERR_02014_UNSUPPORTED_OPERATION, getClass().getName()
            .concat( "." ).concat( "isLast()" ) ) );
    }
}